// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.importing

import com.redrixdev.tikstik.core.data.db.StickerEntity
import com.redrixdev.tikstik.media.Fingerprint
import com.redrixdev.tikstik.media.MediaProbe
import com.redrixdev.tikstik.media.Probe
import java.io.File

enum class CandidateStatus {
    NEW,

    // Same sticker and same kind (animated or still) as one in the collection.
    IN_COLLECTION,

    // Listed in an earlier import, chosen then or not, and not in the collection now: no longer new.
    KNOWN,
}

/** [file]: the mirror copy; [sure]: certainly a sticker (an unregistered cutout or photo is listed too, just unmarked). */
data class Candidate(
    val sticker: CachedSticker,
    val file: File,
    val probe: Probe,
    val fingerprint: Fingerprint,
    val status: CandidateStatus,
    val sure: Boolean,
    val seenAt: Long = sticker.seenAt,
) {
    val key: String get() = sticker.provenance.key
    val origin: StickerOrigin get() = sticker.origin
    val animated: Boolean get() = probe.animated
}

/** What has to be decoded with Android to review what was read. */
interface StickerImages {
    fun fingerprint(bytes: ByteArray): Fingerprint?

    fun solidRectangle(bytes: ByteArray): Boolean?

    fun flat(bytes: ByteArray): Boolean?
}

/** A sticker you already have, of which TikTok now has a higher-quality version. */
data class Upgrade(val owned: StickerEntity, val better: Candidate)

/** [reseen]: what you already have and TikTok showed again (id → ms). */
data class ImportReview(val candidates: List<Candidate>, val reseen: Map<String, Long>, val upgrades: List<Upgrade>)

/** A sticker's identity: same kind (for WhatsApp, animated and still are different) and the same key or the same image. */
object ImportAnalysis {

    sealed interface Verdict {
        data class Sticker(val candidate: Candidate) : Verdict

        data object NotSticker : Verdict

        // Half-written or damaged: not offered, and reported.
        data object Unreadable : Verdict
    }

    /** [references]: fingerprints of what TikTok identifies as something else (profile pictures, covers…). */
    fun judge(
        sticker: CachedSticker,
        file: File,
        bytes: ByteArray,
        fingerprint: Fingerprint?,
        references: List<Fingerprint>,
        images: StickerImages,
    ): Verdict {
        if (fingerprint == null) return Verdict.Unreadable
        if (sticker.origin == StickerOrigin.SEEN) {
            // With the whole file the quantizer is always known: the decision is final.
            val header = CacheImageHeader.parse(bytes) ?: return Verdict.Unreadable
            if (!GeneralCacheRules.accepts(header)) return Verdict.NotSticker
            // A flat still is not a sticker (a placeholder, an empty card); an animation may start blank.
            if (!header.animated && (!fingerprint.informative || images.flat(bytes) == true)) return Verdict.NotSticker
            if (references.any { it.matches(fingerprint, Fingerprint.TWIN_THRESHOLD) }) return Verdict.NotSticker
        }
        val sure = !sticker.origin.needsShapeCheck || (!sticker.opaque && images.solidRectangle(bytes) == true)
        return Verdict.Sticker(Candidate(sticker, file, MediaProbe.probe(bytes), fingerprint, CandidateStatus.NEW, sure))
    }

    /** The same sticker arrives through several places or variants: the sure, higher-quality one wins, with the most recent date. */
    fun unique(measured: List<Candidate>): List<Candidate> {
        val unique = mutableListOf<Candidate>()
        val order = compareByDescending<Candidate> { it.sure }
            .thenBy { it.origin.needsShapeCheck }
            .thenByDescending { it.sticker.provenance.rank }
            .thenByDescending { it.sticker.file.size }
        for (candidate in measured.sortedWith(order)) {
            val twin = unique.indexOfFirst { it.animated == candidate.animated && (it.key == candidate.key || it.fingerprint.matches(candidate.fingerprint)) }
            when {
                twin < 0 -> unique += candidate
                candidate.seenAt > unique[twin].seenAt -> unique[twin] = unique[twin].copy(seenAt = candidate.seenAt)
            }
        }
        return unique.sortedByDescending { it.seenAt }
    }

    /** [shown]: keys already listed in an import, whether imported then or not. */
    fun classify(unique: List<Candidate>, collection: List<StickerEntity>, shown: Set<String>): ImportReview {
        val owned = collection.map { it to Fingerprint.parse(it.phash) }
        val reseen = HashMap<String, Long>()
        val upgrades = mutableListOf<Upgrade>()
        val upgraded = HashSet<String>()
        val candidates = unique.map { candidate ->
            val matches = owned.filter { (sticker, fingerprint) ->
                sticker.animated == candidate.animated && (sticker.sourceKey == candidate.key || fingerprint?.matches(candidate.fingerprint) == true)
            }.map { it.first }
            for (sticker in matches) reseen[sticker.id] = maxOf(reseen[sticker.id] ?: 0, candidate.seenAt * 1000)
            val best = matches.maxByOrNull { it.rank }
            if (best != null && candidate.sticker.provenance.rank > best.rank && upgraded.add(best.id)) upgrades += Upgrade(best, candidate)
            candidate.copy(
                status = when {
                    matches.isNotEmpty() -> CandidateStatus.IN_COLLECTION
                    candidate.key in shown -> CandidateStatus.KNOWN
                    else -> CandidateStatus.NEW
                },
            )
        }
        return ImportReview(candidates, reseen, upgrades)
    }

    /** What is shown, newest to oldest: what you already have, only if TikTok showed it in the last 24 h. */
    fun shown(candidates: List<Candidate>, nowSec: Long): List<Candidate> =
        candidates.filter { it.status != CandidateStatus.IN_COLLECTION || it.seenAt >= nowSec - OWNED_WINDOW_SEC }.sortedByDescending { it.seenAt }

    private const val OWNED_WINDOW_SEC = 24 * 60 * 60L
}
