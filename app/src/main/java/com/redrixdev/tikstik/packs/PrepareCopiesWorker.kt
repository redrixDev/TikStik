// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.packs

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.redrixdev.tikstik.collection.CollectionRepository
import com.redrixdev.tikstik.core.common.ConversionDispatcher
import com.redrixdev.tikstik.core.common.Logger
import com.redrixdev.tikstik.core.data.db.StickerEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/** Prepares the missing WhatsApp copies and trays at background priority; first it sweeps leftovers of interruptions. */
@HiltWorker
class PrepareCopiesWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val copies: WhatsAppCopies,
    private val collection: CollectionRepository,
    private val builder: PackBuilder,
    @param:ConversionDispatcher private val conversion: CoroutineDispatcher,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        collection.sweepOrphans()
        val pending = copies.missing()
        if (pending.isNotEmpty()) prepare(pending)
        builder.repairTrays()
        // Retrying does not fix an original that cannot be converted; what is still pending is tried again next run.
        return Result.success()
    }

    private suspend fun prepare(pending: List<StickerEntity>) {
        val done = MutableStateFlow(0)
        val failed = AtomicInteger()
        coroutineScope {
            // StateFlow drops intermediate values: the published progress never goes backwards.
            val reporter = launch { done.collect { setProgress(workDataOf(DONE to it, TOTAL to pending.size)) } }
            pending.map { sticker ->
                launch(conversion) {
                    try {
                        copies.ensure(sticker, conversion)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failed.incrementAndGet()
                        Logger.warn(AREA, "copy failed", e)
                    }
                    done.update { it + 1 }
                }
            }.joinAll()
            reporter.cancel()
        }
        if (failed.get() > 0) Logger.warn(AREA, "${failed.get()} of ${pending.size} copies could not be prepared")
    }

    companion object {
        const val NAME = "prepare_copies"
        private const val AREA = "Copies"
        private const val DONE = "done"
        private const val TOTAL = "total"

        fun progressOf(info: WorkInfo): CopyProgress? {
            val total = info.progress.getInt(TOTAL, 0)
            return if (total == 0) null else CopyProgress(info.progress.getInt(DONE, 0), total)
        }
    }
}
