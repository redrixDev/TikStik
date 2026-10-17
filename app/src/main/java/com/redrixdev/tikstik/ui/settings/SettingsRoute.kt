// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.ui.info.InfoPage

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onConnectTikTok: () -> Unit,
    onOpenInfo: (InfoPage) -> Unit,
    onOpenHowTo: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Donations: the address lives in the strings so a fork can change it; empty, the card does not exist.
    val donate = stringResource(R.string.donate_url)
    val policy = stringResource(R.string.privacy_policy_url)
    val google = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onAuthResult(result.resultCode == Activity.RESULT_OK, result.data)
    }
    LaunchedEffect(viewModel) {
        viewModel.authRequests.collect { intent ->
            try {
                google.launch(IntentSenderRequest.Builder(intent).build())
            } catch (_: ActivityNotFoundException) {
                viewModel.onAuthResult(false, null)
            }
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.openAppSettings.collect {
            runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) }
        }
    }
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    SettingsScreen(
        state = state,
        onBack = onBack,
        onConnectTikTok = onConnectTikTok,
        onDisconnectTikTok = viewModel::disconnectTikTok,
        onSetPackBaseName = viewModel::setPackBaseName,
        onOpenInfo = onOpenInfo,
        onOpenHowTo = onOpenHowTo,
        onPrivacyPolicy = if (policy.isBlank()) null else { { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, policy.toUri())) } } },
        onSupport = if (donate.isBlank()) null else { { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, donate.toUri())) } } },
        onSignIn = viewModel::signIn,
        onBackupNow = viewModel::backupNow,
        onSignOut = viewModel::signOut,
        onConfirmMerge = viewModel::confirmMerge,
        onCancelMerge = viewModel::cancelMerge,
        onSetAutoUpdate = viewModel::setAutoUpdate,
        onCheckUpdates = viewModel::checkForUpdates,
        onShowUpdate = viewModel::showUpdate,
    )
}
