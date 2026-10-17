// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.home

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.core.designsystem.ScreenPreview
import com.redrixdev.tikstik.core.designsystem.brand.BrandMark
import com.redrixdev.tikstik.core.designsystem.component.BubbleFillProgress
import com.redrixdev.tikstik.core.designsystem.component.ConfirmDialog
import com.redrixdev.tikstik.core.designsystem.component.EmptyState
import com.redrixdev.tikstik.core.designsystem.component.Segmented
import com.redrixdev.tikstik.core.designsystem.component.StickerPreview
import com.redrixdev.tikstik.core.designsystem.component.StickerThumb
import com.redrixdev.tikstik.core.designsystem.component.ThumbBadges
import com.redrixdev.tikstik.core.designsystem.component.ThumbPlacement
import com.redrixdev.tikstik.core.designsystem.component.TrayImage
import com.redrixdev.tikstik.core.designsystem.component.placedAt
import com.redrixdev.tikstik.core.designsystem.icon.TikStikIcons
import com.redrixdev.tikstik.core.designsystem.theme.Motion
import com.redrixdev.tikstik.core.designsystem.theme.Surface1
import com.redrixdev.tikstik.core.designsystem.theme.TextMid
import com.redrixdev.tikstik.core.designsystem.theme.TikStikTheme
import com.redrixdev.tikstik.core.designsystem.theme.motion
import com.redrixdev.tikstik.core.designsystem.theme.navigationBarsStable
import com.redrixdev.tikstik.core.designsystem.theme.statusBarsStable
import com.redrixdev.tikstik.core.ui.StickerUi
import com.redrixdev.tikstik.ui.create.CreateSheet
import com.redrixdev.tikstik.ui.create.CreateUi
import com.redrixdev.tikstik.ui.create.PackChoiceSheet
import com.redrixdev.tikstik.ui.create.PackChoiceUi
import com.redrixdev.tikstik.ui.importing.ImportPanel
import com.redrixdev.tikstik.ui.importing.ImportReviewUi
import com.redrixdev.tikstik.whatsapp.WhatsAppApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.io.File

/** Progress of the background preparation of WhatsApp copies. */
@Immutable
data class PrepareProgress(val done: Int, val total: Int)

/** Home state; [loaded] avoids drawing the empty state before the collection is read. */
@Immutable
data class HomeUiState(
    val loaded: Boolean = false,
    val still: List<StickerUi> = emptyList(),
    val animated: List<StickerUi> = emptyList(),
    val selection: Set<String> = emptySet(),
    val connected: Boolean = true,
    val reading: ReadingUi? = null,
    val preparing: PrepareProgress? = null,
    val trays: List<TrayImage> = emptyList(),
    val importReview: ImportReviewUi? = null,
    val packChoice: PackChoiceUi? = null,
    val create: CreateUi? = null,
    val cacheDenied: Boolean = false,
)

sealed interface HomeAction {
    data class Toggle(val id: String) : HomeAction
    data class Select(val ids: Set<String>) : HomeAction
    data object ClearSelection : HomeAction
    data object DeleteSelection : HomeAction
    data object AddSelection : HomeAction
    data object Import : HomeAction
    data object CancelImport : HomeAction
    data class ToggleCandidate(val key: String) : HomeAction
    data class SelectCandidates(val keys: Set<String>) : HomeAction
    data object SaveCandidates : HomeAction
    data object CloseImport : HomeAction
    data object DismissCacheDenied : HomeAction
    data object NewPack : HomeAction
    data class PickPack(val packId: String) : HomeAction
    data object DismissPackChoice : HomeAction
    data object CreatePacks : HomeAction
    data class AddToWhatsApp(val packId: String, val app: WhatsAppApp) : HomeAction
    data object DismissCreate : HomeAction
}

internal const val PAGE_STILL = 0
internal const val PAGE_ANIMATED = 1

