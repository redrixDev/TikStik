// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.common

import androidx.core.util.AtomicFile
import java.io.File

/** Writes to a temporary file and renames it: a half-written file is never left behind. */
fun File.writeAtomically(bytes: ByteArray) {
    parentFile?.mkdirs()
    val atomic = AtomicFile(this)
    val out = atomic.startWrite()
    try {
        out.write(bytes)
        atomic.finishWrite(out)
    } catch (e: Throwable) {
        atomic.failWrite(out)
        throw e
    }
}
