// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.whatsapp

import android.content.Intent
import com.redrixdev.tikstik.core.common.IoDispatcher
import com.redrixdev.tikstik.core.data.settings.PendingAdd
import com.redrixdev.tikstik.core.data.settings.SettingsRepository
import com.redrixdev.tikstik.packs.PackRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** A pack to open in WhatsApp so the user confirms it there. */
data class AddRequest(val packId: String, val name: String, val app: WhatsAppApp)

/** What each WhatsApp has and what is offered (10 at most), one pack at a time; what does not fit stays pending. */
@Singleton
class WhatsAppPacks @Inject constructor(
    private val gateway: WhatsAppGateway,
    private val packs: PackRepository,
    private val settings: SettingsRepository,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    // Opened in WhatsApp with no answer yet: it keeps being offered in the meantime.
    private var inFlight: AddRequest? = null
    private var checkedAll = false

    private val _installed = MutableStateFlow<List<WhatsAppApp>>(emptyList())
    val installed: StateFlow<List<WhatsAppApp>> = _installed.asStateFlow()

    private val _added = MutableStateFlow<Map<String, Set<WhatsAppApp>>>(emptyMap())

    /** packId → the WhatsApp apps that already have it. */
    val added: StateFlow<Map<String, Set<WhatsAppApp>>> = _added.asStateFlow()

    /** The WhatsApp where the pending pack did not fit; the limit sheet shows while it exists. */
    val limit: Flow<WhatsAppApp?> = settings.settings.map { it.pendingAdd?.app }.distinctUntilChanged()

    private val launches = Channel<AddRequest>(Channel.BUFFERED)

    /** The route opens them in WhatsApp and reports the answer through [onResult]. */
    val requests: Flow<AddRequest> = launches.receiveAsFlow()

    fun intent(request: AddRequest): Intent = gateway.addIntent(request.app, request.packId, request.name)

    fun openIntent(app: WhatsAppApp): Intent? = gateway.openIntent(app)

    /** On returning to the app: refreshes what is offered and, if there is room now, continues with the pending pack. */
    suspend fun refresh() = mutex.withLock {
        sync()
        retryPending()
    }

    suspend fun add(packId: String, app: WhatsAppApp) = mutex.withLock {
        val pack = packs.get(packId) ?: return@withLock
        if (pack.stickerIds.size < PackRules.MIN_STICKERS) return@withLock
        if (binder { gateway.isAdded(app, packId) } == true) return@withLock sync()
        if (packs.offered().count { it.pack.id != packId } >= PackRules.MAX_PACKS) {
            settings.setPendingAdd(PendingAdd(packId, app))
            return@withLock
        }
        launch(AddRequest(packId, pack.pack.name, app))
    }

    /** Back from WhatsApp: its result code varies between versions, so the whitelist, read by [sync], is what counts. */
    suspend fun onResult() = mutex.withLock {
        inFlight = null
        sync()
    }

    suspend fun dismissLimit() {
        settings.setPendingAdd(null)
    }

    // Offered before opening WhatsApp: the first thing it does is query the provider.
    private suspend fun launch(request: AddRequest) {
        packs.setOffered(setOf(request.packId), true)
        inFlight = request
        launches.send(request)
    }

    // Whatever is in some WhatsApp is offered; whatever all of them clearly lack frees its slot. When unknown, nothing changes.
    private suspend fun sync() {
        val installed = binder { gateway.installed() }
        _installed.value = installed
        val nowIn = mutableSetOf<String>()
        val nowOut = mutableSetOf<String>()
        val all = packs.packs.first()
        val ids = all.mapTo(HashSet()) { it.pack.id }
        val added = _added.value.filterKeys { it in ids }.toMutableMap()
        // All of them once per process (some may be unoffered after a restore); afterwards, only the offered ones and the one in flight.
        val toCheck = if (checkedAll) all.filter { it.pack.offered || it.pack.id == inFlight?.packId } else all
        if (installed.isNotEmpty()) checkedAll = true
        for (pack in toCheck) {
            val id = pack.pack.id
            val states = binder { installed.associateWith { gateway.isAdded(it, id) } }
            val before = added[id].orEmpty()
            added[id] = states.filter { (app, answer) -> answer ?: (app in before) }.keys
            when {
                states.values.any { it == true } -> if (!pack.pack.offered) nowIn += id
                installed.isNotEmpty() && states.values.all { it == false } && id != inFlight?.packId -> if (pack.pack.offered) nowOut += id
            }
        }
        if (nowIn.isNotEmpty()) packs.setOffered(nowIn, true)
        if (nowOut.isNotEmpty()) packs.setOffered(nowOut, false)
        _added.value = added
    }

    private suspend fun retryPending() {
        val pending = settings.settings.first().pendingAdd ?: return
        val pack = packs.get(pending.packId)
        when {
            pack == null || pack.stickerIds.size < PackRules.MIN_STICKERS || pending.app !in _installed.value ||
                pending.app in _added.value[pending.packId].orEmpty() -> settings.setPendingAdd(null)
            packs.offered().count { it.pack.id != pending.packId } < PackRules.MAX_PACKS -> {
                settings.setPendingAdd(null)
                launch(AddRequest(pending.packId, pack.pack.name, pending.app))
            }
        }
    }

    // Queries to WhatsApp are blocking binder calls.
    private suspend fun <T> binder(block: () -> T): T = withContext(io) { block() }
}
