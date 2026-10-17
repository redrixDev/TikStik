// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReleaseTest {

    @Test
    fun readsWhatGitHubAnswers() {
        val release = GitHubRelease.parse(
            """
            {"html_url":"https://github.com/redrixDev/TikStik/releases/tag/v1.1.0","tag_name":"v1.1.0","name":"TikStik 1.1.0",
             "draft":false,"prerelease":false,"body":null,
             "assets":[
               {"name":"notes.txt","size":3,"browser_download_url":"https://example.com/notes.txt","content_type":"text/plain"},
               {"name":"TikStik-1.1.0.apk","size":12345,"browser_download_url":"https://github.com/redrixDev/TikStik/releases/download/v1.1.0/TikStik-1.1.0.apk","content_type":"application/vnd.android.package-archive"}
             ]}
            """.trimIndent(),
        )
        val update = release.toUpdate()
        assertThat(update).isNotNull()
        assertThat(update!!.version).isEqualTo(Version(1, 1, 0))
        assertThat(update.versionName).isEqualTo("1.1.0")
        assertThat(update.notes).isEmpty()
        assertThat(update.apkUrl).endsWith("TikStik-1.1.0.apk")
        assertThat(update.apkBytes).isEqualTo(12345)
    }

    @Test
    fun onlyFinishedReleasesWithAnApkCount() {
        val apk = GitHubAsset("TikStik-1.1.0.apk", 10, "https://example.com/TikStik-1.1.0.apk")
        assertThat(GitHubRelease(tagName = "v1.1.0", prerelease = true, assets = listOf(apk)).toUpdate()).isNull()
        assertThat(GitHubRelease(tagName = "v1.1.0", draft = true, assets = listOf(apk)).toUpdate()).isNull()
        assertThat(GitHubRelease(tagName = "v1.1.0").toUpdate()).isNull()
        assertThat(GitHubRelease(tagName = "v1.1.0", assets = listOf(apk.copy(url = "http://example.com/x.apk"))).toUpdate()).isNull()
        assertThat(GitHubRelease(tagName = "next", name = "TikStik 1.1.0", assets = listOf(apk)).toUpdate()?.versionName).isEqualTo("1.1.0")
        assertThat(GitHubRelease(tagName = "v1.1.0", body = "  Notes  ", assets = listOf(apk)).toUpdate()?.notes).isEqualTo("Notes")
        // With two APKs there is no telling which one to install.
        assertThat(GitHubRelease(tagName = "v1.1.0", assets = listOf(apk, apk.copy(name = "TikStik-1.1.0-debug.apk"))).toUpdate()).isNull()
    }

    @Test
    fun notesLoseTheirMarkdownMarks() {
        val apk = GitHubAsset("TikStik-1.1.0.apk", 10, "https://example.com/TikStik-1.1.0.apk")
        val body = "### Fixes\r\n- Faster restore\r\n\r\n\r\n\r\n**Full Changelog**: https://example.com/compare `v1`"
        assertThat(GitHubRelease(tagName = "v1.1.0", body = body, assets = listOf(apk)).toUpdate()?.notes)
            .isEqualTo("Fixes\n- Faster restore\n\nFull Changelog: https://example.com/compare v1")
    }
}
