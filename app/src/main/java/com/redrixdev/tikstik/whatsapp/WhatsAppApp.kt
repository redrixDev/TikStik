// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.whatsapp

import kotlinx.serialization.Serializable

/** The two WhatsApp apps that accept sticker packs. */
@Serializable
enum class WhatsAppApp(val packageName: String) {
    CONSUMER("com.whatsapp"),
    BUSINESS("com.whatsapp.w4b"),
}
