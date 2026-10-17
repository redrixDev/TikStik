// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.packs

import android.content.Context
import android.os.Build
import android.os.FileObserver
import androidx.datastore.core.DataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import com.redrixdev.tikstik.collection.CollectionRepository
import com.redrixdev.tikstik.core.common.Clock
import com.redrixdev.tikstik.core.data.db.AppDatabase
import com.redrixdev.tikstik.core.data.db.StickerEntity
import com.redrixdev.tikstik.core.data.files.StickerFiles
import com.redrixdev.tikstik.core.data.settings.AppSettingsSerializer
import com.redrixdev.tikstik.core.data.settings.SettingsRepository
import com.redrixdev.tikstik.media.MediaFixtures
import com.redrixdev.tikstik.media.MediaProbe
import com.redrixdev.tikstik.media.StickerOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class WhatsAppCopiesTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var root: File
    private lateinit var files: StickerFiles
    private lateinit var collection: CollectionRepository
    private lateinit var copies: WhatsAppCopies
    private lateinit var packs: PackRepository
    private lateinit var builder: PackBuilder
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        root = File(context.cacheDir, "copies_test").apply { deleteRecursively() }
        files = StickerFiles(root)
        collection = CollectionRepository(db, files, Clock { 0L }, Dispatchers.IO)
        copies = WhatsAppCopies(db, files, Dispatchers.IO)
        packs = PackRepository(db, files, collection, Clock { 0L }, Dispatchers.IO)
        val settings = SettingsRepository(DataStoreFactory.create(AppSettingsSerializer, scope = scope) { File(root, "settings.json") })
        builder = PackBuilder(context, packs, copies, settings, Dispatchers.Default, Dispatchers.Default)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
        root.deleteRecursively()
    }

    private fun add(path: String): StickerEntity = runBlocking {
        val bytes = MediaFixtures.bytes(path)
        val probe = MediaProbe.probe(bytes)
        val sticker = StickerEntity(
            MediaFixtures.sha256(bytes), probe.format.extension, probe.animated, probe.width ?: 0, probe.height ?: 0,
            probe.frames, probe.durationMs, bytes.size.toLong(), null, null, 0, null, 0, 0,
        )
        collection.add(sticker, bytes)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun concurrentRequestsShareOneCopy() = runBlocking<Unit> {
        val sticker = add("media/gif_interlaced_300x180_12f_0ms.gif")
        val target = copies.file(sticker.id)
        val dir = target.parentFile!!.apply { mkdirs() }
        // Each finished write of the copy: renamed into place, or written straight to its name.
        val writes = AtomicInteger()
        val observer = object : FileObserver(dir, FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE) {
            override fun onEvent(event: Int, path: String?) {
                if (path == target.name) writes.incrementAndGet()
            }
        }
        observer.startWatching()
        try {
            val copy = List(4) { async(Dispatchers.Default) { copies.ensure(sticker) } }.awaitAll().distinct().single()
            assertThat(StickerOutput.isValidSticker(copy.readBytes(), sticker.animated)).isTrue()
            // Once made, it is not converted again.
            copies.ensure(sticker)
            // The events arrive on the observer's own thread.
            delay(500)
        } finally {
            observer.stopWatching()
        }
        assertThat(writes.get()).isEqualTo(1)
        assertThat(copies.missing()).isEmpty()
    }

    @Test
    fun theWorkerPreparesEverythingMissingAndSkipsWhatFails() = runBlocking<Unit> {
        val good = listOf("media/gif_single_200x260.gif", "media/webp_small_lossless_128.webp", "gallery/anim_00.webp").map(::add)
        val broken = add("media/gif_busy_360_120f_40ms.gif").also { files.write(files.original(it.id, it.fileExt), ByteArray(64)) }
        // As after moving to another phone: a pack without its tray.
        val pack = packs.create({ "TikStik $it" }, animated = false, good.take(2).map { it.id }, "😀", byteArrayOf(9))
        files.delete(packs.tray(pack.id))

        val worker = TestListenableWorkerBuilder<PrepareCopiesWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                    PrepareCopiesWorker(appContext, workerParameters, copies, collection, builder, Dispatchers.IO)
            })
            .build()

        assertThat(worker.doWork()).isEqualTo(ListenableWorker.Result.success())
        good.forEach { assertThat(StickerOutput.isValidSticker(copies.file(it.id).readBytes(), it.animated)).isTrue() }
        assertThat(copies.missing().map { it.id }).containsExactly(broken.id)
        assertThat(StickerOutput.isValidTray(packs.tray(pack.id).readBytes())).isTrue()
    }
}
