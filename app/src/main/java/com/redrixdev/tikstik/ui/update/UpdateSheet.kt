// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.update

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.core.designsystem.component.AppSheet
import com.redrixdev.tikstik.core.designsystem.component.BrandProgressBar
import com.redrixdev.tikstik.core.designsystem.component.PrimaryButton
import com.redrixdev.tikstik.core.designsystem.component.QuietButton
import com.redrixdev.tikstik.core.designsystem.theme.TextMid
import com.redrixdev.tikstik.core.designsystem.theme.navigationBarsStable
import com.redrixdev.tikstik.update.AvailableUpdate
import com.redrixdev.tikstik.update.UpdatePhase

/** The new version: its notes, then "Update", the download, and "Install" (or the permission it needs) once it is on the phone. */
@Composable
fun UpdateSheet(
    update: AvailableUpdate,
    phase: UpdatePhase,
    installAllowed: Boolean,
    onUpdate: () -> Unit,
    onInstall: () -> Unit,
    onLater: () -> Unit,
) {
    val context = LocalContext.current
    AppSheet(onDismissRequest = onLater) { close ->
        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBarsStable).padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.update_title, update.versionName), style = MaterialTheme.typography.titleLarge)
            if (update.notes.isNotBlank()) {
                Text(update.notes, style = MaterialTheme.typography.bodyMedium, color = TextMid, maxLines = 12, overflow = TextOverflow.Ellipsis)
            }
            when (phase) {
                is UpdatePhase.Downloading -> {
                    BrandProgressBar(phase.fraction, Modifier.fillMaxWidth(), height = 8.dp)
                    Text(stringResource(R.string.update_downloading), style = MaterialTheme.typography.bodySmall, color = TextMid)
                }
                is UpdatePhase.Ready -> {
                    Text(
                        stringResource(if (installAllowed) R.string.update_ready else R.string.update_allow),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMid,
                    )
                    PrimaryButton(
                        stringResource(if (installAllowed) R.string.update_install else R.string.update_allow_action),
                        onClick = onInstall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    QuietButton(stringResource(R.string.update_later), onClick = { close(onLater) }, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                else -> {
                    if (phase is UpdatePhase.Failed) {
                        Text(stringResource(R.string.update_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    } else if (update.apkBytes > 0) {
                        Text(
                            stringResource(R.string.update_size, Formatter.formatShortFileSize(context, update.apkBytes)),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMid,
                        )
                    }
                    PrimaryButton(
                        stringResource(if (phase is UpdatePhase.Failed) R.string.update_retry else R.string.update_action),
                        onClick = onUpdate,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    QuietButton(stringResource(R.string.update_later), onClick = { close(onLater) }, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
    }
}
