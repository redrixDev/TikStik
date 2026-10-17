// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A Drive HTTP error: 401 is an expired token and `storageQuotaExceeded` a full Drive. */
class DriveException(val code: Int, private val reason: String) : IOException("Drive $code") {
    val unauthorized: Boolean get() = code == 401
    val full: Boolean get() = "storageQuotaExceeded" in reason

    // Google asks to retry 429, 5xx and rate-limit 403 responses with exponential backoff.
    val transient: Boolean get() = code == 429 || code >= 500 || (code == 403 && "ateLimitExceeded" in reason)
}

/** Drive could not be reached (no network, DNS, timeout): only this counts as "offline". */
class DriveNetworkException(cause: IOException) : IOException("Drive unreachable", cause)

/** The Drive backup was written by a newer TikStik version: it is not overwritten. */
class NewerBackupException : Exception("newer backup format")

@Serializable
data class DriveUser(val emailAddress: String = "")

@Serializable
data class DriveFile(val id: String, val name: String, val modifiedTime: String = "")

/** TikStik's private folder in the user's Drive (`appDataFolder`); blocking calls, off the main thread. */
interface DriveClient {
    fun user(): DriveUser

    fun list(): List<DriveFile>

    fun download(id: String): ByteArray

    /** A new file, metadata and content in a single request; returns its id. */
    fun create(name: String, mime: String, bytes: ByteArray): String

    fun delete(id: String)
}

/** Minimal Drive v3 REST client over `HttpURLConnection`: six calls do not justify the official library. */
class HttpDriveClient(private val token: String) : DriveClient {

    @Serializable private data class About(val user: DriveUser)

    @Serializable private data class FileList(val files: List<DriveFile> = emptyList(), val nextPageToken: String? = null)

    @Serializable private data class Created(val id: String)

    override fun user(): DriveUser = json.decodeFromString<About>(text(call("GET", "$API/about?fields=user(emailAddress)"))).user

    override fun list(): List<DriveFile> {
        val files = mutableListOf<DriveFile>()
        val tokens = HashSet<String>()
        var page: String? = null
        do {
            val url = "$API/files?spaces=appDataFolder&pageSize=1000&fields=nextPageToken,files(id,name,modifiedTime)" +
                (page?.let { "&pageToken=" + URLEncoder.encode(it, "UTF-8") } ?: "")
            val list = json.decodeFromString<FileList>(text(call("GET", url)))
            files += list.files
            page = list.nextPageToken
            // A token seen before would ask for the same pages forever.
            if (page != null && !tokens.add(page)) throw IOException("Drive repeated a page")
        } while (page != null)
        return files
    }

    override fun download(id: String): ByteArray = network { call("GET", "$API/files/$id?alt=media").inputStream.use { it.readBytes() } }

    override fun create(name: String, mime: String, bytes: ByteArray): String {
        val boundary = "tikstik" + System.nanoTime()
        val meta = """{"name":${json.encodeToString(name)},"parents":["appDataFolder"]}"""
        val body = listOf(
            "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n--$boundary\r\nContent-Type: $mime\r\n\r\n".toByteArray(),
            bytes,
            "\r\n--$boundary--".toByteArray(),
        )
        return json.decodeFromString<Created>(text(call("POST", "$UPLOAD/files?uploadType=multipart&fields=id", "multipart/related; boundary=$boundary", body))).id
    }

    override fun delete(id: String) {
        try {
            call("DELETE", "$API/files/$id").inputStream.close()
        } catch (e: DriveException) {
            // Already gone (another phone deleted it first): the outcome is the same.
            if (e.code != 404) throw e
        }
    }

    private fun call(method: String, url: String, type: String? = null, body: List<ByteArray>? = null): HttpURLConnection = network {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", type)
            conn.setFixedLengthStreamingMode(body.sumOf { it.size.toLong() })
            conn.outputStream.use { out -> body.forEach(out::write) }
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            // Only the reason, to tell "Drive full" apart; the body is never logged.
            val reason = conn.errorStream?.use { it.readBytes().decodeToString() }.orEmpty()
            conn.disconnect()
            throw DriveException(code, reason)
        }
        conn
    }

    private fun text(conn: HttpURLConnection) = network { conn.inputStream.use { it.readBytes().decodeToString() } }

    private inline fun <T> network(block: () -> T): T = try {
        block()
    } catch (e: DriveException) {
        throw e
    } catch (e: IOException) {
        throw DriveNetworkException(e)
    }

    private companion object {
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        val json = Json { ignoreUnknownKeys = true }
    }
}
