// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.packs

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.core.designsystem.component.AppSheet
import com.redrixdev.tikstik.core.designsystem.component.PrimaryButton
import com.redrixdev.tikstik.core.designsystem.theme.TextMid
import com.redrixdev.tikstik.core.designsystem.theme.navigationBarsStable
import com.redrixdev.tikstik.core.ui.displayName
import com.redrixdev.tikstik.whatsapp.WhatsAppApp

/** No room left in that WhatsApp: an app cannot remove packs from WhatsApp, so it explains how to do it there. */
@Composable
fun LimitSheet(app: WhatsAppApp, onOpenWhatsApp: () -> Unit, onDismiss: () -> Unit) {
    AppSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBarsStable).padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
            Text(stringResource(R.string.limit_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.limit_body), style = MaterialTheme.typography.bodyLarge, color = TextMid)
            Spacer(Modifier.height(20.dp))
            PrimaryButton(stringResource(R.string.limit_open, app.displayName()), onOpenWhatsApp, Modifier.fillMaxWidth())
        }
    }
}
