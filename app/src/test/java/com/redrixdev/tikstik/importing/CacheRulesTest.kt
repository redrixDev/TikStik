// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.importing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CacheRulesTest {

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun header(flags: Int, w: Int, h: Int) = CacheImageHeader.parse(vp8x(flags, w, h))!!

    private fun general(flags: Int, w: Int, h: Int, quantizer: Int?) = GeneralCacheRules.accepts(header(flags, w, h).copy(quantizer = quantizer))

    @Test
    fun savedPanelAcceptsEveryWebpSticker() {
        // Animated with alpha (498×332), made from video without alpha (240×240) and made from a photo (1206×1132).
        assertThat(header(0x12, 498, 332).savedSticker).isTrue()
        assertThat(header(0x00, 240, 240).savedSticker).isTrue()
        assertThat(header(0x00, 1206, 1132).savedSticker).isTrue()
        assertThat(header(0x10, 72, 56).savedSticker).isFalse()
    }

    // Cases from a census of a real cache, with the quantizer measured in each file.
    @Test
    fun generalCacheKeepsStickersAndDropsTikTokInterfaceImages() {
        assertThat(general(0x12, 240, 240, 27)).isTrue()
        assertThat(general(0x12, 498, 498, 27)).isTrue()
        assertThat(general(0x02, 240, 180, 27)).isTrue()
        // Still video stickers: an interface template, but 240 on a side.
        assertThat(general(0x00, 240, 240, 27)).isTrue()
        assertThat(general(0x00, 240, 180, 26)).isTrue()
        assertThat(general(0x10, 240, 240, 27)).isTrue()
        // Memes and photo stickers: a different quality.
        assertThat(general(0x00, 1170, 1302, 39)).isTrue()
        assertThat(general(0x00, 960, 740, 39)).isTrue()
        assertThat(general(0x20, 735, 676, 0)).isTrue()
        // Cutouts with transparency, up to 512.
        assertThat(general(0x10, 498, 490, 59)).isTrue()
        assertThat(general(0x10, 1370, 1148, 59)).isFalse()
        // Interface: profile pictures, logos, cards and icons.
        assertThat(general(0x00, 100, 100, 27)).isFalse()
        assertThat(general(0x00, 300, 300, 27)).isFalse()
        assertThat(general(0x00, 432, 448, 27)).isFalse()
        assertThat(general(0x00, 100, 100, 24)).isFalse()
        assertThat(general(0x10, 100, 100, 27)).isFalse()
        // Covers, slides and screenshots (very tall) and post previews (640).
        assertThat(general(0x00, 360, 640, 27)).isFalse()
        assertThat(general(0x00, 1080, 1920, 39)).isFalse()
        assertThat(general(0x00, 200, 400, 0)).isFalse()
        assertThat(general(0x00, 640, 800, 39)).isFalse()
        // No quantizer yet: not rejected because of it.
        assertThat(general(0x20, 300, 300, null)).isTrue()
        assertThat(general(0x10, 72, 56, null)).isFalse()
        val png = hex("89504e470d0a1a0a0000000d49484452" + "0000003c0000003c" + "0806000000")
        assertThat(GeneralCacheRules.accepts(CacheImageHeader.parse(png)!!)).isFalse()
        assertThat(CacheImageHeader.parse(png)!!.savedSticker).isFalse()
    }

    @Test
    fun readsSimpleVp8Size() {
        val h = CacheImageHeader.parse(hex("52494646000000005745425056503820000000000000009d012af000f000"))!!
        assertThat(h.width).isEqualTo(240)
        assertThat(h.height).isEqualTo(240)
        assertThat(h.alpha).isFalse()
        assertThat(h.savedSticker).isTrue()
    }
}

/** A VP8X header: flags and canvas (width-1 and height-1 as 24-bit little endian). */
internal fun vp8x(flags: Int, w: Int, h: Int): ByteArray {
    val b = ByteArray(32)
    "RIFF".toByteArray().copyInto(b, 0)
    "WEBPVP8X".toByteArray().copyInto(b, 8)
    b[16] = 10
    b[20] = flags.toByte()
    for (i in 0..2) b[24 + i] = ((w - 1) shr (8 * i)).toByte()
    for (i in 0..2) b[27 + i] = ((h - 1) shr (8 * i)).toByte()
    return b
}
