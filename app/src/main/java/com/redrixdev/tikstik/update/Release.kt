// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What GitHub answers for the latest release, reduced to what the app needs. */
@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList(),
) {
    companion object {
        // A release with no notes comes with `"body": null`: coerced to the default instead of failing.
        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        fun parse(text: String): GitHubRelease = json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class GitHubAsset(
    val name: String = "",
    val size: Long = 0,
    @SerialName("browser_download_url") val url: String = "",
)

/** A newer version and its APK: what the update sheet offers. */
data class AvailableUpdate(val version: Version, val versionName: String, val notes: String, val apkUrl: String, val apkBytes: Long)

/** The release as an update, or null when it is not a finished one with a readable number and a single APK. */
fun GitHubRelease.toUpdate(): AvailableUpdate? {
    if (draft || prerelease) return null
    val version = Version.parse(tagName) ?: Version.parse(name) ?: return null
    val apk = assets.singleOrNull { it.name.endsWith(".apk", ignoreCase = true) && it.url.startsWith("https://") } ?: return null
    return AvailableUpdate(version, version.toString(), plainNotes(body), apk.url, apk.size)
}

// The notes are Markdown: heading marks, emphasis and code marks are dropped, so they read as plain text.
private fun plainNotes(markdown: String): String = markdown.lines()
    .map { line -> line.trim().trimStart('#').trim().replace(MARKS, "") }
    .joinToString("\n")
    .replace(BLANK_LINES, "\n\n")
    .trim()

private val MARKS = Regex("""[*_`]+""")
private val BLANK_LINES = Regex("""\n{3,}""")
