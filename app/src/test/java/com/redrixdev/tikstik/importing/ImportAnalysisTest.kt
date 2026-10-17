// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.importing

import com.google.common.truth.Truth.assertThat
import com.redrixdev.tikstik.access.CacheFile
import com.redrixdev.tikstik.core.data.db.StickerEntity
import com.redrixdev.tikstik.media.Fingerprint
import org.junit.Test
import java.io.File
import java.util.Base64

class ImportAnalysisTest {

    // Lossy WebP from libwebp, quality 70 (quantizer 28, the interface band) and 100 (quantizer 0).
    private val q28 = Base64.getDecoder().decode("UklGRkgAAABXRUJQVlA4IDwAAADwAQCdASoQABAAAkA4JbACdAEPDBLyyoAA/v0gSFszfoL7OJ8W3qD1gUipJgn/jFX/q5BZ/+hsP/9doAA=")
    private val q0 = Base64.getDecoder().decode(
        "UklGRowAAABXRUJQVlA4IIAAAABQBACdASoQABAAAAAAJbACdLoB+AH4gU4D6AN0A+wDU/22A/AAJu5bcrn4AP7//6Pf4//hHFWMPeIarSH8eUZ//hoqKT6jIIpibonXnP5siYOjSAqwd/gzGcukXom3B12t8H+WyFlJnLMv8BR0s5+NQ/vM7Szn40eweeruP4AAAA==",
    )
    private val animated = javaClass.getResourceAsStream("/media/anim_400x300_8f_60ms.webp")!!.readBytes()

    // The same frame with a different size in the header: the quantizer does not change.
    private fun sized(webp: ByteArray, w: Int, h: Int) = webp.copyOf().also {
        it[26] = w.toByte()
        it[27] = (w shr 8).toByte()
        it[28] = h.toByte()
        it[29] = (h shr 8).toByte()
    }

    private fun fingerprint(hex: String) = Fingerprint.parse("$hex:100")!!

    private val rich = fingerprint("0f0f0f0f0f0f0f0f" + "f0f0f0f0f0f0f0f0" + "00ff00ff00ff00ff" + "123456789abcdef0")
    private val other = fingerprint("ff00ff00ff00ff00" + "0f0f0f0f0f0f0f0f" + "fedcba9876543210" + "f0f0f0f0f0f0f0f0")
    private val blank = fingerprint("0".repeat(64))

    // A gray card: the fingerprint has bits, but no contrast.
    private val flatImage = sized(q0, 168, 168)

    private val images = object : StickerImages {
        override fun fingerprint(bytes: ByteArray) = rich

        override fun solidRectangle(bytes: ByteArray) = true

        override fun flat(bytes: ByteArray) = bytes.contentEquals(flatImage)
    }

    private fun sticker(origin: StickerOrigin, key: String = "k", rank: Int = StickerProvenance.RANK_MID, seenAt: Long = 100, opaque: Boolean = false) =
        CachedSticker(StickerProvenance(key, "v", rank), CacheFile("/x/$key.cnt", 1000, seenAt), origin, seenAt, opaque)

    private fun judge(origin: StickerOrigin, bytes: ByteArray, fp: Fingerprint? = rich, refs: List<Fingerprint> = emptyList(), opaque: Boolean = false) =
        ImportAnalysis.judge(sticker(origin, opaque = opaque), File("x"), bytes, fp, refs, images)

    @Test
    fun generalCacheIsDecidedWithTheWholeFile() {
        // Profile photo (interface template, 100×100) out; video sticker (240) and meme in.
        assertThat(judge(StickerOrigin.SEEN, sized(q28, 100, 100), opaque = true)).isEqualTo(ImportAnalysis.Verdict.NotSticker)
        assertThat(judge(StickerOrigin.SEEN, sized(q28, 240, 240), opaque = true)).isInstanceOf(ImportAnalysis.Verdict.Sticker::class.java)
        assertThat(judge(StickerOrigin.SEEN, sized(q0, 1170, 1302), opaque = true)).isInstanceOf(ImportAnalysis.Verdict.Sticker::class.java)
        assertThat(judge(StickerOrigin.SEEN, sized(q0, 360, 640), opaque = true)).isEqualTo(ImportAnalysis.Verdict.NotSticker)
        assertThat(judge(StickerOrigin.SEEN, animated)).isInstanceOf(ImportAnalysis.Verdict.Sticker::class.java)
    }

    @Test
    fun blankStillsAndTwinsOfProfilePhotosAreNotStickers() {
        assertThat(judge(StickerOrigin.SEEN, sized(q0, 170, 170), fp = blank, opaque = true)).isEqualTo(ImportAnalysis.Verdict.NotSticker)
        assertThat(judge(StickerOrigin.SEEN, flatImage, opaque = true)).isEqualTo(ImportAnalysis.Verdict.NotSticker)
        // An animated sticker may start blank.
        assertThat(judge(StickerOrigin.SEEN, animated, fp = blank)).isInstanceOf(ImportAnalysis.Verdict.Sticker::class.java)
        assertThat(judge(StickerOrigin.SEEN, sized(q0, 300, 260), refs = listOf(rich), opaque = true)).isEqualTo(ImportAnalysis.Verdict.NotSticker)
    }

