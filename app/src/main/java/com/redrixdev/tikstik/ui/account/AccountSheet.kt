// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.account

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.backup.AccountState
import com.redrixdev.tikstik.backup.BackupWork
import com.redrixdev.tikstik.backup.message
import com.redrixdev.tikstik.core.designsystem.component.AppSheet
import com.redrixdev.tikstik.core.designsystem.component.BouncingDots
import com.redrixdev.tikstik.core.designsystem.component.PrimaryButton
import com.redrixdev.tikstik.core.designsystem.icon.TikStikIcons
import com.redrixdev.tikstik.core.designsystem.theme.BrandGreen
import com.redrixdev.tikstik.core.designsystem.theme.OnGreen
import com.redrixdev.tikstik.core.designsystem.theme.Surface2
import com.redrixdev.tikstik.core.designsystem.theme.Surface3
import com.redrixdev.tikstik.core.designsystem.theme.TextHigh
import com.redrixdev.tikstik.core.designsystem.theme.TextLow
import com.redrixdev.tikstik.core.designsystem.theme.TextMid
import com.redrixdev.tikstik.core.designsystem.theme.motion
import com.redrixdev.tikstik.core.designsystem.theme.navigationBarsStable

/** First row of Settings: signed out it invites to sign in; signed in it says who you are and how the backup is doing. */
@Composable
fun AccountRow(account: AccountState, onClick: () -> Unit) {
    val signedIn = account.email != null
    val subtitle = when {
        account.work == BackupWork.CONNECTING -> stringResource(R.string.account_connecting)
        !signedIn -> stringResource(R.string.account_sign_in_body)
        else -> backupStatus(account)
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 68.dp).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(if (signedIn) BrandGreen else Surface3), contentAlignment = Alignment.Center) {
            if (account.email != null) {
                Text(account.email.initial(), style = MaterialTheme.typography.titleMedium, color = OnGreen)
            } else {
                Icon(TikStikIcons.AccountCircle, null, tint = TextHigh, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                account.email ?: stringResource(R.string.account_sign_in),
                style = MaterialTheme.typography.bodyLarge,
                color = TextHigh,
                maxLines = if (account.email == null) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (signedIn && account.problem != null) MaterialTheme.colorScheme.error else TextMid,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (account.work != null) BouncingDots(TextMid) else Icon(TikStikIcons.KeyboardArrowRight, null, tint = TextMid)
    }
}

/** The state of the Drive backup, in one line. */
@Composable
private fun backupStatus(account: AccountState): String {
    val progress = account.progress
    return when {
        account.work == BackupWork.BACKING_UP ->
            if (progress != null) pluralStringResource(R.plurals.backup_working, progress.total, progress.done, progress.total) else stringResource(R.string.backup_working_plain)
        account.work == BackupWork.RESTORING ->
            if (progress != null) pluralStringResource(R.plurals.restore_working, progress.total, progress.done, progress.total) else stringResource(R.string.restore_working_plain)
        account.problem != null -> stringResource(account.problem.message())
        account.lastBackupAt > 0 -> stringResource(R.string.backup_last, relative(account.lastBackupAt))
        else -> stringResource(R.string.backup_never)
    }
}

private fun relative(at: Long): String =
    DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString().replaceFirstChar { it.lowercase() }

@Composable
fun AccountSheet(
    account: AccountState,
    onSignIn: () -> Unit,
    onBackupNow: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    val busy = account.work != null
    val appear = motion(tween<Float>(250, delayMillis = 90))
    val vanish = motion(tween<Float>(150))
    val resize = motion<IntSize>()
    AppSheet(onDismissRequest = onDismiss) { close ->
        // Signing in replaces the whole content: the old fades out whole, unclipped, while the sheet resizes to the new.
        AnimatedContent(
            targetState = account.email,
            contentKey = { it != null },
            transitionSpec = { (fadeIn(appear) togetherWith fadeOut(vanish)) using SizeTransform(clip = false) { _, _ -> resize } },
            label = "account",
        ) { email ->
            Column(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBarsStable).padding(horizontal = 20.dp).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (email == null) {
                    Text(stringResource(R.string.account_title), style = MaterialTheme.typography.titleLarge)
                    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Surface2).padding(vertical = 6.dp)) {
                        Benefit({ Image(painterResource(R.drawable.ic_google_drive), null, Modifier.size(22.dp)) }, stringResource(R.string.account_benefit_drive))
                        Benefit({ Icon(TikStikIcons.Check, null, tint = BrandGreen, modifier = Modifier.size(22.dp)) }, stringResource(R.string.account_benefit_recover))
                    }
                    // Once signed in, this content is on its way out: the button keeps its dots instead of flashing its text.
                    PrimaryButton(stringResource(R.string.account_connect), onClick = onSignIn, loading = busy || account.email != null, modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.account_no_signup),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLow,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(BrandGreen), contentAlignment = Alignment.Center) {
                            Text(email.initial(), style = MaterialTheme.typography.titleMedium, color = OnGreen)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(email, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Surface2).padding(vertical = 4.dp)) {
                        Feature(
                            icon = { Image(painterResource(R.drawable.ic_google_drive), null, Modifier.size(22.dp)) },
                            title = stringResource(R.string.drive_name),
                            status = backupStatus(account),
                            error = account.problem != null,
                            busy = busy,
                            onBackupNow = onBackupNow,
                        )
                    }
                    Text(stringResource(R.string.backup_auto), style = MaterialTheme.typography.bodySmall, color = TextLow)
                    TextButton(onClick = { close(onSignOut) }, enabled = !busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(stringResource(R.string.account_disconnect), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun Benefit(icon: @Composable () -> Unit, text: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextHigh)
    }
}

/** One thing the account keeps: icon, name, state and "Back up now", or dots while a backup or restore runs. */
@Composable
private fun Feature(
    icon: @Composable () -> Unit,
    title: String,
    status: String,
    error: Boolean,
    busy: Boolean,
    onBackupNow: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextHigh, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (error) MaterialTheme.colorScheme.error else TextMid,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (busy) {
            BouncingDots(TextMid, Modifier.padding(horizontal = 10.dp))
        } else {
            IconButton(onClick = onBackupNow) {
                Icon(TikStikIcons.CloudUpload, contentDescription = stringResource(R.string.backup_now), tint = BrandGreen)
            }
        }
    }
}

// Drive may not return the email: without it, a dot instead of a crash.
private fun String.initial(): String = firstOrNull()?.uppercase() ?: "·"
