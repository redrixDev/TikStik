// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.backup

import kotlinx.serialization.Serializable

/** The Drive backup index (`manifest.json`), neutral enough for iPhone too: JSON plus the originals as they are. */
@Serializable
data class BackupManifest(
    val format: Int = FORMAT,
    val savedAt: Long,
    // The phone that wrote it (random install id).
    val device: String,
    val stickers: List<BackupSticker>,
    val packs: List<BackupPack>,
    val importKeys: List<BackupImportKey> = emptyList(),
    val packBaseName: String? = null,
    // What someone deleted on purpose on some phone.
    val tombstones: List<BackupTombstone> = emptyList(),
) {
    companion object {
        const val FORMAT = 1
        const val FILE = "manifest.json"

        fun stickerFile(sticker: BackupSticker) = "sticker_${sticker.id}.${sticker.ext}"
    }
}

/** [inCollection] false: no longer in the collection, but some pack uses it. */
@Serializable
data class BackupSticker(
    val id: String,
    val ext: String,
    val animated: Boolean,
    val width: Int,
    val height: Int,
    val frames: Int,
    val durationMs: Int,
    val bytes: Long,
    val sourceKey: String? = null,
    val variant: String? = null,
    val rank: Int = 0,
    val phash: String? = null,
    val addedAt: Long,
    val seenAt: Long = 0,
    val inCollection: Boolean = true,
)

@Serializable
data class BackupPack(
    val id: String,
    val name: String,
    val number: Int,
    val animated: Boolean,
    val createdAt: Long,
    val imageDataVersion: Int,
    val stickers: List<BackupPackSticker>,
)

@Serializable
data class BackupPackSticker(val stickerId: String, val emoji: String)

@Serializable
data class BackupImportKey(val key: String, val shownAt: Long, val importedAt: Long? = null)

@Serializable
data class BackupTombstone(val kind: Kind = Kind.UNKNOWN, val id: String, val deletedAt: Long) {
    // UNKNOWN: a kind from a newer version; it is ignored instead of making the backup unreadable.
    enum class Kind { STICKER, PACK, UNKNOWN }
}
