// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.core.designsystem.component.BrandProgressBar
import com.redrixdev.tikstik.core.designsystem.component.CountPill
import com.redrixdev.tikstik.core.designsystem.component.PillHeight
import com.redrixdev.tikstik.core.designsystem.component.PillLabel
import com.redrixdev.tikstik.core.designsystem.component.TrayImage
import com.redrixdev.tikstik.core.designsystem.component.drawEchoFill
import com.redrixdev.tikstik.core.designsystem.component.rememberTrayRequest
import com.redrixdev.tikstik.core.designsystem.icon.TikStikIcons
import com.redrixdev.tikstik.core.designsystem.rememberHaptics
import com.redrixdev.tikstik.core.designsystem.theme.BrandGreen
import com.redrixdev.tikstik.core.designsystem.theme.BrandViolet
import com.redrixdev.tikstik.core.designsystem.theme.Ink
import com.redrixdev.tikstik.core.designsystem.theme.Motion
import com.redrixdev.tikstik.core.designsystem.theme.OnGreen
import com.redrixdev.tikstik.core.designsystem.theme.Surface2
import com.redrixdev.tikstik.core.designsystem.theme.Surface3
import com.redrixdev.tikstik.core.designsystem.theme.TextHigh
import com.redrixdev.tikstik.core.designsystem.theme.motion
import com.redrixdev.tikstik.core.designsystem.theme.navigationBarsStable
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Reading TikTok: with no [total] it is connecting or searching; then reading and, with [reviewing], reviewing. */
@Immutable
data class ReadingUi(val done: Int, val total: Int, val connected: Boolean, val reviewing: Boolean = false)

@Immutable
sealed interface DockMode {
    /** Main action: import or, when not set up, connect. */
    data class Idle(val connected: Boolean) : DockMode
    data class Reading(val reading: ReadingUi) : DockMode
    data class Selecting(val count: Int) : DockMode
}

private val DockHeight = 76.dp

/** Bottom dock: one floating piece that imports, turns into a bar while reading and selects, never changing size. */
@Composable
fun HomeDock(
    mode: DockMode,
    trays: List<TrayImage>,
    onImport: () -> Unit,
    onConnect: () -> Unit,
    onCancelRead: () -> Unit,
    onPacks: () -> Unit,
    onClearSelection: () -> Unit,
    onDelete: () -> Unit,
    onAddSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .windowInsetsPadding(WindowInsets.navigationBarsStable)
            .padding(start = 20.dp, end = 20.dp, bottom = 18.dp, top = 8.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .height(DockHeight)
            .drawBehind {
                // The logo's echoes behind the dock, offset to the bottom left.
                val r = CornerRadius(size.height / 2)
                val step = 5.dp.toPx()
                drawRoundRect(BrandViolet.copy(alpha = 0.55f), topLeft = Offset(-step * 2, step * 2), size = size, cornerRadius = r)
                drawRoundRect(BrandGreen.copy(alpha = 0.6f), topLeft = Offset(-step, step), size = size, cornerRadius = r)
            }
            .clip(RoundedCornerShape(DockHeight / 2))
            .background(Surface2)
            .padding(8.dp),
    ) {
        val fade = motion<Float>()
        val slide = motion(Motion.offset())
        AnimatedContent(
            targetState = mode,
            contentKey = { it::class },
            // Enters from below and leaves upward across the whole dock: clipped at its edge, not at the padding.
            transitionSpec = {
                (fadeIn(fade) + slideInVertically(slide) { it + it / 4 }) togetherWith
                    (fadeOut(fade) + slideOutVertically(slide) { -(it + it / 4) }) using SizeTransform(clip = false)
            },
            label = "dock",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            when (current) {
                is DockMode.Idle -> IdleRow(current.connected, trays, onImport, onConnect, onPacks)
                is DockMode.Reading -> ReadingRow(current.reading, trays, onCancelRead, onPacks)
                is DockMode.Selecting -> SelectingRow(current.count, onClearSelection, onDelete, onAddSelection)
            }
        }
    }
}

@Composable
private fun IdleRow(connected: Boolean, trays: List<TrayImage>, onImport: () -> Unit, onConnect: () -> Unit, onPacks: () -> Unit) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (connected) {
            MainPill(stringResource(R.string.home_import), TikStikIcons.Download, onImport, Modifier.weight(1f))
        } else {
            MainPill(stringResource(R.string.home_connect_action), TikStikIcons.Link, onConnect, Modifier.weight(1f))
        }
        PacksButton(trays, onPacks)
        // The square button's corner must not poke out of the dock's curve.
        Spacer(Modifier.width(6.dp))
    }
}

