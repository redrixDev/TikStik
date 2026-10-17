// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import androidx.core.graphics.scale
import java.nio.ByteBuffer

/** The first frame via the system decoders, the same way the thresholds were measured; null if it cannot be decoded. */
object FirstFrame {

    /** Subsampled while the longer side stays at least 2×[maxSide]. */
    fun bitmap(raw: ByteArray, maxSide: Int): Bitmap? {
        val (width, height) = bounds(raw) ?: return null
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= maxSide) sample *= 2
        return runCatching { BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample }) }.getOrNull()
    }

    /** Exactly [width]×[height], without keeping the aspect ratio. */
    fun scaled(raw: ByteArray, width: Int, height: Int): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(raw))) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSize(width, height)
            }
        } else {
            val full = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return null
            full.scale(width, height).also { if (it !== full) full.recycle() }
        }
    }.getOrNull()

    // Before API 28 the downscaling differs and the fingerprint may vary by a few bits; the thresholds tolerate it.
    fun fingerprint(raw: ByteArray): Fingerprint? {
        val (width, height) = bounds(raw) ?: return null
        val grid = scaled(raw, Fingerprint.GRID_WIDTH, Fingerprint.GRID_HEIGHT) ?: return null
        return Fingerprint.of(grid.takePixels(), width, height)
    }

    fun isSolidRectangle(raw: ByteArray): Boolean? {
        val bitmap = runCatching { BitmapFactory.decodeByteArray(raw, 0, raw.size) }.getOrNull() ?: return null
        val (width, height) = bitmap.width to bitmap.height
        val alpha = bitmap.takePixels().also { pixels -> for (i in pixels.indices) pixels[i] = pixels[i] ushr 24 }
        return StickerShape.isSolidRectangle(alpha, width, height)
    }

    fun isFlat(raw: ByteArray): Boolean? = bitmap(raw, FLAT_SAMPLE_SIDE)?.let { StickerShape.isFlat(it.takePixels()) }

    private fun bounds(raw: ByteArray): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, options)
        return if (options.outWidth > 0 && options.outHeight > 0) options.outWidth to options.outHeight else null
    }

    /** The ARGB pixels; the bitmap is recycled. */
    fun Bitmap.takePixels(): IntArray {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        recycle()
        return pixels
    }

    private const val FLAT_SAMPLE_SIDE = 64
}
