// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.whatsapp

import android.content.Intent
import androidx.datastore.core.DataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.redrixdev.tikstik.collection.CollectionRepository
import com.redrixdev.tikstik.core.common.Clock
import com.redrixdev.tikstik.core.data.db.AppDatabase
import com.redrixdev.tikstik.core.data.db.StickerEntity
import com.redrixdev.tikstik.core.data.files.StickerFiles
import com.redrixdev.tikstik.core.data.settings.AppSettingsSerializer
import com.redrixdev.tikstik.core.data.settings.PendingAdd
import com.redrixdev.tikstik.core.data.settings.SettingsRepository
import com.redrixdev.tikstik.packs.PackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** "Add" per pack, the whitelist, the limit of 10 and the persisted pending add, with an in-memory fake WhatsApp. */
@RunWith(AndroidJUnit4::class)
class WhatsAppPacksTest {

    private class FakeGateway : WhatsAppGateway {
        var installed = listOf(WhatsAppApp.CONSUMER)
        val whitelist = mutableSetOf<Pair<WhatsAppApp, String>>()
        var unknown = false
        val asked = mutableListOf<String>()

        override fun installed() = installed
        override fun isAdded(app: WhatsAppApp, packId: String): Boolean? {
            asked += packId
            return if (unknown) null else (app to packId) in whitelist
        }
        override fun addIntent(app: WhatsAppApp, packId: String, name: String) = Intent()
        override fun openIntent(app: WhatsAppApp): Intent? = null
    }

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "whatsapp_packs_test")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gateway = FakeGateway()
    private lateinit var db: AppDatabase
    private lateinit var packs: PackRepository
    private lateinit var settings: SettingsRepository
    private lateinit var whatsApp: WhatsAppPacks
    private var time = 1L

    @Before
    fun setUp() {
        root.deleteRecursively()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val files = StickerFiles(root)
        val clock = Clock { time++ }
        val collection = CollectionRepository(db, files, clock, Dispatchers.IO)
        packs = PackRepository(db, files, collection, clock, Dispatchers.IO)
        settings = SettingsRepository(DataStoreFactory.create(AppSettingsSerializer, scope = scope) { File(root, "settings.json") })
        whatsApp = WhatsAppPacks(gateway, packs, settings, Dispatchers.IO)
        runBlocking { (1..40).forEach { collection.add(StickerEntity("s$it", "webp", false, 512, 512, 1, 0, 3, null, null, 0, null, it.toLong(), 0), byteArrayOf(1)) } }
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
        root.deleteRecursively()
    }

    private fun pack(size: Int = 3, from: Int = 1) = runBlocking {
        packs.create({ "TikStik $it" }, animated = false, (from until from + size).map { "s$it" }, "😀", byteArrayOf(9)).id
    }

    private fun offered(id: String) = runBlocking { packs.get(id)!!.pack.offered }

    private suspend fun nextRequest() = withTimeout(2_000) { whatsApp.requests.first() }

    private suspend fun noRequest() = withTimeoutOrNull(300) { whatsApp.requests.first() }

    @Test
    fun aPackIsOfferedBeforeOpeningWhatsAppAndKeptOnlyIfItIsAdded() = runBlocking<Unit> {
        val id = pack()
        whatsApp.add(id, WhatsAppApp.CONSUMER)
        assertThat(nextRequest()).isEqualTo(AddRequest(id, "TikStik 1", WhatsAppApp.CONSUMER))
        assertThat(offered(id)).isTrue()

        // The user cancels in WhatsApp: it is no longer offered.
        whatsApp.onResult()
        assertThat(offered(id)).isFalse()

        whatsApp.add(id, WhatsAppApp.CONSUMER)
        nextRequest()
        gateway.whitelist += WhatsAppApp.CONSUMER to id
        whatsApp.onResult()
        assertThat(offered(id)).isTrue()
        assertThat(whatsApp.added.value[id]).containsExactly(WhatsAppApp.CONSUMER)
    }

    @Test
    fun lessThanThreeIsNeverOffered() = runBlocking<Unit> {
        val id = pack(size = 2)
        whatsApp.add(id, WhatsAppApp.CONSUMER)
        assertThat(noRequest()).isNull()
        assertThat(offered(id)).isFalse()
    }

    @Test
    fun theEleventhWaitsAndGoesAloneWhenThereIsRoom() = runBlocking<Unit> {
        val ten = (0 until 10).map { pack(from = 1 + it * 3) }
        packs.setOffered(ten.toSet(), true)
        ten.forEach { gateway.whitelist += WhatsAppApp.CONSUMER to it }
        val eleventh = pack(from = 31)

        whatsApp.add(eleventh, WhatsAppApp.CONSUMER)
        assertThat(noRequest()).isNull()
        assertThat(offered(eleventh)).isFalse()
        assertThat(settings.settings.first().pendingAdd).isEqualTo(PendingAdd(eleventh, WhatsAppApp.CONSUMER))
        assertThat(whatsApp.limit.first()).isEqualTo(WhatsAppApp.CONSUMER)

        // Coming back without removing anything, the sheet stays.
        whatsApp.refresh()
        assertThat(noRequest()).isNull()
        assertThat(whatsApp.limit.first()).isEqualTo(WhatsAppApp.CONSUMER)

        // The user removes one in WhatsApp: its slot frees up and the pending one is added by itself.
        gateway.whitelist -= WhatsAppApp.CONSUMER to ten[4]
        whatsApp.refresh()
        assertThat(offered(ten[4])).isFalse()
        assertThat(nextRequest().packId).isEqualTo(eleventh)
        assertThat(offered(eleventh)).isTrue()
        assertThat(whatsApp.limit.first()).isNull()
    }

    @Test
    fun aPendingPackThatNoLongerExistsIsForgotten() = runBlocking<Unit> {
        val id = pack()
        settings.setPendingAdd(PendingAdd(id, WhatsAppApp.CONSUMER))
        packs.delete(id)
        whatsApp.refresh()
        assertThat(settings.settings.first().pendingAdd).isNull()
        assertThat(noRequest()).isNull()
    }

    @Test
    fun aPendingPackWithTooFewStickersIsForgotten() = runBlocking<Unit> {
        val id = pack(size = 2)
        settings.setPendingAdd(PendingAdd(id, WhatsAppApp.CONSUMER))
        whatsApp.refresh()
        assertThat(settings.settings.first().pendingAdd).isNull()
        assertThat(noRequest()).isNull()
        assertThat(offered(id)).isFalse()
    }

    @Test
    fun ifWhatsAppCannotSayNothingChanges() = runBlocking<Unit> {
        val id = pack()
        packs.setOffered(setOf(id), true)
        gateway.unknown = true
        whatsApp.refresh()
        assertThat(offered(id)).isTrue()

        gateway.unknown = false
        gateway.installed = emptyList()
        whatsApp.refresh()
        assertThat(offered(id)).isTrue()
    }

    @Test
    fun anUnknownAnswerKeepsWhatWasKnown() = runBlocking<Unit> {
        val id = pack()
        gateway.whitelist += WhatsAppApp.CONSUMER to id
        whatsApp.refresh()
        assertThat(whatsApp.added.value[id]).containsExactly(WhatsAppApp.CONSUMER)

        // A failed query is no answer: the pack must not show as missing from WhatsApp.
        gateway.unknown = true
        whatsApp.refresh()
        assertThat(whatsApp.added.value[id]).containsExactly(WhatsAppApp.CONSUMER)

        gateway.installed = emptyList()
        whatsApp.refresh()
        assertThat(whatsApp.added.value[id]).isEmpty()
    }

    @Test
    fun afterTheFirstCheckOnlyOfferedPacksAreAskedAbout() = runBlocking<Unit> {
        val offeredOne = pack()
        val others = (0 until 5).map { pack(from = 4 + it * 3) }
        gateway.whitelist += WhatsAppApp.CONSUMER to offeredOne
        whatsApp.refresh()
        assertThat(gateway.asked).containsAtLeastElementsIn(others + offeredOne)
        // Each round, only the offered ones: with 100 packs it was hundreds of queries.
        gateway.asked.clear()
        whatsApp.refresh()
        assertThat(gateway.asked).containsExactly(offeredOne)
    }

    @Test
    fun businessCountsOnItsOwn() = runBlocking<Unit> {
        gateway.installed = WhatsAppApp.entries
        val id = pack()
        gateway.whitelist += WhatsAppApp.CONSUMER to id
        whatsApp.refresh()
        assertThat(offered(id)).isTrue()

        whatsApp.add(id, WhatsAppApp.CONSUMER)
        assertThat(noRequest()).isNull()
        whatsApp.add(id, WhatsAppApp.BUSINESS)
        assertThat(nextRequest().app).isEqualTo(WhatsAppApp.BUSINESS)
        gateway.whitelist += WhatsAppApp.BUSINESS to id
        whatsApp.onResult()
        assertThat(whatsApp.added.value[id]).containsExactlyElementsIn(WhatsAppApp.entries)
    }
}
