// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.packs

import android.content.Context
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.core.common.ConversionDispatcher
import com.redrixdev.tikstik.core.common.DefaultDispatcher
import com.redrixdev.tikstik.core.common.Logger
import com.redrixdev.tikstik.core.data.db.PackEntity
import com.redrixdev.tikstik.core.data.db.PackWithStickers
import com.redrixdev.tikstik.core.data.db.StickerEntity
import com.redrixdev.tikstik.core.data.settings.SettingsRepository
import com.redrixdev.tikstik.media.WhatsAppConverter
import com.redrixdev.tikstik.whatsapp.PackRules
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** [packed]: the ones that went in; [convertFailed]: the ones that could not be prepared; [notCreated]: the ones in packs that could not be saved. */
data class CreateReport(val created: List<PackEntity>, val packed: List<String>, val convertFailed: Int, val notCreated: Int)

/** What went into an existing pack and what it already had. */
data class AddReport(val added: List<String>, val already: List<String>)

/** Puts stickers into packs: prepares their copies first and, when the cover changes, the tray. Never leaves a pack half done. */
@Singleton
class PackBuilder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val packs: PackRepository,
    private val copies: WhatsAppCopies,
    private val settings: SettingsRepository,
    @DefaultDispatcher cpu: CoroutineDispatcher,
    // For work nobody is waiting on (repairing trays, restoring), at background priority.
    @param:ConversionDispatcher private val background: CoroutineDispatcher,
) {
    // With the user waiting, two at a time: fast without blowing up native memory on low-end phones.
    private val converting = cpu.limitedParallelism(CONVERSIONS)

    /** New packs with the chosen stickers, numbered from the highest saved number. */
    suspend fun create(stickers: List<StickerEntity>, onProgress: (done: Int, total: Int) -> Unit): CreateReport {
        val wanted = stickers.distinctBy { it.id }
        val ready = prepare(wanted, onProgress)
        val base = settings.settings.first().packBaseName ?: context.getString(R.string.pack_name_default)
        val created = mutableListOf<PackEntity>()
        val packed = mutableListOf<String>()
        var notCreated = 0
        for ((animated, group) in ready.groupBy { it.animated }) {
            for (chunk in PackPlanner.split(group)) {
                try {
                    val tray = tray(chunk.first().id)
                    val ids = chunk.map { it.id }
                    created += packs.create({ context.getString(R.string.pack_name, base, it) }, animated, ids, PackRules.DEFAULT_EMOJI, tray)
                    packed += ids
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.warn(AREA, "pack not created", e)
                    notCreated += chunk.size
                }
            }
        }
        return CreateReport(created, packed, wanted.size - ready.size, notCreated)
    }

    /** The stickers of the pack's kind that it lacks, until it is full; the rest stay out. */
    suspend fun addTo(packId: String, stickers: List<StickerEntity>, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): AddReport {
        val pack = packs.get(packId) ?: return AddReport(emptyList(), emptyList())
        val inside = pack.stickerIds.toSet()
        val fits = PackPlanner.fitting(stickers.filter { it.animated == pack.pack.animated }.map { it.id }, inside).toSet()
        val ready = prepare(stickers.filter { it.id in fits }, onProgress)
        val added = packs.add(packId, ready.map { it.id }, PackRules.DEFAULT_EMOJI)
        return AddReport(added, stickers.map { it.id }.filter { it in inside })
    }

    /** Rebuilds a pack from the backup with the stickers already here; false if it existed or could not be done. */
    suspend fun restore(pack: PackEntity, stickerIds: List<String>, emojis: Map<String, String>): Boolean {
        // With its copies made, as on create: it can go to WhatsApp as soon as it appears, and what fails to convert stays out.
        val ready = stickerIds.filter { id -> convertible(id, background) }
        val cover = ready.firstOrNull() ?: return false
        return try {
            packs.restore(pack, ready, emojis, tray(cover, background))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(AREA, "pack not restored", e)
            false
        }
    }

    /** The packs that used [oldId] switch to [better], with its copy ready first and a new tray where it was the cover. */
    suspend fun follow(oldId: String, better: StickerEntity) {
        val using = packs.packs.first().filter { oldId in it.stickerIds }
        if (using.isEmpty()) return
        try {
            copies.ensure(better, converting)
            val tray = if (using.any { it.stickerIds.first() == oldId }) tray(better.id) else null
            for (pack in using) packs.replaceSticker(pack.pack.id, oldId, better.id, tray.takeIf { pack.stickerIds.first() == oldId })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(AREA, "packs did not follow the better version", e)
        }
    }

    suspend fun reorder(packId: String, order: List<String>) {
        val pack = packs.get(packId) ?: return
        packs.reorder(packId, order, trayIfNewCover(pack, order.firstOrNull()))
    }

    suspend fun remove(packId: String, stickerIds: Set<String>) {
        val pack = packs.get(packId) ?: return
        packs.remove(packId, stickerIds, trayIfNewCover(pack, pack.stickerIds.firstOrNull { it !in stickerIds }))
    }

    /** Missing trays (after moving to another phone) are made again from the cover. */
    suspend fun repairTrays() {
        for (pack in packs.packs.first()) {
            val cover = pack.stickerIds.firstOrNull() ?: continue
            if (packs.tray(pack.pack.id).exists()) continue
            try {
                packs.restoreTray(pack.pack.id, tray(cover, background))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.warn(AREA, "tray not restored", e)
            }
        }
    }

    private suspend fun trayIfNewCover(pack: PackWithStickers, cover: String?): ByteArray? =
        if (cover == null || cover == pack.stickerIds.firstOrNull()) null else tray(cover)

    private suspend fun tray(stickerId: String, dispatcher: CoroutineDispatcher = converting): ByteArray {
        val copy = copies.ensure(stickerId, dispatcher) ?: throw IllegalStateException("Sticker without its original")
        return withContext(dispatcher) { WhatsAppConverter.tray(copy.readBytes()) }
    }

    private suspend fun convertible(id: String, dispatcher: CoroutineDispatcher): Boolean = try {
        copies.ensure(id, dispatcher) != null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.warn(AREA, "copy failed", e)
        false
    }

    // What cannot be converted stays out; the rest goes on.
    private suspend fun prepare(stickers: List<StickerEntity>, onProgress: (Int, Int) -> Unit): List<StickerEntity> {
        val done = AtomicInteger()
        return coroutineScope {
            stickers.map { sticker ->
                async {
                    val ok = try {
                        copies.ensure(sticker, converting)
                        true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Logger.warn(AREA, "copy failed", e)
                        false
                    }
                    onProgress(done.incrementAndGet(), stickers.size)
                    sticker.takeIf { ok }
                }
            }.awaitAll().filterNotNull()
        }
    }

    private companion object {
        const val AREA = "Packs"
        const val CONVERSIONS = 2
    }
}
