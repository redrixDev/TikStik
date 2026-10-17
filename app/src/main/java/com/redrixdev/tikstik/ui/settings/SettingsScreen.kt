// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.backup.AccountState
import com.redrixdev.tikstik.core.data.settings.SettingsRepository
import com.redrixdev.tikstik.core.designsystem.ScreenPreview
import com.redrixdev.tikstik.core.designsystem.brand.BrandMark
import com.redrixdev.tikstik.core.designsystem.component.ConfirmDialog
import com.redrixdev.tikstik.core.designsystem.component.SectionLabel
import com.redrixdev.tikstik.core.designsystem.component.TopBar
import com.redrixdev.tikstik.core.designsystem.icon.TikStikIcons
import com.redrixdev.tikstik.core.designsystem.theme.BrandGreen
import com.redrixdev.tikstik.core.designsystem.theme.BrandViolet
import com.redrixdev.tikstik.core.designsystem.theme.OnGreen
import com.redrixdev.tikstik.core.designsystem.theme.Outline
import com.redrixdev.tikstik.core.designsystem.theme.Surface1
import com.redrixdev.tikstik.core.designsystem.theme.Surface3
import com.redrixdev.tikstik.core.designsystem.theme.TextHigh
import com.redrixdev.tikstik.core.designsystem.theme.TextMid
import com.redrixdev.tikstik.core.designsystem.theme.TikStikTheme
import com.redrixdev.tikstik.core.designsystem.theme.VioletDeep
import com.redrixdev.tikstik.core.designsystem.theme.safeDrawingStable
import com.redrixdev.tikstik.ui.account.AccountRow
import com.redrixdev.tikstik.ui.account.AccountSheet
import com.redrixdev.tikstik.ui.info.InfoPage

/** [packBaseName] null: the default name. */
@Immutable
data class SettingsUiState(
    val account: AccountState = AccountState(),
    val tiktokConnected: Boolean = false,
    val packBaseName: String? = null,
    val updates: UpdatesUi = UpdatesUi(),
)

/** [available]: a newer version GitHub lists; [installed]: the one running. */
@Immutable
data class UpdatesUi(val automatic: Boolean = true, val available: String? = null, val installed: String = "")

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onConnectTikTok: () -> Unit,
    onDisconnectTikTok: () -> Unit,
    onSetPackBaseName: (String?) -> Unit,
    onOpenInfo: (InfoPage) -> Unit,
    onOpenHowTo: () -> Unit,
    onPrivacyPolicy: (() -> Unit)?,
    onSupport: (() -> Unit)?,
    onSignIn: () -> Unit,
    onBackupNow: () -> Unit,
    onSignOut: () -> Unit,
    onConfirmMerge: () -> Unit,
    onCancelMerge: () -> Unit,
    onSetAutoUpdate: (Boolean) -> Unit,
    onCheckUpdates: () -> Unit,
    onShowUpdate: () -> Unit,
) {
    // Dialogs and sheets that survive recreating the screen.
    var confirmRevoke by rememberSaveable { mutableStateOf(false) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var showAccount by rememberSaveable { mutableStateOf(false) }
    var editingName by rememberSaveable { mutableStateOf(false) }
    val defaultName = stringResource(R.string.pack_name_default)

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawingStable)) {
        TopBar(stringResource(R.string.settings_title), onBack)
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .align(Alignment.CenterHorizontally)
                .widthIn(max = 640.dp)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (onSupport != null) SupportCard(onSupport)

            SectionLabel(stringResource(R.string.settings_section_account))
            Group {
                AccountRow(state.account) { showAccount = true }
                TikTokRow(state.tiktokConnected, onConnect = onConnectTikTok, onDisconnect = { confirmRevoke = true })
            }

            SectionLabel(stringResource(R.string.settings_section_packs))
            Group {
                Item(TikStikIcons.DriveFileRenameOutline, stringResource(R.string.settings_pack_name), subtitle = state.packBaseName ?: defaultName) {
                    editingName = true
                }
            }

            SectionLabel(stringResource(R.string.settings_section_about))
            Group {
                Item(TikStikIcons.HelpOutline, stringResource(R.string.settings_howto), onClick = onOpenHowTo)
                Item(TikStikIcons.PrivacyTip, stringResource(R.string.settings_privacy)) { onOpenInfo(InfoPage.PRIVACY) }
                if (onPrivacyPolicy != null) Item(TikStikIcons.Link, stringResource(R.string.settings_privacy_policy), onClick = onPrivacyPolicy)
                Item(TikStikIcons.Description, stringResource(R.string.settings_licenses)) { onOpenInfo(InfoPage.LICENSES) }
                Item(TikStikIcons.Info, stringResource(R.string.settings_about)) { onOpenInfo(InfoPage.ABOUT) }
            }

            SectionLabel(stringResource(R.string.settings_section_updates))
            Group {
                SwitchItem(
                    TikStikIcons.Tune,
                    stringResource(R.string.settings_updates_auto),
                    stringResource(R.string.settings_updates_auto_body),
                    checked = state.updates.automatic,
                    onToggle = onSetAutoUpdate,
                )
                val available = state.updates.available
                if (available != null) {
                    Item(TikStikIcons.Download, stringResource(R.string.settings_update_available, available), onClick = onShowUpdate)
                } else {
                    Item(TikStikIcons.Download, stringResource(R.string.settings_updates_check), subtitle = stringResource(R.string.settings_version, state.updates.installed), onClick = onCheckUpdates)
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }

    if (confirmRevoke) {
        ConfirmDialog(
            title = stringResource(R.string.settings_revoke_title),
            body = stringResource(R.string.settings_revoke_body),
            confirm = stringResource(R.string.settings_revoke),
            onConfirm = {
                confirmRevoke = false
                onDisconnectTikTok()
            },
            onDismiss = { confirmRevoke = false },
        )
    }
    if (confirmSignOut) {
        ConfirmDialog(
            title = stringResource(R.string.account_disconnect_title),
            body = stringResource(R.string.account_disconnect_body),
            confirm = stringResource(R.string.account_disconnect),
            onConfirm = {
                confirmSignOut = false
                onSignOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }
    // Kept by the account, not by the screen: it survives recreating the screen on its own.
    state.account.mergeWith?.let { email ->
        ConfirmDialog(
            title = stringResource(R.string.account_merge_title),
            body = stringResource(R.string.account_merge_body, email),
            confirm = stringResource(R.string.account_merge),
            onConfirm = onConfirmMerge,
            onDismiss = onCancelMerge,
            danger = false,
        )
    }
    if (showAccount) {
        AccountSheet(
            account = state.account,
            onSignIn = onSignIn,
            onBackupNow = onBackupNow,
            onSignOut = {
                showAccount = false
                confirmSignOut = true
            },
            onDismiss = { showAccount = false },
        )
    }
    if (editingName) {
        PackNameDialog(
            current = state.packBaseName ?: defaultName,
            defaultName = defaultName,
            onSave = {
                onSetPackBaseName(it.trim().takeIf { name -> name.isNotEmpty() && name != defaultName })
                editingName = false
            },
            onDismiss = { editingName = false },
        )
    }
}

@Composable
private fun PackNameDialog(current: String, defaultName: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        shape = MaterialTheme.shapes.large,
        title = { Text(stringResource(R.string.settings_pack_name), style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(SettingsRepository.MAX_BASE_NAME) },
                singleLine = true,
                supportingText = { Text(stringResource(R.string.create_name_hint, name.ifBlank { defaultName })) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandGreen, cursorColor = BrandGreen, unfocusedBorderColor = Outline),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }) {
                Text(stringResource(R.string.action_save), color = BrandGreen, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = TextHigh, style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

/** The way to support the project: deep violet with a green glow, so it does not look like one more row. */
@Composable
private fun SupportCard(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(BrandViolet, VioletDeep)))
            .drawBehind {
                val center = Offset(size.width, size.height)
                val radius = size.width * 0.55f
                drawCircle(Brush.radialGradient(listOf(BrandGreen.copy(alpha = 0.45f), Color.Transparent), center = center, radius = radius), radius = radius, center = center)
            }
            .border(1.dp, Color.White.copy(alpha = 0.14f), MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(Modifier.size(36.dp), alive = true, twinkle = true)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_support), style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(stringResource(R.string.settings_support_body), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.82f))
        }
        // Room between the text and the pill: touching it looked crowded.
        Spacer(Modifier.width(20.dp))
        Text(
            stringResource(R.string.settings_support_cta),
            style = MaterialTheme.typography.labelLarge,
            color = VioletDeep,
            maxLines = 1,
            modifier = Modifier.clip(CircleShape).background(Color.White).padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Surface1)) { content() }
}

