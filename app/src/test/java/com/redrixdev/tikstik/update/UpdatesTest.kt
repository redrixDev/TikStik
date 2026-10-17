// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.update

import androidx.datastore.core.DataStore
import com.google.common.truth.Truth.assertThat
import com.redrixdev.tikstik.core.common.Clock
import com.redrixdev.tikstik.core.data.settings.AppSettings
import com.redrixdev.tikstik.core.data.settings.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class UpdatesTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val apkBytes = ByteArray(1000) { it.toByte() }
    private val newer = GitHubRelease(tagName = "v1.1.0", body = "Fixes", assets = listOf(GitHubAsset("TikStik-1.1.0.apk", 1000, "https://example.com/TikStik-1.1.0.apk")))

    private class FakeSource(var release: GitHubRelease?, var bytes: ByteArray) : UpdateSource {
        var checks = 0
        var downloads = 0
        var down = false

        override fun latest(): GitHubRelease? {
            checks++
            if (down) throw IOException("no network")
            return release
        }

        override fun download(url: String, into: File, maxBytes: Long, onProgress: (Long, Long) -> Unit) {
            downloads++
            if (down) throw IOException("no network")
            into.writeBytes(bytes)
            onProgress(bytes.size.toLong(), bytes.size.toLong())
        }
    }

    private class MemoryStore : DataStore<AppSettings> {
        private val state = MutableStateFlow(AppSettings())
        override val data: Flow<AppSettings> = state

        override suspend fun updateData(transform: suspend (t: AppSettings) -> AppSettings): AppSettings =
            transform(state.value).also { state.value = it }
    }

    private var now = 1_700_000_000_000L
    private val store = MemoryStore()

    private fun TestScope.updates(source: UpdateSource, installed: Version = Version(1, 0, 0)) = Updates(
        source, SettingsRepository(store), Clock { now }, InstalledVersion(installed), UpdateDir(File(temp.root, "update")),
        StandardTestDispatcher(testScheduler), this,
    )

    @Test
    fun offersANewerReleaseAndRemembersLater() = runTest {
        val source = FakeSource(newer, apkBytes)
        val updates = updates(source)
        updates.checkIfDue().join()
        val found = updates.state.first().available
        assertThat(found?.versionName).isEqualTo("1.1.0")
        assertThat(found?.notes).isEqualTo("Fixes")
        updates.skip().join()
        assertThat(updates.state.first().skipped).isEqualTo("1.1.0")
        updates.show().join()
        assertThat(updates.state.first().skipped).isNull()
    }

    @Test
    fun checksAtMostOnceADayAndIgnoresWhatIsNotNewer() = runTest {
        val source = FakeSource(GitHubRelease(tagName = "v1.0.0", assets = newer.assets), apkBytes)
        val updates = updates(source)
        updates.checkIfDue().join()
        assertThat(updates.state.first().available).isNull()
        now += 60 * 60 * 1000
        updates.checkIfDue().join()
        assertThat(source.checks).isEqualTo(1)
        now += 24 * 60 * 60 * 1000
        updates.checkIfDue().join()
        assertThat(source.checks).isEqualTo(2)
        updates.setCheckAutomatically(false).join()
        now += 48 * 60 * 60 * 1000
        updates.checkIfDue().join()
        assertThat(source.checks).isEqualTo(2)
    }

    @Test
    fun aFailedCheckIsReported() = runTest {
        val source = FakeSource(newer, apkBytes).apply { down = true }
        assertThat(updates(source).check()).isEqualTo(CheckResult.FAILED)
    }

    @Test
    fun downloadsTheApkOnceAndChecksItsSize() = runTest {
        val source = FakeSource(newer, apkBytes)
        val updates = updates(source)
        assertThat(updates.check()).isEqualTo(CheckResult.FOUND)
        updates.download().join()
        val ready = updates.state.first().phase as UpdatePhase.Ready
        assertThat(ready.justDownloaded).isTrue()
        assertThat(ready.apk.readBytes()).isEqualTo(apkBytes)
        updates.installerOpened()
        assertThat((updates.state.first().phase as UpdatePhase.Ready).justDownloaded).isFalse()
        updates.download().join()
        assertThat(source.downloads).isEqualTo(1)
    }

    @Test
    fun aSecondTapWhileStartingDoesNotDownloadTwice() = runTest {
        val source = FakeSource(newer, apkBytes)
        val updates = updates(source)
        updates.check()
        val first = updates.download()
        val second = updates.download()
        first.join()
        second.join()
        assertThat(source.downloads).isEqualTo(1)
        assertThat((updates.state.first().phase as UpdatePhase.Ready).justDownloaded).isTrue()
    }

    @Test
    fun aShortDownloadFailsAndLeavesNoApk() = runTest {
        val source = FakeSource(newer, apkBytes.copyOf(999))
        val updates = updates(source)
        updates.check()
        updates.download().join()
        assertThat(updates.state.first().phase).isEqualTo(UpdatePhase.Failed)
        assertThat(File(temp.root, "update").listFiles()?.map { it.name }?.filter { it.endsWith(".apk") }).isEmpty()
    }

    @Test
    fun forgetsTheUpdateOnceItIsInstalled() = runTest {
        val source = FakeSource(newer, apkBytes)
        val before = updates(source)
        before.check()
        before.download().join()
        val after = updates(source, installed = Version(1, 1, 0))
        after.checkIfDue().join()
        assertThat(after.state.first().available).isNull()
        assertThat(store.data.first().updates.found).isNull()
        assertThat(File(temp.root, "update").listFiles().orEmpty()).isEmpty()
    }
}