    @Test
    fun savedIsAlwaysAStickerButUnreadableIsReported() {
        assertThat(judge(StickerOrigin.SAVED, sized(q28, 100, 100))).isInstanceOf(ImportAnalysis.Verdict.Sticker::class.java)
        assertThat(judge(StickerOrigin.SAVED, sized(q28, 100, 100), fp = null)).isEqualTo(ImportAnalysis.Verdict.Unreadable)
    }

    @Test
    fun onlySolidRectanglesWithoutRecordAreSure() {
        val cutout = judge(StickerOrigin.SEEN, animated) as ImportAnalysis.Verdict.Sticker
        val opaquePhoto = judge(StickerOrigin.SEEN, sized(q0, 1170, 1302), opaque = true) as ImportAnalysis.Verdict.Sticker
        assertThat(cutout.candidate.sure).isTrue()
        assertThat(opaquePhoto.candidate.sure).isFalse()
    }

    private fun candidate(key: String, fp: Fingerprint, animatedSticker: Boolean, rank: Int = StickerProvenance.RANK_MID, seenAt: Long = 100): Candidate {
        val bytes = if (animatedSticker) animated else sized(q0, 240, 240)
        val verdict = ImportAnalysis.judge(sticker(StickerOrigin.SAVED, key, rank, seenAt), File(key), bytes, fp, emptyList(), images)
        return (verdict as ImportAnalysis.Verdict.Sticker).candidate
    }

    private fun owned(id: String, key: String?, fp: Fingerprint, animatedSticker: Boolean, rank: Int = StickerProvenance.RANK_MID) =
        StickerEntity(id, "webp", animatedSticker, 240, 240, if (animatedSticker) 8 else 1, 0, 1000, key, null, rank, fp.encode(), 0, 0)

    @Test
    fun uniqueKeepsOnePerImageButAnimatedAndStillApart() {
        val a = candidate("a", rich, animatedSticker = false, seenAt = 100)
        val b = candidate("b", rich, animatedSticker = false, seenAt = 300)
        val c = candidate("c", rich, animatedSticker = true, seenAt = 200)
        val unique = ImportAnalysis.unique(listOf(a, b, c))
        assertThat(unique).hasSize(2)
        assertThat(unique.first().seenAt).isEqualTo(300L)
    }

    @Test
    fun classifyAgainstTheCollection() {
        val stillOwned = owned("s1", "kStill", rich, animatedSticker = false)
        val lowOwned = owned("s2", "kLow", other, animatedSticker = false, rank = StickerProvenance.RANK_LOW)
        val animatedVersion = candidate("kAnim", rich, animatedSticker = true)
        val sameStill = candidate("kOther", rich, animatedSticker = false)
        val betterQuality = candidate("kLow", other, animatedSticker = false, rank = StickerProvenance.RANK_FULL, seenAt = 500)
        val review = ImportAnalysis.classify(
            listOf(animatedVersion, sameStill, betterQuality),
            listOf(stillOwned, lowOwned),
            shown = setOf("kAnim"),
        )
        val status = review.candidates.associate { it.key to it.status }
        // Owning the still version does not hide the animated one.
        assertThat(status["kAnim"]).isEqualTo(CandidateStatus.KNOWN)
        assertThat(status["kOther"]).isEqualTo(CandidateStatus.IN_COLLECTION)
        assertThat(status["kLow"]).isEqualTo(CandidateStatus.IN_COLLECTION)
        assertThat(review.upgrades.map { it.owned.id to it.better.key }).containsExactly("s2" to "kLow")
        assertThat(review.reseen).containsExactly("s1", 100_000L, "s2", 500_000L)
    }

    @Test
    fun newOnlyTheFirstTimeItShowsUp() {
        val shownBefore = candidate("k1", rich, animatedSticker = false, seenAt = 900)
        val firstTime = candidate("k2", other, animatedSticker = false, seenAt = 100)
        val review = ImportAnalysis.classify(listOf(shownBefore, firstTime), emptyList(), shown = setOf("k1"))
        val status = review.candidates.associate { it.key to it.status }
        assertThat(status["k1"]).isEqualTo(CandidateStatus.KNOWN)
        assertThat(status["k2"]).isEqualTo(CandidateStatus.NEW)
    }

    @Test
    fun whatYouOwnIsShownOnlyIfSeenInTheLast24Hours() {
        val now = 100_000L
        val recent = candidate("r", rich, animatedSticker = false, seenAt = now - 3_600).copy(status = CandidateStatus.IN_COLLECTION)
        val old = candidate("o", other, animatedSticker = false, seenAt = now - 25 * 3_600).copy(status = CandidateStatus.IN_COLLECTION)
        val oldButNew = candidate("n", other, animatedSticker = true, seenAt = now - 48 * 3_600)
        assertThat(ImportAnalysis.shown(listOf(old, oldButNew, recent), now).map { it.key }).containsExactly("r", "n").inOrder()
    }
}
