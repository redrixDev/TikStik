// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.update

import com.google.common.truth.Truth.assertThat
import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress

class GitHubReleasesTest {

    @get:Rule
    val temp = TemporaryFolder()

    // A local server that sends [size] bytes without announcing their length, as a chunked response does.
    private fun serving(size: Int, block: (url: String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/app.apk") { exchange ->
            exchange.sendResponseHeaders(200, 0)
            runCatching { exchange.responseBody.use { it.write(ByteArray(size)) } }
        }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}/app.apk")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun aDownloadLargerThanTheReleaseSaysIsCut() = serving(200_000) { url ->
        val into = temp.newFile()
        assertThrows(IOException::class.java) { GitHubReleases("owner/name").download(url, into, 1_000) { _, _ -> } }
        assertThat(into.length()).isAtMost(1_000L)
    }

    @Test
    fun theReleaseSizeStandsInForAMissingLength() = serving(1_000) { url ->
        val into = temp.newFile()
        var last = 0L to 0L
        GitHubReleases("owner/name").download(url, into, 1_000) { done, total -> last = done to total }
        assertThat(last).isEqualTo(1_000L to 1_000L)
        assertThat(into.length()).isEqualTo(1_000L)
    }
}
