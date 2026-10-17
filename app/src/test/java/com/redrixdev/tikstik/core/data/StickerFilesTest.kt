// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.data

import com.google.common.truth.Truth.assertThat
import com.redrixdev.tikstik.core.data.files.StickerFiles
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StickerFilesTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val files by lazy { StickerFiles(temp.root) }

    @Test
    fun writeReplacesWholeFile() {
        val file = files.original("abc", "webp")
        files.write(file, byteArrayOf(1, 2, 3))
        files.write(file, byteArrayOf(9))
        assertThat(file.readBytes()).isEqualTo(byteArrayOf(9))
        assertThat(file.parentFile!!.list()).asList().containsExactly("abc.webp")
    }

    @Test
    fun namesThatCouldBePathsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { files.original("../x", "webp") }
        assertThrows(IllegalArgumentException::class.java) { files.tray("a/b") }
    }

    @Test
    fun sweepRemovesOnlyOldFilesWithoutRow() {
        val now = 10_000_000L
        val kept = files.original("kept", "webp").write(now - 7_200_000)
        val orphan = files.whatsApp("gone").write(now - 7_200_000)
        val fresh = files.original("fresh", "png").write(now - 60_000)
        val leftover = File(files.original("kept", "webp").path + ".new").write(now - 7_200_000)
        val tray = files.tray("pack_1").write(now - 7_200_000)

        val removed = files.sweep(stickerIds = setOf("kept"), packIds = setOf("pack_1"), now = now, olderThanMs = 3_600_000)

        assertThat(removed).isEqualTo(2)
        assertThat(kept.exists()).isTrue()
        assertThat(fresh.exists()).isTrue()
        assertThat(tray.exists()).isTrue()
        assertThat(orphan.exists()).isFalse()
        assertThat(leftover.exists()).isFalse()
    }

    private fun File.write(modified: Long): File = apply {
        parentFile!!.mkdirs()
        writeBytes(byteArrayOf(0))
        setLastModified(modified)
    }
}
