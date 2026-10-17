// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.packs

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.core.designsystem.component.TrayImage
import com.redrixdev.tikstik.core.designsystem.theme.LocalReduceMotion
import com.redrixdev.tikstik.core.designsystem.theme.TikStikTheme
import com.redrixdev.tikstik.whatsapp.WhatsAppApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/** When removing stickers, each one reaches its cell without overshooting or lagging, even when the header changes. */
@RunWith(AndroidJUnit4::class)
class PackDetailReflowTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aStickerMovingUpARowDoesNotPassItsCell() = assertNoOvershoot(9, keep = { it % 2 == 0 }, track = 6)

    @Test
    fun aStickerMovingUpTwoRowsDoesNotPassItsCell() = assertNoOvershoot(12, keep = { it == 0 || it >= 7 }, track = 9)

    @Test
    fun theStickerLeftAloneStaysPutWhenThePackDropsBelowThree() = assertStill(pack((0 until 6).toList()), pack(listOf(0)))

    @Test
    fun theStickersStayPutWhenThePackIsAddedToWhatsApp() =
        assertStill(pack((0 until 6).toList()), pack((0 until 6).toList(), addedTo = setOf(WhatsAppApp.CONSUMER)))

    private fun pack(ids: List<Int>, addedTo: Set<WhatsAppApp> = emptySet()) = PackDetailUi(
        id = "p", name = "Paquete", animated = false, stickers = ids.map { PackStickerUi("s$it", File("none")) },
        tray = TrayImage(File("none"), 1), offered = addedTo.isNotEmpty(), installed = listOf(WhatsAppApp.CONSUMER), addedTo = addedTo,
        addable = emptyList(),
    )

    // The first sticker keeps its cell: when the header changes height, the cell jumps and the sticker used to arrive later.
    private fun assertStill(before: PackDetailUi, after: PackDetailUi) {
        var state by mutableStateOf(before)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            TikStikTheme {
                CompositionLocalProvider(LocalReduceMotion provides false) {
                    PackDetailScreen(state, {}, {}, {}, {}, {}, {}, {})
                }
            }
        }
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        val sticker = rule.activity.getString(R.string.cd_sticker)
        val start = rule.onAllNodesWithContentDescription(sticker)[0].getUnclippedBoundsInRoot().top.value
        state = after
        val tops = List(FRAMES) {
            rule.mainClock.advanceTimeByFrame()
            rule.onAllNodesWithContentDescription(sticker)[0].getUnclippedBoundsInRoot().top.value
        }
        assertWithMessage("the first sticker moved although it stays in its cell").that(tops.maxOf { abs(it - start) }).isAtMost(TOLERANCE_DP)
    }

    private fun assertNoOvershoot(size: Int, keep: (Int) -> Boolean, track: Int) {
        val before = (0 until size).toList()
        val after = before.filter(keep)
        var state by mutableStateOf(pack(before))
        rule.mainClock.autoAdvance = false
        rule.setContent {
            TikStikTheme {
                CompositionLocalProvider(LocalReduceMotion provides false) {
                    PackDetailScreen(state, {}, {}, {}, {}, {}, {}, {})
                }
            }
        }
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        val sticker = rule.activity.getString(R.string.cd_sticker)
        val start = rule.onAllNodesWithContentDescription(sticker)[track].getUnclippedBoundsInRoot().top.value
        state = pack(after)
        // Frame by frame, the same sticker at its new position in the list.
        val now = after.indexOf(track)
        val tops = List(FRAMES) {
            rule.mainClock.advanceTimeByFrame()
            rule.onAllNodesWithContentDescription(sticker)[now].getUnclippedBoundsInRoot().top.value
        }
        val final = tops.last()
        assertWithMessage("the sticker did not change row").that(start - final).isGreaterThan(MIN_MOVE_DP)
        assertWithMessage("it overshot its cell and came back").that(final - tops.min()).isAtMost(TOLERANCE_DP)
    }

    private companion object {
        const val SETTLE_MS = 2_000L
        const val FRAMES = 90
        const val MIN_MOVE_DP = 50f
        const val TOLERANCE_DP = 0.2f
    }
}
