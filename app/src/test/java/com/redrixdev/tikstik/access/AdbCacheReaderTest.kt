// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.access

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class AdbCacheReaderTest {

    // Binary content that mimics what used to confuse the splitting of head's output.
    private val files = mapOf(
        "/c/a b.cnt" to "RIFF\n==> x <==\n".toByteArray() + ByteArray(40) { it.toByte() },
        "/c/it's.cnt" to ByteArray(10) { 0x0A },
        "/c/empty.cnt" to ByteArray(0),
    )

    private val link = FakeLink().apply {
        connect()
        respond = ::sh
    }

    // Just enough sh for the reader's commands: stat, `head -q -c N` and cat, with single-quoted paths.
    private fun sh(command: String): ByteArray {
        val paths = Regex("'((?:[^']|'\\\\'')*)'").findAll(command).map { it.groupValues[1].replace("'\\''", "'") }.toList()
        return when {
            command.startsWith("find ") -> files.entries.joinToString("") { (path, bytes) -> "${bytes.size} 1700000000 $path\n" }.toByteArray()
            command.startsWith("head -q -c ") -> {
                val count = command.removePrefix("head -q -c ").substringBefore(' ').toInt()
                paths.fold(ByteArray(0)) { out, path -> out + (files[path]?.take(count)?.toByteArray() ?: ByteArray(0)) }
            }
            command.startsWith("cat ") -> paths.fold(ByteArray(0)) { out, path -> out + (files[path] ?: ByteArray(0)) }
            else -> ByteArray(0)
        }
    }

    private val reader = AdbCacheReader(AdbShell(link))

    @Test
    fun listsWithSizesAndPathsWithSpaces() {
        assertThat(reader.list("/c").map { it.path to it.size }).containsExactly(
            "/c/a b.cnt" to 55L, "/c/it's.cnt" to 10L, "/c/empty.cnt" to 0L,
        )
    }

    @Test
    fun headsAreSplitBySizeNotBySeparators() {
        val heads = reader.heads(reader.list("/c"), count = 32)
        assertThat(heads.getValue("/c/a b.cnt")).isEqualTo(files.getValue("/c/a b.cnt").copyOf(32))
        assertThat(heads.getValue("/c/it's.cnt")).isEqualTo(files.getValue("/c/it's.cnt"))
        assertThat(heads.getValue("/c/empty.cnt")).isEmpty()
        assertThat(link.commands.count { it.startsWith("head") }).isEqualTo(1)
    }

    @Test
    fun readsWholeFilesInOneBatch() {
        val all = reader.read(reader.list("/c"))
        files.forEach { (path, bytes) -> assertThat(all.getValue(path)).isEqualTo(bytes) }
        assertThat(link.commands.count { it.startsWith("cat") }).isEqualTo(1)
    }

    @Test
    fun aFileThatChangedIsDroppedAndTheRestKept() {
        val listed = reader.list("/c").map { if (it.path == "/c/it's.cnt") it.copy(size = 99) else it }
        val all = reader.read(listed)
        assertThat(all.keys).containsExactly("/c/a b.cnt", "/c/empty.cnt")
    }

    @Test
    fun aCutConnectionFailsInsteadOfReturningHalfTheOutput() {
        val broken = object : AdbLink by link {
            override fun open(command: String): ShellStream = FakeStream(ByteArray(100), chunk = 10, failAfter = 50)
        }
        assertThrows(IOException::class.java) { AdbShell(broken).run("cat '/c/a b.cnt'") }
    }

    @Test
    fun aCancelledReadIsACancellationNotACut() {
        // Like adbd in the middle of a long cat: nothing arrives until the watchdog closes the stream.
        val stuck = object : AdbLink by link {
            override fun open(command: String): ShellStream = object : ShellStream {
                @Volatile override var isClosed = false

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    while (!isClosed) Thread.sleep(10)
                    return -1
                }

                override fun close() {
                    isClosed = true
                }
            }
        }
        assertThrows(CancellationException::class.java) { AdbShell(stuck, cancelled = { true }).run("cat '/c/a b.cnt'") }
    }

    @Test
    fun aCacheTheShellCannotEnterIsAnAccessErrorNotAnEmptyCache() {
        val denied = FakeLink().apply {
            connect()
            respond = { command -> if (command.startsWith("ls -d ")) "ls: /c: Permission denied\n".toByteArray() else ByteArray(0) }
        }
        val error = assertThrows(AccessException::class.java) { AdbCacheReader(AdbShell(denied)).list("/c") }
        assertThat(error.error).isEqualTo(AccessError.CACHE_DENIED)
    }

    @Test
    fun aCutWhileReadingIsReportedAsACut() {
        val listed = reader.list("/c")
        val broken = object : AdbLink by link {
            override fun open(command: String): ShellStream = FakeStream(ByteArray(100), chunk = 10, failAfter = 50)
        }
        assertThrows(IOException::class.java) { AdbCacheReader(AdbShell(broken)).read(listed) }
    }
}
