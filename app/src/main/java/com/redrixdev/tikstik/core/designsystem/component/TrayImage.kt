// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil3.request.ImageRequest
import java.io.File

/** A pack's tray: the file keeps its name, so [version] changes whenever its image does. */
@Immutable
data class TrayImage(val file: File, val version: Long)

@Composable
fun rememberTrayRequest(tray: TrayImage): ImageRequest {
    val context = LocalContext.current
    return remember(tray) {
        ImageRequest.Builder(context).data(tray.file).memoryCacheKey("${tray.file.path}#${tray.version}").build()
    }
}
