// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FingerprintTest {

    private val base = "0f0f0f0f0f0f0f0f" + "f0f0f0f0f0f0f0f0" + "00ff00ff00ff00ff" + "123456789abcdef0"

    // One bit in a different nibble each time.
    private fun flip(hex: String, bits: Int): String {
        val chars = hex.toCharArray()
        repeat(bits) { i -> chars[i * 4] = (chars[i * 4].digitToInt(16) xor 1).digitToChar(16) }
        return String(chars)
    }

    private fun print(hex: String, aspect: Int = 100) = Fingerprint.parse("$hex:$aspect")!!

    @Test
    fun sameStickerWithSmallDifferences() {
        assertThat(print(base).matches(print(base))).isTrue()
        assertThat(print(base).matches(print(flip(base, 8), 104))).isTrue()
    }

    @Test
    fun differentStickers() {
        assertThat(print(base).matches(print(flip(base, 14)))).isFalse()
        assertThat(print(base).matches(print(base, 160))).isFalse()
        assertThat(Fingerprint.parse("corrupto")).isNull()
        assertThat(Fingerprint.parse(null)).isNull()
    }

    @Test
    fun twinsToleratesMoreBits() {
        assertThat(print(base).matches(print(flip(base, 14)), Fingerprint.TWIN_THRESHOLD)).isTrue()
    }

    @Test
    fun blankImagesNeverMatch() {
        val blank = "0".repeat(64)
        assertThat(print(blank).matches(print(blank))).isFalse()
        assertThat(print("0".repeat(60) + "0013").matches(print("0".repeat(60) + "0031"))).isFalse()
        assertThat(print(blank).informative).isFalse()
        assertThat(print(base).informative).isTrue()
    }

    @Test
    fun sparseImagesNeedACloserMatch() {
        // 24 informative bits: the margin drops from 10 to 8.
        val sparse = "ffffff" + "0".repeat(58)
        assertThat(print(sparse).matches(print(flip(sparse, 6)))).isTrue()
        assertThat(print(sparse).matches(print(flip(sparse, 9)))).isFalse()
    }

    @Test
    fun encodesBackToTheSameText() {
        assertThat(print(base, 75).encode()).isEqualTo("$base:75")
        assertThat(print("f".repeat(64)).encode()).isEqualTo("f".repeat(64) + ":100")
    }

    @Test
    fun fromPixelsComparesNeighboursOverGray() {
        // Columns of decreasing luminance: each pixel is lighter than the one to its right.
        val falling = IntArray(Fingerprint.GRID_WIDTH * Fingerprint.GRID_HEIGHT) { i ->
            val v = 255 - (i % Fingerprint.GRID_WIDTH) * 15
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val print = Fingerprint.of(falling, 300, 150)
        assertThat(print.encode()).isEqualTo("f".repeat(64) + ":200")
        // Transparency is composited over gray: an empty image carries no information.
        assertThat(Fingerprint.of(IntArray(falling.size), 1, 1).informative).isFalse()
    }
}
