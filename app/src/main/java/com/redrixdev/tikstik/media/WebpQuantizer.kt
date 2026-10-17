// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.media

/** `y_ac_qi` (0–127) of the first lossy frame, read from the VP8 header (RFC 6386 §9.6); null if bytes are missing. */
object WebpQuantizer {

    fun firstFrame(b: ByteArray): Int? {
        if (!b.isWebp()) return null
        for (chunk in b.riffChunks(truncated = true)) {
            when (chunk.fourcc) {
                "VP8 " -> return keyFrameQuantizer(b, chunk.data)
                "VP8L" -> return null
                // Animated: the first frame is inside ANMF, after a 16-byte header.
                "ANMF" -> {
                    val inner = b.riffChunks(chunk.data + 16, minOf(b.size.toLong(), chunk.data.toLong() + chunk.size).toInt(), truncated = true)
                    for (frame in inner) {
                        if (frame.fourcc == "VP8 ") return keyFrameQuantizer(b, frame.data)
                        if (frame.fourcc == "VP8L") return null
                    }
                    return null
                }
            }
        }
        return null
    }

    // Tag (3) + start code 9D 01 2A (3) + size (4), then partition 0, in the RFC's order.
    private fun keyFrameQuantizer(b: ByteArray, start: Int): Int? {
        if (start + 10 > b.size) return null
        if (b.u8(start) and 1 != 0) return null
        if (b.u8(start + 3) != 0x9D || b.u8(start + 4) != 0x01 || b.u8(start + 5) != 0x2A) return null
        return try {
            val d = BoolDecoder(b, start + 10)
            d.literal(1) // colour space
            d.literal(1) // clamping type
            if (d.flag()) { // segmentation
                val updateMap = d.flag()
                if (d.flag()) {
                    d.literal(1)
                    repeat(4) { if (d.flag()) d.signed(7) }
                    repeat(4) { if (d.flag()) d.signed(6) }
                }
                if (updateMap) repeat(3) { if (d.flag()) d.literal(8) }
            }
            d.literal(1) // filter type
            d.literal(6) // level
            d.literal(3) // sharpness
            if (d.flag() && d.flag()) repeat(8) { if (d.flag()) d.signed(6) }
            d.literal(2) // partitions
            d.literal(7)
        } catch (_: OutOfData) {
            null
        }
    }

    private class OutOfData : RuntimeException()

    /** VP8 boolean decoder (RFC 6386 §7). */
    private class BoolDecoder(private val b: ByteArray, start: Int) {
        private var pos = start + 2
        private var value: Int
        private var range = 255
        private var bitCount = 0

        init {
            if (start + 2 > b.size) throw OutOfData()
            value = (b.u8(start) shl 8) or b.u8(start + 1)
        }

        fun bit(probability: Int): Int {
            val split = 1 + (((range - 1) * probability) shr 8)
            val bigSplit = split shl 8
            val result = if (value >= bigSplit) {
                range -= split
                value -= bigSplit
                1
            } else {
                range = split
                0
            }
            while (range < 128) {
                value = value shl 1
                range = range shl 1
                if (++bitCount == 8) {
                    bitCount = 0
                    if (pos >= b.size) throw OutOfData()
                    value = value or b.u8(pos++)
                }
            }
            return result
        }

        fun flag(): Boolean = bit(128) == 1

        fun literal(n: Int): Int {
            var v = 0
            repeat(n) { v = (v shl 1) or bit(128) }
            return v
        }

        fun signed(n: Int): Int {
            val v = literal(n)
            return if (flag()) -v else v
        }
    }
}
