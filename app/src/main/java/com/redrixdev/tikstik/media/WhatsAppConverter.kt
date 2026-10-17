// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.media

import android.graphics.Bitmap
import com.redrixdev.tikstik.media.FirstFrame.takePixels
import java.io.ByteArrayOutputStream
import java.io.IOException

/** The original is never touched: the WhatsApp copy comes from it, and the pack tray from the copy. */
object WhatsAppConverter {

    // Stills that are neither GIF nor WebP are decoded to at most this size: the output is 512.
    private const val STILL_DECODE_SIDE = 1024

    @Throws(IOException::class)
    fun sticker(original: ByteArray, animated: Boolean): ByteArray {
        val limit = StickerOutput.limit(animated)
        val probe = MediaProbe.probe(original)
        val copy = when {
            StickerOutput.isValidSticker(original, animated) -> original
            probe.format == ImageFormat.WEBP || probe.format == ImageFormat.GIF ->
                NativeCodec.toWhatsApp(original, StickerOutput.framesWithinLimit(probe), limit)
            else -> {
                val bitmap = FirstFrame.bitmap(original, STILL_DECODE_SIDE) ?: throw IOException("undecodable ${probe.format}")
                val (width, height) = bitmap.width to bitmap.height
                NativeCodec.stillToWhatsApp(bitmap.takePixels(), width, height, limit)
            }
        }
        if (!StickerOutput.isValidSticker(copy, animated)) throw IOException("output breaks whatsapp rules")
        return copy
    }

    /** The tray comes from the first frame of the WhatsApp copy, which is already square. */
    @Throws(IOException::class)
    fun tray(copy: ByteArray): ByteArray {
        val bitmap = FirstFrame.scaled(copy, StickerOutput.TRAY_SIDE, StickerOutput.TRAY_SIDE) ?: throw IOException("undecodable copy")
        val png = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
        if (!StickerOutput.isValidTray(png)) throw IOException("tray breaks whatsapp rules")
        return png
    }
}
