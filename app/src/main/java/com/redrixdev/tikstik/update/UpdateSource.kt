// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.update

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Where new versions are published; a fake replaces GitHub in tests. Blocking calls, off the main thread. */
interface UpdateSource {
    /** The latest finished release, or null when nothing is published yet. */
    fun latest(): GitHubRelease?

    /** Downloads [url] into [into], never more than [maxBytes] when known, reporting the bytes done and the total (0 while unknown). */
    fun download(url: String, into: File, maxBytes: Long, onProgress: (done: Long, total: Long) -> Unit)
}

/** GitHub's public releases API over `HttpURLConnection`: two calls do not justify a client library. */
class GitHubReleases(private val repository: String) : UpdateSource {

    override fun latest(): GitHubRelease? {
        val conn = open("https://api.github.com/repos/$repository/releases/latest")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) return null
            if (code !in 200..299) throw IOException("GitHub $code")
            return GitHubRelease.parse(conn.inputStream.use { it.readBytes().decodeToString() })
        } finally {
            conn.disconnect()
        }
    }

    override fun download(url: String, into: File, maxBytes: Long, onProgress: (Long, Long) -> Unit) {
        val conn = open(url)
        try {
            if (conn.responseCode !in 200..299) throw IOException("download ${conn.responseCode}")
            val announced = conn.contentLengthLong
            val total = if (announced > 0) announced else maxBytes.coerceAtLeast(0)
            var done = 0L
            conn.inputStream.use { input ->
                into.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        // Without a length from the server, the size GitHub listed is what stops a stream that never ends.
                        if (maxBytes > 0 && done + read > maxBytes) throw IOException("download larger than announced")
                        out.write(buffer, 0, read)
                        done += read
                        onProgress(done, total)
                    }
                }
            }
            if (announced > 0 && done != announced) throw IOException("download incomplete")
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 60_000
        // GitHub rejects requests without a User-Agent.
        setRequestProperty("User-Agent", "TikStik")
    }
}
