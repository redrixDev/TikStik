// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.packs

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PackPlannerTest {

    private fun sizes(count: Int) = PackPlanner.split((1..count).toList()).map { it.size }

    @Test
    fun fillsPacksOfThirtyAndKeepsTheLastAtThreeOrMore() {
        assertThat(sizes(0)).isEmpty()
        assertThat(sizes(1)).containsExactly(1)
        assertThat(sizes(2)).containsExactly(2)
        assertThat(sizes(3)).containsExactly(3)
        assertThat(sizes(30)).containsExactly(30)
        assertThat(sizes(31)).containsExactly(28, 3).inOrder()
        assertThat(sizes(32)).containsExactly(29, 3).inOrder()
        assertThat(sizes(33)).containsExactly(30, 3).inOrder()
        assertThat(sizes(61)).containsExactly(30, 28, 3).inOrder()
        assertThat(sizes(90)).containsExactly(30, 30, 30).inOrder()
    }

    @Test
    fun keepsTheOrderAndLosesNothing() {
        for (count in 1..125) {
            val items = (1..count).toList()
            assertThat(PackPlanner.split(items).flatten()).containsExactlyElementsIn(items).inOrder()
        }
    }

    @Test
    fun neverRepeatsASticker() {
        assertThat(PackPlanner.split(listOf("a", "b", "a", "c", "b"))).containsExactly(listOf("a", "b", "c"))
    }

    @Test
    fun fittingSkipsWhatThePackHasAndStopsWhenFull() {
        val inside = (1..27).toList()
        assertThat(PackPlanner.fitting(listOf(5, 40, 41, 40, 42, 43), inside)).containsExactly(40, 41, 42).inOrder()
        assertThat(PackPlanner.fitting(listOf(1, 2), inside)).isEmpty()
        assertThat(PackPlanner.fitting(listOf(99), (1..30).toList())).isEmpty()
    }
}
