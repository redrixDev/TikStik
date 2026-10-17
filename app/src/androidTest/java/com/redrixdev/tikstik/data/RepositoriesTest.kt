// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.redrixdev.tikstik.collection.CollectionRepository
import com.redrixdev.tikstik.core.common.Clock
import com.redrixdev.tikstik.core.data.db.AppDatabase
import com.redrixdev.tikstik.core.data.db.StickerEntity
import com.redrixdev.tikstik.core.data.db.TombstoneKind
import com.redrixdev.tikstik.core.data.files.StickerFiles
import com.redrixdev.tikstik.packs.PackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RepositoriesTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var root: File
    private lateinit var files: StickerFiles
    private lateinit var collection: CollectionRepository
    private lateinit var packs: PackRepository
    private var time = 1_000L
    private val clock = Clock { time++ }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        root = File(context.cacheDir, "repositories_test").apply { deleteRecursively() }
        files = StickerFiles(root)
        collection = CollectionRepository(db, files, clock, Dispatchers.IO)
        packs = PackRepository(db, files, collection, clock, Dispatchers.IO)
    }

    @After
    fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    private fun sticker(id: String, seenAt: Long = 0, addedAt: Long = time++, animated: Boolean = false) =
        StickerEntity(id, "webp", animated, 512, 512, if (animated) 8 else 1, 0, 3, null, null, 0, null, addedAt, seenAt)

    private fun ids() = runBlocking { collection.collection.first().map { it.id } }

    @Test
    fun collectionIsOrderedByLatestOfSeenAndAdded() = runBlocking<Unit> {
        collection.add(sticker("a", seenAt = 0, addedAt = 100), byteArrayOf(1))
        collection.add(sticker("b", seenAt = 500, addedAt = 50), byteArrayOf(2))
        collection.add(sticker("c", seenAt = 0, addedAt = 300), byteArrayOf(3))
        assertThat(ids()).containsExactly("b", "c", "a").inOrder()

        collection.touchSeen(mapOf("a" to 900L))
        assertThat(ids()).containsExactly("a", "b", "c").inOrder()

        // Seen long ago on TikTok but just added: goes to the top.
        collection.add(sticker("d", seenAt = 200, addedAt = 1_000), byteArrayOf(4))
        assertThat(ids()).containsExactly("d", "a", "b", "c").inOrder()
    }

    @Test
    fun addWritesTheFileBeforeTheRow() = runBlocking<Unit> {
        val added = collection.add(sticker("a"), byteArrayOf(7, 7))
        assertThat(collection.file(added).readBytes()).isEqualTo(byteArrayOf(7, 7))
        // A failing disk (here, the folder is a file) leaves no row without its file.
        File(root, "originals").deleteRecursively()
        File(root, "originals").writeBytes(byteArrayOf(0))
        runCatching { collection.add(sticker("b"), byteArrayOf(1)) }
        assertThat(db.stickers().get("b")).isNull()
    }

    @Test
    fun deletingKeepsWhatAPackStillUses() = runBlocking<Unit> {
        listOf("a", "b", "c", "d").forEach { collection.add(sticker(it), byteArrayOf(1)) }
        packs.create({ "TikStik $it" }, animated = false, listOf("a", "b", "c"), "😀", byteArrayOf(9))

        collection.delete(setOf("a", "d"))

        assertThat(ids()).containsExactly("c", "b").inOrder()
        assertThat(db.stickers().get("a")!!.inCollection).isFalse()
        assertThat(files.original("a", "webp").exists()).isTrue()
        assertThat(db.stickers().get("d")).isNull()
        assertThat(files.original("d", "webp").exists()).isFalse()
        assertThat(db.tombstones().all().map { it.kind to it.id }).containsExactly(TombstoneKind.STICKER to "a", TombstoneKind.STICKER to "d")
    }

    @Test
    fun removingFromAPackPurgesWhatNobodyUses() = runBlocking<Unit> {
        listOf("a", "b", "c", "d").forEach { collection.add(sticker(it), byteArrayOf(1)) }
        val pack = packs.create({ "TikStik $it" }, animated = false, listOf("a", "b", "c", "d"), "😀", byteArrayOf(9))
        collection.delete(setOf("a"))

        packs.remove(pack.id, setOf("a"), trayPng = byteArrayOf(8))

        assertThat(db.stickers().get("a")).isNull()
        assertThat(files.original("a", "webp").exists()).isFalse()
        assertThat(files.tray(pack.id).readBytes()).isEqualTo(byteArrayOf(8))
    }

    @Test
    fun everyChangeRaisesTheVersionAndNumbersFollowTheHighest() = runBlocking<Unit> {
        listOf("a", "b", "c").forEach { collection.add(sticker(it), byteArrayOf(1)) }
        val first = packs.create({ "TikStik $it" }, animated = false, listOf("a", "b", "c"), "😀", byteArrayOf(9))
        val second = packs.create({ "TikStik $it" }, animated = false, listOf("a", "b"), "😀", byteArrayOf(9))
        assertThat(first.number to second.number).isEqualTo(1 to 2)

        packs.rename(first.id, "Fiesta 2024")
        packs.reorder(first.id, listOf("c", "a", "b"), trayPng = null)
        packs.delete(second.id)
        val third = packs.create({ "TikStik $it" }, animated = false, listOf("a"), "😀", byteArrayOf(9))

        val saved = db.packs().get(first.id)!!
        assertThat(saved.pack.name).isEqualTo("Fiesta 2024")
        assertThat(saved.stickerIds).containsExactly("c", "a", "b").inOrder()
        assertThat(saved.pack.imageDataVersion).isEqualTo(3)
        // The renamed name does not count: numbering follows the stored number.
        assertThat(third.name).isEqualTo("TikStik 2")
    }

    @Test
    fun aChangeThatChangesNothingKeepsTheVersion() = runBlocking<Unit> {
        listOf("a", "b", "c").forEach { collection.add(sticker(it), byteArrayOf(1)) }
        val pack = packs.create({ "TikStik $it" }, animated = false, listOf("a", "b", "c"), "😀", byteArrayOf(9))

        // The same name with spaces, a sticker dropped where it was and one it does not have: WhatsApp has nothing to reload.
        packs.rename(pack.id, " TikStik 1 ")
        packs.reorder(pack.id, listOf("a", "b", "c"), trayPng = byteArrayOf(8))
        packs.remove(pack.id, setOf("z"), trayPng = null)

        assertThat(db.packs().get(pack.id)!!.pack.imageDataVersion).isEqualTo(1)
        assertThat(files.tray(pack.id).readBytes()).isEqualTo(byteArrayOf(9))
    }

    @Test
    fun aPackInWhatsAppKeepsAtLeastThree() = runBlocking<Unit> {
        listOf("a", "b", "c").forEach { collection.add(sticker(it), byteArrayOf(1)) }
        val pack = packs.create({ "TikStik $it" }, animated = false, listOf("a", "b", "c"), "😀", byteArrayOf(9))
        packs.setOffered(setOf(pack.id), offered = true)

        assertThrows(IllegalArgumentException::class.java) { runBlocking { packs.remove(pack.id, setOf("a"), null) } }
        assertThat(db.packs().get(pack.id)!!.stickerIds).hasSize(3)
    }

    @Test
    fun aBetterVersionTakesThePlaceButPacksKeepTheirs() = runBlocking<Unit> {
        listOf("a", "b", "c").forEach { collection.add(sticker(it), byteArrayOf(1)) }
        packs.create({ "TikStik $it" }, animated = false, listOf("a", "b", "c"), "😀", byteArrayOf(9))

        collection.replace("a", sticker("a2", seenAt = 2_000), byteArrayOf(5))

        assertThat(ids()).contains("a2")
        assertThat(ids()).doesNotContain("a")
        assertThat(db.packs().packs().first().single().stickerIds).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun aPackSwitchesToTheBetterVersionKeepingPositionAndEmoji() = runBlocking<Unit> {
        listOf("a", "b", "c").forEach { collection.add(sticker(it), byteArrayOf(1)) }
        val pack = packs.create({ "TikStik $it" }, animated = false, listOf("a", "b", "c"), "😀", byteArrayOf(9))
        collection.replace("a", sticker("a2", seenAt = 2_000), byteArrayOf(5))

        packs.replaceSticker(pack.id, "a", "a2", trayPng = byteArrayOf(8))

        val saved = db.packs().get(pack.id)!!
        assertThat(saved.stickerIds).containsExactly("a2", "b", "c").inOrder()
        assertThat(saved.items.first { it.stickerId == "a2" }.emoji).isEqualTo("😀")
        assertThat(saved.pack.imageDataVersion).isEqualTo(2)
        assertThat(files.tray(pack.id).readBytes()).isEqualTo(byteArrayOf(8))
        // Nobody uses the old version any more: it is gone for good.
        assertThat(db.stickers().get("a")).isNull()
        assertThat(files.original("a", "webp").exists()).isFalse()
    }

    @Test
    fun aBetterVersionWithTheSameBytesOnlyUpdatesItsData() = runBlocking<Unit> {
        collection.add(sticker("a"), byteArrayOf(1))

        collection.replace("a", sticker("a", seenAt = 2_000).copy(rank = 3), byteArrayOf(1))

        assertThat(ids()).containsExactly("a")
        assertThat(db.stickers().get("a")!!.rank).isEqualTo(3)
        assertThat(db.tombstones().all()).isEmpty()
        assertThat(files.original("a", "webp").exists()).isTrue()
    }

    @Test
    fun sweepRemovesOrphansButNeverLiveFiles() = runBlocking<Unit> {
        collection.add(sticker("a"), byteArrayOf(1))
        val orphan = files.original("ghost", "webp").apply { writeBytes(byteArrayOf(1)) }
        orphan.setLastModified(0)
        time = 10 * 60 * 60 * 1000L
        files.original("a", "webp").setLastModified(0)

        assertThat(collection.sweepOrphans()).isEqualTo(1)
        assertThat(orphan.exists()).isFalse()
        assertThat(files.original("a", "webp").exists()).isTrue()
    }
}
