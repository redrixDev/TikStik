// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.ui

import androidx.compose.runtime.Immutable
import java.io.File

/** A collection sticker, just enough to draw it. */
@Immutable
data class StickerUi(val id: String, val file: File, val animated: Boolean, val inPack: Boolean = false)
