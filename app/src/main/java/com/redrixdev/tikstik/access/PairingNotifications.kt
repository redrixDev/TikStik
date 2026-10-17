// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.access

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.redrixdev.tikstik.MainActivity
import com.redrixdev.tikstik.R

/** A single pairing notification that changes state; the code is typed into it. */
internal object PairingNotifications {
    const val ID = 41
    const val KEY_CODE = "pairing_code"
    private const val CHANNEL_WAIT = "pairing_wait"
    private const val CHANNEL_CODE = "pairing_code"

    // The code channel too: if the user blocked only that one, there would be nowhere to type it.
    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            context.getSystemService<NotificationManager>()?.getNotificationChannel(CHANNEL_CODE)?.importance != NotificationManager.IMPORTANCE_NONE

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL_WAIT, context.getString(R.string.pair_channel_wait), NotificationManager.IMPORTANCE_LOW))
        // High importance: it shows as a heads-up over Settings, where the code is.
        manager.createNotificationChannel(NotificationChannel(CHANNEL_CODE, context.getString(R.string.pair_channel_code), NotificationManager.IMPORTANCE_HIGH))
    }

    fun waiting(context: Context): Notification = base(context, CHANNEL_WAIT)
        .setContentTitle(context.getString(R.string.pair_wait_title))
        .setContentText(context.getString(R.string.pair_wait_text))
        .setOngoing(true)
        .setSilent(true)
        .addAction(0, context.getString(R.string.action_cancel), PairingService.stopIntent(context))
        .build()

    fun askCode(context: Context, retry: Boolean): Notification {
        val input = RemoteInput.Builder(KEY_CODE).setLabel(context.getString(R.string.pair_code_label)).build()
        val action = NotificationCompat.Action.Builder(R.drawable.ic_stat_tikstik, context.getString(R.string.pair_code_action), PairingService.codeIntent(context))
            .addRemoteInput(input)
            .setAllowGeneratedReplies(false)
            .build()
        return base(context, CHANNEL_CODE)
            .setContentTitle(context.getString(if (retry) R.string.pair_code_retry else R.string.pair_code_title))
            .setContentText(context.getString(R.string.pair_code_text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setOngoing(true)
            .addAction(action)
            .build()
    }

    fun working(context: Context): Notification = base(context, CHANNEL_WAIT)
        .setContentTitle(context.getString(R.string.pair_working))
        .setProgress(0, 0, true)
        .setOngoing(true)
        .setSilent(true)
        .build()

    fun timedOut(context: Context): Notification = base(context, CHANNEL_WAIT)
        .setContentTitle(context.getString(R.string.pair_timeout_title))
        .setContentText(context.getString(R.string.pair_timeout_text))
        .setAutoCancel(true)
        .setSilent(true)
        .build()

    fun done(context: Context): Notification = base(context, CHANNEL_CODE)
        .setContentTitle(context.getString(R.string.pair_done_title))
        .setContentText(context.getString(R.string.pair_done_text))
        .setAutoCancel(true)
        .setTimeoutAfter(DONE_VISIBLE_MS)
        .build()

    fun post(context: Context, notification: Notification) {
        if (!canPost(context)) return
        try {
            NotificationManagerCompat.from(context).notify(ID, notification)
        } catch (_: SecurityException) {
            // The permission was revoked between the check and the notify.
        }
    }

    fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(ID)

    private fun base(context: Context, channel: String) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_stat_tikstik)
        .setColor(ContextCompat.getColor(context, R.color.brand_green))
        .setContentIntent(
            PendingIntent.getActivity(
                context,
                1,
                // Brings TikStik's task to the front with the connect screen; Settings stays in its own.
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                // Intent flags are ignored when a PendingIntent is reused: this way an old one is never launched.
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT,
            ),
        )

    private const val DONE_VISIBLE_MS = 60_000L
}
