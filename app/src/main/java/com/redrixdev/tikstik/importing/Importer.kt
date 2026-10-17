// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.importing

import com.redrixdev.tikstik.access.AccessController
import com.redrixdev.tikstik.access.CacheFile
import com.redrixdev.tikstik.collection.CollectionRepository
import com.redrixdev.tikstik.core.common.Clock
import com.redrixdev.tikstik.core.common.DefaultDispatcher
import com.redrixdev.tikstik.core.common.IoDispatcher
import com.redrixdev.tikstik.core.common.sha256
import com.redrixdev.tikstik.core.data.db.AppDatabase
import com.redrixdev.tikstik.core.data.db.ImportKeyEntity
import com.redrixdev.tikstik.core.data.db.SQL_BATCH
import com.redrixdev.tikstik.core.data.db.StickerEntity
import com.redrixdev.tikstik.media.Fingerprint
import com.redrixdev.tikstik.packs.CopyPreparation
import com.redrixdev.tikstik.packs.PackBuilder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

sealed interface ImportProgress {
    data object Connecting : ImportProgress

    data object Searching : ImportProgress

    data class Reading(val done: Int, val total: Int) : ImportProgress

    data class Reviewing(val done: Int, val total: Int) : ImportProgress
}

sealed interface ImportResult {
    data object NoTikTok : ImportResult

    /** [candidates] newest to oldest; [unreadable]: could not be read from TikTok or cannot be opened. */
    data class Review(val candidates: List<Candidate>, val unreadable: Int, val found: Int) : ImportResult
}

/** [last]: the most recently seen of the chosen ones, to jump to its tab when it appears; null if nothing went in. */
data class SaveResult(val added: Int, val last: StickerEntity?)

