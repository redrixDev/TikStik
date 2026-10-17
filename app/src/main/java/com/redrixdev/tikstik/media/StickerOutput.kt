// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.media

/** What WhatsApp's official validator demands of each file (StickerPackValidator.java in the stickers repository). */
object StickerOutput {
    const val SIDE = 512
    const val STILL_LIMIT = 100 * 1024
    const val ANIMATED_LIMIT = 500 * 1024
    const val TRAY_SIDE = 96
    const val TRAY_LIMIT = 50 * 1024
    const val MAX_DURATION_MS = 10_000
    private const val MIN_FRAME_MS = 8
    private val TRAY_SIDES = 24..512

    fun limit(animated: Boolean): Int = if (animated) ANIMATED_LIMIT else STILL_LIMIT

    /** Frames of the original that fit in the 10 s WhatsApp allows; the rest is not processed. */
    fun framesWithinLimit(probe: Probe): Int =
        if (probe.durationMs > MAX_DURATION_MS) (probe.frames.toLong() * MAX_DURATION_MS / probe.durationMs).toInt().coerceAtLeast(1) else probe.frames

    fun isValidSticker(b: ByteArray, animated: Boolean): Boolean {
        if (b.size > limit(animated) || !b.isWebp() || b.le32(4) != b.size - 8) return false
        val chunks = b.riffChunks().toList()
        if (chunks.lastOrNull()?.end != b.size) return false
        val probe = MediaProbe.probe(b)
        if (probe.width != SIDE || probe.height != SIDE) return false
        val durations = chunks.filter { it.fourcc == "ANMF" && it.size >= 16 }.map { b.le24(it.data + 12) }
        return if (animated) {
            probe.animated && durations.size > 1 && durations.all { it >= MIN_FRAME_MS } && durations.sum() <= MAX_DURATION_MS
        } else {
            !probe.animated && durations.size <= 1
        }
    }

    fun isValidTray(b: ByteArray): Boolean {
        val probe = MediaProbe.probe(b)
        return b.size <= TRAY_LIMIT && probe.format == ImageFormat.PNG && probe.width in TRAY_SIDES && probe.height in TRAY_SIDES
    }
}