@Composable
fun HomeScreen(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
    onOpenPacks: () -> Unit,
    onOpenSettings: () -> Unit,
    onConnect: () -> Unit,
    scrollRequests: Flow<Int> = emptyFlow(),
) {
    val selectionMode = state.selection.isNotEmpty()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var preparingOpen by rememberSaveable { mutableStateOf(false) }
    // The enlarged sticker and its thumbnail's bounds, so the preview can close back into it.
    var preview by remember { mutableStateOf<Pair<File, Rect?>?>(null) }

    BackHandler(selectionMode) { onAction(HomeAction.ClearSelection) }

    val dock = when {
        selectionMode -> DockMode.Selecting(state.selection.size)
        state.reading != null -> DockMode.Reading(state.reading)
        else -> DockMode.Idle(state.connected)
    }
    // Still and animated on two pages: a selection is always of one kind and is dropped when the page changes.
    val pager = rememberPagerState(initialPage = PAGE_STILL) { 2 }
    val scope = rememberCoroutineScope()
    // Only on a real page change: when the screen is recreated (theme, font, rotation) the selection stays.
    var settledOn by rememberSaveable { mutableIntStateOf(pager.settledPage) }
    LaunchedEffect(pager.settledPage) {
        if (pager.settledPage != settledOn) onAction(HomeAction.ClearSelection)
        settledOn = pager.settledPage
    }
    val grids = List(2) { rememberLazyGridState() }
    LaunchedEffect(scrollRequests) {
        scrollRequests.collect { page ->
            pager.animateScrollToPage(page)
            grids[page].animateScrollToItem(0)
        }
    }

    // With the review or the preview on top, Home does not exist for TalkBack even though it shows dimmed.
    val covered = Modifier.then(if (state.importReview != null || preview != null) Modifier.clearAndSetSemantics { } else Modifier)
    // What the dock covers at the bottom: empty states center in the space left, not in the whole screen.
    val dockSpace = 108.dp + WindowInsets.navigationBarsStable.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().then(covered)) {
            Box(Modifier.padding(horizontal = 16.dp)) {
                HomeTopBar(state.preparing, { preparingOpen = true }, onOpenSettings)
            }
            if (state.loaded && state.still.isEmpty() && state.animated.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f).padding(bottom = dockSpace), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = stringResource(R.string.home_empty_title),
                        body = stringResource(R.string.home_empty_body),
                        illustration = { BrandMark(Modifier.size(150.dp), alive = true) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Segmented(
                        options = listOf(
                            stringResource(R.string.filter_still) + stringResource(R.string.separator) + state.still.size,
                            stringResource(R.string.filter_animated) + stringResource(R.string.separator) + state.animated.size,
                        ),
                        selected = pager.currentPage,
                        onSelect = { scope.launch { pager.animateScrollToPage(it) } },
                        position = { pager.currentPage + pager.currentPageOffsetFraction },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    val current = if (pager.currentPage == PAGE_STILL) state.still else state.animated
                    IconButton(
                        onClick = { onAction(HomeAction.Select(current.map { it.id }.toSet())) },
                        enabled = current.isNotEmpty(),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            TikStikIcons.SelectAll,
                            contentDescription = stringResource(R.string.action_select_all),
                            tint = if (current.isNotEmpty()) TextMid else TextMid.copy(alpha = 0.35f),
                        )
                    }
                }
                HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1) { page ->
                    val list = if (page == PAGE_STILL) state.still else state.animated
                    if (state.loaded && list.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(bottom = dockSpace), contentAlignment = Alignment.Center) {
                            EmptyState(
                                title = stringResource(if (page == PAGE_STILL) R.string.home_none_still else R.string.home_none_animated),
                                body = stringResource(R.string.home_none_body),
                            )
                        }
                        return@HorizontalPager
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(104.dp),
                        state = grids[page],
                        modifier = Modifier.fillMaxSize(),
                        // The dock sits over the navigation bar: with 3-button navigation the last row was hidden under it.
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = dockSpace),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(list, key = { it.id }) { sticker ->
                            val placement = remember { ThumbPlacement() }
                            StickerThumb(
                                file = sticker.file,
                                selected = sticker.id in state.selection,
                                selectionMode = selectionMode,
                                onClick = { onAction(HomeAction.Toggle(sticker.id)) },
                                onLongClick = { preview = sticker.file to placement.bounds() },
                                badges = ThumbBadges(inPack = sticker.inPack),
                                modifier = Modifier
                                    .placedAt(placement)
                                    .animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = motion(Motion.offset())),
                            )
                        }
                    }
                }
            }
        }

        HomeDock(
            mode = dock,
            trays = state.trays,
            onImport = { onAction(HomeAction.Import) },
            onConnect = onConnect,
            onCancelRead = { onAction(HomeAction.CancelImport) },
            onPacks = onOpenPacks,
            onClearSelection = { onAction(HomeAction.ClearSelection) },
            onDelete = { confirmDelete = true },
            onAddSelection = { onAction(HomeAction.AddSelection) },
            modifier = Modifier.align(Alignment.BottomCenter).then(covered),
        )

        ImportPanel(
            review = state.importReview,
            onToggle = { onAction(HomeAction.ToggleCandidate(it)) },
            onSelect = { onAction(HomeAction.SelectCandidates(it)) },
            onSave = { onAction(HomeAction.SaveCandidates) },
            onClose = { onAction(HomeAction.CloseImport) },
        )

        preview?.let { (file, origin) -> StickerPreview(file, origin, onClosed = { preview = null }) }
    }

    if (confirmDelete) {
        val count = state.selection.size
        ConfirmDialog(
            title = pluralStringResource(R.plurals.selection_delete_title, count, count),
            body = stringResource(R.string.selection_delete_body),
            confirm = stringResource(R.string.action_delete),
            onConfirm = {
                confirmDelete = false
                onAction(HomeAction.DeleteSelection)
            },
            onDismiss = { confirmDelete = false },
        )
    }
    state.packChoice?.let { choice ->
        PackChoiceSheet(
            choice = choice,
            onNew = { onAction(HomeAction.NewPack) },
            onPick = { onAction(HomeAction.PickPack(it)) },
            onDismiss = { onAction(HomeAction.DismissPackChoice) },
        )
    }
    if (state.cacheDenied) {
        // The exact build: it tells whether a system update fixes it.
        ConfirmDialog(
            title = stringResource(R.string.cache_denied_title),
            body = stringResource(R.string.cache_denied_body, Build.VERSION.RELEASE, Build.ID),
            confirm = stringResource(R.string.action_got_it),
            onConfirm = { onAction(HomeAction.DismissCacheDenied) },
            onDismiss = { onAction(HomeAction.DismissCacheDenied) },
            danger = false,
            showCancel = false,
        )
    }
    if (preparingOpen) PreparingSheet(state.preparing, onDismiss = { preparingOpen = false })
    state.create?.let { create ->
        CreateSheet(
            state = create,
            onCreate = { onAction(HomeAction.CreatePacks) },
            onAddToWhatsApp = { pack, app -> onAction(HomeAction.AddToWhatsApp(pack, app)) },
            onViewPacks = {
                onAction(HomeAction.DismissCreate)
                onOpenPacks()
            },
            onDismiss = { onAction(HomeAction.DismissCreate) },
        )
    }
}

