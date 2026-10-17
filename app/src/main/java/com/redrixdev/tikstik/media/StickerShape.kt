// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.media

/** Shape of what is visible: TikTok video stickers are solid rectangles; gifts, emojis and avatars are cutouts. */
object StickerShape {

    // Measured on a real cache: stickers give a fill of 1.000 and drawings between 0.31 and 0.98.
    private const val MIN_FILL = 0.99

    // Measured: a gray placeholder gives 0.4 and the flattest still sticker in Saved, 13.
    private const val MAX_FLAT_SPREAD = 2.0

    /** [alpha]: [width]×[height] values 0–255, row by row. */
    fun isSolidRectangle(alpha: IntArray, width: Int, height: Int): Boolean {
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        var visible = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (alpha[y * width + x] < 128) continue
                visible++
                minX = minOf(minX, x)
                maxX = maxOf(maxX, x)
                minY = minOf(minY, y)
                maxY = maxOf(maxY, y)
            }
        }
        if (visible == 0) return false
        val box = (maxX - minX + 1).toLong() * (maxY - minY + 1)
        // A tiny rectangle in the middle of nowhere is not a video sticker.
        return box * 4 >= width.toLong() * height && visible >= box * MIN_FILL
    }

    /** Flat image: the luminance of what is visible barely varies. [argb]: ARGB pixels. */
    fun isFlat(argb: IntArray): Boolean {
        var n = 0
        var sum = 0.0
        var sumSquares = 0.0
        for (c in argb) {
            if (c ushr 24 < 128) continue
            val luma = 0.299 * (c shr 16 and 255) + 0.587 * (c shr 8 and 255) + 0.114 * (c and 255)
            n++
            sum += luma
            sumSquares += luma * luma
        }
        if (n == 0) return true
        val mean = sum / n
        return sumSquares / n - mean * mean < MAX_FLAT_SPREAD * MAX_FLAT_SPREAD
    }
}
