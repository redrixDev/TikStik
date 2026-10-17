// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.packs

import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.core.ui.Notice
import com.redrixdev.tikstik.core.ui.UiText

/** Pack and WhatsApp notices with their approved wording; never an exception's message. */
internal object PackNotices {

    val saveFailed = Notice(UiText.Res(R.string.pack_err_save), error = true)
    val updated = Notice(UiText.Res(R.string.pack_updated))
    val removeMinimum = Notice(UiText.Res(R.string.pack_remove_min_message), error = true)
    val rejected = Notice(UiText.Res(R.string.wa_rejected), error = true)
    val openFailed = Notice(UiText.Res(R.string.wa_open_failed), error = true)

    // If none could be prepared it is an error, not "0 stickers added".
    fun stickersAdded(count: Int) =
        if (count == 0) Notice(UiText.Res(R.string.pack_added_none), error = true) else Notice(UiText.Plural(R.plurals.pack_stickers_added, count))

    /** "N added to Pack", plus what did not fit and stays selected; if all were already inside, it says so without being an error. */
    fun addedTo(added: Int, already: Int, left: Int, name: String): Notice {
        if (added == 0 && already > 0 && left == 0) return Notice(UiText.Plural(R.plurals.pack_already_in, already, listOf(already, shortName(name))))
        if (added == 0) return Notice(UiText.Res(R.string.pack_added_none), error = true)
        val text = UiText.Plural(R.plurals.pack_added_to, added, listOf(added, shortName(name)))
        return Notice(if (left > 0) UiText.Join(listOf(text, UiText.Plural(R.plurals.pack_left_no_room, left))) else text)
    }

    // The notice is one or two lines: a long name gets truncated.
    private fun shortName(name: String) = if (name.length <= NAME_MAX) name else name.take(NAME_MAX - 1).trimEnd() + "…"

    private const val NAME_MAX = 22
}
