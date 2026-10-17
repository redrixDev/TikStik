// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** The Drive backup: resumable (uploads what is missing), needs network, and backs off when the network fails. */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val account: BackupAccount,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        account.runBackup(inputData.getBoolean(MANUAL, false)) { done, total -> setProgress(workDataOf(BackupAccount.DONE to done, BackupAccount.TOTAL to total)) }
            .toResult(runAttemptCount)

    companion object {
        const val MANUAL = "manual"
    }
}

/** Merges an account's backup into this phone; it keeps going if you leave the screen. */
@HiltWorker
class RestoreWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val account: BackupAccount,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        account.runRestore { done, total -> setProgress(workDataOf(BackupAccount.DONE to done, BackupAccount.TOTAL to total)) }.toResult(runAttemptCount)
}

// With exponential backoff, ten attempts span a few hours; after that it stops insisting, as Drive asks.
private const val MAX_ATTEMPTS = 10

private fun WorkOutcome.toResult(attempt: Int): androidx.work.ListenableWorker.Result = when (this) {
    WorkOutcome.DONE -> androidx.work.ListenableWorker.Result.success()
    WorkOutcome.RETRY -> if (attempt + 1 >= MAX_ATTEMPTS) androidx.work.ListenableWorker.Result.failure() else androidx.work.ListenableWorker.Result.retry()
    WorkOutcome.FAILED -> androidx.work.ListenableWorker.Result.failure()
}