@Composable
private fun ReadingRow(reading: ReadingUi, trays: List<TrayImage>, onCancel: () -> Unit, onPacks: () -> Unit) {
    // Reading fills the first half of the bar and reviewing the second: it never sits still at 100 %.
    val fraction = if (reading.total == 0) null else (reading.done / reading.total.toFloat()) * 0.5f + if (reading.reviewing) 0.5f else 0f
    val animated by animateFloatAsState(fraction ?: 0f, motion(), label = "read")
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.weight(1f).height(PillHeight).clip(CircleShape).background(Surface3),
            contentAlignment = Alignment.Center,
        ) {
            if (fraction != null) {
                Canvas(Modifier.fillMaxSize()) { drawEchoFill(animated, CornerRadius(size.height / 2), 0f) }
            } else {
                val haptics = rememberHaptics()
                BrandProgressBar(null, Modifier.fillMaxSize(), height = PillHeight, track = Surface3, onEdge = haptics::edge)
            }
            PillLabel(
                when {
                    fraction != null && reading.reviewing -> stringResource(R.string.home_reviewing, reading.done, reading.total)
                    fraction != null -> stringResource(R.string.home_reading, reading.done, reading.total)
                    reading.connected -> stringResource(R.string.home_reading_search)
                    else -> stringResource(R.string.home_reading_start)
                },
                color = if (animated > 0.55f) OnGreen else TextHigh,
                modifier = Modifier.padding(horizontal = 56.dp),
            )
            IconButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).size(48.dp)) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(Ink.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                    Icon(TikStikIcons.Close, stringResource(R.string.action_cancel), tint = TextHigh, modifier = Modifier.size(18.dp))
                }
            }
        }
        Box(Modifier.graphicsLayer { alpha = 0.4f }) { PacksButton(trays, onPacks, enabled = false) }
        Spacer(Modifier.width(6.dp))
    }
}

@Composable
private fun SelectingRow(count: Int, onClear: () -> Unit, onDelete: () -> Unit, onAdd: () -> Unit) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DockIcon(TikStikIcons.Close, stringResource(R.string.action_clear_selection), onClear)
        DockIcon(TikStikIcons.DeleteOutline, stringResource(R.string.action_delete), onDelete, tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.weight(1f))
        CountPill(
            count = count,
            label = stringResource(R.string.selection_add),
            description = stringResource(R.string.selection_add_description),
            onClick = onAdd,
            modifier = Modifier.width(184.dp),
        )
    }
}

/** Main green pill: a single line; the text shrinks rather than wrapping. */
@Composable
private fun MainPill(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier
            .height(PillHeight)
            .clip(CircleShape)
            .background(BrandGreen)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            }
            .semantics { role = Role.Button }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = OnGreen, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        PillLabel(label, OnGreen)
    }
}

@Composable
private fun DockIcon(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color = TextHigh) {
    Box(
        Modifier
            .size(PillHeight)
            .clip(CircleShape)
            .background(Surface3)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint) }
}

/** Packs button: a pack's cover fills it and, with several, switches to a random other one every 2.8 s. */
@Composable
private fun PacksButton(trays: List<TrayImage>, onClick: () -> Unit, enabled: Boolean = true) {
    val description = stringResource(R.string.cd_packs)
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(trays.size) {
        shown = if (trays.isEmpty()) 0 else Random.nextInt(trays.size)
        if (trays.size < 2) return@LaunchedEffect
        while (true) {
            delay(2800)
            // Never the same one twice in a row.
            var next = Random.nextInt(trays.size - 1)
            if (next >= shown) next++
            shown = next
        }
    }
    Box(
        Modifier
            .size(PillHeight)
            .clip(RoundedCornerShape(18.dp))
            .background(Surface3)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        val tray = trays.getOrNull(shown)
        if (tray == null) {
            Icon(TikStikIcons.CollectionsBookmark, null, tint = TextHigh)
        } else {
            val appear = motion<Float>(tween(420))
            val leave = motion<Float>(tween(300))
            AnimatedContent(
                tray,
                transitionSpec = {
                    (fadeIn(appear) + scaleIn(appear, initialScale = 0.82f)) togetherWith (fadeOut(leave) + scaleOut(leave, targetScale = 1.1f))
                },
                label = "packsCover",
            ) { current ->
                // Slightly enlarged and clipped at the corners: no gap along the edges.
                AsyncImage(
                    model = rememberTrayRequest(current),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = 1.12f
                        scaleY = 1.12f
                    },
                )
            }
        }
    }
}
