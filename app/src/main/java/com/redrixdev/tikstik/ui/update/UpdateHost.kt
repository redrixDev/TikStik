// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.redrixdev.tikstik.update.UpdatePhase
import java.io.File

/** Over every screen: offers the version GitHub lists and, once downloaded, hands the APK to Android's installer. */
@Composable
fun UpdateHost(viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val update = state.available ?: return
    if (update.versionName == state.skipped) return
    val context = LocalContext.current
    val phase = state.phase
    var allowed by remember { mutableStateOf(context.packageManager.canRequestPackageInstalls()) }
    // The permission can also be granted from the system settings: re-read on every return to the app.
    LifecycleResumeEffect(Unit) {
        allowed = context.packageManager.canRequestPackageInstalls()
        onPauseOrDispose { }
    }
    val allowInstalls = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        allowed = context.packageManager.canRequestPackageInstalls()
        if (allowed && phase is UpdatePhase.Ready) openInstaller(context, phase.apk)
    }
    val install: (File) -> Unit = { apk ->
        if (context.packageManager.canRequestPackageInstalls()) {
            openInstaller(context, apk)
        } else {
            // If no screen answers it, as with the installer, nothing opens and the sheet stays instead of the app closing.
            try {
                allowInstalls.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()))
            } catch (_: ActivityNotFoundException) {
            }
        }
    }
    LaunchedEffect(phase) {
        if (phase is UpdatePhase.Ready && phase.justDownloaded) {
            viewModel.installerOpened()
            install(phase.apk)
        }
    }
    UpdateSheet(
        update = update,
        phase = phase,
        installAllowed = allowed,
        onUpdate = viewModel::download,
        onInstall = { (phase as? UpdatePhase.Ready)?.let { install(it.apk) } },
        onLater = viewModel::later,
    )
}

private fun openInstaller(context: Context, apk: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // Without an installer (a restricted profile) nothing opens and the sheet stays as it is.
    runCatching { context.startActivity(intent) }
}
