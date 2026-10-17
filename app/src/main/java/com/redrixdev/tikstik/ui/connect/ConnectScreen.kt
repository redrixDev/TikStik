// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.connect

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.access.DeviceBrand
import com.redrixdev.tikstik.core.designsystem.ScreenPreview
import com.redrixdev.tikstik.core.designsystem.component.AnimatedCheck
import com.redrixdev.tikstik.core.designsystem.component.AppSheet
import com.redrixdev.tikstik.core.designsystem.component.BrandProgressBar
import com.redrixdev.tikstik.core.designsystem.component.PrimaryButton
import com.redrixdev.tikstik.core.designsystem.component.SecondaryButton
import com.redrixdev.tikstik.core.designsystem.component.TopBar
import com.redrixdev.tikstik.core.designsystem.icon.TikStikIcons
import com.redrixdev.tikstik.core.designsystem.theme.BrandGreen
import com.redrixdev.tikstik.core.designsystem.theme.BrandViolet
import com.redrixdev.tikstik.core.designsystem.theme.Motion
import com.redrixdev.tikstik.core.designsystem.theme.Surface1
import com.redrixdev.tikstik.core.designsystem.theme.Surface2
import com.redrixdev.tikstik.core.designsystem.theme.TextMid
import com.redrixdev.tikstik.core.designsystem.theme.TikStikTheme
import com.redrixdev.tikstik.core.designsystem.theme.Warning
import com.redrixdev.tikstik.core.designsystem.theme.motion
import com.redrixdev.tikstik.core.designsystem.theme.navigationBarsStable
import com.redrixdev.tikstik.core.designsystem.theme.safeDrawingStable

enum class ConnectStep { STORAGE, DEV_OPTIONS, PAIRING, FINISH, DONE }

/** Wizard state; on Android 8–10 ([legacyStorage]) there is only the permission step. */
@Immutable
data class ConnectUiState(
    val step: ConnectStep,
    val legacyStorage: Boolean = false,
    val onWifi: Boolean = true,
    val waitingForCode: Boolean = false,
    val finishing: Boolean = false,
    val notificationsEnabled: Boolean = false,
    // Ready, but without the permission: the user turns debugging on by hand.
    val manual: Boolean = false,
    val brand: DeviceBrand? = null,
    // Android 8–10: the permission was denied permanently and only the app's settings page remains.
    val storageBlocked: Boolean = false,
)

/** "Connect with TikTok" wizard: per step, an illustration of what will be seen, a sentence and a button. */
@Composable
fun ConnectScreen(
    state: ConnectUiState,
    onBack: () -> Unit,
    onRequestStorage: () -> Unit,
    onOpenAboutPhone: () -> Unit,
    onStartPairing: () -> Unit,
    onFinish: () -> Unit,
    onDone: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenHelpVideo: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val steps = if (state.legacyStorage) listOf(ConnectStep.STORAGE) else listOf(ConnectStep.DEV_OPTIONS, ConnectStep.PAIRING, ConnectStep.FINISH)
    val index = steps.indexOf(state.step).let { if (it < 0) steps.size else it }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawingStable)) {
        TopBar(title = stringResource(R.string.connect_title), onBack = onBack)
        if (steps.size > 1) StepProgress(index, steps.size, Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .align(Alignment.CenterHorizontally)
                .widthIn(max = 560.dp)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            AnimatedContent(
                targetState = state.step,
                transitionSpec = {
                    (slideInHorizontally(Motion.offset()) { it / 2 } + fadeIn()) togetherWith
                        (slideOutHorizontally(Motion.offset()) { -it / 3 } + fadeOut())
                },
                label = "step",
            ) { step ->
                when (step) {
                    ConnectStep.STORAGE -> StepCard(
                        illustration = { IconIllustration(TikStikIcons.Folder) },
                        title = stringResource(R.string.connect_storage_title),
                        body = stringResource(if (state.storageBlocked) R.string.connect_storage_blocked else R.string.connect_storage_body),
                        action = stringResource(if (state.storageBlocked) R.string.connect_dev_action else R.string.connect_storage_action),
                        onAction = if (state.storageBlocked) onOpenAppSettings else onRequestStorage,
                    )
                    ConnectStep.DEV_OPTIONS -> StepCard(
                        illustration = { DevOptionsMock() },
                        title = stringResource(R.string.connect_dev_title),
                        body = stringResource(R.string.connect_dev_body),
                        action = stringResource(R.string.connect_dev_action),
                        onAction = onOpenAboutPhone,
                        help = { HelpContent(withBrandLine(R.string.connect_dev_help, state.brand.buildHelp()), notifications = null, onOpenNotificationSettings, onOpenHelpVideo) },
                    )
                    ConnectStep.PAIRING -> StepCard(
                        illustration = { PairingMock() },
                        title = stringResource(R.string.connect_pair_title),
                        body = stringResource(R.string.connect_pair_body),
                        action = stringResource(R.string.connect_pair_action),
                        onAction = onStartPairing,
                        help = {
                            HelpContent(
                                withBrandLine(R.string.connect_pair_help, state.brand.pairHelp()),
                                state.notificationsEnabled,
                                onOpenNotificationSettings,
                                onOpenHelpVideo,
                            )
                        },
                        status = { StatusSlot(onWifi = state.onWifi, waiting = state.waitingForCode) },
                    )
                    ConnectStep.FINISH -> StepCard(
                        illustration = { IconIllustration(TikStikIcons.TaskAlt) },
                        title = stringResource(R.string.connect_finish_title),
                        body = stringResource(R.string.connect_finish_body),
                        action = stringResource(R.string.connect_finish_action),
                        onAction = onFinish,
                        loading = state.finishing,
                    )
                    ConnectStep.DONE -> Done(state.manual, state.brand.grantHelp(), onDone)
                }
            }
        }
    }
}

