// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.backup

import android.annotation.SuppressLint
import android.content.Intent
import androidx.datastore.core.DataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.testing.WorkManagerTestInitHelper.ExecutorsMode
import com.google.common.truth.Truth.assertThat
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.collection.CollectionRepository
import com.redrixdev.tikstik.core.common.Clock
import com.redrixdev.tikstik.core.data.db.AppDatabase
import com.redrixdev.tikstik.core.data.db.StickerEntity
import com.redrixdev.tikstik.core.data.files.StickerFiles
import com.redrixdev.tikstik.core.data.settings.AppSettingsSerializer
import com.redrixdev.tikstik.core.data.settings.BackupInfo
import com.redrixdev.tikstik.core.data.settings.SettingsRepository
import com.redrixdev.tikstik.core.ui.Notices
import com.redrixdev.tikstik.core.ui.UiText
import com.redrixdev.tikstik.media.MediaFixtures
import com.redrixdev.tikstik.media.MediaProbe
import com.redrixdev.tikstik.packs.CopyPreparation
import com.redrixdev.tikstik.packs.PackBuilder
import com.redrixdev.tikstik.packs.PackLimits
import com.redrixdev.tikstik.packs.PackRepository
import com.redrixdev.tikstik.packs.WhatsAppCopies
import com.redrixdev.tikstik.whatsapp.PackRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The Drive backup and the account, with in-memory fake Drives and two phones (two databases and two folders). */
@RunWith(AndroidJUnit4::class)
class BackupSyncTest {

    // The backup uploads in parallel: the fake Drive has to handle concurrent calls.
    private class FakeDrive(private val email: String) : DriveClient {
        val files = LinkedHashMap<String, Pair<String, ByteArray>>()
        val modified = HashMap<String, String>()
        private var next = 0
        // While set, sticker downloads wait for it, outside the lock: a restore can be held halfway.
        @Volatile var hold: CountDownLatch? = null
        val held = CountDownLatch(1)
        @Volatile var offline = false

        override fun user() = DriveUser(email)
        @Synchronized override fun list(): List<DriveFile> {
            if (offline) throw DriveNetworkException(IOException("offline"))
            return files.map { (id, file) -> DriveFile(id, file.first, modified.getValue(id)) }
        }
        override fun download(id: String): ByteArray {
            val (name, bytes) = synchronized(this) { files.getValue(id) }
            hold?.takeIf { name.startsWith("sticker_") }?.let { held.countDown(); it.await() }
            return bytes
        }
        @Synchronized override fun create(name: String, mime: String, bytes: ByteArray): String {
            val id = "f${next++}"
            files[id] = name to bytes
            modified[id] = "%06d".format(next)
            return id
        }

        @Synchronized override fun delete(id: String) {
            files.remove(id)
        }

        fun names() = files.values.map { it.first }
        fun manifest(): BackupManifest = Json.decodeFromString(BackupManifest.serializer(), files.values.single { it.first == BackupManifest.FILE }.second.decodeToString())
    }

    // Google's sheet chooses [chosen]; the token is the account's email.
    private class FakeAuthorizer : GoogleAuthorizer {
        @Volatile var chosen = A
        val revoked = CopyOnWriteArrayList<String>()

        override suspend fun authorize(email: String?) = AuthStep.Token(email ?: chosen)
        override fun fromIntent(data: Intent?): AuthStep = AuthStep.Failed
        override suspend fun clearToken(token: String) = Unit
        override suspend fun revoke(email: String) {
            revoked += email
        }
    }

