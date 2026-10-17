// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.importing

import com.redrixdev.tikstik.media.FirstFrame
import javax.inject.Inject

/** With the system decoders, which the thresholds were measured with. */
class AndroidStickerImages @Inject constructor() : StickerImages {
    override fun fingerprint(bytes: ByteArray) = FirstFrame.fingerprint(bytes)

    override fun solidRectangle(bytes: ByteArray) = FirstFrame.isSolidRectangle(bytes)

    override fun flat(bytes: ByteArray) = FirstFrame.isFlat(bytes)
}
