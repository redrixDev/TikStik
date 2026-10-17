// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.packs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.core.designsystem.component.PrimaryButton
import com.redrixdev.tikstik.core.designsystem.component.SecondaryButton
import com.redrixdev.tikstik.core.designsystem.component.StatusPill
import com.redrixdev.tikstik.core.designsystem.icon.TikStikIcons
import com.redrixdev.tikstik.core.designsystem.theme.TextMid
import com.redrixdev.tikstik.core.ui.displayName
import com.redrixdev.tikstik.whatsapp.PackRules
import com.redrixdev.tikstik.whatsapp.WhatsAppApp

@Composable
fun WhatsAppActions(
    size: Int,
    installed: List<WhatsAppApp>,
    addedTo: Set<WhatsAppApp>,
    onAdd: (WhatsAppApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Same height with a button, "In WhatsApp" or "Add N more": if it changed, the slots jumped and the sticker landed late.
    val reserved = if (installed.isEmpty()) 0.dp else ROW_FIRST + ROW_NEXT * (installed.size - 1)
    Column(
        modifier.fillMaxWidth().heightIn(min = reserved),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (size < PackRules.MIN_STICKERS) {
            val missing = PackRules.MIN_STICKERS - size
            Text(
                pluralStringResource(R.plurals.pack_wa_needs, missing, missing),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMid,
                textAlign = TextAlign.Center,
            )
            return@Column
        }
        installed.forEachIndexed { i, app ->
            if (app in addedTo) {
                StatusPill(stringResource(R.string.pack_status_in, app.displayName()), positive = true, icon = TikStikIcons.Check, modifier = Modifier.padding(4.dp))
            } else {
                val label = stringResource(if (addedTo.isNotEmpty()) R.string.pack_add_also else R.string.pack_add_to, app.displayName())
                if (i == 0) {
                    PrimaryButton(label, { onAdd(app) }, Modifier.fillMaxWidth())
                } else {
                    SecondaryButton(label, { onAdd(app) }, Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
        }
    }
}

// The height of `PrimaryButton` and that of `SecondaryButton` with its top padding.
private val ROW_FIRST = 56.dp
private val ROW_NEXT = 60.dp
