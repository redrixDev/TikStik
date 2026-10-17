// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StickerShapeTest {

    private fun alpha(w: Int, h: Int, visible: (Int, Int) -> Boolean) = IntArray(w * h) { if (visible(it % w, it / w)) 255 else 0 }

    @Test
    fun videoStickersAreSolidRectangles() {
        assertThat(StickerShape.isSolidRectangle(alpha(240, 240) { _, _ -> true }, 240, 240)).isTrue()
        // With transparent bands top and bottom to make it square.
        assertThat(StickerShape.isSolidRectangle(alpha(240, 240) { _, y -> y in 30 until 210 }, 240, 240)).isTrue()
    }

    @Test
    fun drawnAssetsAreNot() {
        val circle = alpha(168, 168) { x, y -> (x - 84) * (x - 84) + (y - 84) * (y - 84) < 70 * 70 }
        assertThat(StickerShape.isSolidRectangle(circle, 168, 168)).isFalse()
        // Rounded corners of radius 40 in 200 px: a fill ratio of 0.97.
        val r = 40
        val rounded = alpha(200, 200) { x, y ->
            val cx = x.coerceIn(r, 199 - r)
            val cy = y.coerceIn(r, 199 - r)
            (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r
        }
        assertThat(StickerShape.isSolidRectangle(rounded, 200, 200)).isFalse()
        assertThat(StickerShape.isSolidRectangle(alpha(100, 100) { _, _ -> false }, 100, 100)).isFalse()
        assertThat(StickerShape.isSolidRectangle(alpha(100, 100) { x, y -> x in 40..49 && y in 40..49 }, 100, 100)).isFalse()
    }

    @Test
    fun flatImagesHaveNoContrast() {
        val gray = IntArray(64 * 64) { i -> (0xFF shl 24) or (0x77 + (i % 3) - 1).let { v -> (v shl 16) or (v shl 8) or v } }
        assertThat(StickerShape.isFlat(gray)).isTrue()
        // Black text on white: high contrast even though almost everything is background.
        val text = IntArray(64 * 64) { i -> if (i % 64 in 20..23 && i / 64 in 10..50) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        assertThat(StickerShape.isFlat(text)).isFalse()
        assertThat(StickerShape.isFlat(IntArray(16))).isTrue()
    }
}
