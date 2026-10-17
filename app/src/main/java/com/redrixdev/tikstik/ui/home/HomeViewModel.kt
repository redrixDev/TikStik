// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redrixdev.tikstik.access.AccessController
import com.redrixdev.tikstik.access.AccessError
import com.redrixdev.tikstik.access.AccessException
import com.redrixdev.tikstik.collection.CollectionRepository
import com.redrixdev.tikstik.core.common.ApplicationScope
import com.redrixdev.tikstik.core.common.Logger
import com.redrixdev.tikstik.core.data.db.PackWithStickers
import com.redrixdev.tikstik.core.data.db.StickerEntity
import com.redrixdev.tikstik.core.designsystem.component.TrayImage
import com.redrixdev.tikstik.core.ui.Notices
import com.redrixdev.tikstik.core.ui.StickerUi
import com.redrixdev.tikstik.importing.Candidate
import com.redrixdev.tikstik.importing.CandidateStatus
import com.redrixdev.tikstik.importing.ImportProgress
import com.redrixdev.tikstik.importing.ImportResult
import com.redrixdev.tikstik.importing.Importer
import com.redrixdev.tikstik.packs.CopyPreparation
import com.redrixdev.tikstik.packs.PackBuilder
import com.redrixdev.tikstik.packs.PackLimits
import com.redrixdev.tikstik.packs.PackPlanner
import com.redrixdev.tikstik.packs.PackRepository
import com.redrixdev.tikstik.ui.create.CreateUi
import com.redrixdev.tikstik.ui.create.CreatedPackUi
import com.redrixdev.tikstik.ui.create.NewPackUi
import com.redrixdev.tikstik.ui.create.PackChoiceUi
import com.redrixdev.tikstik.ui.create.PackFitUi
import com.redrixdev.tikstik.ui.create.SkipReason
import com.redrixdev.tikstik.ui.create.SkipUi
import com.redrixdev.tikstik.ui.importing.CandidateUi
import com.redrixdev.tikstik.ui.importing.ImportReviewUi
import com.redrixdev.tikstik.ui.packs.PackNotices
import com.redrixdev.tikstik.whatsapp.PackRules
import com.redrixdev.tikstik.whatsapp.WhatsAppApp
import com.redrixdev.tikstik.whatsapp.WhatsAppPacks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val collection: CollectionRepository,
    private val packs: PackRepository,
    copies: CopyPreparation,
    private val access: AccessController,
    private val importer: Importer,
    private val builder: PackBuilder,
    private val whatsApp: WhatsAppPacks,
    private val notices: Notices,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private data class Review(val candidates: List<Candidate>, val selection: Set<String>, val unreadable: Int, val found: Int, val saving: Boolean = false)

    private data class Importing(val reading: ReadingUi? = null, val review: Review? = null, val denied: Boolean = false)

    /** Taking the selection to packs: "Which pack?" and the create sheet. */
    private sealed interface Packing {
        data object Idle : Packing
        data object Choosing : Packing
        data class Review(val ids: List<String>) : Packing
        // [finished]: when adding to a pack the sheet closes by itself; when creating, it moves on to Done.
        data class Working(val done: Int, val total: Int, val finished: Boolean = false) : Packing
        data class Done(val packIds: List<String>, val skipped: List<SkipUi>) : Packing
    }

    private data class PackingUi(val trays: List<TrayImage>, val choice: PackChoiceUi?, val create: CreateUi?)

    private val selection = MutableStateFlow<Set<String>>(emptySet())
    private val importing = MutableStateFlow(Importing())
    private val packing = MutableStateFlow<Packing>(Packing.Idle)
    private var importJob: Job? = null
    private var addJob: Job? = null

    private val pageRequests = Channel<Int>(Channel.CONFLATED)

    /** After saving, the tab of the last sticker seen, scrolled to the top. */
    val scrollRequests: Flow<Int> = pageRequests.receiveAsFlow()

    private val library = combine(collection.collection, packs.packs) { stickers, allPacks ->
        val inPacks = allPacks.flatMapTo(HashSet()) { it.stickerIds }
        stickers.map { StickerUi(it.id, collection.file(it), it.animated, it.id in inPacks) }
    }

    private val packingUi = combine(
        collection.collection,
        packs.packs,
        selection,
        packing,
        combine(whatsApp.installed, whatsApp.added, ::Pair),
    ) { stickers, allPacks, selected, current, (installed, added) ->
        val chosen = stickers.filter { it.id in selected }
        PackingUi(
            trays = allPacks.map(::tray),
            choice = if (current == Packing.Choosing && chosen.isNotEmpty()) choiceUi(chosen, allPacks) else null,
            create = when (current) {
                Packing.Idle, Packing.Choosing -> null
                is Packing.Review -> {
                    val byId = stickers.associateBy { it.id }
                    val plan = plan(current.ids.mapNotNull(byId::get))
                    CreateUi.Review(
                        packs = plan.take((PackLimits.MAX - allPacks.size).coerceAtLeast(0)).map { pack -> NewPackUi(pack.first().animated, pack.map(collection::file)) },
                        limit = PackLimits.MAX.takeIf { allPacks.size + plan.size > it },
                    )
                }
                is Packing.Working -> CreateUi.Working(current.done, current.total, current.finished)
                is Packing.Done -> CreateUi.Done(
                    created = current.packIds.mapNotNull { id -> allPacks.firstOrNull { it.pack.id == id } }
                        .map { CreatedPackUi(it.pack.id, it.pack.name, it.stickerIds.size, added[it.pack.id].orEmpty()) },
                    skipped = current.skipped,
                    installed = installed,
                )
            },
        )
    }

    val state: StateFlow<HomeUiState> = combine(library, copies.progress, access.status, selection, importing) { items, progress, status, selected, import ->
        HomeUiState(
            loaded = true,
            still = items.filterNot { it.animated },
            animated = items.filter { it.animated },
            selection = selected,
            connected = status.ready,
            reading = import.reading,
            preparing = progress?.let { PrepareProgress(it.done, it.total) },
            cacheDenied = import.denied,
            importReview = import.review?.let { review ->
                ImportReviewUi(review.candidates.map(::candidateUi), review.selection, review.unreadable, review.found, review.saving)
            },
        )
    }.combine(packingUi) { home, p -> home.copy(trays = p.trays, packChoice = p.choice, create = p.create) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch { copies.resume() }
    }

    fun refresh() = access.refresh()

    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.Toggle -> selection.update { if (action.id in it) it - action.id else it + action.id }
            is HomeAction.Select -> selection.value = action.ids
            HomeAction.ClearSelection -> {
                selection.value = emptySet()
                // With nothing selected, no "Which pack?" sheet is left waiting to come back on its own.
                packing.update { if (it == Packing.Choosing) Packing.Idle else it }
            }
            HomeAction.DeleteSelection -> deleteSelection()
            HomeAction.Import -> startImport()
            HomeAction.CancelImport -> cancelImport()
            is HomeAction.ToggleCandidate -> updateReview { it.copy(selection = if (action.key in it.selection) it.selection - action.key else it.selection + action.key) }
            is HomeAction.SelectCandidates -> updateReview { it.copy(selection = action.keys) }
            HomeAction.SaveCandidates -> saveCandidates()
            HomeAction.CloseImport -> importing.update { if (it.review?.saving == true) it else it.copy(review = null) }
            HomeAction.DismissCacheDenied -> importing.update { it.copy(denied = false) }
            HomeAction.AddSelection -> chooseWhere()
            HomeAction.NewPack -> viewModelScope.launch { packing.value = Packing.Review(chosen().map { it.id }) }
            is HomeAction.PickPack -> addSelectionTo(action.packId)
            HomeAction.DismissPackChoice -> packing.update { if (it == Packing.Choosing) Packing.Idle else it }
            HomeAction.CreatePacks -> createPacks()
            is HomeAction.AddToWhatsApp -> addToWhatsApp(action.packId, action.app)
            HomeAction.DismissCreate -> dismissCreate()
        }
    }

    // With no packs, straight to a new one; otherwise the user picks where.
    private fun chooseWhere() {
        if (selection.value.isEmpty()) return
        viewModelScope.launch {
            packing.value = if (packs.packs.first().isEmpty()) Packing.Review(chosen().map { it.id }) else Packing.Choosing
        }
    }

    // In collection order: the most recently seen first.
    private suspend fun chosen() = collection.collection.first().filter { it.id in selection.value }

    // A second tap while adding started another add that found everything already inside.
    private fun addSelectionTo(packId: String) {
        if (addJob?.isActive == true) return
        packing.value = Packing.Idle
        addJob = viewModelScope.launch {
            val chosen = chosen()
            val pack = packs.get(packId)?.pack ?: return@launch
            val progress = AtomicReference(0 to 0)
            var shown = false
            // If the copies were already prepared it finishes at once: the sheet only appears when there is a real wait.
            val sheet = launch {
                delay(SHOW_WORK_AFTER_MS)
                shown = true
                val (done, total) = progress.get()
                packing.value = Packing.Working(done, total)
                // Not a flicker: it stays visible at least a moment before closing.
                delay(MIN_WORK_SHOWN_MS)
            }
            val report = try {
                builder.addTo(packId, chosen) { done, total ->
                    progress.set(done to total)
                    packing.update { if (it is Packing.Working) it.copy(done = done, total = total) else it }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.warn(AREA_PACKS, "add to pack failed", e)
                null
            } finally {
                if (!shown) sheet.cancel()
            }
            if (shown) {
                sheet.join()
                packing.update { if (it is Packing.Working) it.copy(finished = true) else it }
            }
            if (report == null) {
                notices.post(PackNotices.saveFailed)
                return@launch
            }
            // What did not fit stays selected so it can go to another pack.
            val done = (report.added + report.already).toSet()
            selection.update { it - done }
            notices.post(PackNotices.addedTo(report.added.size, report.already.size, chosen.count { it.id !in done }, pack.name))
        }
    }

    // The new packs, in the same order the create sheet shows them.
    private fun plan(stickers: List<StickerEntity>) = stickers.groupBy { it.animated }.values.flatMap { PackPlanner.split(it) }

    private fun createPacks() {
        val review = packing.value as? Packing.Review ?: return
        packing.value = Packing.Working(0, review.ids.size)
        viewModelScope.launch {
            val ids = review.ids.toSet()
            val room = PackLimits.MAX - packs.packs.first().size
            // Only the packs that fit under the limit; the rest stays selected, like what does not fit in a pack.
            val chosen = plan(collection.collection.first().filter { it.id in ids }).take(room.coerceAtLeast(0)).flatten()
            val report = try {
                builder.create(chosen) { done, total -> packing.update { if (it is Packing.Working) it.copy(done = done, total = total) else it } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The "No pack could be created" screen.
                Logger.warn(AREA_PACKS, "create packs failed", e)
                packing.value = Packing.Done(emptyList(), emptyList())
                return@launch
            }
            // Only what went into a pack stops being selected.
            selection.update { it - report.packed.toSet() }
            val skipped = buildList {
                if (report.convertFailed > 0) add(SkipUi(SkipReason.CONVERT, report.convertFailed))
                if (report.notCreated > 0) add(SkipUi(SkipReason.TRAY, report.notCreated))
            }
            packing.value = Packing.Done(report.created.map { it.id }, skipped)
        }
    }

    private fun addToWhatsApp(packId: String, app: WhatsAppApp) {
        // With a single pack the sheet closes; with several it stays open so each one gets its turn.
        val created = (state.value.create as? CreateUi.Done)?.created.orEmpty()
        if (packing.value is Packing.Done && created.count { it.size >= PackRules.MIN_STICKERS } <= 1) packing.value = Packing.Idle
        write { whatsApp.add(packId, app) }
    }

    private fun dismissCreate() {
        when (val current = packing.value) {
            is Packing.Working -> if (current.finished) packing.value = Packing.Idle
            else -> packing.value = Packing.Idle
        }
    }

    private fun tray(pack: PackWithStickers) = TrayImage(packs.tray(pack.pack.id), pack.pack.imageDataVersion.toLong())

    // Packs of the same kind with room; those that already hold everything selected are listed, but disabled.
    private fun choiceUi(chosen: List<StickerEntity>, allPacks: List<PackWithStickers>): PackChoiceUi {
        val kinds = chosen.map { it.animated }.toSet()
        val fits = allPacks.filter { it.pack.animated in kinds && it.stickerIds.size < PackRules.MAX_STICKERS }.map { pack ->
            val ofKind = chosen.filter { it.animated == pack.pack.animated }.map { it.id }
            val wanted = ofKind.count { it !in pack.stickerIds }
            PackFitUi(pack.pack.id, pack.pack.name, tray(pack), pack.pack.animated, pack.stickerIds.size, wanted, PackPlanner.fitting(ofKind, pack.stickerIds).size)
        }.sortedByDescending { it.fits > 0 }
        val animated = chosen.count { it.animated }
        val newPacks = chosen.groupBy { it.animated }.values.sumOf { PackPlanner.split(it).size }
        return PackChoiceUi(animated, chosen.size - animated, newPacks, fits)
    }

    private fun startImport() {
        if (importJob?.isActive == true) return
        importing.value = Importing(reading = ReadingUi(0, 0, connected = false))
        importJob = viewModelScope.launch {
            try {
                // Progress arriving after a cancel must not reopen the reading: nothing would be left to close it.
                val result = importer.read(onProgress = { progress -> importing.update { if (it.reading == null) it else it.copy(reading = progress.toUi()) } }, onNetworkApproval = { notices.post(ImportNotices.newNetwork) })
                importing.value = Importing()
                show(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AccessException) {
                // Retrying is pointless: a dialog explains what is happening and what to look for.
                importing.value = Importing(denied = e.error == AccessError.CACHE_DENIED)
                access.refresh()
                if (e.error != AccessError.CACHE_DENIED) notices.post(ImportNotices.of(e.error))
            } catch (e: Exception) {
                Logger.warn(AREA, "import failed", e)
                importing.value = Importing()
                notices.post(if (e is IOException) ImportNotices.cut else ImportNotices.failed)
            }
        }
    }

    private fun show(result: ImportResult) {
        when {
            result is ImportResult.NoTikTok -> notices.post(ImportNotices.noTikTok)
            result is ImportResult.Review && result.candidates.isEmpty() ->
                notices.post(if (result.unreadable > 0) ImportNotices.unreadable(result.unreadable) else ImportNotices.nothing)
            // Nothing selected at first: the user picks by hand or with "Select all".
            result is ImportResult.Review -> importing.value = Importing(review = Review(result.candidates, emptySet(), result.unreadable, result.found))
        }
    }

    private fun cancelImport() {
        importJob?.cancel()
        importJob = null
        importing.update { it.copy(reading = null) }
    }

    private fun saveCandidates() {
        val review = importing.value.review ?: return
        val chosen = review.candidates.filter { it.key in review.selection }
        if (chosen.isEmpty() || review.saving) return
        updateReview { it.copy(saving = true) }
        // In the app scope: "back" while saving closes Home, and the save must not stop halfway.
        appScope.launch {
            val result = try {
                importer.save(chosen)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.warn(AREA, "save failed", e)
                updateReview { it.copy(saving = false) }
                notices.post(ImportNotices.failed)
                return@launch
            }
            importing.value = Importing()
            notices.post(ImportNotices.saved(result.added))
            val last = result.last ?: return@launch
            // Jumps only once it is in the list: otherwise the grid would stay where it was.
            withTimeoutOrNull(LIST_UPDATE_MS) { collection.collection.first { stickers -> stickers.any { it.id == last.id } } }
            pageRequests.send(if (last.animated) PAGE_ANIMATED else PAGE_STILL)
        }
    }

    private fun deleteSelection() {
        val ids = selection.value
        if (ids.isEmpty()) return
        selection.value = emptySet()
        write { collection.delete(ids) }
    }

    // In the app scope (leaving Home does not undo a half-done delete), with the failure notice if it fails.
    private fun write(block: suspend () -> Unit) {
        appScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.warn(AREA, "write failed", e)
                notices.post(PackNotices.saveFailed)
            }
        }
    }

    private fun updateReview(change: (Review) -> Review) = importing.update { current -> current.review?.let { current.copy(review = change(it)) } ?: current }

    private fun candidateUi(candidate: Candidate) = CandidateUi(
        key = candidate.key,
        file = candidate.file,
        animated = candidate.animated,
        fresh = candidate.status == CandidateStatus.NEW,
        owned = candidate.status == CandidateStatus.IN_COLLECTION,
    )

    private fun ImportProgress.toUi(): ReadingUi = when (this) {
        ImportProgress.Connecting -> ReadingUi(0, 0, connected = false)
        ImportProgress.Searching -> ReadingUi(0, 0, connected = true)
        is ImportProgress.Reading -> ReadingUi(done, total, connected = true)
        is ImportProgress.Reviewing -> ReadingUi(done, total, connected = true, reviewing = true)
    }

    private companion object {
        const val AREA = "Import"
        const val AREA_PACKS = "Packs"
        const val LIST_UPDATE_MS = 3_000L
        const val SHOW_WORK_AFTER_MS = 300L
        const val MIN_WORK_SHOWN_MS = 700L
    }
}
