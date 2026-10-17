// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.importing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** URL shapes seen in the real cache; ids and signatures are made up. */
class ProvenanceTest {

    private val q = "?rk3s=x&x-expires=1&x-signature=y"

    @Test
    fun fullStickerFromSingaporeCdn() {
        val p = Provenance.classify("https://p16-tiktok-dm-sticker-sign-sg.ibyteimg.com/tos-alisg-i-dhq7zx4c1p-sg/7d93e050b3074d669064d9eaaaabf257~tplv-dhq7zx4c1p-full.awebp$q")!!
        assertThat(p.key).isEqualTo("7d93e050b3074d669064d9eaaaabf257")
        assertThat(p.variant).isEqualTo("full")
        assertThat(p.rank).isEqualTo(StickerProvenance.RANK_FULL)
    }

    @Test
    fun videoStickerVariantsRankedByQuality() {
        val low = Provenance.classify("https://p16-tiktok-dm-sticker-sign.tiktokcdn-us.com/tos-useast5-i-ug1xhfywnj-tx/abcdef0123456789~tplv-ug1xhfywnj-video2sticker-low.awebp$q")!!
        val mid = Provenance.classify("https://p19-tiktok-dm-sticker-sign-useast8.tiktokcdn-us.com/tos-useast8-i-ug1xhfywnj-tx2/abcdef0123456789~tplv-ug1xhfywnj-video2sticker-mid.awebp$q")!!
        assertThat(low.key).isEqualTo(mid.key)
        assertThat(low.rank).isEqualTo(StickerProvenance.RANK_LOW)
        assertThat(mid.rank).isEqualTo(StickerProvenance.RANK_MID)
    }

    @Test
    fun photoStickersFromTheStickerCdnAreStickers() {
        val thumb = Provenance.classify("https://p16-tiktok-dm-sticker-sign.tiktokcdn-us.com/tos-useast5-i-ug1xhfywnj-tx/abcdef0123456789~tplv-ug1xhfywnj-photo-comment-thumbnail.awebp$q")!!
        val full = Provenance.classify("https://p16-tiktok-dm-sticker-sign.tiktokcdn-us.com/x/abcdef0123456789~tplv-ug1xhfywnj-photo-comment-full.awebp$q")!!
        assertThat(thumb.key).isEqualTo(full.key)
        assertThat(full.rank).isEqualTo(StickerProvenance.RANK_FULL)
    }

    @Test
    fun avatarsCoversAndIconsAreRejectedByHost() {
        assertThat(Provenance.classify("https://p16-common-sign.tiktokcdn-us.com/tos-alisg-avt-0068/19ca1eb0b1a9c054a36019b5225f1329~tplv-tiktokx-cropcenter:100:100.jpeg$q")).isNull()
        assertThat(Provenance.classify("https://p16-common-sign.tiktokcdn.com/tos-alisg-p-0037/o0fqCeRCE9DlpWNExIFKcEApBMAhIgCnimAcdA~tplv-photomode-zoomcover:720:720.avif$q")).isNull()
        assertThat(Provenance.classify("https://p16-comment-sign-sg.tiktokcdn.com/abc/abcdef0123456789~tplv-jj85edgx6n-image-medium.jpeg$q")).isNull()
        assertThat(Provenance.classify("https://sf16-tiktok-im-scp-sign.tiktokcdn-us.com/a/b.png")).isNull()
    }

    @Test
    fun rejectsLookalikeAndInsecureHosts() {
        assertThat(Provenance.classify("http://p16-tiktok-dm-sticker-sign.tiktokcdn-us.com/x/abcdef0123456789~tplv-a-full.awebp")).isNull()
        assertThat(Provenance.classify("https://p16-tiktok-dm-sticker-sign.tiktokcdn-us.com.evil.example/x/abcdef0123456789~tplv-a-full.awebp")).isNull()
        assertThat(Provenance.classify("not a url")).isNull()
    }
}
