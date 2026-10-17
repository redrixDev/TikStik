// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.home

import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.access.AccessError
import com.redrixdev.tikstik.core.ui.Notice
import com.redrixdev.tikstik.core.ui.NoticeAction
import com.redrixdev.tikstik.core.ui.UiText

/** Each import error with its approved text; never an exception's message. */
internal object ImportNotices {

    fun of(error: AccessError): Notice = when (error) {
        AccessError.NOT_CONFIGURED -> Notice(UiText.Res(R.string.err_not_configured), NoticeAction.CONNECT, error = true)
        // No button: turn on Wi-Fi and tap Import again.
        AccessError.NO_WIFI -> Notice(UiText.Res(R.string.err_no_wifi), error = true)
        AccessError.WIRELESS_DEBUGGING_OFF -> Notice(UiText.Res(R.string.err_wireless_off), NoticeAction.OPEN_WIRELESS_DEBUGGING, error = true)
        AccessError.NETWORK_NOT_ALLOWED -> Notice(UiText.Res(R.string.err_network_not_allowed), error = true)
        AccessError.CONNECTION_FAILED -> Notice(UiText.Res(R.string.err_connection), error = true)
        AccessError.PAIRING_LOST -> Notice(UiText.Res(R.string.err_pairing_lost), NoticeAction.CONNECT, error = true)
        // Home explains it in a dialog; if it arrives by another path, the generic one: it is not true that there are no stickers.
        AccessError.CACHE_DENIED -> failed
    }

    val noTikTok = Notice(UiText.Res(R.string.home_no_tiktok), error = true)
    val cut = Notice(UiText.Res(R.string.import_err_cut), error = true)
    val failed = Notice(UiText.Res(R.string.import_err_failed), error = true)
    val newNetwork = Notice(UiText.Res(R.string.import_allow_network))
    val nothing = Notice(UiText.Res(R.string.import_none))

    fun unreadable(count: Int) = Notice(UiText.Plural(R.plurals.import_unreadable, count), error = true)

    fun saved(count: Int) = Notice(if (count > 0) UiText.Plural(R.plurals.import_saved, count) else UiText.Res(R.string.import_saved_none))
}
