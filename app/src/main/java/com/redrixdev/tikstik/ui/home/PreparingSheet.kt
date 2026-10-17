// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.core.designsystem.component.AppSheet
import com.redrixdev.tikstik.core.designsystem.component.PrimaryButton
import com.redrixdev.tikstik.core.designsystem.theme.TextMid
import com.redrixdev.tikstik.core.designsystem.theme.navigationBarsStable
import com.redrixdev.tikstik.ui.create.PreparingStatus

/** Progress of the background preparation, opened from its pill; it only informs, so it can be dismissed any time and closes by itself when done. */
@Composable
fun PreparingSheet(progress: PrepareProgress?, onDismiss: () -> Unit) {
    var last by remember { mutableStateOf(progress) }
    if (progress != null) last = progress
    AppSheet(onDismissRequest = onDismiss, skipPartiallyExpanded = true) { close ->
        val over = progress == null
        LaunchedEffect(over) { if (over) close(onDismiss) }
        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBarsStable).padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.prep_sheet_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            last?.let { PreparingStatus(it.done, it.total) }
            Text(stringResource(R.string.prep_background), style = MaterialTheme.typography.bodySmall, color = TextMid, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            PrimaryButton(stringResource(R.string.action_got_it), onClick = { close(onDismiss) }, modifier = Modifier.fillMaxWidth())
        }
    }
}
