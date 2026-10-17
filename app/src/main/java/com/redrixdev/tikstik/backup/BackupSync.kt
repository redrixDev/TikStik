// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.backup

import com.redrixdev.tikstik.collection.CollectionRepository
import com.redrixdev.tikstik.core.common.IoDispatcher
import com.redrixdev.tikstik.core.common.Logger
import com.redrixdev.tikstik.core.common.sha256
import com.redrixdev.tikstik.core.data.db.AppDatabase
import com.redrixdev.tikstik.core.data.db.ImportKeyEntity
import com.redrixdev.tikstik.core.data.db.PackEntity
import com.redrixdev.tikstik.core.data.db.SQL_BATCH
import com.redrixdev.tikstik.core.data.db.StickerEntity
import com.redrixdev.tikstik.core.data.db.TombstoneEntity
import com.redrixdev.tikstik.core.data.db.TombstoneKind
import com.redrixdev.tikstik.core.data.settings.PackGap
import com.redrixdev.tikstik.core.data.settings.SettingsRepository
import com.redrixdev.tikstik.media.MediaProbe
import com.redrixdev.tikstik.packs.PackBuilder
import com.redrixdev.tikstik.packs.PackLimits
import com.redrixdev.tikstik.whatsapp.PackRules
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** This install's random id, kept in `noBackupFilesDir` so it never travels to another phone. */
data class InstallId(val value: String)

/** [changed]: a new backup was written; when nothing changed since the last one, nothing is uploaded. */
data class BackupOutcome(val manifest: BackupManifest, val hash: String, val changed: Boolean)

/** [notFit]: packs of the backup left in Drive because this phone already holds as many as TikStik allows. */
data class RestoreOutcome(val stickers: Int, val packs: Int, val notFit: Int = 0)

