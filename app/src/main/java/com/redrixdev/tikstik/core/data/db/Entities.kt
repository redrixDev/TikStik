// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** An original (`files/originals/<id>.<fileExt>`); with [inCollection] false it was deleted but some pack still uses it. */
// No indices of its own: no query used them.
@Entity(tableName = "stickers")
data class StickerEntity(
    @PrimaryKey val id: String,
    val fileExt: String,
    val animated: Boolean,
    val width: Int,
    val height: Int,
    val frames: Int,
    val durationMs: Int,
    val bytes: Long,
    val sourceKey: String?,
    val variant: String?,
    val rank: Int,
    val phash: String?,
    val addedAt: Long,
    // Last time TikTok showed it (0: unknown); orders the collection.
    val seenAt: Long,
    val inCollection: Boolean = true,
)

/** A pack. [number] is the one in its default name, to number new packs even after a rename. */
@Entity(tableName = "packs")
data class PackEntity(
    @PrimaryKey val id: String,
    val name: String,
    val number: Int,
    val animated: Boolean,
    val imageDataVersion: Int,
    val createdAt: Long,
    // WhatsApp allows 10 per app: only offered packs show in the provider.
    val offered: Boolean,
)

@Entity(
    tableName = "pack_stickers",
    primaryKeys = ["packId", "stickerId"],
    foreignKeys = [
        ForeignKey(PackEntity::class, ["id"], ["packId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(StickerEntity::class, ["id"], ["stickerId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("stickerId")],
)
data class PackStickerEntity(val packId: String, val stickerId: String, val position: Int, val emoji: String)

data class PackWithStickers(
    @Embedded val pack: PackEntity,
    @Relation(parentColumn = "id", entityColumn = "packId") val items: List<PackStickerEntity>,
) {
    val stickerIds: List<String> get() = items.sortedBy { it.position }.map { it.stickerId }
}

/** A TikTok sticker by its key: when it first showed up on import and whether it was ever imported. */
@Entity(tableName = "import_keys")
data class ImportKeyEntity(@PrimaryKey val key: String, val shownAt: Long, val importedAt: Long?)

enum class TombstoneKind { STICKER, PACK }

/** Something the user deleted on purpose: the Drive backup deletes it too (a merge with a deletion log). */
@Entity(tableName = "tombstones", primaryKeys = ["kind", "id"])
data class TombstoneEntity(val kind: TombstoneKind, val id: String, val deletedAt: Long)
