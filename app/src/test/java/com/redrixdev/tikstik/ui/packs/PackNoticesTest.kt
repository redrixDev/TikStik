// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.packs

import com.google.common.truth.Truth.assertThat
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.core.ui.UiText
import org.junit.Test

class PackNoticesTest {

    @Test
    fun allAlreadyInsideIsNotAnError() {
        val notice = PackNotices.addedTo(added = 0, already = 3, left = 0, name = "Gatos")
        assertThat(notice.error).isFalse()
        assertThat(notice.text).isEqualTo(UiText.Plural(R.plurals.pack_already_in, 3, listOf(3, "Gatos")))
    }

    @Test
    fun nothingAddedAndSomethingLeftIsAnError() {
        // If something did not get in, "already there" would be a half-truth.
        assertThat(PackNotices.addedTo(added = 0, already = 2, left = 1, name = "Gatos").error).isTrue()
        assertThat(PackNotices.addedTo(added = 0, already = 0, left = 0, name = "Gatos").error).isTrue()
    }

    @Test
    fun noneAddedFromThePackIsAnError() {
        assertThat(PackNotices.stickersAdded(0).error).isTrue()
        assertThat(PackNotices.stickersAdded(0).text).isEqualTo(UiText.Res(R.string.pack_added_none))
        assertThat(PackNotices.stickersAdded(2).error).isFalse()
    }

    @Test
    fun addedCountsOnlyTheNewOnes() {
        val notice = PackNotices.addedTo(added = 2, already = 5, left = 0, name = "Gatos")
        assertThat(notice.error).isFalse()
        assertThat(notice.text).isEqualTo(UiText.Plural(R.plurals.pack_added_to, 2, listOf(2, "Gatos")))
    }
}
