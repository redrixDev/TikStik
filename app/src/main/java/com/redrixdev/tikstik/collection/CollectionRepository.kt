// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.collection

import androidx.room.withTransaction
import com.redrixdev.tikstik.core.common.Clock
import com.redrixdev.tikstik.core.common.IoDispatcher
import com.redrixdev.tikstik.core.data.db.AppDatabase
import com.redrixdev.tikstik.core.data.db.SQL_BATCH
import com.redrixdev.tikstik.core.data.db.StickerEntity
import com.redrixdev.tikstik.core.data.db.TombstoneEntity
import com.redrixdev.tikstik.core.data.db.TombstoneKind
import com.redrixdev.tikstik.core.data.files.StickerFiles
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** The collection: originals on disk and their index in Room. On create, file then row; on delete, row then file. */
@Singleton
class CollectionRepository @Inject constructor(
    private val db: AppDatabase,
    private val files: StickerFiles,
    private val clock: Clock,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private val stickers = db.stickers()
    private val packs = db.packs()
    private val writes = Mutex()

    val collection: Flow<List<StickerEntity>> = stickers.collection()

    fun file(sticker: StickerEntity): File = files.original(sticker.id, sticker.fileExt)

    /** Adds an original; if it was already there (even outside the collection), it returns to it with the most recent seen date. */
    suspend fun add(sticker: StickerEntity, bytes: ByteArray): StickerEntity = writes.withLock {
        withContext(io) {
            val existing = stickers.get(sticker.id)
            if (existing == null) files.write(file(sticker), bytes)
            // If it was deleted before, it comes back now: the backup merge only drops it if it was deleted after being seen.
            val seen = if (sticker.inCollection && db.tombstones().get(TombstoneKind.STICKER, sticker.id) != null) clock.now() else sticker.seenAt
            val saved = existing?.copy(inCollection = true, seenAt = maxOf(existing.seenAt, seen)) ?: sticker.copy(seenAt = maxOf(sticker.seenAt, seen))
            stickers.upsert(saved)
            saved
        }
    }

    /** A better version takes the place of [oldId] in the collection; packs switch afterwards, once its copy exists. */
    suspend fun replace(oldId: String, better: StickerEntity, bytes: ByteArray): StickerEntity = writes.withLock {
        withContext(io) {
            val old = stickers.get(oldId)
            val saved = better.copy(addedAt = old?.addedAt ?: better.addedAt, seenAt = maxOf(better.seenAt, old?.seenAt ?: 0))
            // Same bytes with better provenance: it is the same sticker, only its data changes.
            if (old != null && saved.id == oldId) {
                return@withContext saved.copy(inCollection = old.inCollection).also { stickers.upsert(it) }
            }
            if (stickers.get(saved.id) == null) files.write(file(saved), bytes)
            val orphan = db.withTransaction {
                stickers.upsert(saved)
                // The old version leaves the collection on purpose: the Drive backup must not keep it either.
                if (old != null) db.tombstones().upsert(listOf(TombstoneEntity(TombstoneKind.STICKER, oldId, clock.now())))
                when {
                    old == null -> null
                    packs.used(listOf(oldId)).isNotEmpty() -> null.also { stickers.leaveCollection(listOf(oldId)) }
                    else -> old.also { stickers.delete(listOf(oldId)) }
                }
            }
            orphan?.let(::deleteFiles)
            saved
        }
    }

    /** TikTok showed these stickers again (id → ms): they move up in the collection. */
    suspend fun touchSeen(seen: Map<String, Long>) = withContext(io) {
        db.withTransaction { seen.forEach { (id, at) -> stickers.touchSeen(id, at) } }
    }

    /** The user deletes them: those used by a pack leave the collection and the rest are deleted for good. */
    suspend fun delete(ids: Set<String>) = writes.withLock {
        withContext(io) {
            val now = clock.now()
            val gone = db.withTransaction {
                ids.chunked(SQL_BATCH).flatMap { batch ->
                    val used = packs.used(batch).toSet()
                    val free = stickers.get(batch - used)
                    stickers.leaveCollection(used)
                    stickers.delete(free.map { it.id })
                    db.tombstones().upsert(batch.map { TombstoneEntity(TombstoneKind.STICKER, it, now) })
                    free
                }
            }
            gone.forEach(::deleteFiles)
        }
    }

    /** Deletes those no longer in the collection nor in any pack (after removing them from a pack or deleting it). */
    suspend fun purgeUnused() = writes.withLock {
        withContext(io) {
            val gone = db.withTransaction {
                stickers.unused().chunked(SQL_BATCH).flatMap { batch -> stickers.get(batch).also { stickers.delete(batch) } }
            }
            gone.forEach(::deleteFiles)
        }
    }

    /** Deletes files without a row that are older than an hour (leftovers of a crash in the middle of a write). */
    suspend fun sweepOrphans(): Int = writes.withLock {
        withContext(io) {
            files.sweep(stickers.allIds().toSet(), packs.allIds().toSet(), clock.now(), ORPHAN_AGE_MS)
        }
    }

    private fun deleteFiles(sticker: StickerEntity) {
        files.delete(file(sticker))
        files.delete(files.whatsApp(sticker.id))
    }

    private companion object {
        const val ORPHAN_AGE_MS = 60 * 60 * 1000L
    }
}
