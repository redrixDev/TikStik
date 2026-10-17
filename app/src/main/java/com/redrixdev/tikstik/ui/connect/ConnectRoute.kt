// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.connect

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.access.AndroidDeviceState
import com.redrixdev.tikstik.access.PairingNotifications
import com.redrixdev.tikstik.access.PairingService
import com.redrixdev.tikstik.access.PairingState.Phase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ConnectRoute(onBack: () -> Unit, onDone: () -> Unit, viewModel: ConnectViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    fun openPairing() {
        PairingService.start(context)
        context.tryStart(AndroidDeviceState.wirelessDebuggingIntent())
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openPairing() else viewModel.onNotificationsDenied()
    }
    val activity = LocalActivity.current
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val canAskAgain = activity?.shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) == true
        viewModel.onStorageResult(granted, canAskAgain, PairingNotifications.canPost(context))
    }

    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // While visible it advances on its own: the user comes back from Settings with a step done.
            launch {
                while (true) {
                    viewModel.refresh(PairingNotifications.canPost(context))
                    delay(POLL_MS)
                }
            }
            // With the app visible, restarting the service extends the shortService deadline or resumes an interrupted wait.
            viewModel.pairingPhase.collect { phase ->
                if (phase == Phase.WAITING_FOR_DIALOG || phase == Phase.WAITING_FOR_CODE || phase == Phase.TIMED_OUT) PairingService.start(context)
            }
        }
    }

    val video = stringResource(R.string.video_connect_url)
    ConnectScreen(
        state = state,
        onBack = onBack,
        onRequestStorage = { storage.launch(Manifest.permission.READ_EXTERNAL_STORAGE) },
        onOpenAboutPhone = { context.tryStart(AndroidDeviceState.aboutPhoneIntent()) },
        onStartPairing = {
            if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else openPairing()
        },
        onFinish = viewModel::finish,
        onDone = onDone,
        onOpenNotificationSettings = {
            context.tryStart(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        },
        onOpenHelpVideo = { context.tryStart(Intent(Intent.ACTION_VIEW, video.toUri())) },
        onOpenAppSettings = { context.tryStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) },
    )
}

private const val POLL_MS = 1_200L

private fun Context.tryStart(intent: Intent) {
    try {
        // In its own task: inside TikStik's, coming back took several Back presses or the notification.
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Some manufacturers remove the screen: the wizard keeps showing the step.
    }
}
