// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Base64

// 16×16 WebPs made with libwebp at known quality: the higher the quality, the lower the quantizer.
class WebpQuantizerTest {

    private fun webp(base64: String) = Base64.getDecoder().decode(base64)

    private val q70 = webp("UklGRkgAAABXRUJQVlA4IDwAAADwAQCdASoQABAAAkA4JbACdAEPDBLyyoAA/v0gSFszfoL7OJ8W3qD1gUipJgn/jFX/q5BZ/+hsP/9doAA=")
    private val q80 = webp("UklGRlIAAABXRUJQVlA4IEYAAADQAQCdASoQABAAAUAmJbACdAEOtYmoAAD+/pJTK/q69hsr/m1zEfLZ3olK4NU8uH7Ryd1/vl1//l6Z6r//60/PS2/vgwAA")
    private val q100 = webp(
        "UklGRowAAABXRUJQVlA4IIAAAABQBACdASoQABAAAAAAJbACdLoB+AH4gU4D6AN0A+wDU/22A/AAJu5bcrn4AP7//6Pf4//hHFWMPeIarSH8eUZ//hoqKT6jIIpibonXnP5siYOjSAqwd/gzGcukXom3B12t8H+WyFlJnLMv8BR0s5+NQ/vM7Szn40eweeruP4AAAA==",
    )
    private val alphaQ80 = webp(
        "UklGRsgAAABXRUJQVlA4WAoAAAAQAAAADwAADwAAQUxQSBkAAAABDzD/ERGCLMAEfGxTSaEzhgwi+t+CUPcAAFZQOCCIAAAAsAIAnQEqEAAQAAFAJiWwAnQGKa/M/2AJtKedvZqAAP7+d05Ipbp4/RYHea6up5ftWp+WoGAYD13O8tdDVVjqID71O8ViV3vJIZu9NSr0a9gaAy6OV00tVHCL7T5/5SFc/qarSdxKcyj5jX3Dbgl92qWyXHLz/O7T//L0z1X//1p+elt/fBgAAA==",
    )
    private val lossless = webp("UklGRiwAAABXRUJQVlA4TCAAAAAvD8ADALmM6H/sClGIgvc/ICQgTPD/sKgORMQEgNW7AA==")
    private val animQ80 = webp(
        "UklGRvAAAABXRUJQVlA4WAoAAAACAAAADwAADwAAQU5JTQYAAAAAAAAAAABBTk1GXgAAAAAAAAAAAA8AAA8AAGQAAAJWUDggRgAAANABAJ0BKhAAEAABQCYlsAJ0AQ61iagAAP7+klMr+rr2Gyv+bXMR8tneiUrg1Ty4ftHJ3X++XX/+Xpnqv//rT89Lb++DAABBTk1GXgAAAAAAAAAAAA8AAA8AAGQAAABWUDggRgAAAFQCAJ0BKhAAEAAAACYlsAJ0yfw9/0/1LXx4mAD+76Glde2vBL9lN+DHPTl0KZdhshslO5w3/1ek+q+1qY7Wpj+2enYAAAA=",
    )

    @Test
    fun lossyStillsByQuality() {
        assertThat(WebpQuantizer.firstFrame(q70)).isEqualTo(28)
        assertThat(WebpQuantizer.firstFrame(q80)).isEqualTo(19)
        assertThat(WebpQuantizer.firstFrame(q100)).isEqualTo(0)
    }

    @Test
    fun alphaAndAnimationUseTheFirstFrame() {
        assertThat(WebpQuantizer.firstFrame(alphaQ80)).isEqualTo(19)
        assertThat(WebpQuantizer.firstFrame(animQ80)).isEqualTo(19)
    }

    @Test
    fun losslessHasNone() {
        assertThat(WebpQuantizer.firstFrame(lossless)).isNull()
    }

    @Test
    fun worksFromTheStartOfTheFileAndGivesUpWhenItIsTooShort() {
        // What is read from the cache without pulling the whole file: the first bytes.
        assertThat(WebpQuantizer.firstFrame(q70.copyOf(48))).isEqualTo(28)
        assertThat(WebpQuantizer.firstFrame(animQ80.copyOf(110))).isEqualTo(19)
        assertThat(WebpQuantizer.firstFrame(q70.copyOf(30))).isNull()
        assertThat(WebpQuantizer.firstFrame(ByteArray(0))).isNull()
        assertThat(WebpQuantizer.firstFrame("not a webp at all".toByteArray())).isNull()
    }

    @Test
    fun aChunkSizeNear2To31DoesNotOverflow() {
        // A chunk with a forged size (0x7FFFFFF0) overflowed the Int, seemed to fit and was read past the end of the array.
        val huge = byteArrayOf(0xF0.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F)
        for (fourcc in listOf("ANMF", "VP8 ", "VP8X")) {
            val b = "RIFF".toByteArray() + ByteArray(4) + "WEBP".toByteArray() + fourcc.toByteArray() + huge + ByteArray(40)
            assertThat(WebpQuantizer.firstFrame(b)).isNull()
            MediaProbe.probe(b)
        }
    }
}
