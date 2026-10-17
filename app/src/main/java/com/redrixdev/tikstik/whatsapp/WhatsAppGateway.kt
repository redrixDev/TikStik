// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.whatsapp

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.redrixdev.tikstik.core.common.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** WhatsApp as seen from TikStik: which apps exist and whether a pack is already in each one (official WhitelistCheck.java). */
interface WhatsAppGateway {
    /** Installed and enabled: a disabled one counts as not installed, as in the official sample. */
    fun installed(): List<WhatsAppApp>

    /** What its whitelist says; null if unknown (failed query or a WhatsApp without that provider). */
    fun isAdded(app: WhatsAppApp, packId: String): Boolean?

    fun addIntent(app: WhatsAppApp, packId: String, name: String): Intent

    fun openIntent(app: WhatsAppApp): Intent?
}

class AndroidWhatsApp @Inject constructor(@param:ApplicationContext private val context: Context) : WhatsAppGateway {

    private val authority = StickerContentProvider.authority(context.packageName)

    override fun installed(): List<WhatsAppApp> = WhatsAppApp.entries.filter { app ->
        try {
            context.packageManager.getApplicationInfo(app.packageName, 0).enabled
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    override fun isAdded(app: WhatsAppApp, packId: String): Boolean? {
        val whitelist = app.packageName + WHITELIST_PROVIDER
        if (context.packageManager.resolveContentProvider(whitelist, 0) == null) return null
        val uri = Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(whitelist).appendPath("is_whitelisted")
            .appendQueryParameter("authority", authority)
            .appendQueryParameter("identifier", packId)
            .build()
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(cursor.getColumnIndexOrThrow("result")) == 1 else null
            }
        } catch (e: RuntimeException) {
            Logger.warn(AREA, "whitelist query failed", e)
            null
        }
    }

    override fun addIntent(app: WhatsAppApp, packId: String, name: String): Intent =
        Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK")
            .setPackage(app.packageName)
            .putExtra("sticker_pack_id", packId)
            .putExtra("sticker_pack_authority", authority)
            .putExtra("sticker_pack_name", name)

    override fun openIntent(app: WhatsAppApp): Intent? = context.packageManager.getLaunchIntentForPackage(app.packageName)

    private companion object {
        const val AREA = "WhatsApp"
        const val WHITELIST_PROVIDER = ".provider.sticker_whitelist_check"
    }
}
