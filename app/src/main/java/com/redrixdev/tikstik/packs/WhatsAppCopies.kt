// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.packs

import com.redrixdev.tikstik.core.common.IoDispatcher
import com.redrixdev.tikstik.core.data.db.AppDatabase
import com.redrixdev.tikstik.core.data.db.StickerEntity
import com.redrixdev.tikstik.core.data.files.StickerFiles
import com.redrixdev.tikstik.media.WhatsAppConverter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** One WhatsApp copy per sticker: prepared in the background or, if still missing, when the pack is created. */
@Singleton
class WhatsAppCopies @Inject constructor(
    private val db: AppDatabase,
    private val files: StickerFiles,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    // One lock per sticker, striped, shared by the Worker and "Create packs".
    private val locks = Array(LOCK_STRIPES) { Mutex() }

    fun file(stickerId: String): File = files.whatsApp(stickerId)

    suspend fun missing(): List<StickerEntity> = withContext(io) {
        db.stickers().all().filterNot { files.whatsApp(it.id).exists() }
    }

    /** The copy of [sticker], converted on [dispatcher] if it does not exist yet. */
    suspend fun ensure(sticker: StickerEntity, dispatcher: CoroutineDispatcher = io): File =
        locks[Math.floorMod(sticker.id.hashCode(), LOCK_STRIPES)].withLock {
            withContext(dispatcher) {
                val copy = files.whatsApp(sticker.id)
                if (!copy.exists()) {
                    val original = files.original(sticker.id, sticker.fileExt).readBytes()
                    files.write(copy, WhatsAppConverter.sticker(original, sticker.animated))
                }
                copy
            }
        }

    /** The copy of sticker [id]; null if it no longer exists. */
    suspend fun ensure(id: String, dispatcher: CoroutineDispatcher = io): File? =
        withContext(io) { db.stickers().get(id) }?.let { ensure(it, dispatcher) }

    private companion object {
        const val LOCK_STRIPES = 32
    }
}
