// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.packs

import androidx.room.withTransaction
import com.redrixdev.tikstik.collection.CollectionRepository
import com.redrixdev.tikstik.core.common.Clock
import com.redrixdev.tikstik.core.common.IoDispatcher
import com.redrixdev.tikstik.core.data.db.AppDatabase
import com.redrixdev.tikstik.core.data.db.PackEntity
import com.redrixdev.tikstik.core.data.db.PackWithStickers
import com.redrixdev.tikstik.core.data.db.StickerEntity
import com.redrixdev.tikstik.core.data.db.TombstoneEntity
import com.redrixdev.tikstik.core.data.db.TombstoneKind
import com.redrixdev.tikstik.core.data.files.StickerFiles
import com.redrixdev.tikstik.whatsapp.PackRules
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** The packs. Any change bumps the pack's `imageDataVersion`, which is how WhatsApp knows to reload it. */
@Singleton
class PackRepository @Inject constructor(
    private val db: AppDatabase,
    private val files: StickerFiles,
    private val collection: CollectionRepository,
    private val clock: Clock,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private val dao = db.packs()
    private val writes = Mutex()

    val packs: Flow<List<PackWithStickers>> = dao.packs()

    fun pack(packId: String): Flow<PackWithStickers?> = dao.observe(packId)

    fun stickers(packId: String): Flow<List<StickerEntity>> = dao.stickers(packId)

    suspend fun get(packId: String): PackWithStickers? = withContext(io) { dao.get(packId) }

    /** The packs WhatsApp can see: already in it or being added. */
    suspend fun offered(): List<PackWithStickers> = withContext(io) { dao.offered() }

    fun tray(packId: String): File = files.tray(packId)

    /** Rewrites a missing tray (it does not travel between phones) without changing the pack. */
    suspend fun restoreTray(packId: String, trayPng: ByteArray) = writes.withLock {
        withContext(io) { if (dao.get(packId) != null) files.write(files.tray(packId), trayPng) }
    }

    /** Creates a pack numbered after the highest in use; the tray is written before the rows. */
    suspend fun create(
        name: (number: Int) -> String,
        animated: Boolean,
        stickerIds: List<String>,
        emoji: String,
        trayPng: ByteArray,
    ): PackEntity = writes.withLock {
        require(stickerIds.size in 1..PackRules.MAX_STICKERS) { "Invalid pack size" }
        withContext(io) {
            val now = clock.now()
            db.withTransaction {
                val number = dao.maxNumber() + 1
                val pack = PackEntity("pack_${number}_$now", name(number), number, animated, 1, now, offered = false)
                files.write(files.tray(pack.id), trayPng)
                dao.save(pack, stickerIds, stickerIds.associateWith { emoji })
                pack
            }
        }
    }

    /** A pack from the Drive backup, with its id, name and number; not offered, because it is not in WhatsApp here yet. */
    suspend fun restore(pack: PackEntity, stickerIds: List<String>, emojis: Map<String, String>, trayPng: ByteArray): Boolean = writes.withLock {
        withContext(io) {
            db.withTransaction {
                if (dao.get(pack.id) != null) return@withTransaction false
                files.write(files.tray(pack.id), trayPng)
                dao.save(pack.copy(offered = false), stickerIds, emojis)
                true
            }
        }
    }

    suspend fun rename(packId: String, name: String) = change(packId) { pack ->
        val clean = name.trim().take(PackRules.MAX_NAME)
        if (clean.isEmpty()) null else Change(pack.pack.copy(name = clean), pack.stickerIds)
    }

    /** New order; when the first sticker changes, the cover changes and its new tray comes along. */
    suspend fun reorder(packId: String, order: List<String>, trayPng: ByteArray?) = change(packId, trayPng) { pack ->
        require(order.toSet() == pack.stickerIds.toSet()) { "The order does not contain the same stickers" }
        Change(pack.pack, order)
    }

    /** Removes stickers: at least one remains, and three when the pack is in WhatsApp, which accepts no fewer. */
    suspend fun remove(packId: String, stickerIds: Set<String>, trayPng: ByteArray?) {
        change(packId, trayPng) { pack ->
            val keep = pack.stickerIds - stickerIds
            require(keep.size >= PackRules.minimumKept(pack.pack.offered)) { "Too few stickers would remain" }
            Change(pack.pack, keep)
        }
        collection.purgeUnused()
    }

    /** Appends until the pack is full; returns the stickers that got in. */
    suspend fun add(packId: String, stickerIds: List<String>, emoji: String): List<String> {
        var added = emptyList<String>()
        change(packId) { pack ->
            val fresh = PackPlanner.fitting(stickerIds, pack.stickerIds)
            added = fresh
            if (fresh.isEmpty()) null else Change(pack.pack, pack.stickerIds + fresh, fresh.associateWith { emoji })
        }
        return added
    }

    /** A better version of a sticker takes the place of [oldId] in the pack: same position, same emoji, new version. */
    suspend fun replaceSticker(packId: String, oldId: String, newId: String, trayPng: ByteArray?) {
        change(packId, trayPng) { pack ->
            if (oldId !in pack.stickerIds || newId in pack.stickerIds) return@change null
            Change(pack.pack, pack.stickerIds.map { if (it == oldId) newId else it }, mapOf(newId to pack.items.first { it.stickerId == oldId }.emoji))
        }
        collection.purgeUnused()
    }

    suspend fun delete(packId: String) {
        writes.withLock {
            withContext(io) {
                db.withTransaction {
                    dao.delete(packId)
                    db.tombstones().upsert(listOf(TombstoneEntity(TombstoneKind.PACK, packId, clock.now())))
                }
                files.delete(files.tray(packId))
            }
        }
        collection.purgeUnused()
    }

    /** Starts or stops offering packs to WhatsApp. */
    suspend fun setOffered(packIds: Set<String>, offered: Boolean) = withContext(io) { dao.setOffered(packIds, offered) }

    private class Change(val pack: PackEntity, val order: List<String>, val newEmojis: Map<String, String> = emptyMap())

    // The new tray only when the change is valid, and before the row: whoever reads the new version already finds its cover.
    private suspend fun change(packId: String, trayPng: ByteArray? = null, edit: (PackWithStickers) -> Change?) = writes.withLock {
        withContext(io) {
            db.withTransaction {
                val current = dao.get(packId) ?: return@withTransaction
                val next = edit(current) ?: return@withTransaction
                // Nothing changed: a new version would make WhatsApp reload and could beat a real edit from another phone in the backup.
                if (next.pack == current.pack && next.order == current.stickerIds && next.newEmojis.isEmpty()) return@withTransaction
                if (trayPng != null) files.write(files.tray(packId), trayPng)
                val emojis = current.items.associate { it.stickerId to it.emoji } + next.newEmojis
                dao.save(next.pack.copy(imageDataVersion = current.pack.imageDataVersion + 1), next.order, emojis)
            }
        }
    }
}