    private inner class Phone(name: String) {
        val root = File(context.cacheDir, "backup_test_$name").apply { deleteRecursively() }
        val db: AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val files = StickerFiles(root)
        val clock = Clock { time++ }
        val collection = CollectionRepository(db, files, clock, Dispatchers.IO)
        val packs = PackRepository(db, files, collection, clock, Dispatchers.IO)
        val settings = SettingsRepository(DataStoreFactory.create(AppSettingsSerializer, scope = scope) { File(root, "settings.json") })
        val builder = PackBuilder(context, packs, WhatsAppCopies(db, files, Dispatchers.IO), settings, Dispatchers.Default, Dispatchers.Default)
        val sync = BackupSync(db, collection, builder, settings, { InstallId(name) }, Dispatchers.IO)

        fun entity(bytes: ByteArray): StickerEntity {
            val probe = MediaProbe.probe(bytes)
            return StickerEntity(MediaFixtures.sha256(bytes), probe.format.extension, probe.animated, probe.width ?: 0, probe.height ?: 0, probe.frames, probe.durationMs, bytes.size.toLong(), null, null, 0, null, time++, 0)
        }

        fun add(path: String): StickerEntity = runBlocking {
            val bytes = MediaFixtures.bytes(path)
            collection.add(entity(bytes), bytes)
        }

        // A pack with the stickers of those images: only that goes to the backup.
        fun pack(vararg paths: String): List<StickerEntity> = runBlocking {
            paths.map { add(it) }.also { builder.create(it) { _, _ -> } }
        }

        fun ids() = runBlocking { collection.collection.first().map { it.id }.toSet() }

        fun close() {
            db.close()
            root.deleteRecursively()
        }
    }

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var time = 1_000L
    private val drive = FakeDrive(A)
    private val driveB = FakeDrive(B)
    private val a = Phone("a")
    private val b = Phone("b")

    private var testWork = false

    @After
    @SuppressLint("RestrictedApi")
    fun tearDown() {
        // First what runs in the background, then what it uses.
        scope.cancel()
        a.close()
        b.close()
        if (testWork) {
            WorkManagerTestInitHelper.closeWorkDatabase()
            // The WorkManager for tests would stay for the classes that follow, which use the app's own.
            WorkManagerImpl.setDelegate(null)
        }
    }

    private fun backup(phone: Phone, lastHash: String? = null) = runBlocking { phone.sync.backup(drive, time++, lastHash) { _, _ -> } }

