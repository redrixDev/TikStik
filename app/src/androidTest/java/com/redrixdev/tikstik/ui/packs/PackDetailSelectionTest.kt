// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.packs

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.core.designsystem.component.TrayImage
import com.redrixdev.tikstik.core.designsystem.theme.TikStikTheme
import com.redrixdev.tikstik.core.ui.StickerUi
import com.redrixdev.tikstik.whatsapp.WhatsAppApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** With stickers selected for removal, adding others from the sheet releases the selection. */
@RunWith(AndroidJUnit4::class)
class PackDetailSelectionTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun addingStickersClearsTheSelection() {
        var added: Set<String>? = null
        val pack = PackDetailUi(
            id = "p", name = "Paquete", animated = false, stickers = List(4) { PackStickerUi("s$it", File("none")) },
            tray = TrayImage(File("none"), 1), offered = false, installed = listOf(WhatsAppApp.CONSUMER), addedTo = emptySet(),
            addable = listOf(StickerUi("n0", File("none"), animated = false)),
        )
        rule.setContent { TikStikTheme { PackDetailScreen(pack, {}, {}, {}, {}, onAddStickers = { added = it }, {}, {}) } }
        val app = rule.activity
        val sticker = app.getString(R.string.cd_sticker)
        val remove = app.resources.getQuantityString(R.plurals.pack_remove, 1, 1)

        rule.onAllNodesWithContentDescription(sticker)[0].performClick()
        rule.onNodeWithText(remove).assertExists()
        rule.onAllNodesWithContentDescription(app.getString(R.string.slot_add))[0].performClick()
        // The one in the sheet, not the pack's own behind it.
        val inSheet = hasContentDescription(sticker) and hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle))
        rule.onNode(inSheet).performClick()
        rule.onNodeWithText(app.resources.getQuantityString(R.plurals.add_stickers_action, 1, 1)).performClick()
        rule.waitForIdle()

        assertThat(added).containsExactly("n0")
        rule.onNodeWithText(remove).assertDoesNotExist()
    }
}
