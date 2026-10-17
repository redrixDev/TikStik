// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.importing

import com.redrixdev.tikstik.media.WebpQuantizer

/** Format, size, transparency, animation and quantizer of a cache image, from its first bytes only. */
data class CacheImageHeader(
    val format: Format,
    val width: Int,
    val height: Int,
    val alpha: Boolean,
    val animated: Boolean,
    // null if it was not within the bytes read (or the image is lossless): then it is not rejected because of it.
    val quantizer: Int? = null,
) {
    enum class Format { WEBP, PNG, OTHER }

    val maxSide: Int get() = maxOf(width, height)

    // In Saved and in the picker every WebP is a sticker (photo ones reach 1206×1132); PNG icons and tiny images are out.
    val savedSticker: Boolean get() = format == Format.WEBP && maxSide >= GeneralCacheRules.MIN_SIDE

    /** Without a URL naming the variant, quality is derived from the size. */
    val rank: Int
        get() = when {
            maxSide >= 300 -> StickerProvenance.RANK_FULL
            maxSide >= 200 -> StickerProvenance.RANK_MID
            else -> StickerProvenance.RANK_LOW
        }

    companion object {
        // Enough for the quantizer of simple WebPs (above all the opaque ones in the general cache).
        const val HEADER_BYTES = 96

        fun parse(b: ByteArray): CacheImageHeader? {
            fun u(i: Int) = if (i < b.size) b[i].toInt() and 0xFF else 0
            if (b.size >= 30 && u(0) == 'R'.code && u(8) == 'W'.code && u(9) == 'E'.code) {
                val quantizer = WebpQuantizer.firstFrame(b)
                return when (String(b, 12, 4, Charsets.US_ASCII)) {
                    "VP8X" -> CacheImageHeader(
                        Format.WEBP,
                        width = (u(24) or (u(25) shl 8) or (u(26) shl 16)) + 1,
                        height = (u(27) or (u(28) shl 8) or (u(29) shl 16)) + 1,
                        alpha = u(20) and 0x10 != 0,
                        animated = u(20) and 0x02 != 0,
                        quantizer = quantizer,
                    )
                    // Simple VP8: no transparency; the size follows the 9D 01 2A signature.
                    "VP8 " -> CacheImageHeader(
                        Format.WEBP,
                        width = (u(26) or (u(27) shl 8)) and 0x3FFF,
                        height = (u(28) or (u(29) shl 8)) and 0x3FFF,
                        alpha = false,
                        animated = false,
                        quantizer = quantizer,
                    )
                    // VP8L: 14 bits each of width and height after the 0x2F signature, plus the alpha bit.
                    "VP8L" -> {
                        val bits = u(21) or (u(22) shl 8) or (u(23) shl 16) or (u(24) shl 24)
                        CacheImageHeader(
                            Format.WEBP,
                            width = (bits and 0x3FFF) + 1,
                            height = ((bits ushr 14) and 0x3FFF) + 1,
                            alpha = (bits ushr 28) and 1 == 1,
                            animated = false,
                        )
                    }
                    else -> null
                }
            }
            if (b.size >= 24 && u(0) == 0x89 && u(1) == 'P'.code) {
                val w = (u(16) shl 24) or (u(17) shl 16) or (u(18) shl 8) or u(19)
                val h = (u(20) shl 24) or (u(21) shl 16) or (u(22) shl 8) or u(23)
                return CacheImageHeader(Format.PNG, w, h, alpha = true, animated = false)
            }
            return CacheImageHeader(Format.OTHER, 0, 0, alpha = false, animated = false)
        }
    }
}

/** Can an unrecorded image be a sticker? Rules from a census of a real cache (3,051 files). */
object GeneralCacheRules {
    // Below this, interface icons (72×56, 72×72…).
    const val MIN_SIDE = 90

    // TikTok's transparent cutouts never exceed this.
    const val MAX_CUTOUT_SIDE = 512

    // Video stickers use the interface template, but always 240 on a side.
    const val VIDEO_STICKER_SIDE = 240

    // `y_ac_qi` of avatars, covers, logos, cards and icons.
    val INTERFACE_QUANTIZERS = 20..30

    // Very tall images are covers, slides and screenshots; 640 wide, post previews.
    const val PORTRAIT = 1.7
    const val POST_PREVIEW_WIDTH = 640

    // What passes is not certain (a stray gift can slip through) and is offered unchecked; what is rejected is rejected on evidence.
    fun accepts(h: CacheImageHeader): Boolean {
        if (h.format != CacheImageHeader.Format.WEBP || h.maxSide < MIN_SIDE) return false
        if (h.animated) return true
        if (h.quantizer != null && h.quantizer in INTERFACE_QUANTIZERS && h.maxSide != VIDEO_STICKER_SIDE) return false
        if (h.alpha) return h.maxSide <= MAX_CUTOUT_SIDE
        return h.height < h.width * PORTRAIT && h.width != POST_PREVIEW_WIDTH
    }
}
