// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// SQLite before 3.32 (Android 8–11) allows at most 999 parameters per query: long IN lists go in batches.
const val SQL_BATCH = 500

@Dao
interface StickerDao {
    // Newly added or most recently re-shown by TikTok first, whichever is more recent.
    @Query(
        "SELECT * FROM stickers WHERE inCollection = 1 " +
            "ORDER BY MAX(seenAt, addedAt) DESC, seenAt DESC, id",
    )
    fun collection(): Flow<List<StickerEntity>>

    @Query("SELECT * FROM stickers WHERE id = :id")
    suspend fun get(id: String): StickerEntity?

    @Query("SELECT * FROM stickers WHERE id IN (:ids)")
    suspend fun get(ids: Collection<String>): List<StickerEntity>

    @Query("SELECT id FROM stickers")
    suspend fun allIds(): List<String>

    // Including those no longer in the collection: some pack uses them.
    @Query("SELECT * FROM stickers")
    suspend fun all(): List<StickerEntity>

    @Upsert
    suspend fun upsert(sticker: StickerEntity)

    @Query("UPDATE stickers SET seenAt = :at WHERE id = :id AND seenAt < :at")
    suspend fun touchSeen(id: String, at: Long)

    @Query("UPDATE stickers SET inCollection = 0 WHERE id IN (:ids)")
    suspend fun leaveCollection(ids: Collection<String>)

    @Query("DELETE FROM stickers WHERE id IN (:ids)")
    suspend fun delete(ids: Collection<String>)

    // Out of the collection and used by no pack: safe to delete.
    @Query("SELECT id FROM stickers WHERE inCollection = 0 AND id NOT IN (SELECT stickerId FROM pack_stickers)")
    suspend fun unused(): List<String>
}

@Dao
interface PackDao {
    @Transaction
    @Query("SELECT * FROM packs ORDER BY createdAt, number")
    fun packs(): Flow<List<PackWithStickers>>

    @Transaction
    @Query("SELECT * FROM packs WHERE id = :id")
    suspend fun get(id: String): PackWithStickers?

    @Transaction
    @Query("SELECT * FROM packs WHERE id = :id")
    fun observe(id: String): Flow<PackWithStickers?>

    // A pack's stickers in order, including those no longer in the collection.
    @Query("SELECT stickers.* FROM pack_stickers JOIN stickers ON stickers.id = pack_stickers.stickerId WHERE packId = :packId ORDER BY position")
    fun stickers(packId: String): Flow<List<StickerEntity>>

    @Transaction
    @Query("SELECT * FROM packs WHERE offered = 1 ORDER BY createdAt, number")
    suspend fun offered(): List<PackWithStickers>

    @Query("SELECT id FROM packs")
    suspend fun allIds(): List<String>

    @Query("SELECT COALESCE(MAX(number), 0) FROM packs")
    suspend fun maxNumber(): Int

    @Upsert
    suspend fun upsert(pack: PackEntity)

    @Query("DELETE FROM pack_stickers WHERE packId = :packId")
    suspend fun clearStickers(packId: String)

    @Insert
    suspend fun insertStickers(items: List<PackStickerEntity>)

    @Query("DELETE FROM packs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT DISTINCT stickerId FROM pack_stickers WHERE stickerId IN (:stickerIds)")
    suspend fun used(stickerIds: Collection<String>): List<String>

    @Query("UPDATE packs SET offered = :offered WHERE id IN (:ids)")
    suspend fun setOffered(ids: Collection<String>, offered: Boolean)

    // Saves a whole pack, row and ordered stickers, in one go.
    @Transaction
    suspend fun save(pack: PackEntity, stickerIds: List<String>, emojis: Map<String, String>) {
        upsert(pack)
        clearStickers(pack.id)
        insertStickers(stickerIds.mapIndexed { i, id -> PackStickerEntity(pack.id, id, i, emojis.getValue(id)) })
    }
}

@Dao
interface ImportKeyDao {
    @Query("SELECT * FROM import_keys WHERE key IN (:keys)")
    suspend fun get(keys: Collection<String>): List<ImportKeyEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNew(keys: List<ImportKeyEntity>)

    @Query("UPDATE import_keys SET importedAt = :at WHERE key IN (:keys) AND importedAt IS NULL")
    suspend fun markImported(keys: Collection<String>, at: Long)

    @Query("SELECT * FROM import_keys")
    suspend fun all(): List<ImportKeyEntity>
}

@Dao
interface TombstoneDao {
    @Upsert
    suspend fun upsert(tombstones: List<TombstoneEntity>)

    @Query("SELECT * FROM tombstones")
    suspend fun all(): List<TombstoneEntity>

    @Query("SELECT * FROM tombstones WHERE kind = :kind AND id = :id")
    suspend fun get(kind: TombstoneKind, id: String): TombstoneEntity?
}
