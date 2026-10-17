// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.packs

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.redrixdev.tikstik.core.common.Logger
import com.redrixdev.tikstik.core.ui.Notices
import com.redrixdev.tikstik.whatsapp.WhatsAppPacks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** At the root: opens in WhatsApp what needs confirming, shows the limit sheet wherever you are and, on return, refreshes what WhatsApp has. */
@Composable
fun WhatsAppHost(whatsApp: WhatsAppPacks, notices: Notices) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // WhatsApp's reason is technical: it goes to the log, and the notice is the approved one.
        result.data?.getStringExtra(VALIDATION_ERROR)?.let {
            Logger.warn(AREA, "pack rejected", detail = it)
            notices.post(PackNotices.rejected)
        }
        scope.launch { guarded(notices) { whatsApp.onResult() } }
    }
    LaunchedEffect(whatsApp) {
        whatsApp.requests.collect { request ->
            try {
                launcher.launch(whatsApp.intent(request))
            } catch (_: ActivityNotFoundException) {
                notices.post(PackNotices.openFailed)
                guarded(notices) { whatsApp.onResult() }
            }
        }
    }
    LifecycleResumeEffect(whatsApp) {
        scope.launch { guarded(notices) { whatsApp.refresh() } }
        onPauseOrDispose { }
    }
    val limit by whatsApp.limit.collectAsStateWithLifecycle(initialValue = null)
    limit?.let { app ->
        LimitSheet(
            app = app,
            onOpenWhatsApp = { whatsApp.openIntent(app)?.let { runCatching { context.startActivity(it) } } },
            onDismiss = { scope.launch { guarded(notices) { whatsApp.dismissLimit() } } },
        )
    }
}

private const val AREA = "WhatsApp"
// The extra WhatsApp returns when rejecting a pack (official AddStickerPackActivity.java).
private const val VALIDATION_ERROR = "validation_error"

// A failure saving what WhatsApp reports must not crash the app.
private suspend fun guarded(notices: Notices, block: suspend () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.warn(AREA, "WhatsApp state not saved", e)
        notices.post(PackNotices.saveFailed)
    }
}
