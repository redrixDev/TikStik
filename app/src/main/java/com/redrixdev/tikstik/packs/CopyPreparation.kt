// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.packs

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class CopyProgress(val done: Int, val total: Int)

/** Background preparation of the missing copies, and its progress for the Home indicator. */
@Singleton
class CopyPreparation @Inject constructor(private val workManager: WorkManager) {

    /** null when none is running. */
    val progress: Flow<CopyProgress?> = workManager.getWorkInfosForUniqueWorkFlow(PrepareCopiesWorker.NAME)
        .map { infos -> infos.firstOrNull { it.state == WorkInfo.State.RUNNING }?.let(PrepareCopiesWorker::progressOf) }
        .distinctUntilChanged()

    // Chained after the running one so nothing added meanwhile is missed.
    fun start() = enqueue(ExistingWorkPolicy.APPEND_OR_REPLACE)

    /** On opening the app: one left half-done at close waits for the system scheduler (half a minute on the emulator); it is replaced so it carries on now. */
    suspend fun resume() {
        val running = workManager.getWorkInfosForUniqueWorkFlow(PrepareCopiesWorker.NAME).first().any { it.state == WorkInfo.State.RUNNING }
        enqueue(if (running) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE)
    }

    private fun enqueue(policy: ExistingWorkPolicy) {
        workManager.enqueueUniqueWork(PrepareCopiesWorker.NAME, policy, OneTimeWorkRequestBuilder<PrepareCopiesWorker>().build())
    }
}
