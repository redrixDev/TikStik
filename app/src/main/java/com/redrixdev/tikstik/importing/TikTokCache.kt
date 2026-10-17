// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.importing

import com.redrixdev.tikstik.access.CacheFile
import com.redrixdev.tikstik.access.CacheReader

/** Where TikTok showed it; only the general cache without a record needs its shape checked. */
enum class StickerOrigin {
    // Saved and Created (`stable_sticker`): they are yours.
    SAVED,

    // With a record from the sticker CDN, in any cache (comments, chats…).
    CDN,

    // The sticker picker (`store_sticker`, `static_sticker`): only library stickers live there.
    PICKER,

    // General or message cache without a record: mixed with profile photos, covers, icons and gifts.
    SEEN,
    ;

    val needsShapeCheck: Boolean get() = this == SEEN
}

/** [seenAt] (s): the most recent copy in any of its caches; [opaque]: still, with neither alpha nor record, so its shape does not confirm it. */
data class CachedSticker(
    val provenance: StickerProvenance,
    val file: CacheFile,
    val origin: StickerOrigin,
    val seenAt: Long = file.modifiedSec,
    val opaque: Boolean = false,
)

/** [references]: what TikTok identifies as something else; an unrecorded twin of one of them is that, not a sticker. */
data class CacheScan(val stickers: List<CachedSticker>, val references: List<CacheFile>)

/** Every image Fresco shows, in `…/cache/picture/<cache>/…/<name>.cnt`; the origin comes from the record or the folder. */
object TikTokCache {

    val PACKAGES = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")

    /** [androidData]: `…/Android/data` on primary storage (the shell and the app see it the same way). */
    fun root(androidData: String, pkg: String) = "$androidData/$pkg/cache/picture"

    // Caches with no stickers of yours: avatars, searches, stories, icons…
    private val IGNORED = listOf("dm_avatar", "search_cache", "story_skylight", "photomode", "tab_icon", "coin_bottom_tab", "qrcode", "tools_default")

    // Only profile photos: they serve as references.
    private const val AVATARS = "/dm_avatar/"
    private const val CONFIG = "image_config"
    private const val CONFIG_BYTES = 4096

    // The codec takes at most 16 MB (codec.cpp): a bigger file is no sticker and is not read whole.
    private const val MAX_IMAGE_BYTES = 16L * 1024 * 1024

    fun originOf(path: String): StickerOrigin? = when {
        CONFIG in path || IGNORED.any { "/$it" in path } -> null
        "/stable_sticker/" in path -> StickerOrigin.SAVED
        "/store_sticker/" in path || "/static_sticker/" in path -> StickerOrigin.PICKER
        "/fresco_cache/" in path || "/fresco_small_cache/" in path || "/dm_default/" in path -> StickerOrigin.SEEN
        else -> null
    }

    fun scan(reader: CacheReader, roots: List<String>): CacheScan {
        val found = mutableListOf<CachedSticker>()
        val references = mutableListOf<CacheFile>()
        for (root in roots) {
            val listed = reader.list(root)
            val images = listed.filter { CONFIG !in it.path && it.size in 1..MAX_IMAGE_BYTES }.groupBy { it.path.substringAfterLast('/') }
            val lastSeen = images.mapValues { (_, copies) -> copies.maxOf { it.modifiedSec } }
            val identified = HashSet<String>()
            // 1) With a record: if the URL is from the sticker CDN, it is a sticker with an id and a variant.
            val configs = reader.heads(listed.filter { CONFIG in it.path }, CONFIG_BYTES)
            for ((configPath, content) in configs) {
                val url = recordUrl(content) ?: continue
                val provenance = Provenance.classify(url)
                val name = configPath.substringAfterLast('/')
                val copies = images[name].orEmpty()
                // What is in Saved is yours even if its record is not from the sticker CDN.
                if (provenance == null && copies.any { originOf(it.path) == StickerOrigin.SAVED }) continue
                identified += name
                if (provenance == null) {
                    copies.firstOrNull { originOf(it.path) == StickerOrigin.SEEN }?.let { references += it }
                    continue
                }
                val image = copies.maxWithOrNull(compareBy<CacheFile>({ "stable_sticker" in it.path }, { it.modifiedSec })) ?: continue
                found += CachedSticker(provenance, image, StickerOrigin.CDN, lastSeen.getValue(name))
            }
            references += images.values.flatten().filter { AVATARS in it.path }
            // 2) Without a record: the folder gives the origin and the header filters; if it is in Saved, that copy wins.
            val unidentified = images.filterKeys { it !in identified }
            val headers = reader.heads(unidentified.values.flatten().filter { originOf(it.path) != null }, CacheImageHeader.HEADER_BYTES)
            for ((name, copies) in unidentified) {
                for (file in copies.sortedBy { originOf(it.path)?.ordinal ?: Int.MAX_VALUE }) {
                    val origin = originOf(file.path) ?: continue
                    val header = headers[file.path]?.let(CacheImageHeader::parse) ?: continue
                    val ok = if (origin == StickerOrigin.SEEN) GeneralCacheRules.accepts(header) else header.savedSticker
                    if (!ok) continue
                    val opaque = origin == StickerOrigin.SEEN && !header.alpha && !header.animated
                    found += CachedSticker(Provenance.fromCacheName(name, header.rank), file, origin, lastSeen.getValue(name), opaque)
                    break
                }
            }
        }
        return CacheScan(bestPerSticker(found), references)
    }

    // The record is text with fields separated by "|", newlines or tabs.
    private fun recordUrl(content: ByteArray): String? =
        String(content, Charsets.UTF_8).split('|', '\n', '\t').firstOrNull { it.startsWith("url=") }?.removePrefix("url=")

    /** The best of its variants (low, mid, full), with the most recent date of all of them. */
    fun bestPerSticker(items: List<CachedSticker>): List<CachedSticker> =
        items.groupBy { it.provenance.key }
            .map { (_, variants) -> variants.maxWith(compareBy({ it.provenance.rank }, { it.file.size })).copy(seenAt = variants.maxOf { it.seenAt }) }
            .sortedByDescending { it.seenAt }
}
