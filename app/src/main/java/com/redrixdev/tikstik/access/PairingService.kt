// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.access

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.RemoteInput
import androidx.core.app.ServiceCompat
import com.redrixdev.tikstik.access.PairingState.Phase
import com.redrixdev.tikstik.core.common.IoDispatcher
import com.redrixdev.tikstik.core.common.Logger
import dagger.hilt.android.AndroidEntryPoint
import io.github.muntashirakon.adb.android.AdbMdns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.InetAddress
import javax.inject.Inject

// "Pair with code" only announces its port over mDNS while it is open: when it appears, the code is asked on top of it.
@AndroidEntryPoint
class PairingService : Service() {

    @Inject lateinit var access: AdbAccess

    @Inject lateinit var state: PairingState

    @Inject @IoDispatcher lateinit var io: CoroutineDispatcher

    private val scope by lazy { CoroutineScope(SupervisorJob() + io) }
    private var mdns: AdbMdns? = null
    private var limit: Job? = null

    @Volatile private var target: Pair<String, Int>? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stop()
            ACTION_CODE -> {
                // The process may have died since the notice went out: wait for the dialog again.
                if (mdns == null) start()
                onCode(RemoteInput.getResultsFromIntent(intent)?.getCharSequence(PairingNotifications.KEY_CODE)?.filter(Char::isDigit)?.toString().orEmpty())
            }
            else -> start()
        }
        return START_NOT_STICKY
    }

    // Calling again with the app visible extends the shortService deadline by another 3 minutes.
    private fun start() {
        PairingNotifications.ensureChannels(this)
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE else 0
        val notification = if (target != null) PairingNotifications.askCode(this, retry = false) else PairingNotifications.waiting(this)
        ServiceCompat.startForeground(this, PairingNotifications.ID, notification, type)
        if (state.phase.value in RESTARTABLE) state.set(Phase.WAITING_FOR_DIALOG)
        discover()
        limit?.cancel()
        limit = scope.launch {
            delay(MAX_WAIT_MS)
            timedOut()
        }
    }

    private fun discover() {
        if (mdns != null) return
        mdns = AdbMdns(this, AdbMdns.SERVICE_TYPE_TLS_PAIRING) { host: InetAddress?, port: Int ->
            if (port > 0) {
                target = (host?.hostAddress ?: LOCALHOST) to port
                state.set(Phase.WAITING_FOR_CODE)
                PairingNotifications.post(this, PairingNotifications.askCode(this, retry = false))
            } else {
                target = null
                state.set(Phase.WAITING_FOR_DIALOG)
                PairingNotifications.post(this, PairingNotifications.waiting(this))
            }
        }.also { it.start() }
    }

    private fun onCode(code: String) {
        val found = target
        if (code.length != CODE_LENGTH || found == null) {
            PairingNotifications.post(this, if (found == null) PairingNotifications.waiting(this) else PairingNotifications.askCode(this, retry = true))
            return
        }
        state.set(Phase.PAIRING)
        PairingNotifications.post(this, PairingNotifications.working(this))
        scope.launch {
            val paired = try {
                access.pair(found.first, found.second, code)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Logger.warn(AREA, "pairing failed", e)
                false
            }
            if (paired) {
                state.set(Phase.PAIRED)
                // While still posted as the service's notification, Android strips its FLAG_AUTO_CANCEL.
                ServiceCompat.stopForeground(this@PairingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                PairingNotifications.post(this@PairingService, PairingNotifications.done(this@PairingService))
                stopSelf()
            } else {
                state.set(if (target == null) Phase.WAITING_FOR_DIALOG else Phase.WAITING_FOR_CODE)
                PairingNotifications.post(
                    this@PairingService,
                    if (target == null) PairingNotifications.waiting(this@PairingService) else PairingNotifications.askCode(this@PairingService, retry = true),
                )
            }
        }
    }

    // shortService deadline reached: stop right away or Android raises an ANR.
    override fun onTimeout(startId: Int, fgsType: Int) = timedOut()

    // Android 14 calls this one; from 15 the system calls both, so timedOut acts only once.
    @Deprecated("The Android 14 overload; from 15 the one with the type is called as well.")
    override fun onTimeout(startId: Int) = timedOut()

    private fun timedOut() {
        if (state.phase.value == Phase.TIMED_OUT) return
        state.set(Phase.TIMED_OUT)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        PairingNotifications.post(this, PairingNotifications.timedOut(this))
        stopSelf()
    }

    private fun stop() {
        if (state.phase.value != Phase.PAIRED) state.set(Phase.IDLE)
        PairingNotifications.cancel(this)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { mdns?.stop() }
        mdns = null
        scope.cancel()
        if (state.phase.value != Phase.PAIRED && state.phase.value != Phase.TIMED_OUT) state.set(Phase.IDLE)
        super.onDestroy()
    }

    companion object {
        private const val AREA = "Pairing"
        private const val ACTION_CODE = "com.redrixdev.tikstik.action.PAIRING_CODE"
        private const val ACTION_STOP = "com.redrixdev.tikstik.action.PAIRING_STOP"
        private const val CODE_LENGTH = 6
        private const val LOCALHOST = "127.0.0.1"
        private val RESTARTABLE = setOf(Phase.IDLE, Phase.PAIRED, Phase.TIMED_OUT)

        // Android 13 and earlier have no system deadline: set our own.
        private const val MAX_WAIT_MS = 10 * 60_000L

        /** Starts waiting for the pairing dialog, or extends the deadline if already waiting; only with the app visible. */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, PairingService::class.java))
        }

        internal fun codeIntent(context: Context): PendingIntent = PendingIntent.getService(
            context,
            2,
            Intent(context, PairingService::class.java).setAction(ACTION_CODE),
            // Mutable: RemoteInput writes the code into the intent.
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
        )

        internal fun stopIntent(context: Context): PendingIntent = PendingIntent.getService(
            context,
            3,
            Intent(context, PairingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
