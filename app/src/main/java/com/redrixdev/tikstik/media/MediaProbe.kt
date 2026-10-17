// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.media

enum class ImageFormat(val extension: String) { WEBP("webp"), GIF("gif"), PNG("png"), JPEG("jpg"), OTHER("bin") }

/** What is known about an image without decoding it; frames and duration follow the codec's rules. */
data class Probe(val format: ImageFormat, val width: Int?, val height: Int?, val frames: Int, val durationMs: Int) {
    val animated: Boolean get() = frames > 1
}

object MediaProbe {
    // Like Fresco, which TikTok displays them with: under 11 ms shows as 100; codec.cpp applies the same rule.
    private const val SHORT_FRAME_MS = 11
    private const val SHORT_FRAME_SHOWN_MS = 100

    fun shownDelay(ms: Int) = if (ms < SHORT_FRAME_MS) SHORT_FRAME_SHOWN_MS else ms

    fun probe(b: ByteArray): Probe = when {
        b.isWebp() -> webp(b)
        b.size >= 13 && b.ascii(0, 4) == "GIF8" -> gif(b)
        // Only the first frame of an APNG is used.
        b.size >= 24 && b.u8(0) == 0x89 && b.ascii(1, 3) == "PNG" -> Probe(ImageFormat.PNG, b.be32(16), b.be32(20), 1, 0)
        b.size >= 4 && b.u8(0) == 0xFF && b.u8(1) == 0xD8 -> jpeg(b)
        else -> Probe(ImageFormat.OTHER, null, null, 1, 0)
    }

    private fun webp(b: ByteArray): Probe {
        var width: Int? = null
        var height: Int? = null
        var animated = false
        var frames = 0
        var duration = 0
        for (chunk in b.riffChunks()) {
            val d = chunk.data
            when (chunk.fourcc) {
                "VP8X" -> if (chunk.size >= 10) {
                    animated = b.u8(d) and 0x02 != 0
                    width = 1 + b.le24(d + 4)
                    height = 1 + b.le24(d + 7)
                }
                "VP8 " -> if (width == null && chunk.size >= 10) {
                    width = b.le16(d + 6) and 0x3FFF
                    height = b.le16(d + 8) and 0x3FFF
                }
                "VP8L" -> if (width == null && chunk.size >= 5) {
                    val bits = b.le32(d + 1)
                    width = (bits and 0x3FFF) + 1
                    height = ((bits shr 14) and 0x3FFF) + 1
                }
                "ANMF" -> if (chunk.size >= 16) {
                    frames++
                    duration += shownDelay(b.le24(d + 12))
                }
            }
        }
        return if (animated && frames > 0) Probe(ImageFormat.WEBP, width, height, frames, duration) else Probe(ImageFormat.WEBP, width, height, 1, 0)
    }

    private fun gif(b: ByteArray): Probe {
        var pos = 13
        val packed = b.u8(10)
        if (packed and 0x80 != 0) pos += 3 * (1 shl ((packed and 7) + 1))
        var frames = 0
        var duration = 0
        var delay = 0
        fun skipSubBlocks(start: Int): Int {
            var p = start
            while (p < b.size) {
                val n = b.u8(p)
                p += 1 + n
                if (n == 0) break
            }
            return p
        }
        while (pos < b.size) {
            when (b.u8(pos)) {
                0x21 -> {
                    if (pos + 1 >= b.size) break
                    if (b.u8(pos + 1) == 0xF9 && pos + 7 < b.size) delay = b.le16(pos + 4) * 10
                    pos = skipSubBlocks(pos + 2)
                }
                0x2C -> {
                    if (pos + 10 > b.size) break
                    frames++
                    duration += shownDelay(delay)
                    delay = 0
                    val local = b.u8(pos + 9)
                    pos += 10
                    if (local and 0x80 != 0) pos += 3 * (1 shl ((local and 7) + 1))
                    // +1: the LZW minimum code size precedes the data.
                    pos = skipSubBlocks(pos + 1)
                }
                else -> break
            }
        }
        val width = b.le16(6)
        val height = b.le16(8)
        return if (frames > 1) Probe(ImageFormat.GIF, width, height, frames, duration) else Probe(ImageFormat.GIF, width, height, 1, 0)
    }

    // The size is in the first SOF marker (C0–CF except C4, C8 and CC, which are not SOF).
    private fun jpeg(b: ByteArray): Probe {
        var pos = 2
        while (pos + 9 <= b.size && b.u8(pos) == 0xFF) {
            val marker = b.u8(pos + 1)
            when {
                marker == 0xFF -> pos++
                marker == 0x01 || marker in 0xD0..0xD9 -> pos += 2
                marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC ->
                    return Probe(ImageFormat.JPEG, b.be16(pos + 7), b.be16(pos + 5), 1, 0)
                else -> pos += 2 + b.be16(pos + 2)
            }
        }
        return Probe(ImageFormat.JPEG, null, null, 1, 0)
    }
}