/** Backs up to Drive and merges the backup into this phone; each pass uploads only what is missing, so an interruption loses nothing. */
@Singleton
class BackupSync @Inject constructor(
    private val db: AppDatabase,
    private val collection: CollectionRepository,
    private val builder: PackBuilder,
    private val settings: SettingsRepository,
    // Read when backing up, on the I/O thread, not when the object is created.
    private val install: dagger.Lazy<InstallId>,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
    private val running = Mutex()

    /** The most recent backup on Drive, if any. */
    suspend fun read(drive: DriveClient): BackupManifest? = withContext(io) { remote(drive, drive.list())?.manifest }

    suspend fun backup(drive: DriveClient, now: Long, lastHash: String?, accountId: String? = null, onProgress: suspend (done: Int, total: Int) -> Unit): BackupOutcome = exclusive {
        val files = drive.list()
        // If TikStik's data was deleted from Drive, the account id comes back with the same value.
        if (accountId != null && files.none { it.name == ACCOUNT_FILE }) {
            drive.create(ACCOUNT_FILE, JSON, json.encodeToString(Account.serializer(), Account(accountId)).encodeToByteArray())
        }
        val remote = remote(drive, files)
        // A backup from a newer TikStik version is never overwritten: its new fields would be lost.
        if (remote?.newer == true) throw NewerBackupException()
        val merged = BackupMerge.merge(remote?.manifest, local(now))
        val hash = json.encodeToString(BackupManifest.serializer(), merged.copy(savedAt = 0)).encodeToByteArray().sha256()
        val present = files.mapTo(HashSet()) { it.name }
        val local = db.stickers().all().associateBy { it.id }
        val missing = merged.stickers.filter { BackupManifest.stickerFile(it) !in present && it.id in local }
        if (missing.isEmpty() && hash == lastHash && remote != null) return@exclusive BackupOutcome(merged, hash, changed = false)

        val done = AtomicInteger()
        val uploads = Semaphore(PARALLEL)
        coroutineScope {
            missing.map { sticker ->
                async {
                    uploads.withPermit {
                        val bytes = collection.file(local.getValue(sticker.id)).readBytes()
                        drive.create(BackupManifest.stickerFile(sticker), mime(sticker.ext), bytes)
                    }
                    onProgress(done.incrementAndGet(), missing.size)
                }
            }.awaitAll()
        }
        // The new manifest before removing the old one: an interruption leaves two, and the newest is read.
        drive.create(BackupManifest.FILE, JSON, json.encodeToString(BackupManifest.serializer(), merged).encodeToByteArray())
        // Only the manifests that were read and merged: an unreadable one may belong to another version.
        files.filter { it.name == BackupManifest.FILE && it.id in remote?.read.orEmpty() }.forEach { drive.delete(it.id) }
        pruneGaps(merged)
        // Drop deleted stickers and those in no pack that predate the newest manifest read; newer ones may come from another phone.
        val keep = merged.stickers.mapTo(HashSet(), BackupManifest::stickerFile)
        val deleted = merged.tombstones.filter { it.kind == BackupTombstone.Kind.STICKER }.mapTo(HashSet()) { it.id }
        val newestRead = files.filter { it.id in remote?.read.orEmpty() }.maxOfOrNull { it.modifiedTime }
        files.filter { it.name.startsWith(PREFIX) && it.name !in keep }
            // One without a date may be newer than the manifests read: it stays.
            .filter { it.name.removePrefix(PREFIX).substringBefore('.') in deleted || (newestRead != null && it.modifiedTime.isNotEmpty() && it.modifiedTime < newestRead) }
            .forEach { drive.delete(it.id) }
        BackupOutcome(merged, hash, changed = true)
    }

    /** Merges the Drive backup into this phone without deleting anything. */
    suspend fun restore(drive: DriveClient, onProgress: suspend (done: Int, total: Int) -> Unit): RestoreOutcome = exclusive {
        val files = drive.list()
        val saved = remote(drive, files)?.manifest ?: return@exclusive RestoreOutcome(0, 0)
        // Merged with the local data first: what was deleted on this phone before signing in does not come back.
        val remote = BackupMerge.merge(saved, local(0))
        val inDrive = saved.packs.associateBy { it.id }
        // Newest first: when not all of them fit, the most recent arrive and the rest wait in Drive until there is room.
        val newest = remote.packs.asReversed()
        val byName = files.associateBy { it.name }
        val here = db.stickers().allIds().toSet()
        // Only packs that can arrive take the room: one whose stickers are neither here nor in Drive would hold it for good.
        val available = remote.stickers.filter { valid(it) && (it.id in here || BackupManifest.stickerFile(it) in byName) }.mapTo(HashSet()) { it.id }
        val packsHere = db.packs().allIds().toSet()
        val fresh = newest.filter { p -> p.id !in packsHere && p.id in inDrive && p.stickers.any { it.stickerId in available } }
        val waiting = fresh.drop((PackLimits.MAX - packsHere.size).coerceAtLeast(0)).mapTo(HashSet()) { it.id }
        // Their stickers wait in Drive with them, unless another pack uses them too.
        val (waitingPacks, otherPacks) = remote.packs.partition { it.id in waiting }
        val elsewhere = otherPacks.flatMapTo(HashSet()) { p -> p.stickers.map { it.stickerId } }
        val leftInDrive = waitingPacks.flatMap { p -> p.stickers.map { it.stickerId } }.filterNotTo(HashSet()) { it in elsewhere }
        val wanted = remote.stickers.filter { valid(it) && it.id !in here && it.id !in leftInDrive && BackupManifest.stickerFile(it) in byName }
        val restored = AtomicInteger()
        val done = AtomicInteger()
        val downloads = Semaphore(PARALLEL)
        coroutineScope {
            wanted.map { sticker ->
                async {
                    val bytes = downloads.withPermit { drive.download(byName.getValue(BackupManifest.stickerFile(sticker)).id) }
                    // Nothing from Drive is trusted: its SHA-256 is its id, and it gets probed again.
                    if (bytes.sha256() == sticker.id) {
                        collection.add(entity(sticker, bytes), bytes)
                        restored.incrementAndGet()
                    } else {
                        Logger.warn(AREA, "restored sticker does not match its id")
                    }
                    onProgress(done.incrementAndGet(), wanted.size)
                }
            }.awaitAll()
        }
        val present = db.stickers().allIds().toSet()
        var packs = 0
        var notFit = 0
        for (pack in newest) {
            val listed = inDrive[pack.id] ?: continue
            val here = db.packs().get(pack.id)
            // One restored by an earlier run cut before its gaps were saved gets them now; one edited since keeps its own.
            if (here != null && here.pack.imageDataVersion != listed.imageDataVersion) continue
            // Nothing from Drive is trusted: within what WhatsApp accepts, and never a sticker without an emoji.
            val emojis = listed.stickers.associate { it.stickerId to it.emoji.ifBlank { PackRules.DEFAULT_EMOJI } }
            if (here == null) {
                // Never beyond TikStik's own limit, counted again before each one: packs made meanwhile take room too.
                if (pack.id in waiting || db.packs().allIds().size >= PackLimits.MAX) {
                    notFit++
                    continue
                }
                val ids = listed.stickers.map { it.stickerId }.filter { it in present }.take(PackRules.MAX_STICKERS)
                if (ids.isEmpty()) continue
                val entity = PackEntity(listed.id, listed.name.take(PackRules.MAX_NAME), listed.number, listed.animated, listed.imageDataVersion, listed.createdAt, offered = false)
                // All of them noted before the pack exists: if this is cut halfway, the backup still lists the ones it has not got.
                settings.updatePackGaps { it + (pack.id to listed.stickers.map { s -> PackGap(s.stickerId, emojis.getValue(s.stickerId)) }) }
                if (!builder.restore(entity, ids, emojis)) {
                    settings.updatePackGaps { it - pack.id }
                    continue
                }
                packs++
            }
            // Whatever did not arrive or could not be converted stays noted: the backup still lists it in the pack.
            val stored = db.packs().get(pack.id)?.stickerIds.orEmpty().toSet()
            val missing = listed.stickers.filter { it.stickerId !in stored }.map { PackGap(it.stickerId, emojis.getValue(it.stickerId)) }
            settings.updatePackGaps { if (missing.isEmpty()) it - pack.id else it + (pack.id to missing) }
        }
        db.importKeys().insertNew(remote.importKeys.map { ImportKeyEntity(it.key, it.shownAt, it.importedAt) })
        remote.importKeys.filter { it.importedAt != null }.groupBy { it.importedAt!! }.forEach { (at, keys) ->
            keys.map { it.key }.chunked(SQL_BATCH).forEach { db.importKeys().markImported(it, at) }
        }
        if (settings.settings.first().packBaseName == null && remote.packBaseName != null) settings.setPackBaseName(remote.packBaseName)
        // Deletions from other phones apply here too: re-importing something deleted there counts as seen now.
        db.tombstones().upsert(
            remote.tombstones.mapNotNull { t ->
                when (t.kind) {
                    BackupTombstone.Kind.STICKER -> TombstoneEntity(TombstoneKind.STICKER, t.id, t.deletedAt)
                    BackupTombstone.Kind.PACK -> TombstoneEntity(TombstoneKind.PACK, t.id, t.deletedAt)
                    BackupTombstone.Kind.UNKNOWN -> null
                }
            },
        )
        RestoreOutcome(restored.get(), packs, notFit)
    }

    // A backup halfway through a restore would see a restored pack before its gaps and drop them from Drive.
    private suspend fun <T> exclusive(block: suspend CoroutineScope.() -> T): T = running.withLock { withContext(io, block) }

    private suspend fun local(now: Long): BackupManifest {
        val packs = db.packs().packs().first()
        val gaps = settings.settings.first().packGaps
        // Only the packs and their stickers; the gaps come with the Drive backup.
        val inPacks = packs.flatMapTo(HashSet()) { it.stickerIds }
        return BackupManifest(
            savedAt = now,
            device = install.get().value,
            stickers = db.stickers().all().filter { it.id in inPacks }.map {
                BackupSticker(
                    it.id, it.fileExt, it.animated, it.width, it.height, it.frames, it.durationMs, it.bytes,
                    it.sourceKey, it.variant, it.rank, it.phash, it.addedAt, it.seenAt, it.inCollection,
                )
            },
            packs = packs.map { p ->
                BackupPack(
                    p.pack.id, p.pack.name, p.pack.number, p.pack.animated, p.pack.createdAt, p.pack.imageDataVersion,
                    p.items.sortedBy { it.position }.map { BackupPackSticker(it.stickerId, it.emoji) } +
                        gaps[p.pack.id].orEmpty().filter { gap -> p.items.none { it.stickerId == gap.stickerId } }
                            // Never more than WhatsApp allows in one pack.
                            .take((PackRules.MAX_STICKERS - p.items.size).coerceAtLeast(0))
                            .map { BackupPackSticker(it.stickerId, it.emoji) },
                )
            },
            importKeys = db.importKeys().all().map { BackupImportKey(it.key, it.shownAt, it.importedAt) },
            packBaseName = settings.settings.first().packBaseName,
            tombstones = db.tombstones().all().map {
                BackupTombstone(if (it.kind == TombstoneKind.STICKER) BackupTombstone.Kind.STICKER else BackupTombstone.Kind.PACK, it.id, it.deletedAt)
            },
        )
    }

    // A gap expires when its sticker is no longer in the backup, another phone removed it, or its pack no longer exists.
    private suspend fun pruneGaps(merged: BackupManifest) {
        val known = merged.stickers.mapTo(HashSet()) { it.id }
        val local = db.packs().packs().first().associate { it.pack.id to it.pack.imageDataVersion }
        val winners = merged.packs.associateBy { it.id }
        settings.updatePackGaps { gaps ->
            gaps.filterKeys { it in local }.mapValues { (packId, list) ->
                val winner = winners[packId]
                val newerElsewhere = winner != null && winner.imageDataVersion > local.getValue(packId)
                list.filter { gap -> gap.stickerId in known && !(newerElsewhere && winner.stickers.none { it.stickerId == gap.stickerId }) }
            }.filterValues { it.isNotEmpty() }
        }
    }

    /** [read]: the manifests that could be read, the only ones a backup may replace. */
    private class Remote(val manifest: BackupManifest, val newer: Boolean, val read: Set<String>)

    // All readable manifests, merged: two phones backing up at once leave two, and all of them get deleted.
    private fun remote(drive: DriveClient, files: List<DriveFile>): Remote? {
        val read = HashSet<String>()
        val manifests = files.filter { it.name == BackupManifest.FILE }.sortedBy { it.modifiedTime }.mapNotNull { file ->
            try {
                json.decodeFromString(BackupManifest.serializer(), drive.download(file.id).decodeToString()).also { read += file.id }
            } catch (e: kotlinx.serialization.SerializationException) {
                Logger.warn(AREA, "unreadable manifest")
                null
            } catch (e: IllegalArgumentException) {
                Logger.warn(AREA, "unreadable manifest")
                null
            }
        }
        // Starting from null so a single manifest also goes through the merge, which drops loose stickers from old backups.
        val merged = manifests.fold<BackupManifest, BackupManifest?>(null) { acc, next -> BackupMerge.merge(acc, next) } ?: return null
        return Remote(merged.copy(tombstones = merged.tombstones.filter { it.kind != BackupTombstone.Kind.UNKNOWN }), manifests.any { it.format > BackupManifest.FORMAT }, read)
    }

    /** The account's random id, stored on Drive; if two are created at once, the oldest wins. */
    suspend fun accountId(drive: DriveClient): String = withContext(io) {
        fun oldest() = drive.list().filter { it.name == ACCOUNT_FILE }.minByOrNull { it.modifiedTime }
        val file = oldest() ?: run {
            drive.create(ACCOUNT_FILE, JSON, json.encodeToString(Account.serializer(), Account(UUID.randomUUID().toString())).encodeToByteArray())
            oldest() ?: throw IOException("account id not found after creating it")
        }
        json.decodeFromString(Account.serializer(), drive.download(file.id).decodeToString()).id
    }

    private fun entity(sticker: BackupSticker, bytes: ByteArray): StickerEntity {
        val probe = MediaProbe.probe(bytes)
        return StickerEntity(
            id = sticker.id,
            fileExt = probe.format.extension,
            animated = probe.animated,
            width = probe.width ?: sticker.width,
            height = probe.height ?: sticker.height,
            frames = probe.frames,
            durationMs = probe.durationMs,
            bytes = bytes.size.toLong(),
            sourceKey = sticker.sourceKey,
            variant = sticker.variant,
            rank = sticker.rank,
            phash = sticker.phash,
            addedAt = sticker.addedAt,
            seenAt = sticker.seenAt,
            inCollection = sticker.inCollection,
        )
    }

    // Drive content may come from anywhere: only SHA-256 ids and known extensions, never paths.
    private fun valid(sticker: BackupSticker) = sticker.id.matches(SHA256) && sticker.ext in EXTENSIONS

    private fun mime(ext: String) = when (ext) {
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "png" -> "image/png"
        "jpg" -> "image/jpeg"
        else -> "application/octet-stream"
    }

    @Serializable
    private data class Account(val id: String)

    private companion object {
        const val AREA = "Backup"
        const val ACCOUNT_FILE = "account.json"
        const val JSON = "application/json"
        const val PREFIX = "sticker_"
        const val PARALLEL = 3
        val SHA256 = Regex("[0-9a-f]{64}")
        val EXTENSIONS = setOf("webp", "gif", "png", "jpg", "bin")
    }
}
