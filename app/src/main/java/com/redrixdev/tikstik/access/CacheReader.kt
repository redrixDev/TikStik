// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.access

import com.redrixdev.tikstik.access.AdbShell.Companion.quote
import java.io.File

data class CacheFile(val path: String, val size: Long, val modifiedSec: Long)

/** Read-only access to the `.cnt` files of a TikTok cache. */
interface CacheReader {
    fun list(root: String): List<CacheFile>

    /** The first [count] bytes of each file; unreadable ones are left out. */
    fun heads(files: List<CacheFile>, count: Int): Map<String, ByteArray>

    /** The whole files; those that changed or cannot be read are left out. */
    fun read(files: List<CacheFile>): Map<String, ByteArray>
}

/** Over ADB: one command per batch, and the output is split by the `stat` sizes, never by searching for separators. */
class AdbCacheReader(private val shell: AdbShell) : CacheReader {

    override fun list(root: String): List<CacheFile> {
        // Without permission `find` lists nothing and it would look like an empty cache: tell them apart first.
        if ("Permission denied" in shell.text("ls -d ${quote(root)} 2>&1")) throw AccessException(AccessError.CACHE_DENIED)
        return shell.text("find ${quote(root)} -type f -name '*.cnt' -exec stat -c '%s %Y %n' {} + 2>/dev/null")
            .lineSequence()
            .mapNotNull { line ->
                val parts = line.split(' ', limit = 3)
                val size = parts.getOrNull(0)?.toLongOrNull()
                val modified = parts.getOrNull(1)?.toLongOrNull()
                if (parts.size == 3 && size != null && modified != null) CacheFile(parts[2], size, modified) else null
            }
            .toList()
    }

    // One process per batch: a `head` per file took 26.8 s for 3,000 files on the emulator; `-q` drops the separators.
    override fun heads(files: List<CacheFile>, count: Int): Map<String, ByteArray> =
        inBatches(files, { minOf(count.toLong(), it.size) }, HEAD_FILES) { batch -> "head -q -c $count ${paths(batch)} 2>/dev/null" }

    override fun read(files: List<CacheFile>): Map<String, ByteArray> =
        inBatches(files, { it.size }, MAX_FILES) { batch -> "cat ${paths(batch)} 2>/dev/null" }

    private fun paths(batch: List<CacheFile>) = batch.joinToString(" ") { quote(it.path) }

    // A batch that is not the expected size is retried file by file; an IOException is a dropped connection and propagates as is.
    private fun inBatches(files: List<CacheFile>, expected: (CacheFile) -> Long, maxFiles: Int, command: (List<CacheFile>) -> String): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        for (batch in batches(files, expected, maxFiles)) {
            val data = shell.run(command(batch))
            if (data.size.toLong() == batch.sumOf(expected)) {
                var at = 0
                for (file in batch) {
                    val length = expected(file).toInt()
                    out[file.path] = data.copyOfRange(at, at + length)
                    at += length
                }
            } else {
                for (file in batch) {
                    val single = shell.run(command(listOf(file)))
                    if (single.size.toLong() == expected(file)) out[file.path] = single
                }
            }
        }
        return out
    }

    private fun batches(files: List<CacheFile>, expected: (CacheFile) -> Long, maxFiles: Int): List<List<CacheFile>> {
        val batches = mutableListOf<MutableList<CacheFile>>()
        var bytes = 0L
        for (file in files) {
            if (batches.isEmpty() || batches.last().size >= maxFiles || bytes + expected(file) > MAX_BYTES) {
                batches += mutableListOf<CacheFile>()
                bytes = 0
            }
            batches.last() += file
            bytes += expected(file)
        }
        return batches
    }

    private companion object {
        const val MAX_FILES = 40

        // The paths go in the command: 200 are about 26 KB, far from the 256 KB per ADB v2 message (`AdbProtocol.MAX_PAYLOAD_V2`).
        const val HEAD_FILES = 200
        const val MAX_BYTES = 2_000_000L
    }
}

/** Android 8–10: direct reads with the storage permission. */
class StorageCacheReader : CacheReader {

    override fun list(root: String): List<CacheFile> =
        File(root).walkTopDown()
            .filter { it.isFile && it.name.endsWith(".cnt") }
            .map { CacheFile(it.path, it.length(), it.lastModified() / 1000) }
            .toList()

    override fun heads(files: List<CacheFile>, count: Int): Map<String, ByteArray> =
        files.mapNotNull { file ->
            runCatching {
                File(file.path).inputStream().use { input -> file.path to input.readNBytesCompat(count) }
            }.getOrNull()
        }.toMap()

    override fun read(files: List<CacheFile>): Map<String, ByteArray> =
        files.mapNotNull { file ->
            runCatching { File(file.path).readBytes() }.getOrNull()?.takeIf { it.size.toLong() == file.size }?.let { file.path to it }
        }.toMap()

    // InputStream.readNBytes arrives in API 33; a single read() may come up short.
    private fun java.io.InputStream.readNBytesCompat(count: Int): ByteArray {
        val buffer = ByteArray(count)
        var total = 0
        while (total < count) {
            val n = read(buffer, total, count - total)
            if (n < 0) break
            total += n
        }
        return buffer.copyOf(total)
    }
}
