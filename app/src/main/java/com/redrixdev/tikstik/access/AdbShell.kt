// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.access

import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

// Each command ends in a marker because libadb does not always signal the close; a watchdog cuts stalls and cancellations.
class AdbShell(
    private val link: AdbLink,
    private val cancelled: () -> Boolean = { false },
    private val stallMs: Long = 15_000,
) {

    /** The full output of [command]; [IOException] if it is cut before the end marker, [CancellationException] if [cancelled]. */
    fun run(command: String): ByteArray {
        val stream = link.open("exec:$command; echo $END_MARKER")
        val out = GrowingBuffer()
        val lastProgress = AtomicLong(System.nanoTime())
        val finished = AtomicBoolean(false)
        val stalled = AtomicBoolean(false)
        val watchdog = Thread {
            while (!finished.get()) {
                try {
                    Thread.sleep(WATCH_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    return@Thread
                }
                val idleMs = (System.nanoTime() - lastProgress.get()) / 1_000_000
                if (!finished.get() && (cancelled() || idleMs > stallMs)) {
                    stalled.set(idleMs > stallMs)
                    runCatching { stream.close() }
                    return@Thread
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
        try {
            while (!out.endsWith(MARKER_LINE)) {
                val n = out.readFrom(stream)
                if (n < 0) break
                lastProgress.set(System.nanoTime())
            }
        } catch (e: IOException) {
            if (!stream.isClosed) throw e
        } finally {
            finished.set(true)
            watchdog.interrupt()
            runCatching { stream.close() }
        }
        if (!out.endsWith(MARKER_LINE)) {
            if (stalled.get()) throw IOException("shell stalled")
            // A cancelled read is not a dropped connection: reported as one, the import would warn of a cut that never happened.
            if (cancelled()) throw CancellationException("shell cancelled")
            throw IOException("shell output incomplete (${out.size} bytes)")
        }
        return out.toByteArray(out.size - MARKER_LINE.size)
    }

    fun text(command: String): String = String(run(command), Charsets.UTF_8)

    /** A buffer that doubles as it grows and checks its tail without copying. */
    private class GrowingBuffer {
        private var data = ByteArray(64 * 1024)
        var size = 0
            private set

        fun readFrom(stream: ShellStream): Int {
            if (size == data.size) data = data.copyOf(data.size * 2)
            val n = stream.read(data, size, data.size - size)
            if (n > 0) size += n
            return n
        }

        fun endsWith(suffix: ByteArray): Boolean {
            if (size < suffix.size) return false
            val start = size - suffix.size
            return suffix.indices.all { data[start + it] == suffix[it] }
        }

        fun toByteArray(length: Int): ByteArray = data.copyOf(length)
    }

    companion object {
        private const val END_MARKER = "__TIKSTIK_END__"
        private val MARKER_LINE = "$END_MARKER\n".toByteArray()
        private const val WATCH_INTERVAL_MS = 250L

        /** sh single quotes: nothing inside is interpreted. */
        fun quote(path: String): String = "'" + path.replace("'", "'\\''") + "'"
    }
}
