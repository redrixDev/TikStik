// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.importing

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.core.designsystem.component.CountPill
import com.redrixdev.tikstik.core.designsystem.component.QuietButton
import com.redrixdev.tikstik.core.designsystem.component.Segmented
import com.redrixdev.tikstik.core.designsystem.component.StickerPreview
import com.redrixdev.tikstik.core.designsystem.component.StickerThumb
import com.redrixdev.tikstik.core.designsystem.component.ThumbBadges
import com.redrixdev.tikstik.core.designsystem.component.ThumbPlacement
import com.redrixdev.tikstik.core.designsystem.component.newGlow
import com.redrixdev.tikstik.core.designsystem.component.placedAt
import com.redrixdev.tikstik.core.designsystem.component.rememberNewGlow
import com.redrixdev.tikstik.core.designsystem.component.sheetScrollGuard
import com.redrixdev.tikstik.core.designsystem.theme.BrandGreen
import com.redrixdev.tikstik.core.designsystem.theme.Ink
import com.redrixdev.tikstik.core.designsystem.theme.Motion
import com.redrixdev.tikstik.core.designsystem.theme.Surface1
import com.redrixdev.tikstik.core.designsystem.theme.Surface2
import com.redrixdev.tikstik.core.designsystem.theme.TextMid
import com.redrixdev.tikstik.core.designsystem.theme.Warning
import com.redrixdev.tikstik.core.designsystem.theme.motion
import com.redrixdev.tikstik.core.designsystem.theme.navigationBarsStable
import com.redrixdev.tikstik.core.designsystem.theme.statusBarsStable
import kotlinx.coroutines.launch
import java.io.File

/** A sticker read from TikTok, just enough to draw it: [fresh] carries the new dot; [owned] is already in the collection. */
@Immutable
data class CandidateUi(val key: String, val file: File, val animated: Boolean, val fresh: Boolean, val owned: Boolean)

/** The review of what was imported, newest to oldest. */
@Immutable
data class ImportReviewUi(
    val candidates: List<CandidateUi>,
    val selection: Set<String>,
    val unreadable: Int,
    val found: Int,
    val saving: Boolean = false,
)

/** Cannot be closed while saving; keeps the last review while it closes. */
@Composable
fun ImportPanel(
    review: ImportReviewUi?,
    onToggle: (String) -> Unit,
    onSelect: (Set<String>) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    var last by remember { mutableStateOf(review) }
    if (review != null) last = review
    val saving = review?.saving == true
    PanelFrame(open = review != null, saving = saving, onClose = onClose) {
        last?.let { ReviewContent(it, saving, onToggle, onSelect, onSave) }
    }
}

/** The frame: a scrim that darkens (and closes on tap) and the card that rises; dragging only translates it, never remeasures. */
@Composable
private fun PanelFrame(open: Boolean, saving: Boolean, onClose: () -> Unit, content: @Composable () -> Unit) {
    // TalkBack announces it on opening, like a sheet.
    val title = stringResource(R.string.home_import)
    BackHandler(enabled = open && !saving, onBack = onClose)
    val fade = motion<Float>()
    val rise = motion(Motion.offset())
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var pulled by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(open) { if (open) pulled = 0f }
    val canPull = open && !saving
    fun release(velocity: Float) {
        // Deliberate: more than a third, or a quick fling that has already pulled it down a bit.
        val far = pulled > height * 0.35f || (velocity > with(density) { 1600.dp.toPx() } && pulled > height * 0.12f)
        if (far && pulled > 0f) {
            onClose()
        } else if (pulled > 0f) {
            scope.launch { animate(pulled, 0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f)) { v, _ -> pulled = v } }
        }
    }
    // The list scrolls first; leftover downward scroll with the list at the top pulls the panel down, and scrolling up raises it first.
    val pull = remember(canPull) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!canPull || source != NestedScrollSource.UserInput || available.y >= 0f || pulled <= 0f) return Offset.Zero
                val used = maxOf(available.y, -pulled)
                pulled += used
                return Offset(0f, used)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!canPull || source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                pulled += available.y
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pulled <= 0f) return Velocity.Zero
                release(available.y)
                return available
            }
        }
    }
    val drag = rememberDraggableState { pulled = maxOf(0f, pulled + it) }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(open, enter = fadeIn(fade), exit = fadeOut(fade)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - (pulled / height).coerceIn(0f, 1f) }
                    .background(Ink.copy(alpha = 0.6f))
                    .pointerInput(saving) { detectTapGestures { if (!saving) onClose() } },
            )
        }
        AnimatedVisibility(
            open,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(rise) { it } + fadeIn(fade),
            exit = slideOutVertically(rise) { it } + fadeOut(fade),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBarsStable)
                    .padding(top = 72.dp)
                    .onSizeChanged { height = it.height.toFloat().coerceAtLeast(1f) }
                    .graphicsLayer { translationY = pulled }
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(Surface1)
                    .nestedScroll(pull)
                    .draggable(drag, Orientation.Vertical, enabled = canPull, onDragStopped = { release(it) })
                    // Taps on the panel must not reach the scrim, which closes.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .semantics { paneTitle = title },
            ) {
                Column(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 12.dp)
                            .size(width = 40.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(TextMid.copy(alpha = 0.5f)),
                    )
                    Box(Modifier.weight(1f).sheetScrollGuard()) { content() }
                }
            }
        }
    }
}