/** Import from TikTok: read just enough while the session is open, review once it is closed and apply what is automatic. */
class Importer @Inject constructor(
    private val access: AccessController,
    private val sources: TikTokSources,
    private val mirror: ImportMirror,
    private val images: StickerImages,
    private val collection: CollectionRepository,
    private val db: AppDatabase,
    private val copies: CopyPreparation,
    private val builder: PackBuilder,
    private val clock: Clock,
    @param:IoDispatcher private val io: CoroutineDispatcher,
    @param:DefaultDispatcher private val cpu: CoroutineDispatcher,
) {

    /** Access errors: `AccessException`; a dropped connection: `IOException`. */
    suspend fun read(onProgress: (ImportProgress) -> Unit, onNetworkApproval: () -> Unit): ImportResult = withContext(io) {
        val roots = sources.roots()
        if (roots.isEmpty()) return@withContext ImportResult.NoTikTok
        onProgress(ImportProgress.Connecting)
        val (scan, references) = access.withReader(onConnected = { onProgress(ImportProgress.Searching) }, onNetworkApproval = onNetworkApproval) { reader ->
            val scan = TikTokCache.scan(reader, roots)
            // References are only needed when something unregistered from the general cache has to be checked.
            val references = if (scan.stickers.any { it.origin.needsShapeCheck }) scan.references else emptyList()
            val stickers = scan.stickers.map { it.file }
            val missing = stickers.filter { mirror.file(it) == null } + references.filterNot(mirror::hasReference)
            val total = stickers.size + references.size
            var done = total - missing.size
            val isReference = references.toHashSet()
            onProgress(ImportProgress.Reading(done, total))
            for (batch in missing.chunked(READ_BATCH)) {
                currentCoroutineContext().ensureActive()
                val read = reader.read(batch)
                val (referenceFiles, stickerFiles) = batch.filter { it.path in read }.partition { it in isReference }
                stickerFiles.forEach { mirror.put(it, read.getValue(it.path)) }
                // A reference is fingerprinted as it arrives and its image dropped: it never reaches the disk and memory holds one batch.
                parallel(referenceFiles) { mirror.putReferenceFingerprint(it, images.fingerprint(read.getValue(it.path))) }
                done += batch.size
                onProgress(ImportProgress.Reading(done, total))
            }
            mirror.keepOnly(stickers, references)
            scan to references
        }
        review(scan, references, onProgress)
    }

    private suspend fun review(scan: CacheScan, referenceFiles: List<CacheFile>, onProgress: (ImportProgress) -> Unit): ImportResult.Review {
        val readable = scan.stickers.mapNotNull { sticker -> mirror.file(sticker.file)?.let { sticker to it } }
        val total = referenceFiles.size + readable.size
        // Ticks come from several threads: counting and reporting under one lock keeps the bar from stepping back.
        val lock = Any()
        var reviewed = 0
        val tick = { synchronized(lock) { if (++reviewed % PROGRESS_EVERY == 0 || reviewed == total) onProgress(ImportProgress.Reviewing(reviewed, total)) } }
        onProgress(ImportProgress.Reviewing(0, total))
        val references = referenceFiles.mapNotNull { mirror.referenceFingerprint(it).also { tick() } }
        val verdicts = parallel(readable) { (sticker, copy) ->
            // The system may clear the cache meanwhile: a vanished copy is unreadable, not a dropped connection.
            val bytes = try { copy.readBytes() } catch (_: IOException) { null }
            (if (bytes == null) ImportAnalysis.Verdict.Unreadable else ImportAnalysis.judge(sticker, copy, bytes, fingerprint(copy, bytes), references, images)).also { tick() }
        }
        val unreadable = scan.stickers.size - readable.size + verdicts.count { it is ImportAnalysis.Verdict.Unreadable }
        val unique = ImportAnalysis.unique(verdicts.filterIsInstance<ImportAnalysis.Verdict.Sticker>().map { it.candidate })
        val keys = unique.map { it.key }.chunked(SQL_BATCH).flatMap { db.importKeys().get(it) }
        val review = ImportAnalysis.classify(
            unique,
            collection.collection.first(),
            shown = keys.mapTo(HashSet()) { it.key },
        )
        currentCoroutineContext().ensureActive()
        // What you already have and TikTok showed again moves up in the collection; a better version replaces it.
        collection.touchSeen(review.reseen)
        for (upgrade in review.upgrades) {
            currentCoroutineContext().ensureActive()
            // The better version and the packs that switch to it go together: cut in between, the packs would keep the worse one for good.
            withContext(NonCancellable) {
                val bytes = runCatching { upgrade.better.file.readBytes() }.getOrNull() ?: return@withContext
                val saved = collection.replace(upgrade.owned.id, entity(upgrade.better, bytes), bytes)
                if (saved.id != upgrade.owned.id) builder.follow(upgrade.owned.id, saved)
            }
        }
        if (review.upgrades.isNotEmpty()) copies.start()
        val now = clock.now()
        val shown = ImportAnalysis.shown(review.candidates, now / 1000)
        // What is listed as new stops being new next time, chosen or not.
        db.importKeys().insertNew(shown.filter { it.status == CandidateStatus.NEW }.map { ImportKeyEntity(it.key, now, null) })
        return ImportResult.Review(shown, unreadable, scan.stickers.size)
    }

    /** Most recently seen last: that way it ends up on top in the same order as the list. */
    suspend fun save(chosen: List<Candidate>): SaveResult = withContext(io) {
        val now = clock.now()
        val before = collection.collection.first().mapTo(HashSet()) { it.id }
        var added = 0
        var last: StickerEntity? = null
        for (candidate in chosen.sortedBy { it.seenAt }) {
            // The mirror copy may be gone (the system clears the cache): that one is skipped.
            val bytes = runCatching { candidate.file.readBytes() }.getOrNull() ?: continue
            last = collection.add(entity(candidate, bytes), bytes)
            if (last.id !in before) added++
        }
        val keys = chosen.map { it.key }
        db.importKeys().insertNew(keys.map { ImportKeyEntity(it, now, null) })
        keys.chunked(SQL_BATCH).forEach { db.importKeys().markImported(it, now) }
        copies.start()
        SaveResult(added, last)
    }

    private fun fingerprint(copy: File, bytes: ByteArray): Fingerprint? =
        mirror.fingerprint(copy) ?: images.fingerprint(bytes)?.also { mirror.putFingerprint(copy, it) }

    private fun entity(candidate: Candidate, bytes: ByteArray) = StickerEntity(
        id = bytes.sha256(),
        fileExt = candidate.probe.format.extension,
        animated = candidate.animated,
        width = candidate.probe.width ?: 0,
        height = candidate.probe.height ?: 0,
        frames = candidate.probe.frames,
        durationMs = candidate.probe.durationMs,
        bytes = bytes.size.toLong(),
        sourceKey = candidate.key,
        variant = candidate.sticker.provenance.variant,
        rank = candidate.sticker.provenance.rank,
        phash = candidate.fingerprint.encode(),
        addedAt = clock.now(),
        seenAt = candidate.seenAt * 1000,
    )

    // Spread across the cores, preserving order.
    private suspend fun <T, R> parallel(items: List<T>, transform: (T) -> R): List<R> = coroutineScope {
        if (items.isEmpty()) return@coroutineScope emptyList()
        val workers = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        items.chunked((items.size + workers - 1) / workers).map { chunk ->
            async(cpu) { chunk.map { currentCoroutineContext().ensureActive(); transform(it) } }
        }.awaitAll().flatten()
    }

    private companion object {
        const val READ_BATCH = 40
        const val PROGRESS_EVERY = 12
    }
}
