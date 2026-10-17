// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StickerOutputTest {

    private fun resource(name: String) = javaClass.getResourceAsStream("/media/$name")!!.readBytes()

    private val animated = resource("anim_512_6f_100ms.webp")
    private val still = resource("static_512_lossy.webp")

    @Test
    fun validStickersPass() {
        assertThat(StickerOutput.isValidSticker(animated, animated = true)).isTrue()
        assertThat(StickerOutput.isValidSticker(still, animated = false)).isTrue()
        assertThat(StickerOutput.isValidSticker(resource("static_512_lossless.webp"), animated = false)).isTrue()
    }

    @Test
    fun theKindMustMatch() {
        assertThat(StickerOutput.isValidSticker(animated, animated = false)).isFalse()
        assertThat(StickerOutput.isValidSticker(still, animated = true)).isFalse()
    }

    @Test
    fun onlyWebpOf512() {
        assertThat(StickerOutput.isValidSticker(resource("anim_400x300_8f_60ms.webp"), animated = true)).isFalse()
        assertThat(StickerOutput.isValidSticker(resource("static_512.png"), animated = false)).isFalse()
    }

    @Test
    fun brokenContainersFail() {
        assertThat(StickerOutput.isValidSticker(animated.copyOf(animated.size - 1), animated = true)).isFalse()
        assertThat(StickerOutput.isValidSticker(animated + byteArrayOf(0, 0), animated = true)).isFalse()
    }

    @Test
    fun framesUnder8msFail() {
        val fast = animated.copyOf()
        val anmf = (12 until fast.size - 4).first { fast.ascii(it, 4) == "ANMF" }
        fast[anmf + 8 + 12] = 5
        fast[anmf + 8 + 13] = 0
        fast[anmf + 8 + 14] = 0
        assertThat(StickerOutput.isValidSticker(fast, animated = true)).isFalse()
    }

    @Test
    fun trayIsASmallPng() {
        assertThat(StickerOutput.isValidTray(resource("static_512.png"))).isTrue()
        assertThat(StickerOutput.isValidTray(still)).isFalse()
    }
}