private enum class TopSlot { NONE, PREPARING }

/** Brand, a slot and settings: the background preparation shows in the slot without moving anything else. */
@Composable
private fun HomeTopBar(preparing: PrepareProgress?, onOpenPreparing: () -> Unit, onOpenSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBarsStable).height(68.dp), verticalAlignment = Alignment.CenterVertically) {
        // On app open and on tap its echoes pull in and pop out; another tap while it runs does not restart it.
        var bounce by remember { mutableIntStateOf(0) }
        var bouncing by remember { mutableStateOf(false) }
        var greeted by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            if (greeted) return@LaunchedEffect
            greeted = true
            bouncing = true
            bounce++
        }
        // Logo and name are one brand mark: tapping either animates it; the empty space beside them does not.
        Row(Modifier.weight(1f)) {
            Row(
                Modifier.weight(1f, fill = false).pointerInput(Unit) {
                    detectTapGestures {
                        if (!bouncing) {
                            bouncing = true
                            bounce++
                        }
                    }
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandMark(Modifier.size(34.dp), bounce = bounce, onBounced = { bouncing = false })
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // While leaving, the preparing pill keeps showing its last progress through the fade.
        var lastPreparing by remember { mutableStateOf(preparing) }
        if (preparing != null) lastPreparing = preparing
        val slot = if (preparing != null) TopSlot.PREPARING else TopSlot.NONE
        AnimatedContent(
            targetState = slot,
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 90)) + scaleIn(tween(220, delayMillis = 90), initialScale = 0.92f))
                    .togetherWith(fadeOut(tween(90)))
                    .using(SizeTransform(clip = false) { _, _ -> tween(260) })
            },
            contentAlignment = Alignment.CenterEnd,
            label = "slot",
        ) { target ->
            when (target) {
                TopSlot.PREPARING -> lastPreparing?.let { PreparingPill(it, onOpenPreparing) }
                TopSlot.NONE -> Spacer(Modifier.width(0.dp))
            }
        }
        IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
            Icon(TikStikIcons.Settings, contentDescription = stringResource(R.string.cd_settings))
        }
    }
}

@Composable
private fun PreparingPill(progress: PrepareProgress, onClick: () -> Unit) {
    val description = stringResource(R.string.home_preparing, progress.done, progress.total)
    Row(
        Modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(Surface1)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 6.dp, end = 12.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BubbleFillProgress(if (progress.total == 0) null else progress.done / progress.total.toFloat(), Modifier.size(26.dp))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.fraction, progress.done, progress.total), style = MaterialTheme.typography.labelMedium, color = TextMid, maxLines = 1)
    }
}

@ScreenPreview
@Composable
private fun HomeEmptyPreview() {
    TikStikTheme { HomeScreen(HomeUiState(loaded = true), {}, {}, {}, {}) }
}

@ScreenPreview
@Composable
private fun HomeReadingPreview() {
    TikStikTheme {
        HomeScreen(HomeUiState(loaded = true, reading = ReadingUi(180, 420, connected = true)), {}, {}, {}, {})
    }
}
