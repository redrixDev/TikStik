// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.whatsapp.WhatsAppApp

@Composable
fun WhatsAppApp.displayName(): String = stringResource(
    when (this) {
        WhatsAppApp.CONSUMER -> R.string.wa_consumer
        WhatsAppApp.BUSINESS -> R.string.wa_business
    },
)