/** Segmented progress: finished steps full, the current one half. */
@Composable
private fun StepProgress(index: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            val fill by animateFloatAsState(if (i < index) 1f else if (i == index) 0.5f else 0f, motion(), label = "seg")
            Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(Surface2)) {
                Box(Modifier.fillMaxWidth(fill).height(6.dp).clip(CircleShape).background(BrandGreen))
            }
        }
    }
}

@Composable
private fun StepCard(
    illustration: @Composable () -> Unit,
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    help: (@Composable () -> Unit)? = null,
    status: (@Composable () -> Unit)? = null,
    loading: Boolean = false,
) {
    var showHelp by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().height(230.dp).clip(MaterialTheme.shapes.large).background(Surface1),
            contentAlignment = Alignment.BottomCenter,
        ) { illustration() }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = TextMid, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        PrimaryButton(action, onAction, Modifier.fillMaxWidth(), loading = loading)
        if (status != null) status() else Spacer(Modifier.height(8.dp))
        if (help != null) {
            // Help opens in a sheet: it does not unfold text that shifts the screen.
            Row(
                Modifier
                    .clip(CircleShape)
                    .clickable { showHelp = true }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(TikStikIcons.HelpOutline, null, tint = BrandGreen, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.connect_help_toggle), style = MaterialTheme.typography.labelLarge, color = BrandGreen)
            }
        }
    }
    if (showHelp && help != null) {
        AppSheet(onDismissRequest = { showHelp = false }) { help() }
    }
}

private enum class PairStatus { NO_WIFI, WAITING }

/** Fixed slot under the button: "You need Wi-Fi" or the wait for the code take turns without moving anything. */
@Composable
private fun StatusSlot(onWifi: Boolean, waiting: Boolean) {
    val status = when {
        !onWifi -> PairStatus.NO_WIFI
        waiting -> PairStatus.WAITING
        else -> null
    }
    val fade = motion<Float>()
    val rise = motion(Motion.offset())
    AnimatedContent(
        targetState = status,
        transitionSpec = { (fadeIn(fade) + slideInVertically(rise) { it / 2 }) togetherWith fadeOut(fade) },
        contentAlignment = Alignment.Center,
        label = "stepStatus",
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { current ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (current) {
                PairStatus.NO_WIFI -> Row(
                    Modifier.clip(CircleShape).background(Warning.copy(alpha = 0.12f)).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(TikStikIcons.WifiOff, null, tint = Warning, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.connect_pair_wifi), style = MaterialTheme.typography.labelMedium, color = Warning)
                }
                PairStatus.WAITING -> Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandProgressBar(null, Modifier.width(96.dp), height = 8.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.connect_pair_waiting), style = MaterialTheme.typography.bodySmall, color = TextMid, maxLines = 1)
                }
                null -> Unit
            }
        }
    }
}