/** TikTok in one row: the mark with its status badge, the name and the action in a pill. */
@Composable
private fun TikTokRow(connected: Boolean, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp)) {
            BrandMark(Modifier.size(32.dp), alive = connected)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Surface1)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(if (connected) BrandGreen else Surface3),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (connected) TikStikIcons.Check else TikStikIcons.LinkOff,
                    null,
                    tint = if (connected) OnGreen else TextMid,
                    modifier = Modifier.size(8.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(stringResource(R.string.settings_section_connection), style = MaterialTheme.typography.bodyLarge, color = TextHigh, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        val error = MaterialTheme.colorScheme.error
        // The touch target keeps 48 dp; the pill itself is drawn smaller.
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .clip(CircleShape)
                .clickable(onClick = if (connected) onDisconnect else onConnect)
                .padding(horizontal = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(if (connected) R.string.settings_revoke else R.string.settings_connect),
                style = MaterialTheme.typography.labelLarge,
                color = if (connected) error else OnGreen,
                maxLines = 1,
                modifier = Modifier
                    .clip(CircleShape)
                    .then(if (connected) Modifier.border(1.dp, error.copy(alpha = 0.55f), CircleShape) else Modifier.background(BrandGreen))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun Item(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = TextHigh, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextHigh)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMid)
        }
        Icon(TikStikIcons.KeyboardArrowRight, null, tint = TextMid)
    }
}

/** A row whose whole surface toggles [checked] without lighting up: only the switch moves. */
@Composable
private fun SwitchItem(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, interactionSource = null, indication = null, role = Role.Switch, onValueChange = onToggle)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = TextHigh, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextHigh)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMid)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnGreen,
                checkedTrackColor = BrandGreen,
                uncheckedThumbColor = TextMid,
                uncheckedTrackColor = Surface3,
                uncheckedBorderColor = Outline,
            ),
        )
    }
}

@ScreenPreview
@Composable
private fun SettingsPreview() {
    TikStikTheme {
        SettingsScreen(SettingsUiState(tiktokConnected = true), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}
