// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.packs

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.core.designsystem.component.TrayImage
import com.redrixdev.tikstik.core.designsystem.theme.TikStikTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Reordering without dragging: every sticker offers "move before" and "move after" to accessibility services. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PackDetailAccessibilityTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun stickersCanBeMovedWithAccessibilityActions() {
        var order: List<String>? = null
        val pack = PackDetailUi(
            id = "p",
            name = "Pack",
            animated = false,
            stickers = (1..3).map { PackStickerUi("s$it", File("none")) },
            tray = TrayImage(File("none"), 1),
            offered = false,
            installed = emptyList(),
            addedTo = emptySet(),
            addable = emptyList(),
        )
        rule.setContent { TikStikTheme { PackDetailScreen(pack, {}, {}, { order = it }, {}, {}, {}, {}) } }
        val before = rule.activity.getString(R.string.cd_move_before)
        val after = rule.activity.getString(R.string.cd_move_after)
        val stickers = rule.onAllNodesWithContentDescription(rule.activity.getString(R.string.cd_sticker), substring = true)

        stickers[1].performCustomAccessibilityActionWithLabel(before)
        assertThat(order).containsExactly("s2", "s1", "s3").inOrder()
        stickers[1].performCustomAccessibilityActionWithLabel(after)
        assertThat(order).containsExactly("s1", "s3", "s2").inOrder()

        // The ends only offer the move that exists.
        stickers[0].assert(lacksAction(before))
        stickers[2].assert(lacksAction(after))
    }

    private fun lacksAction(label: String) = SemanticsMatcher("lacks action $label") { node ->
        node.config.getOrNull(SemanticsActions.CustomActions)?.none { it.label == label } ?: true
    }
}
