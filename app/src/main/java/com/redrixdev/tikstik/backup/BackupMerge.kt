// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.backup

import com.redrixdev.tikstik.backup.BackupTombstone.Kind

/** A union of the packs and their stickers with a record of deletions; loose collection stickers do not go in the backup. */
object BackupMerge {

    // The version with more changes wins; on a tie, an order every phone agrees on, or each copy would flip it back.
    private val WINNER = compareBy<BackupPack>({ it.imageDataVersion }, { it.toString() })

    fun merge(remote: BackupManifest?, local: BackupManifest): BackupManifest {
        if (remote == null) return local.packsOnly().sorted()
        val tombstones = (local.tombstones + remote.tombstones).groupBy { it.kind to it.id }.values.map { list -> list.maxBy { it.deletedAt } }
        val deadPacks = tombstones.filter { it.kind == Kind.PACK }.mapTo(HashSet()) { it.id }
        val deadStickers = tombstones.filter { it.kind == Kind.STICKER }.associate { it.id to it.deletedAt }

        val packs = (local.packs + remote.packs).groupBy { it.id }.values.map { list -> list.maxWith(WINNER) }
            .filterNot { it.id in deadPacks }
        val usedByPacks = packs.flatMapTo(HashSet()) { pack -> pack.stickers.map { it.stickerId } }

        val stickers = (local.stickers + remote.stickers).filter { it.id in usedByPacks }.groupBy { it.id }.values.map { list ->
            val merged = list.first().copy(
                addedAt = list.minOf { it.addedAt },
                seenAt = list.maxOf { it.seenAt },
                inCollection = list.any { it.inCollection },
            )
            // Deleted after the last time it was held or seen: out of the collection, but still in its pack.
            val deleted = (deadStickers[merged.id] ?: Long.MIN_VALUE) >= maxOf(merged.addedAt, merged.seenAt)
            if (deleted) merged.copy(inCollection = false) else merged
        }

        val importKeys = (local.importKeys + remote.importKeys).groupBy { it.key }.values.map { list ->
            BackupImportKey(list.first().key, list.minOf { it.shownAt }, list.mapNotNull { it.importedAt }.minOrNull())
        }
        return local.copy(
            stickers = stickers,
            packs = packs,
            importKeys = importKeys,
            packBaseName = local.packBaseName ?: remote.packBaseName,
            tombstones = tombstones,
        ).sorted()
    }

    // An older backup may carry loose stickers: they do not get through either.
    private fun BackupManifest.packsOnly(): BackupManifest {
        val used = packs.flatMapTo(HashSet()) { pack -> pack.stickers.map { it.stickerId } }
        return copy(stickers = stickers.filter { it.id in used })
    }

    // Same content, same JSON: that is how it knows whether the backup needs uploading.
    private fun BackupManifest.sorted() = copy(
        stickers = stickers.sortedBy { it.id },
        packs = packs.sortedWith(compareBy({ it.createdAt }, { it.id })),
        importKeys = importKeys.sortedBy { it.key },
        tombstones = tombstones.sortedWith(compareBy({ it.kind }, { it.id })),
    )
}
