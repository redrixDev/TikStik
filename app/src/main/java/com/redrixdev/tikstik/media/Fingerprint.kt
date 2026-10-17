// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.media

import kotlin.math.abs

/** 256-bit dHash of the first frame plus its aspect ratio: the same sticker re-encoded or at another size. */
class Fingerprint private constructor(private val bits: LongArray, val aspect: Int) {

    // Bits that depart from a flat image: two nearly blank ones would "match" without looking alike.
    val information: Int = bits.sumOf { java.lang.Long.bitCount(it) }.let { minOf(it, BITS - it) }

    val informative: Boolean get() = information >= MIN_INFORMATION

    fun distance(other: Fingerprint): Int = bits.indices.sumOf { java.lang.Long.bitCount(bits[it] xor other.bits[it]) }

    // With little information the bar is higher: two nearly flat images differ in few bits even when they are different.
    fun matches(other: Fingerprint, threshold: Int = SAME_THRESHOLD): Boolean {
        if (!informative || !other.informative) return false
        if (abs(aspect - other.aspect) > maxOf(aspect, other.aspect) / 10) return false
        return distance(other) <= minOf(threshold, minOf(information, other.information) / 3)
    }

    fun encode(): String = bits.joinToString("") { it.toULong().toString(16).padStart(16, '0') } + ":" + aspect

    override fun equals(other: Any?): Boolean = other is Fingerprint && aspect == other.aspect && bits.contentEquals(other.bits)

    override fun hashCode(): Int = 31 * bits.contentHashCode() + aspect

    companion object {
        const val SAME_THRESHOLD = 10

        // Measured: the same image at another size lands at 5–28 bits and any other at 57 or more.
        const val TWIN_THRESHOLD = 32
        const val GRID_WIDTH = 17
        const val GRID_HEIGHT = 16
        private const val BITS = (GRID_WIDTH - 1) * GRID_HEIGHT

        // Measured: a nearly empty first frame gives 7.
        private const val MIN_INFORMATION = 16

        /** [grid]: the first frame scaled down to 17×16 in ARGB; [width]×[height], its original size. */
        fun of(grid: IntArray, width: Int, height: Int): Fingerprint {
            require(grid.size == GRID_WIDTH * GRID_HEIGHT && width > 0 && height > 0)
            val bits = LongArray(BITS / 64)
            var bit = 0
            for (y in 0 until GRID_HEIGHT) {
                for (x in 0 until GRID_WIDTH - 1) {
                    if (luma(grid[y * GRID_WIDTH + x]) > luma(grid[y * GRID_WIDTH + x + 1])) {
                        bits[bit / 64] = bits[bit / 64] or (1L shl (bit % 64))
                    }
                    bit++
                }
            }
            return Fingerprint(bits, width * 100 / height)
        }

        fun parse(text: String?): Fingerprint? {
            val hex = text?.substringBefore(':') ?: return null
            if (hex.length != BITS / 4) return null
            val bits = LongArray(BITS / 64) { i -> hex.substring(i * 16, i * 16 + 16).toULongOrNull(16)?.toLong() ?: return null }
            return Fingerprint(bits, text.substringAfter(':', "").toIntOrNull() ?: 100)
        }

        // Transparency is composited over mid gray.
        private fun luma(argb: Int): Double {
            val alpha = (argb ushr 24) / 255.0
            fun channel(shift: Int) = (argb ushr shift and 255) * alpha + 128 * (1 - alpha)
            return 0.299 * channel(16) + 0.587 * channel(8) + 0.114 * channel(0)
        }
    }
}
