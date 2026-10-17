// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.whatsapp

/** WhatsApp's rules for packs (official stickers repository). */
object PackRules {
    const val MIN_STICKERS = 3
    const val MAX_STICKERS = 30
    const val MAX_PACKS = 10
    const val MAX_NAME = 128
    // WhatsApp asks for 1 to 3 emojis per sticker; TikStik does not know what each one expresses.
    const val DEFAULT_EMOJI = "😀"

    /** The fewest stickers a pack may keep: WhatsApp's minimum once it is offered there, otherwise one. */
    fun minimumKept(offered: Boolean) = if (offered) MIN_STICKERS else 1
}
