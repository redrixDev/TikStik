// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class MediaProbeTest {

    private fun resource(name: String) = javaClass.getResourceAsStream("/media/$name")!!.readBytes()

    private fun asset(name: String) = File("src/androidTest/assets/media/$name").readBytes()

    @Test
    fun animatedGif() {
        assertThat(MediaProbe.probe(resource("anim_300x200_6f_80ms.gif"))).isEqualTo(Probe(ImageFormat.GIF, 300, 200, 6, 480))
    }

    @Test
    fun gifWithoutDelayPlaysAt100msLikeTheCodec() {
        assertThat(MediaProbe.probe(asset("gif_interlaced_300x180_12f_0ms.gif"))).isEqualTo(Probe(ImageFormat.GIF, 300, 180, 12, 1200))
        assertThat(MediaProbe.probe(asset("gif_single_200x260.gif"))).isEqualTo(Probe(ImageFormat.GIF, 200, 260, 1, 0))
    }

    @Test
    fun framesShorterThan11msPlayAt100msLikeTikTok() {
        // A two-frame GIF with 1 hundredth (10 ms) and a WebP with 5 ms and 11 ms: Fresco shows them at 100, 100 and 11.
        assertThat(MediaProbe.probe(gif(delayCentis = 1, frames = 2))).isEqualTo(Probe(ImageFormat.GIF, 1, 1, 2, 200))
        assertThat(MediaProbe.probe(animatedWebp(listOf(5, 11)))).isEqualTo(Probe(ImageFormat.WEBP, 1, 1, 2, 111))
    }

    private fun gif(delayCentis: Int, frames: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write("GIF89a".toByteArray())
        out.write(byteArrayOf(1, 0, 1, 0, 0, 0, 0))
        repeat(frames) {
            out.write(byteArrayOf(0x21, 0xF9.toByte(), 4, 0, delayCentis.toByte(), 0, 0, 0))
            out.write(byteArrayOf(0x2C, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 0x44, 1, 0))
        }
        out.write(0x3B)
        return out.toByteArray()
    }

    private fun animatedWebp(durations: List<Int>): ByteArray {
        fun le(v: Int, n: Int) = ByteArray(n) { (v shr (8 * it)).toByte() }
        fun chunk(tag: String, body: ByteArray) = tag.toByteArray() + le(body.size, 4) + body
        val vp8x = chunk("VP8X", byteArrayOf(0x02, 0, 0, 0) + le(0, 3) + le(0, 3))
        val anim = chunk("ANIM", ByteArray(6))
        val frames = durations.fold(ByteArray(0)) { acc, ms -> acc + chunk("ANMF", le(0, 3) + le(0, 3) + le(0, 3) + le(0, 3) + le(ms, 3) + byteArrayOf(0)) }
        val body = "WEBP".toByteArray() + vp8x + anim + frames
        return "RIFF".toByteArray() + le(body.size, 4) + body
    }

    @Test
    fun animatedWebp() {
        assertThat(MediaProbe.probe(resource("anim_512_6f_100ms.webp"))).isEqualTo(Probe(ImageFormat.WEBP, 512, 512, 6, 600))
        assertThat(MediaProbe.probe(resource("anim_400x300_8f_60ms.webp"))).isEqualTo(Probe(ImageFormat.WEBP, 400, 300, 8, 480))
    }

    @Test
    fun stillWebpVariants() {
        for (name in listOf("static_512_lossless.webp", "static_512_lossy.webp", "static_512_noalpha_lossy.webp")) {
            assertThat(MediaProbe.probe(resource(name))).isEqualTo(Probe(ImageFormat.WEBP, 512, 512, 1, 0))
        }
    }

    @Test
    fun pngAndJpeg() {
        assertThat(MediaProbe.probe(resource("static_512.png"))).isEqualTo(Probe(ImageFormat.PNG, 512, 512, 1, 0))
        assertThat(MediaProbe.probe(resource("static_512.jpg"))).isEqualTo(Probe(ImageFormat.JPEG, 512, 512, 1, 0))
        assertThat(MediaProbe.probe(asset("jpeg_noisy_1200x1600.jpg"))).isEqualTo(Probe(ImageFormat.JPEG, 1200, 1600, 1, 0))
    }

    @Test
    fun unknownOrTruncatedNeverThrows() {
        assertThat(MediaProbe.probe(ByteArray(0)).format).isEqualTo(ImageFormat.OTHER)
        assertThat(MediaProbe.probe("not an image".toByteArray()).format).isEqualTo(ImageFormat.OTHER)
        for (name in listOf("anim_300x200_6f_80ms.gif", "anim_512_6f_100ms.webp", "static_512.jpg")) {
            val bytes = resource(name)
            for (n in listOf(4, 13, 30, 100, bytes.size / 2)) MediaProbe.probe(bytes.copyOf(n))
        }
    }

    @Test
    fun framesBeyondTenSecondsAreNotProcessed() {
        assertThat(StickerOutput.framesWithinLimit(Probe(ImageFormat.WEBP, 512, 512, 150, 15_000))).isEqualTo(100)
        assertThat(StickerOutput.framesWithinLimit(Probe(ImageFormat.GIF, 512, 512, 6, 480))).isEqualTo(6)
    }
}
