// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.backup

import com.google.common.truth.Truth.assertThat
import com.redrixdev.tikstik.backup.BackupTombstone.Kind
import org.junit.Test

class BackupMergeTest {

    private fun sticker(id: String, addedAt: Long = 10, seenAt: Long = 0, inCollection: Boolean = true) =
        BackupSticker(id, "webp", false, 512, 512, 1, 0, 3, addedAt = addedAt, seenAt = seenAt, inCollection = inCollection)

    private fun pack(id: String, vararg stickers: String, version: Int = 1) =
        BackupPack(id, id, 1, false, 5, version, stickers.map { BackupPackSticker(it, "😀") })

    private fun manifest(
        device: String,
        stickers: List<BackupSticker> = emptyList(),
        packs: List<BackupPack> = emptyList(),
        tombstones: List<BackupTombstone> = emptyList(),
        keys: List<BackupImportKey> = emptyList(),
        base: String? = null,
    ) = BackupManifest(savedAt = 100, device = device, stickers = stickers, packs = packs, importKeys = keys, packBaseName = base, tombstones = tombstones)

    private fun ids(m: BackupManifest) = m.stickers.map { it.id }

    @Test
    fun twoPhonesKeepEverything() {
        val a = manifest("a", listOf(sticker("1"), sticker("2")), listOf(pack("p", "1", "2")))
        val b = manifest("b", listOf(sticker("2", addedAt = 5, seenAt = 50), sticker("3")), listOf(pack("q", "2", "3")))
        val merged = BackupMerge.merge(remote = b, local = a)
        assertThat(ids(merged)).containsExactly("1", "2", "3").inOrder()
        val two = merged.stickers.single { it.id == "2" }
        assertThat(two.addedAt to two.seenAt).isEqualTo(5L to 50L)
        assertThat(merged.device).isEqualTo("a")
    }

    @Test
    fun anEmptyOrPartialPhoneNeverWipesTheCopy() {
        val remote = manifest("b", listOf(sticker("1"), sticker("2")), listOf(pack("p", "1", "2")))
        val merged = BackupMerge.merge(remote = remote, local = manifest("a"))
        assertThat(ids(merged)).containsExactly("1", "2")
        assertThat(merged.packs.map { it.id }).containsExactly("p")
    }

    @Test
    fun looseStickersStayOutEvenFromAnOldCopy() {
        // Only the packs and their stickers: loose ones stay out, whether from here or from an older backup.
        val remote = manifest("b", listOf(sticker("1"), sticker("2")), listOf(pack("p", "1")))
        val local = manifest("a", listOf(sticker("3")))
        assertThat(ids(BackupMerge.merge(remote, local))).containsExactly("1")
        assertThat(ids(BackupMerge.merge(null, local))).isEmpty()
    }

    @Test
    fun aStickerAddedOrSeenAgainAfterTheDeleteStaysInTheCollection() {
        val remote = manifest("b", packs = listOf(pack("p", "1")), tombstones = listOf(BackupTombstone(Kind.STICKER, "1", deletedAt = 20)))
        val local = manifest("a", listOf(sticker("1", addedAt = 30)))
        assertThat(BackupMerge.merge(remote, local).stickers.single().inCollection).isTrue()
        val seenLater = manifest("a", listOf(sticker("1", addedAt = 10, seenAt = 25)))
        assertThat(BackupMerge.merge(remote, seenLater).stickers.single().inCollection).isTrue()
    }

    @Test
    fun aDeletedStickerThatAPackUsesStaysOutOfTheCollection() {
        val remote = manifest("b", listOf(sticker("1")), listOf(pack("p", "1")))
        val local = manifest("a", tombstones = listOf(BackupTombstone(Kind.STICKER, "1", deletedAt = 20)))
        val merged = BackupMerge.merge(remote, local)
        assertThat(merged.stickers.single().inCollection).isFalse()
    }

    @Test
    fun deletedPacksGoAndTheMostChangedVersionWins() {
        val remote = manifest("b", packs = listOf(pack("p", "1", version = 4), pack("q", "1")))
        val local = manifest("a", packs = listOf(pack("p", "1", "2", version = 2)), tombstones = listOf(BackupTombstone(Kind.PACK, "q", 50)))
        val merged = BackupMerge.merge(remote, local)
        assertThat(merged.packs.map { it.id to it.imageDataVersion }).containsExactly("p" to 4)
        assertThat(merged.tombstones.map { it.id }).containsExactly("q")
    }

    @Test
    fun onATieEveryPhonePicksTheSamePack() {
        val mine = pack("p", "s1", version = 2).copy(name = "Mine")
        val theirs = pack("p", "s1", version = 2).copy(name = "Theirs")
        val onA = BackupMerge.merge(manifest("b", packs = listOf(theirs)), manifest("a", packs = listOf(mine)))
        val onB = BackupMerge.merge(manifest("a", packs = listOf(mine)), manifest("b", packs = listOf(theirs)))
        assertThat(onA.packs).isEqualTo(onB.packs)
    }

    @Test
    fun importKeysAndBaseNameJoin() {
        val remote = manifest("b", keys = listOf(BackupImportKey("k", 5, 7), BackupImportKey("r", 1)), base = "Fiesta")
        val local = manifest("a", keys = listOf(BackupImportKey("k", 3, null)))
        val merged = BackupMerge.merge(remote, local)
        assertThat(merged.importKeys).containsExactly(BackupImportKey("k", 3, 7), BackupImportKey("r", 1, null)).inOrder()
        assertThat(merged.packBaseName).isEqualTo("Fiesta")
    }

    @Test
    fun theSameContentGivesTheSameResult() {
        // Every change raises the version, so an older copy of the pack is always behind.
        val a = manifest("a", listOf(sticker("2"), sticker("1")), listOf(pack("p", "1", "2", version = 2)))
        assertThat(BackupMerge.merge(null, a)).isEqualTo(BackupMerge.merge(manifest("a", listOf(sticker("1")), listOf(pack("p", "1"))), a))
    }
}
