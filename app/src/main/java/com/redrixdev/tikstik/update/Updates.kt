// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.update

import com.redrixdev.tikstik.core.common.ApplicationScope
import com.redrixdev.tikstik.core.common.Clock
import com.redrixdev.tikstik.core.common.IoDispatcher
import com.redrixdev.tikstik.core.common.Logger
import com.redrixdev.tikstik.core.data.settings.FoundUpdate
import com.redrixdev.tikstik.core.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** The app's own folder for downloaded updates; tests pass a temporary one. */
data class UpdateDir(val dir: File)

/** The version running now; tests pass any. */
data class InstalledVersion(val version: Version)

enum class CheckResult { UP_TO_DATE, FOUND, FAILED }

sealed interface UpdatePhase {
    data object Idle : UpdatePhase

    /** [fraction] null until the size is known. */
    data class Downloading(val fraction: Float?) : UpdatePhase

    /** [justDownloaded]: the installer opens by itself once; afterwards only when asked. */
    data class Ready(val apk: File, val justDownloaded: Boolean) : UpdatePhase

    data object Failed : UpdatePhase
}

/** [available] is the newer version GitHub lists, if any; [skipped] the one answered with "later". */
data class UpdateState(
    val available: AvailableUpdate? = null,
    val skipped: String? = null,
    val checkAutomatically: Boolean = true,
    val phase: UpdatePhase = UpdatePhase.Idle,
)

/** New versions from GitHub: a check at most once a day, the APK downloaded on request, and Android's installer asking at the end. */
@Singleton
class Updates @Inject constructor(
    private val source: UpdateSource,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val installed: InstalledVersion,
    private val dir: UpdateDir,
    @param:IoDispatcher private val io: CoroutineDispatcher,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val phase = MutableStateFlow<UpdatePhase>(UpdatePhase.Idle)
    // A second tap while the first download is still starting would write the same file twice.
    private val downloading = Mutex()

    val state: Flow<UpdateState> = combine(settings.settings, phase) { saved, phase ->
        val info = saved.updates
        val found = info.found?.toAvailable()?.takeIf { it.version > installed.version }
        UpdateState(found, info.skipped, info.checkAutomatically, if (found == null) UpdatePhase.Idle else phase)
    }

    /** On opening the app: forgets the update once it is installed and, when a day has passed, looks for a new one. */
    fun checkIfDue(): Job = scope.launch {
        val info = settings.settings.first().updates
        val found = info.found?.toAvailable()
        if (info.found != null && (found == null || found.version <= installed.version)) forget()
        if (info.checkAutomatically && clock.now() - info.lastCheckAt >= DAY_MS) check()
    }

    /** Asks GitHub now; what it finds is kept in the settings, so it survives restarts. */
    suspend fun check(): CheckResult {
        val release = try {
            withContext(io) { source.latest() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(AREA, "check failed: ${e.javaClass.simpleName}")
            return CheckResult.FAILED
        }
        val update = release?.toUpdate()?.takeIf { it.version > installed.version }
        val before = settings.settings.first().updates.found?.versionName
        settings.updateUpdates { info ->
            info.copy(
                lastCheckAt = clock.now(),
                found = update?.let { FoundUpdate(it.versionName, it.notes, it.apkUrl, it.apkBytes) },
                // A different version than the one skipped is offered again.
                skipped = info.skipped?.takeIf { it == update?.versionName },
            )
        }
        if (update?.versionName != before) {
            phase.value = UpdatePhase.Idle
            withContext(io) { deleteDownloads() }
        }
        return if (update == null) CheckResult.UP_TO_DATE else CheckResult.FOUND
    }

    fun download(): Job = scope.launch {
        if (!downloading.tryLock()) return@launch
        try {
            downloadNow()
        } finally {
            downloading.unlock()
        }
    }

    private suspend fun downloadNow() {
        val update = settings.settings.first().updates.found?.toAvailable() ?: return
        val apk = apkFile(update)
        if (withContext(io) { complete(apk, update) }) {
            phase.value = UpdatePhase.Ready(apk, justDownloaded = false)
            return
        }
        phase.value = UpdatePhase.Downloading(null)
        try {
            withContext(io) {
                dir.dir.mkdirs()
                val part = File(dir.dir, apk.name + ".part")
                source.download(update.apkUrl, part, update.apkBytes) { done, total ->
                    phase.value = UpdatePhase.Downloading(if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null)
                }
                if (!complete(part, update)) throw IOException("size mismatch")
                if (!part.renameTo(apk)) throw IOException("rename failed")
            }
            phase.value = UpdatePhase.Ready(apk, justDownloaded = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(AREA, "download failed: ${e.javaClass.simpleName}")
            phase.value = UpdatePhase.Failed
        }
    }

    /** After the installer was opened by itself: from then on only on request. */
    fun installerOpened() {
        val current = phase.value
        if (current is UpdatePhase.Ready) phase.value = current.copy(justDownloaded = false)
    }

    fun skip(): Job = scope.launch { settings.updateUpdates { it.copy(skipped = it.found?.versionName) } }

    fun show(): Job = scope.launch { settings.updateUpdates { it.copy(skipped = null) } }

    fun setCheckAutomatically(enabled: Boolean): Job = scope.launch { settings.updateUpdates { it.copy(checkAutomatically = enabled) } }

    private suspend fun forget() {
        settings.updateUpdates { it.copy(found = null, skipped = null) }
        phase.value = UpdatePhase.Idle
        withContext(io) { deleteDownloads() }
    }

    private fun complete(file: File, update: AvailableUpdate) = file.isFile && (update.apkBytes <= 0 || file.length() == update.apkBytes)

    private fun apkFile(update: AvailableUpdate) = File(dir.dir, "TikStik-${update.versionName}.apk")

    private fun deleteDownloads() {
        dir.dir.listFiles()?.forEach { it.delete() }
    }

    private fun FoundUpdate.toAvailable(): AvailableUpdate? =
        Version.parse(versionName)?.let { AvailableUpdate(it, versionName, notes, apkUrl, apkBytes) }

    private companion object {
        const val AREA = "Update"
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
