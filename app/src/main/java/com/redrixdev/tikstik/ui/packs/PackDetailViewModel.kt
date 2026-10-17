// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.packs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redrixdev.tikstik.collection.CollectionRepository
import com.redrixdev.tikstik.core.common.ApplicationScope
import com.redrixdev.tikstik.core.common.Logger
import com.redrixdev.tikstik.core.designsystem.component.TrayImage
import com.redrixdev.tikstik.core.ui.Notices
import com.redrixdev.tikstik.core.ui.StickerUi
import com.redrixdev.tikstik.packs.PackBuilder
import com.redrixdev.tikstik.packs.PackRepository
import com.redrixdev.tikstik.whatsapp.PackRules
import com.redrixdev.tikstik.whatsapp.WhatsAppApp
import com.redrixdev.tikstik.whatsapp.WhatsAppPacks
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel(assistedFactory = PackDetailViewModel.Factory::class)
class PackDetailViewModel @AssistedInject constructor(
    @Assisted private val packId: String,
    private val packs: PackRepository,
    private val collection: CollectionRepository,
    private val builder: PackBuilder,
    private val whatsApp: WhatsAppPacks,
    private val notices: Notices,
    // A change already requested completes even after leaving the screen.
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(packId: String): PackDetailViewModel
    }

    private val adding = MutableStateFlow<AddingProgress?>(null)
    private val exitRequests = Channel<Unit>(Channel.CONFLATED)
    private val writes = Mutex()

    /** The detail's only exit point. */
    val exits: Flow<Unit> = exitRequests.receiveAsFlow()

    val state: StateFlow<PackDetailUi?> = combine(
        packs.pack(packId),
        packs.stickers(packId),
        collection.collection,
        combine(whatsApp.installed, whatsApp.added, ::Pair),
        adding,
    ) { pack, inside, all, (installed, added), progress ->
        if (pack == null) return@combine null
        val ids = pack.stickerIds.toSet()
        PackDetailUi(
            id = pack.pack.id,
            name = pack.pack.name,
            animated = pack.pack.animated,
            stickers = inside.map { PackStickerUi(it.id, collection.file(it)) },
            tray = TrayImage(packs.tray(pack.pack.id), pack.pack.imageDataVersion.toLong()),
            offered = pack.pack.offered,
            installed = installed,
            addedTo = added[pack.pack.id].orEmpty(),
            addable = all.filter { it.animated == pack.pack.animated && it.id !in ids }.map { StickerUi(it.id, collection.file(it), it.animated) },
            adding = progress,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // If the pack ceases to exist (deleted here or in another pane), leave.
        viewModelScope.launch {
            packs.pack(packId).first { it == null }
            close()
        }
    }

    fun close() {
        exitRequests.trySend(Unit)
    }

    fun rename(name: String) = write { packs.rename(packId, name) }

    fun reorder(order: List<String>) = write {
        builder.reorder(packId, order)
        notices.post(PackNotices.updated)
    }

    fun remove(stickerIds: Set<String>) = write {
        val pack = packs.get(packId) ?: return@write
        if (pack.stickerIds.size - stickerIds.size < PackRules.minimumKept(pack.pack.offered)) {
            notices.post(PackNotices.removeMinimum)
            return@write
        }
        builder.remove(packId, stickerIds)
        notices.post(PackNotices.updated)
    }

    fun addStickers(stickerIds: Set<String>) = write {
        val chosen = collection.collection.first().filter { it.id in stickerIds }
        if (chosen.isEmpty()) return@write
        adding.value = AddingProgress(0, chosen.size)
        try {
            val report = builder.addTo(packId, chosen) { done, total -> adding.value = AddingProgress(done, total) }
            notices.post(PackNotices.stickersAdded(report.added.size))
        } finally {
            adding.value = null
        }
    }

    fun addToWhatsApp(app: WhatsAppApp) = write { whatsApp.add(packId, app) }

    fun delete() = write {
        packs.delete(packId)
        // Its slot in WhatsApp is freed.
        whatsApp.refresh()
    }

    // In arrival order (the lock is fair and is requested without waiting for the dispatcher), with the failure notice if it fails.
    private fun write(block: suspend () -> Unit) {
        appScope.launch(start = CoroutineStart.UNDISPATCHED) {
            writes.withLock {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.warn(AREA, "pack change failed", e)
                    notices.post(PackNotices.saveFailed)
                }
            }
        }
    }

    private companion object {
        const val AREA = "Packs"
    }
}
