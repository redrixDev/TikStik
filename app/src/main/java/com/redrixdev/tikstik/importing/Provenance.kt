// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.importing

import java.net.URI

/** Where a sticker comes from: the same sticker shares [key] across variants; [rank] is its quality. */
data class StickerProvenance(val key: String, val variant: String, val rank: Int) {
    companion object {
        const val RANK_LOW = 1
        const val RANK_MID = 2
        const val RANK_FULL = 3
    }
}

/** The log URL (`image_config`) is only looked at in memory: it is signed and never stored or logged. */
object Provenance {

    // The host decides: the sticker CDN serves stickers, including those made from photos (`photo-comment-*`).
    private val stickerHost =
        Regex("^p\\d+-[a-z0-9-]*sticker[a-z0-9-]*\\.(tiktokcdn(-[a-z]+)?\\.com|ibyteimg\\.com|byteimg\\.com)$")

    fun classify(url: String): StickerProvenance? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        if (uri.scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null
        if (!stickerHost.matches(host)) return null
        val last = uri.rawPath?.substringAfterLast('/')?.takeIf { it.isNotEmpty() } ?: return null
        val key = last.substringBefore('~').takeIf { it.length >= 8 } ?: return null
        // `~tplv-<templateId>-<variant>.<ext>`: the template gives the variant and the quality.
        val template = last.substringAfter("~tplv-", "").substringBeforeLast('.')
        val variant = template.substringAfter('-', template).ifEmpty { "unknown" }
        val rank = when {
            variant == "full" || variant.endsWith("-full") || variant.endsWith("-high") -> StickerProvenance.RANK_FULL
            variant.endsWith("-mid") -> StickerProvenance.RANK_MID
            else -> StickerProvenance.RANK_LOW
        }
        return StickerProvenance(key, variant, rank)
    }

    /** Without the log there is no CDN id: the key is the cache name and the quality comes from the size. */
    fun fromCacheName(cacheName: String, rank: Int) = StickerProvenance(
        key = "cache_" + cacheName.substringBefore('.'),
        variant = when (rank) {
            StickerProvenance.RANK_FULL -> "cache-full"
            StickerProvenance.RANK_MID -> "cache-mid"
            else -> "cache-low"
        },
        rank = rank,
    )
}
