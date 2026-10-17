// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.media

internal fun ByteArray.u8(i: Int): Int = this[i].toInt() and 0xFF

internal fun ByteArray.le16(i: Int): Int = u8(i) or (u8(i + 1) shl 8)

internal fun ByteArray.le24(i: Int): Int = le16(i) or (u8(i + 2) shl 16)

internal fun ByteArray.le32(i: Int): Int = le24(i) or (u8(i + 3) shl 24)

internal fun ByteArray.be16(i: Int): Int = (u8(i) shl 8) or u8(i + 1)

internal fun ByteArray.be32(i: Int): Int = (be16(i) shl 16) or be16(i + 2)

internal fun ByteArray.ascii(i: Int, n: Int): String = String(this, i, n, Charsets.US_ASCII)

internal fun ByteArray.isWebp(): Boolean = size >= 12 && ascii(0, 4) == "RIFF" && ascii(8, 4) == "WEBP"

/** A RIFF chunk: [data] is where its payload starts. */
internal class RiffChunk(val fourcc: String, val data: Int, val size: Int) {
    val end: Int get() = data + size + (size and 1)
}

/** A WebP's chunks in order up to the first that does not fit, which with [truncated] is also yielded (start of a file). */
internal fun ByteArray.riffChunks(from: Int = 12, until: Int = size, truncated: Boolean = false): Sequence<RiffChunk> = sequence {
    var pos = from
    while (pos + 8 <= until) {
        val chunk = RiffChunk(ascii(pos, 4), pos + 8, le32(pos + 4))
        // In Long: a tampered size near 2^31 would overflow the Int and seem to fit.
        val fits = chunk.size >= 0 && chunk.data.toLong() + chunk.size <= until
        if (fits || (truncated && chunk.size >= 0)) yield(chunk)
        if (!fits) break
        pos = chunk.end
    }
}