/** Help for a step; [notifications] is null when the step does not need them. */
@Composable
private fun HelpContent(text: String, notifications: Boolean?, onOpenNotificationSettings: () -> Unit, onOpenHelpVideo: () -> Unit) {
    Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBarsStable).padding(start = 24.dp, end = 24.dp, bottom = 20.dp)) {
        Text(stringResource(R.string.connect_help_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = TextMid)
        Spacer(Modifier.height(20.dp))
        if (notifications != null) {
            if (notifications) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(TikStikIcons.NotificationsActive, null, tint = BrandGreen, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.connect_help_notifications_on), style = MaterialTheme.typography.labelLarge, color = BrandGreen)
                }
            } else {
                PrimaryButton(
                    stringResource(R.string.connect_help_notifications),
                    onClick = onOpenNotificationSettings,
                    modifier = Modifier.fillMaxWidth(),
                    icon = TikStikIcons.NotificationsActive,
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        if (stringResource(R.string.video_connect_url).isNotBlank()) {
            SecondaryButton(
                stringResource(R.string.connect_help_video),
                onClick = onOpenHelpVideo,
                modifier = Modifier.fillMaxWidth(),
                icon = TikStikIcons.PlayCircle,
            )
        }
    }
}

@Composable
private fun IconIllustration(icon: ImageVector) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(BrandViolet.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = BrandViolet, modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
private fun Done(manual: Boolean, grantHelp: Int?, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedCheck(size = 120.dp)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.connect_done_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        val body = if (manual) withBrandLine(R.string.connect_done_manual_body, grantHelp) else stringResource(R.string.connect_done_body)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = TextMid, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        PrimaryButton(stringResource(R.string.connect_done_action), onDone, Modifier.fillMaxWidth())
    }
}

// The brand-specific line goes in its own paragraph, below the common text.
@Composable
private fun withBrandLine(@StringRes text: Int, @StringRes brandLine: Int?): String =
    if (brandLine == null) stringResource(text) else stringResource(text) + "\n\n" + stringResource(brandLine)

private fun DeviceBrand?.buildHelp(): Int? = when (this) {
    DeviceBrand.SAMSUNG -> R.string.help_samsung_build
    DeviceBrand.XIAOMI -> R.string.help_xiaomi_build
    DeviceBrand.OPPO, null -> null
}

private fun DeviceBrand?.pairHelp(): Int? = if (this == DeviceBrand.XIAOMI) R.string.help_xiaomi_notifications else null

private fun DeviceBrand?.grantHelp(): Int? = when (this) {
    DeviceBrand.XIAOMI -> R.string.help_xiaomi_grant
    DeviceBrand.OPPO -> R.string.help_oppo_grant
    DeviceBrand.SAMSUNG, null -> null
}

@Composable
private fun ConnectPreview(state: ConnectUiState) {
    TikStikTheme { ConnectScreen(state, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
}

@ScreenPreview
@Composable
private fun DevOptionsPreview() = ConnectPreview(ConnectUiState(ConnectStep.DEV_OPTIONS))

@ScreenPreview
@Composable
private fun PairingWaitingPreview() = ConnectPreview(ConnectUiState(ConnectStep.PAIRING, waitingForCode = true))

@ScreenPreview
@Composable
private fun DonePreview() = ConnectPreview(ConnectUiState(ConnectStep.DONE))

@ScreenPreview
@Composable
private fun DoneManualPreview() = ConnectPreview(ConnectUiState(ConnectStep.DONE, manual = true, brand = DeviceBrand.XIAOMI))

@ScreenPreview
@Composable
private fun StorageBlockedPreview() = ConnectPreview(ConnectUiState(ConnectStep.STORAGE, legacyStorage = true, storageBlocked = true))
