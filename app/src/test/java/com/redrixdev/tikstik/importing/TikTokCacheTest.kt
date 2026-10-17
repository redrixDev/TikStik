// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.importing

import com.google.common.truth.Truth.assertThat
import com.redrixdev.tikstik.access.CacheFile
import com.redrixdev.tikstik.access.CacheReader
import org.junit.Test

/** Every place in the TikTok cache where stickers end up, and what must stay out. */
class TikTokCacheTest {

    private val root = TikTokCache.root("/storage/emulated/0/Android/data", "com.zhiliaoapp.musically")
    private val animatedAlpha = vp8x(0x12, 240, 240)

    // A 120×120 PNG header: the large emojis from messages.
    private val png120 = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52, 0, 0, 0, 120, 0, 0, 0, 120)

    private class FakeReader(private val files: Map<CacheFile, ByteArray>) : CacheReader {
        override fun list(root: String) = files.keys.filter { it.path.startsWith(root) }

        override fun heads(files: List<CacheFile>, count: Int) = files.associate { it.path to this.files.getValue(it).take(count).toByteArray() }

        override fun read(files: List<CacheFile>) = files.associate { it.path to this.files.getValue(it) }
    }

    private fun file(path: String, modified: Long = 1) = CacheFile(path, 1000, modified)

    private fun config(name: String, url: String) = file("$root/image_config/v2.ols100.1-config/1/$name") to "viewWidth=1|url=$url|".toByteArray()

    @Test
    fun findsStickersInEveryKnownPlace() {
        val cdn = "$root/fresco_cache/v2.ols100.1/1/cdn.cnt"
        val photo = "$root/fresco_cache/v2.ols100.1/2/photo.cnt"
        val saved = "$root/fresco_custom_cache/stable_sticker/v2.ols100.1/3/saved.cnt"
        val seen = "$root/fresco_cache/v2.ols100.1/4/seen.cnt"
        val store = "$root/fresco_custom_cache/store_sticker/v2.ols100.1/5/store.cnt"
        val icon = "$root/fresco_cache/v2.ols100.1/6/icon.cnt"
        val avatar = "$root/fresco_custom_cache/dm_avatar/v2.ols100.1/7/avatar.cnt"
        val cover = "$root/fresco_cache/v2.ols100.1/8/cover.cnt"
        val created = "$root/fresco_custom_cache/stable_sticker/v2.ols100.1/9/created.cnt"
        val bigSaved = "$root/fresco_custom_cache/stable_sticker/v2.ols100.1/10/big.cnt"
        val opaqueVideo = "$root/fresco_cache/v2.ols100.1/11/opaque.cnt"
        val photoSticker = "$root/fresco_cache/v2.ols100.1/12/photo_sticker.cnt"
        val dmSticker = "$root/fresco_custom_cache/dm_default/v2.ols100.1/13/dm_sticker.cnt"
        val dmEmoji = "$root/fresco_custom_cache/dm_default/v2.ols100.1/14/dm_emoji.cnt"
        val reader = FakeReader(
            mapOf(
                file(cdn) to animatedAlpha,
                file(photo) to vp8x(0x00, 614, 568),
                file(saved) to vp8x(0x10, 498, 498),
                file(seen) to animatedAlpha,
                file(store) to vp8x(0x12, 300, 300),
                file(icon) to vp8x(0x10, 72, 56),
                file(avatar) to vp8x(0x10, 168, 168),
                // Portrait and opaque in the general cache: a cover.
                file(cover) to vp8x(0x00, 360, 640),
                // Video sticker without a record (240 template).
                file(opaqueVideo) to vp8x(0x00, 240, 240),
                // Photo sticker or meme from a comment.
                file(photoSticker) to vp8x(0x20, 735, 676),
                file(dmSticker) to animatedAlpha,
                file(dmEmoji) to png120,
                // Opaque square in the sticker cache: created from a video.
                file(created) to vp8x(0x00, 240, 240),
                // Saved sticker made from a photo: large and without alpha.
                file(bigSaved) to vp8x(0x00, 1206, 1132),
                config("cdn.cnt", "https://p16-tiktok-dm-sticker-sign-sg.ibyteimg.com/tos-alisg-i-dhq7zx4c1p-sg/0274eed0c5044ccab8645bd85bbcbeac~tplv-dhq7zx4c1p-video2sticker-mid.awebp?x=1"),
                config("photo.cnt", "https://p16-tiktok-dm-sticker-sign-sg.ibyteimg.com/tos-alisg-i-dhq7zx4c1p-sg/abcdef0123456789~tplv-dhq7zx4c1p-photo-comment-full.awebp"),
            ),
        )
        val scan = TikTokCache.scan(reader, listOf(root))
        assertThat(scan.references.map { it.path }).containsExactly(avatar)
        assertThat(scan.stickers.associate { it.file.path to it.origin }).containsExactly(
            cdn, StickerOrigin.CDN,
            photo, StickerOrigin.CDN,
            bigSaved, StickerOrigin.SAVED,
            saved, StickerOrigin.SAVED,
            created, StickerOrigin.SAVED,
            seen, StickerOrigin.SEEN,
            opaqueVideo, StickerOrigin.SEEN,
            photoSticker, StickerOrigin.SEEN,
            dmSticker, StickerOrigin.SEEN,
            store, StickerOrigin.PICKER,
        )
    }

    @Test
    fun savedStickerJustSeenInACommentIsSavedAndRecent() {
        // Same name in Saved (days old) and in the general cache (just now), without a record.
        val saved = file("$root/fresco_custom_cache/stable_sticker/v2.ols100.1/37/same.cnt", modified = 100)
        val now = file("$root/fresco_cache/v2.ols100.1/37/same.cnt", modified = 9_000)
        val found = TikTokCache.scan(FakeReader(mapOf(now to vp8x(0x00, 240, 240), saved to vp8x(0x00, 240, 240))), listOf(root)).stickers.single()
        assertThat(found.origin).isEqualTo(StickerOrigin.SAVED)
        assertThat(found.seenAt).isEqualTo(9_000L)
    }

    @Test
    fun savedStickerIsNeverDroppedByARecord() {
        val saved = "$root/fresco_custom_cache/stable_sticker/v2.ols100.1/40/mine.cnt"
        val reader = FakeReader(mapOf(file(saved) to vp8x(0x10, 498, 498), config("mine.cnt", "https://p16-common-sign.tiktokcdn.com/x/abcdefabcdef~tplv-x-origin.webp")))
        val scan = TikTokCache.scan(reader, listOf(root))
        assertThat(scan.stickers.map { it.file.path to it.origin }).containsExactly(saved to StickerOrigin.SAVED)
        assertThat(scan.references).isEmpty()
    }

    @Test
    fun aFileTooBigToBeAStickerIsNeverRead() {
        val huge = CacheFile("$root/fresco_custom_cache/stable_sticker/v2.ols100.1/41/huge.cnt", 64L * 1024 * 1024, 1)
        assertThat(TikTokCache.scan(FakeReader(mapOf(huge to vp8x(0x10, 498, 498))), listOf(root)).stickers).isEmpty()
    }

    @Test
    fun theBestVariantWinsWithTheLatestDate() {
        val low = "$root/fresco_cache/v2.ols100.1/1/low.cnt"
        val mid = "$root/fresco_cache/v2.ols100.1/2/mid.cnt"
        val reader = FakeReader(
            mapOf(
                file(low, modified = 500) to animatedAlpha,
                file(mid, modified = 100) to animatedAlpha,
                config("low.cnt", "https://p16-tiktok-dm-sticker-sign.tiktokcdn-us.com/x/abcdef0123456789~tplv-a-video2sticker-low.awebp"),
                config("mid.cnt", "https://p16-tiktok-dm-sticker-sign.tiktokcdn-us.com/x/abcdef0123456789~tplv-a-video2sticker-mid.awebp"),
            ),
        )
        val best = TikTokCache.scan(reader, listOf(root)).stickers.single()
        assertThat(best.file.path).isEqualTo(mid)
        assertThat(best.seenAt).isEqualTo(500L)
    }
}
