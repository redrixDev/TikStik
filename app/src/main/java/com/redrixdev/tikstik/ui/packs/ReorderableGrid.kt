// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.packs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.core.designsystem.theme.motion
import kotlinx.coroutines.launch

private const val HEADER_KEY = "__header__"

// No bounce when settling: the slots are fixed, and a sticker overshooting by 1.5–3 dp was seen leaving its slot and coming back.
private val Settle = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset(1, 1))

// Lazy list keys must be storable in a Bundle: the empty slots use prefixed strings.
private const val SLOT_PREFIX = "__slot__"

// Closer than this to an edge, the grid scrolls by itself while dragging; the closer, the faster, up to the maximum step.
private val AUTO_SCROLL_EDGE = 72.dp
private val AUTO_SCROLL_STEP = 14.dp

/** The dragged item moves without being recreated; the [emptySlots] empty slots at the end cannot be dragged. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T : Any> ReorderableGrid(
    items: List<T>,
    key: (T) -> String,
    onReorder: (List<T>) -> Unit,
    emptySlots: Int,
    onEmptySlot: () -> Unit,
    modifier: Modifier = Modifier,
    minCell: Dp = 96.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    header: (@Composable () -> Unit)? = null,
    content: @Composable (item: T, index: Int, dragging: Boolean, actions: Modifier) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // A pack holds at most 30: all stay loaded so they do not fill in visibly when scrolling back.
    val grid = rememberLazyGridState(cacheWindow = LazyLayoutCacheWindow(ahead = 1600.dp, behind = 1600.dp))
    val order = remember(items) { items.toMutableStateList() }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    val delta = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val lift = remember { Animatable(0f) }
    var settling by remember { mutableStateOf(false) }
    // Grid positions exclude the start padding: it is added when drawing and subtracted from the finger.
    val startPx = with(LocalDensity.current) { contentPadding.calculateStartPadding(LocalLayoutDirection.current).toPx() }

    // Where the finger is on the grid: near an edge, the list scrolls by itself to carry it away.
    var finger by remember { mutableStateOf<Offset?>(null) }
    val edge = with(LocalDensity.current) { AUTO_SCROLL_EDGE.toPx() }
    val maxStep = with(LocalDensity.current) { AUTO_SCROLL_STEP.toPx() }

    fun infoOf(k: String?): LazyGridItemInfo? = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == k }
    fun draggable(info: LazyGridItemInfo) = info.key != HEADER_KEY && (info.key as? String)?.startsWith(SLOT_PREFIX) != true

    // If the centre of the dragged item lands on another slot, they swap; returns the compensated offset.
    fun moveUnder(d0: Offset): Offset {
        val current = infoOf(draggingKey) ?: return d0
        var d = d0
        val center = current.offset.toOffset() + d + Offset(current.size.width / 2f, current.size.height / 2f)
        val target = grid.layoutInfo.visibleItemsInfo.filter(::draggable).firstOrNull { info ->
            val o = info.offset.toOffset()
            info.key != current.key && center.x in o.x..(o.x + info.size.width) && center.y in o.y..(o.y + info.size.height)
        }
        if (target != null) {
            val from = order.indexOfFirst { key(it) == current.key }
            val to = order.indexOfFirst { key(it) == target.key }
            if (from >= 0 && to >= 0) {
                order.add(to, order.removeAt(from))
                // It changes slot: compensate so it stays under the finger.
                d += current.offset.toOffset() - target.offset.toOffset()
                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            }
        }
        return d
    }

    LaunchedEffect(draggingKey) {
        if (draggingKey == null) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val y = finger?.y ?: continue
            if (settling) continue
            val height = grid.layoutInfo.viewportSize.height
            val step = when {
                y < edge -> -maxStep * (1f - y / edge).coerceIn(0f, 1f)
                y > height - edge -> maxStep * (1f - (height - y) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (step == 0f) continue
            val moved = grid.scrollBy(step)
            // The dragged item stays under the finger even as the list moves beneath it.
            if (moved != 0f) delta.snapTo(moveUnder(delta.value + Offset(0f, moved)))
        }
    }
    fun finish() {
        finger = null
        if (draggingKey == null) return
        settling = true
        scope.launch {
            launch { lift.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
            delta.animateTo(Offset.Zero, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
            draggingKey = null
            settling = false
            if (order.map(key) != items.map(key)) onReorder(order.toList())
        }
    }

    // No overscroll stretch: the background slots do not stretch along with the stickers.
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minCell),
            state = grid,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            // Clipped to the grid area: otherwise, when scrolling down, they peeked over the top bar.
            modifier = modifier
                // While scrolling (fling included), at the display's high refresh rate.
                .clipToBounds()
                .drawBehind {
                    // Fixed where each item belongs, not where it passes through while settling.
                    grid.layoutInfo.visibleItemsInfo.forEach { info ->
                        if (info.key != HEADER_KEY) drawHole(info.offset.toOffset() + Offset(startPx, 0f), info.size.toSize())
                    }
                }
                .pointerInput(items) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { touch ->
                            if (settling) return@detectDragGesturesAfterLongPress
                            val at = touch - Offset(startPx, 0f)
                            val hit = grid.layoutInfo.visibleItemsInfo.filter(::draggable).firstOrNull { info ->
                                val o = info.offset.toOffset()
                                at.x in o.x..(o.x + info.size.width) && at.y in o.y..(o.y + info.size.height)
                            }
                            if (hit != null) {
                                finger = touch
                                draggingKey = hit.key as String
                                scope.launch { delta.snapTo(Offset.Zero) }
                                scope.launch { lift.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)) }
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onDrag = { change, amount ->
                            if (settling) return@detectDragGesturesAfterLongPress
                            infoOf(draggingKey) ?: return@detectDragGesturesAfterLongPress
                            change.consume()
                            finger = change.position
                            val d = moveUnder(delta.value + amount)
                            scope.launch { delta.snapTo(d) }
                        },
                        onDragEnd = { finish() },
                        onDragCancel = { finish() },
                    )
                },
        ) {
            if (header != null) item(key = HEADER_KEY, span = { GridItemSpan(maxLineSpan) }) { header() }
            itemsIndexed(order, key = { _, it -> key(it) }) { index, item ->
                val dragging = key(item) == draggingKey
                // TalkBack cannot drag: each item offers "move before" and "move after" as actions.
                val before = stringResource(R.string.cd_move_before)
                val after = stringResource(R.string.cd_move_after)
                val actions = Modifier.semantics {
                    customActions = buildList {
                        if (index > 0) add(CustomAccessibilityAction(before) { onReorder(order.moved(index, index - 1)); true })
                        if (index < order.lastIndex) add(CustomAccessibilityAction(after) { onReorder(order.moved(index, index + 1)); true })
                    }
                }
                Box(
                    if (dragging) {
                        Modifier.zIndex(1f).graphicsLayer {
                            translationX = delta.value.x
                            translationY = delta.value.y
                            val s = 1f + 0.08f * lift.value
                            scaleX = s
                            scaleY = s
                            shadowElevation = 16.dp.toPx() * lift.value
                            shape = RoundedCornerShape(20.dp)
                        }
                    } else {
                        Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = motion(Settle))
                    },
                ) { content(item, index, dragging, actions) }
            }
            items(emptySlots, key = { "$SLOT_PREFIX$it" }) { EmptySlot(onEmptySlot) }
        }
    }
}

private fun <T> List<T>.moved(from: Int, to: Int): List<T> = toMutableList().apply { add(to, removeAt(from)) }