@Composable
private fun ReviewContent(
    review: ImportReviewUi,
    saving: Boolean,
    onToggle: (String) -> Unit,
    onSelect: (Set<String>) -> Unit,
    onSave: () -> Unit,
) {
    var preview by remember { mutableStateOf<Pair<File, Rect?>?>(null) }
    // Two pages: what you can add and what you already have; with nothing to add, it opens on the second.
    val addable = remember(review.candidates) { review.candidates.filterNot { it.owned } }
    val owned = remember(review.candidates) { review.candidates.filter { it.owned } }
    val pager = rememberPagerState(initialPage = if (addable.isEmpty() && owned.isNotEmpty()) 1 else 0) { 2 }
    val scope = rememberCoroutineScope()
    val selection = review.selection
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Segmented(
                options = listOf(
                    stringResource(R.string.import_tab_new) + " · ${addable.size}",
                    stringResource(R.string.import_tab_owned) + " · ${owned.size}",
                ),
                selected = pager.currentPage,
                onSelect = { scope.launch { pager.animateScrollToPage(it) } },
                position = { pager.currentPage + pager.currentPageOffsetFraction },
                track = Surface2,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp),
            )
            if (review.unreadable > 0) {
                Text(
                    stringResource(R.string.import_partial, review.unreadable, review.found),
                    style = MaterialTheme.typography.bodySmall,
                    color = Warning,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            val glow = rememberNewGlow()
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1) { page ->
                val list = if (page == 0) addable else owned
                if (list.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(if (page == 0) R.string.import_empty_new else R.string.import_empty_owned),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMid,
                        )
                    }
                    return@HorizontalPager
                }
                val grid = rememberLazyGridState()
                // While scrolling (fling included), at the screen's high refresh rate.
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    state = grid,
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 2.dp, bottom = 12.dp),
                ) {
                    itemsIndexed(list, key = { _, it -> it.key }) { index, candidate ->
                        val placement = remember { ThumbPlacement() }
                        StickerThumb(
                            file = candidate.file,
                            selected = candidate.key in selection,
                            // What you already have cannot be selected; it can be viewed large.
                            selectionMode = !candidate.owned,
                            onClick = { if (!candidate.owned) onToggle(candidate.key) },
                            onLongClick = { preview = candidate.file to placement.bounds() },
                            badges = ThumbBadges(animated = candidate.animated),
                            modifier = Modifier
                                .placedAt(placement)
                                .newGlow(enabled = candidate.fresh, clock = glow, offset = index * 0.137f),
                        )
                    }
                }
            }
            // Starts with nothing selected: "Select all" or by hand. They go into the collection; packs are built later.
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBarsStable).padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val keys = remember(addable) { addable.map { it.key }.toSet() }
                if (keys.isNotEmpty()) {
                    val all = selection.containsAll(keys)
                    QuietButton(
                        stringResource(if (all) R.string.import_select_none else R.string.import_select_all),
                        onClick = { onSelect(if (all) emptySet() else keys) },
                        color = BrandGreen,
                    )
                }
                val n = selection.size
                Spacer(Modifier.weight(1f))
                CountPill(
                    count = n,
                    label = stringResource(R.string.selection_add),
                    description = pluralStringResource(R.plurals.import_save_and_add, n, n),
                    onClick = onSave,
                    modifier = Modifier.width(184.dp),
                    enabled = n > 0,
                    loading = saving,
                )
            }
        }
        preview?.let { (file, origin) -> StickerPreview(file, origin, onClosed = { preview = null }) }
    }
}