    // With a WorkManager for tests: the work is enqueued but never runs. Its own executors, as the account enqueues from several threads.
    private fun account(phone: Phone, auth: FakeAuthorizer, notices: Notices = Notices()): BackupAccount {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, ExecutorsMode.PRESERVE_EXECUTORS)
        testWork = true
        val work = WorkManager.getInstance(context)
        val drives = mapOf(A to drive, B to driveB)
        return BackupAccount(auth, { drives.getValue(it) }, phone.sync, phone.settings, work, CopyPreparation(work), notices, phone.clock, Dispatchers.IO, scope, phone.collection, phone.packs)
    }

    // On timeout it says the last state seen.
    private suspend fun BackupAccount.await(check: (AccountState) -> Boolean): AccountState {
        var last: AccountState? = null
        return withTimeoutOrNull(20_000) { state.first { last = it; check(it) } } ?: throw AssertionError("still $last")
    }

    private fun enqueued(name: String) = WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get()

    private suspend fun Notices.drain() = buildList { while (true) add(withTimeoutOrNull(300) { notices.first() }?.text ?: break) }

    private fun cheapPacks(phone: Phone, sticker: StickerEntity, names: List<String>) = runBlocking {
        names.forEach { name -> phone.packs.create({ name }, animated = false, listOf(sticker.id), "😀", byteArrayOf(9)) }
    }

    @Test
    fun theCopyKeepsBothPhonesAndARestoreAddsWithoutDeleting() = runBlocking<Unit> {
        val s1 = a.add("gallery/still_00.webp")
        val s2 = a.add("gallery/still_01.webp")
        a.builder.create(listOf(s1, s2, a.add("gallery/still_02.webp"))) { _, _ -> }
        backup(a)
        assertThat(drive.names()).containsExactly(BackupManifest.FILE, "sticker_${s1.id}.webp", "sticker_${s2.id}.webp", "sticker_${a.ids().first { it != s1.id && it != s2.id }}.webp")

        val own = b.add("gallery/anim_00.webp")
        val outcome = b.sync.restore(drive) { _, _ -> }
        assertThat(outcome.stickers to outcome.packs).isEqualTo(3 to 1)
        assertThat(b.ids()).containsAtLeastElementsIn(a.ids() + own.id)
        val restored = b.packs.packs.first().single()
        assertThat(restored.pack.name).isEqualTo("TikStik 1")
        assertThat(restored.pack.offered).isFalse()
        assertThat(restored.stickerIds).containsExactlyElementsIn(a.packs.packs.first().single().stickerIds).inOrder()
        // With their WhatsApp copies already made: the pack can be added without waiting.
        assertThat(restored.stickerIds.all { b.files.whatsApp(it).exists() }).isTrue()

        // B's backup adds its own without removing A's; its loose sticker does not go.
        val bPack = b.pack("gallery/still_03.webp", "gallery/still_04.webp", "gallery/still_05.webp")
        backup(b)
        assertThat(drive.manifest().stickers.map { it.id }).containsExactlyElementsIn(a.ids() + bPack.map { it.id })
        assertThat(drive.names()).doesNotContain("sticker_${own.id}.webp")
        assertThat(drive.names().count { it == BackupManifest.FILE }).isEqualTo(1)
    }

    @Test
    fun aLaterRestoreBringsWhatArrivedSinceAndKeepsWhatThisPhoneDeleted() = runBlocking<Unit> {
        a.pack("gallery/still_00.webp", "gallery/still_01.webp", "gallery/still_02.webp")
        backup(a)
        b.sync.restore(drive) { _, _ -> }
        // B deletes the pack it got; meanwhile A makes another one and backs it up.
        b.packs.delete(b.packs.packs.first().single().pack.id)
        a.pack("gallery/still_03.webp", "gallery/still_04.webp", "gallery/still_05.webp")
        backup(a)

        val second = b.sync.restore(drive) { _, _ -> }
        assertThat(second.packs).isEqualTo(1)
        assertThat(b.packs.packs.first().map { it.pack.name }).containsExactly("TikStik 2")
        // Nothing left to bring: a third merge adds nothing and duplicates nothing.
        val third = b.sync.restore(drive) { _, _ -> }
        assertThat(third.stickers to third.packs).isEqualTo(0 to 0)
        assertThat(b.packs.packs.first()).hasSize(1)
    }

    @Test
    fun reimportingWhatAnotherPhoneDeletedKeepsItInTheCollection() = runBlocking<Unit> {
        val x = a.add("gallery/still_00.webp")
        a.builder.create(listOf(x, a.add("gallery/still_01.webp"), a.add("gallery/still_02.webp"))) { _, _ -> }
        // A removes it from the collection (it stays in the pack) and the backup keeps its tombstone.
        a.collection.delete(setOf(x.id))
        backup(a)
        b.sync.restore(drive) { _, _ -> }
        assertThat(b.ids()).doesNotContain(x.id)
        // B imports it again from a TikTok cache older than the deletion: it counts as seen now.
        val bytes = MediaFixtures.bytes("gallery/still_00.webp")
        b.collection.add(b.entity(bytes), bytes)
        backup(b)
        assertThat(drive.manifest().stickers.single { it.id == x.id }.inCollection).isTrue()
    }

    @Test
    fun looseStickersNeverReachDrive() = runBlocking<Unit> {
        val loose = a.add("gallery/still_09.webp")
        a.pack("gallery/still_00.webp", "gallery/still_01.webp", "gallery/still_02.webp")
        backup(a)
        assertThat(drive.manifest().stickers.map { it.id }).doesNotContain(loose.id)
        assertThat(drive.names()).doesNotContain("sticker_${loose.id}.webp")
    }

    @Test
    fun whatLeavesEveryPackLeavesDrive() = runBlocking<Unit> {
        val kept = a.pack("gallery/still_00.webp", "gallery/still_01.webp", "gallery/still_02.webp")
        val gone = a.pack("gallery/still_03.webp", "gallery/still_04.webp", "gallery/still_05.webp")
        backup(a)
        // Deleting the pack leaves its stickers in the collection, but no longer in the backup or in Drive.
        val goneId = a.packs.packs.first().single { gone.first().id in it.stickerIds }.pack.id
        a.packs.delete(goneId)
        backup(a)
        assertThat(drive.manifest().stickers.map { it.id }).containsExactlyElementsIn(kept.map { it.id })
        gone.forEach { assertThat(drive.names()).doesNotContain("sticker_${it.id}.webp") }
        kept.forEach { assertThat(drive.names()).contains("sticker_${it.id}.webp") }
        assertThat(a.ids()).containsAtLeastElementsIn(gone.map { it.id })
    }

    @Test
    fun aStickerUploadedAfterTheLastManifestIsNotRemoved() = runBlocking<Unit> {
        a.pack("gallery/still_00.webp", "gallery/still_01.webp", "gallery/still_02.webp")
        backup(a)
        // Another phone mid-backup: it has uploaded its sticker, but not yet its manifest.
        val inFlight = drive.create("sticker_${"0".repeat(64)}.webp", "image/webp", byteArrayOf(1))
        a.packs.rename(a.packs.packs.first().single().pack.id, "Otro nombre")
        backup(a)
        assertThat(drive.files.keys).contains(inFlight)
    }

    @Test
    fun nothingChangedNothingUploaded() = runBlocking<Unit> {
        a.pack("gallery/still_00.webp", "gallery/still_01.webp", "gallery/still_02.webp")
        val first = backup(a)
        val files = drive.files.keys.toSet()
        val second = backup(a, first.hash)
        assertThat(second.changed).isFalse()
        assertThat(drive.files.keys).isEqualTo(files)
    }

    @Test
    fun aStickerThatDoesNotMatchItsIdIsNotRestored() = runBlocking<Unit> {
        val s = a.pack("gallery/still_00.webp", "gallery/still_01.webp", "gallery/still_02.webp").first()
        backup(a)
        val fileId = drive.files.entries.single { it.value.first == "sticker_${s.id}.webp" }.key
        drive.files[fileId] = drive.files.getValue(fileId).first to MediaFixtures.bytes("gallery/still_09.webp")
        assertThat(b.sync.restore(drive) { _, _ -> }.stickers).isEqualTo(2)
        assertThat(b.ids()).doesNotContain(s.id)
    }

    @Test
    fun aRestoreDoesNotBringBackWhatThisPhoneDeleted() = runBlocking<Unit> {
        val (s1, s2, s3) = a.pack("gallery/still_00.webp", "gallery/still_01.webp", "gallery/still_02.webp")
        backup(a)
        b.add("gallery/still_00.webp")
        b.collection.delete(setOf(s1.id))
        b.sync.restore(drive) { _, _ -> }
        // It comes back in its pack, but not into B's collection.
        assertThat(b.ids()).containsExactly(s2.id, s3.id)
        assertThat(b.packs.packs.first().single().stickerIds).contains(s1.id)
    }

    @Test
    fun aDeletedStickerAddedAgainStaysInTheCopy() = runBlocking<Unit> {
        val s1 = a.add("gallery/still_00.webp")
        a.builder.create(listOf(s1, a.add("gallery/still_01.webp"), a.add("gallery/still_02.webp"))) { _, _ -> }
        backup(a)
        // It leaves the collection but stays in the pack; then it comes back.
        a.collection.delete(setOf(s1.id))
        a.add("gallery/still_00.webp")
        backup(a)
        assertThat(drive.manifest().stickers.single { it.id == s1.id }.inCollection).isTrue()
    }

    @Test
    fun twoManifestsAreMergedAndBothDeleted() = runBlocking<Unit> {
        val s1 = a.pack("gallery/still_00.webp", "gallery/still_01.webp", "gallery/still_02.webp")
        backup(a)
        // B backs up without seeing A's manifest, as if both were backing up at once.
        val hidden = drive.files.entries.single { it.value.first == BackupManifest.FILE }
        drive.files.remove(hidden.key)
        val s2 = b.pack("gallery/still_03.webp", "gallery/still_04.webp", "gallery/still_05.webp")
        backup(b)
        drive.files[hidden.key] = hidden.value
        backup(b)
        assertThat(drive.manifest().stickers.map { it.id }).containsExactlyElementsIn((s1 + s2).map { it.id })
    }

    @Test
    fun anUnknownTombstoneDoesNotBreakTheCopyButANewerFormatIsNotOverwritten() = runBlocking<Unit> {
        a.pack("gallery/still_00.webp", "gallery/still_01.webp", "gallery/still_02.webp")
        backup(a)
        val id = drive.files.entries.single { it.value.first == BackupManifest.FILE }.key
        val text = drive.files.getValue(id).second.decodeToString()
        drive.files[id] = BackupManifest.FILE to text.replace("\"tombstones\":[]", "\"tombstones\":[{\"kind\":\"ALBUM\",\"id\":\"x\",\"deletedAt\":1}]").encodeToByteArray()
        assertThat(b.sync.restore(drive) { _, _ -> }.stickers).isEqualTo(3)

        drive.files[id] = BackupManifest.FILE to text.replace("\"format\":1", "\"format\":2").encodeToByteArray()
        assertThrows(NewerBackupException::class.java) { backup(a) }
        assertThat(drive.files.getValue(id).second.decodeToString()).contains("\"format\":2")
    }

    @Test
    fun anIncompleteRestoredPackKeepsWhatIsMissingInDrive() = runBlocking<Unit> {
        val s1 = a.add("gallery/still_00.webp")
        val s2 = a.add("gallery/still_01.webp")
        val s3 = a.add("gallery/still_02.webp")
        a.builder.create(listOf(s1, s2, s3)) { _, _ -> }
        // Out of the collection but in the pack: it is left with a deletion record.
        a.collection.delete(setOf(s3.id))
        backup(a)
        // One of the pack's stickers does not reach B.
        drive.files.remove(drive.files.entries.single { it.value.first == "sticker_${s3.id}.webp" }.key)
        b.sync.restore(drive) { _, _ -> }
        assertThat(b.packs.packs.first().single().stickerIds).containsExactly(s1.id, s2.id)
        backup(b)
        assertThat(drive.manifest().packs.single().stickers.map { it.stickerId }).containsExactly(s1.id, s2.id, s3.id)
        assertThat(drive.manifest().stickers.map { it.id }).contains(s3.id)
    }

    @Test
    fun anEditOnAnIncompleteRestoredPackStillKeepsTheMissingOnesInDrive() = runBlocking<Unit> {
        val s1 = a.add("gallery/still_00.webp")
        val s2 = a.add("gallery/still_01.webp")
        val s3 = a.add("gallery/still_02.webp")
        a.builder.create(listOf(s1, s2, s3)) { _, _ -> }
        a.collection.delete(setOf(s3.id))
        backup(a)
        drive.files.remove(drive.files.entries.single { it.value.first == "sticker_${s3.id}.webp" }.key)
        b.sync.restore(drive) { _, _ -> }
        // A single change on the incomplete pack must not make the backup delete the missing ones from Drive.
        val pack = b.packs.packs.first().single()
        b.packs.rename(pack.pack.id, "Otro nombre")
        backup(b)
        assertThat(drive.manifest().packs.single().stickers.map { it.stickerId }).containsExactly(s1.id, s2.id, s3.id)
        assertThat(drive.manifest().stickers.map { it.id }).contains(s3.id)
    }

    @Test
    fun aRestoreCutBeforeSavingItsGapsWorksThemOutAgain() = runBlocking<Unit> {
        val (s1, s2, s3) = a.pack("gallery/still_00.webp", "gallery/still_01.webp", "gallery/still_02.webp")
        backup(a)
        drive.files.remove(drive.files.entries.single { it.value.first == "sticker_${s3.id}.webp" }.key)
        b.sync.restore(drive) { _, _ -> }
        // Cut after saving the pack and before its gaps: the retry must find them again, or the next backup drops s3 from Drive.
        b.settings.updatePackGaps { emptyMap() }
        b.sync.restore(drive) { _, _ -> }
        backup(b)
        assertThat(drive.manifest().packs.single().stickers.map { it.stickerId }).containsExactly(s1.id, s2.id, s3.id)
        assertThat(drive.manifest().stickers.map { it.id }).contains(s3.id)
    }

    @Test
    fun aBackupWaitsForTheRestoreToEnd() = runBlocking<Unit> {
        a.pack("gallery/still_00.webp", "gallery/still_01.webp", "gallery/still_02.webp")
        backup(a)
        val hold = CountDownLatch(1).also { drive.hold = it }
        val restore = async(Dispatchers.IO) { b.sync.restore(drive) { _, _ -> } }
        assertThat(drive.held.await(10, TimeUnit.SECONDS)).isTrue()
        val backup = async(Dispatchers.IO) { b.sync.backup(drive, time++, null) { _, _ -> } }
        // Halfway through a restore, a backup would see a restored pack before its gaps.
        val early = try {
            withTimeoutOrNull(1_000) { backup.await() }
        } finally {
            hold.countDown()
        }
        assertThat(early).isNull()
        assertThat(restore.await().packs).isEqualTo(1)
        assertThat(backup.await().manifest.packs).hasSize(1)
    }

    @Test
    fun aStickerWithoutADateIsNotRemoved() = runBlocking<Unit> {
        a.pack("gallery/still_00.webp", "gallery/still_01.webp", "gallery/still_02.webp")
        backup(a)
        // Its age is unknown: it may be another phone's, uploaded after the last manifest.
        val undated = drive.create("sticker_${"0".repeat(64)}.webp", "image/webp", byteArrayOf(1))
        drive.modified[undated] = ""
        a.packs.rename(a.packs.packs.first().single().pack.id, "Otro nombre")
        backup(a)
        assertThat(drive.files.keys).contains(undated)
    }

    @Test
    fun aRestoredPackStaysWithinWhatWhatsAppAccepts() = runBlocking<Unit> {
        a.pack("gallery/still_00.webp", "gallery/still_01.webp", "gallery/still_02.webp")
        backup(a)
        val id = drive.files.entries.single { it.value.first == BackupManifest.FILE }.key
        val text = drive.files.getValue(id).second.decodeToString()
        val edited = text.replace("\"name\":\"TikStik 1\"", "\"name\":\"${"x".repeat(200)}\"").replace("\"emoji\":\"${PackRules.DEFAULT_EMOJI}\"", "\"emoji\":\"\"")
        assertThat(edited).contains("\"emoji\":\"\"")
        assertThat(edited).contains("x".repeat(200))
        drive.files[id] = BackupManifest.FILE to edited.encodeToByteArray()
        b.sync.restore(drive) { _, _ -> }
        val restored = b.packs.packs.first().single()
        assertThat(restored.pack.name).hasLength(PackRules.MAX_NAME)
        assertThat(restored.items.map { it.emoji }.toSet()).containsExactly(PackRules.DEFAULT_EMOJI)
    }

    @Test
    fun aRestoreNeverGoesBeyondTheLimitOfPacks() = runBlocking<Unit> {
        a.pack("gallery/still_00.webp", "gallery/still_01.webp", "gallery/still_02.webp")
        backup(a)
        val sticker = b.add("gallery/still_03.webp")
        repeat(PackLimits.MAX) { b.packs.create({ "Full $it" }, animated = false, listOf(sticker.id), "😀", byteArrayOf(9)) }
        val outcome = b.sync.restore(drive) { _, _ -> }
        assertThat(outcome.packs to outcome.notFit).isEqualTo(0 to 1)
        assertThat(b.packs.packs.first()).hasSize(PackLimits.MAX)
        // Still in Drive, for when there is room.
        assertThat(drive.manifest().packs).hasSize(1)
    }

    @Test
    fun beyondTheLimitTheNewestArriveAndTheRestWaitInDrive() = runBlocking<Unit> {
        // 98 packs in Drive, the oldest with a sticker of its own, and 50 made on the other phone.
        val own = a.add("gallery/still_01.webp")
        cheapPacks(a, own, listOf("Oldest"))
        cheapPacks(a, a.add("gallery/still_00.webp"), (0 until 97).map { "A $it" })
        backup(a)
        cheapPacks(b, b.add("gallery/still_02.webp"), (0 until 50).map { "B $it" })

        val first = b.sync.restore(drive) { _, _ -> }
        assertThat(first.packs to first.notFit).isEqualTo(50 to 48)
        val names = b.packs.packs.first().map { it.pack.name }
        assertThat(names).hasSize(PackLimits.MAX)
        // Everything of this phone stays, and the 50 newest of the backup arrive.
        assertThat(names).containsAtLeastElementsIn((0 until 50).map { "B $it" } + (47 until 97).map { "A $it" })
        // The sticker of a pack that waits is not downloaded.
        assertThat(b.ids()).doesNotContain(own.id)
        // What waits stays in Drive, also after this phone backs up.
        backup(b)
        assertThat(drive.manifest().packs).hasSize(148)
        assertThat(drive.names()).contains("sticker_${own.id}.webp")

        // Making room: the next newest arrive.
        b.packs.packs.first().filter { it.pack.name.startsWith("B ") }.take(10).forEach { b.packs.delete(it.pack.id) }
        val second = b.sync.restore(drive) { _, _ -> }
        assertThat(second.packs to second.notFit).isEqualTo(10 to 38)
        assertThat(b.packs.packs.first().map { it.pack.name }).containsAtLeastElementsIn((37 until 47).map { "A $it" })
    }

    @Test
    fun signingOutBacksUpFirstAndAnotherAccountAsksBeforeMerging() = runBlocking<Unit> {
        val auth = FakeAuthorizer()
        val account = account(a, auth)
        // Packs made without an account join the first one without asking.
        a.pack("gallery/still_00.webp", "gallery/still_01.webp", "gallery/still_02.webp")
        account.signIn()
        account.await { it.email == A && it.work == null }
        val ownerA = a.settings.settings.first().backup.owner
        // Made right before signing out: it still reaches A's backup.
        a.pack("gallery/still_03.webp", "gallery/still_04.webp", "gallery/still_05.webp")
        account.signOut()
        account.await { it.email == null && it.work == null }
        assertThat(drive.manifest().packs).hasSize(2)

        // B already has a backup, made on another phone.
        b.pack("gallery/anim_00.webp", "gallery/anim_01.webp", "gallery/anim_02.webp")
        b.sync.backup(driveB, time++, null) { _, _ -> }

        // Another account: nothing changes here before the answer, and cancelling keeps everything.
        auth.chosen = B
        account.signIn()
        account.await { it.mergeWith == B && it.work == null }
        assertThat(a.settings.settings.first().backup.email).isNull()
        account.cancelMerge()
        account.await { it.mergeWith == null }
        withTimeout(5_000) { while (B !in auth.revoked) delay(20) }
        assertThat(a.settings.settings.first().backup.email).isNull()
        assertThat(a.settings.settings.first().backup.owner).isEqualTo(ownerA)
        assertThat(enqueued("restore")).isEmpty()
        assertThat(a.packs.packs.first()).hasSize(2)

        // Merging: the packs here go to B, B's backup is merged in, and nothing leaves the phone.
        account.signIn()
        account.await { it.mergeWith == B }
        account.confirmMerge()
        account.await { it.email == B && it.mergeWith == null }
        val info = a.settings.settings.first().backup
        assertThat(info.owner).isEqualTo(info.userId)
        assertThat(info.owner).isNotEqualTo(ownerA)
        assertThat(enqueued("restore")).isNotEmpty()
        assertThat(enqueued("backup").map { it.state }).contains(WorkInfo.State.ENQUEUED)
        assertThat(a.packs.packs.first()).hasSize(2)
    }

    @Test
    fun whatAnIncompletePackIsMissingStaysInDriveAfterSigningOutAndBackIn() = runBlocking<Unit> {
        val (s1, s2, s3) = a.pack("gallery/still_00.webp", "gallery/still_01.webp", "gallery/still_02.webp")
        backup(a)
        drive.files.remove(drive.files.entries.single { it.value.first == "sticker_${s3.id}.webp" }.key)
        val account = account(b, FakeAuthorizer())
        account.signIn()
        account.await { it.email == A && it.work == null }
        account.runRestore { _, _ -> }
        account.signOut()
        account.await { it.email == null && it.work == null }
        // Changed while signed out; back with the same account, a backup runs before the merge.
        b.packs.rename(b.packs.packs.first().single().pack.id, "Otro nombre")
        account.signIn()
        account.await { it.email == A && it.work == null }
        account.runBackup(manual = false) { _, _ -> }
        assertThat(drive.manifest().packs.single().stickers.map { it.stickerId }).containsExactly(s1.id, s2.id, s3.id)
    }

    @Test
    fun openingTheAppMergesOnceADayUnlessRetryingCannotHelp() = runBlocking<Unit> {
        val account = account(a, FakeAuthorizer())
        time = 10 * DAY
        a.settings.updateBackup { BackupInfo(email = A, userId = "g_a", synced = listOf("g_a"), restoredAt = time - DAY / 2) }
        account.restoreIfDue().join()
        assertThat(enqueued("restore")).isEmpty()
        a.settings.updateBackup { it.copy(restoredAt = time - 2 * DAY, problem = BackupProblem.SIGN_IN) }
        account.restoreIfDue().join()
        assertThat(enqueued("restore")).isEmpty()
        // A full Drive only stops backups.
        a.settings.updateBackup { it.copy(problem = BackupProblem.DRIVE_FULL) }
        account.restoreIfDue().join()
        assertThat(enqueued("restore")).isNotEmpty()
    }

    @Test
    fun theNotFitNoticeComesOnceAndAFullDriveStaysShown() = runBlocking<Unit> {
        a.pack("gallery/still_00.webp", "gallery/still_01.webp", "gallery/still_02.webp")
        backup(a)
        cheapPacks(b, b.add("gallery/still_03.webp"), (0 until PackLimits.MAX).map { "B $it" })
        val notices = Notices()
        val account = account(b, FakeAuthorizer(), notices)
        b.settings.updateBackup { BackupInfo(email = A, userId = "g_a", problem = BackupProblem.DRIVE_FULL) }
        val notFit = UiText.Plural(R.plurals.backup_not_fit, 1, listOf(1, PackLimits.MAX))

        account.runRestore { _, _ -> }
        assertThat(notices.drain()).contains(notFit)
        assertThat(b.settings.settings.first().backup.problem).isEqualTo(BackupProblem.DRIVE_FULL)
        // The daily merges do not repeat it.
        account.runRestore { _, _ -> }
        assertThat(notices.drain()).doesNotContain(notFit)
    }

    @Test
    fun aPackThatCannotArriveDoesNotTakeTheRoom() = runBlocking<Unit> {
        a.pack("gallery/still_00.webp", "gallery/still_01.webp", "gallery/still_02.webp")
        val newer = a.pack("gallery/still_03.webp", "gallery/still_04.webp", "gallery/still_05.webp")
        val older = a.packs.packs.first().first().pack.id
        backup(a)
        // The newest pack has lost every file in Drive.
        newer.forEach { s -> drive.files.remove(drive.files.entries.single { it.value.first == "sticker_${s.id}.webp" }.key) }
        cheapPacks(b, b.add("gallery/anim_00.webp"), (0 until PackLimits.MAX - 1).map { "B $it" })
        val outcome = b.sync.restore(drive) { _, _ -> }
        assertThat(outcome.packs to outcome.notFit).isEqualTo(1 to 0)
        assertThat(b.packs.packs.first().map { it.pack.id }).contains(older)
    }

    @Test
    fun signingOutWithoutAConnectionWarnsAndKeepsEverything() = runBlocking<Unit> {
        val notices = Notices()
        val account = account(a, FakeAuthorizer(), notices)
        a.pack("gallery/still_00.webp", "gallery/still_01.webp", "gallery/still_02.webp")
        account.signIn()
        account.await { it.email == A && it.work == null }
        drive.offline = true
        account.signOut()
        account.await { it.email == null && it.work == null }
        assertThat(withTimeout(5_000) { notices.notices.first() }.text).isEqualTo(UiText.Res(R.string.backup_sign_out_failed))
        assertThat(a.packs.packs.first()).hasSize(1)
    }

    @Test
    fun anUnreadableManifestIsLeftAlone() = runBlocking<Unit> {
        a.add("gallery/still_00.webp")
        drive.create(BackupManifest.FILE, "application/json", "{ de otra versión".encodeToByteArray())
        backup(a)
        // Only what could be read is replaced.
        assertThat(drive.files.values.count { it.first == BackupManifest.FILE }).isEqualTo(2)
    }

    @Test
    fun theAccountIdIsRandomAndSharedByBothPhones() = runBlocking<Unit> {
        val id = a.sync.accountId(drive)
        assertThat(b.sync.accountId(drive)).isEqualTo(id)
        // A random UUID, not something derived from the account that others can see.
        UUID.fromString(id)
        assertThat(drive.names().count { it == "account.json" }).isEqualTo(1)
    }

    private companion object {
        const val A = "a@example.com"
        const val B = "b@example.com"
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
