// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.media

import java.io.IOException

/** libwebp and giflib over JNI. Errors arrive as [IOException] with a technical message, for the log only. */
internal object NativeCodec {
    init {
        System.loadLibrary("tikcodec")
    }

    /** GIF or WebP to a 512×512 WebP that fits in [limit] bytes; [sourceFrames] are the ones that fit in 10 s. */
    @JvmStatic
    @Throws(IOException::class)
    external fun toWhatsApp(raw: ByteArray, sourceFrames: Int, limit: Int): ByteArray

    /** Already decoded ARGB pixels (PNG, JPEG…) to a 512×512 still WebP that fits in [limit] bytes. */
    @JvmStatic
    @Throws(IOException::class)
    external fun stillToWhatsApp(argb: IntArray, width: Int, height: Int, limit: Int): ByteArray
}
