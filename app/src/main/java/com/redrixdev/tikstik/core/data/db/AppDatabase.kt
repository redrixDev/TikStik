// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

// v1 is frozen with its schema in app/schemas; every change means a new version with a tested migration.
@Database(
    entities = [StickerEntity::class, PackEntity::class, PackStickerEntity::class, ImportKeyEntity::class, TombstoneEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stickers(): StickerDao
    abstract fun packs(): PackDao
    abstract fun importKeys(): ImportKeyDao
    abstract fun tombstones(): TombstoneDao

    companion object {
        const val NAME = "tikstik.db"
    }
}
