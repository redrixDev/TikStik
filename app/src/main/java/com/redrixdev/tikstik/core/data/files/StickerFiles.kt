// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.data.files

import androidx.core.util.AtomicFile
import com.redrixdev.tikstik.core.common.writeAtomically
import java.io.File

/** Untouched originals, one WhatsApp copy per sticker and each pack's tray, always written atomically. */
class StickerFiles(root: File) {

    private val originals = File(root, "originals")
    private val whatsapp = File(root, "whatsapp")
    private val trays = File(root, "trays")

    fun original(id: String, ext: String) = File(originals, "${safe(id)}.${safe(ext)}")
    fun whatsApp(id: String) = File(whatsapp, "${safe(id)}.webp")
    fun tray(packId: String) = File(trays, "${safe(packId)}.png")

    fun write(file: File, bytes: ByteArray) = file.writeAtomically(bytes)

    fun delete(file: File) {
        AtomicFile(file).delete()
    }

    /** Deletes what belongs to no row and has been untouched for more than [olderThanMs] (leftovers of an interruption). */
    fun sweep(stickerIds: Set<String>, packIds: Set<String>, now: Long, olderThanMs: Long): Int {
        var removed = 0
        fun clean(dir: File, keep: (String) -> Boolean) {
            dir.listFiles()?.forEach { file ->
                // An AtomicFile ".new" or ".bak" is a leftover even if its id is still alive.
                val wanted = file.name.count { it == '.' } == 1 && keep(file.name.substringBefore('.'))
                if (!wanted && now - file.lastModified() > olderThanMs && file.delete()) removed++
            }
        }
        clean(originals) { it in stickerIds }
        clean(whatsapp) { it in stickerIds }
        clean(trays) { it in packIds }
        return removed
    }

    // Ids and extensions are ours (SHA-256, "tikstik_…"): never paths.
    private fun safe(part: String): String {
        require(part.matches(SAFE)) { "Invalid file name" }
        return part
    }

    private companion object {
        val SAFE = Regex("[A-Za-z0-9_-]{1,100}")
    }
}
