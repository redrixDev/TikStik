// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.core.designsystem.brand.BrandMark
import com.redrixdev.tikstik.core.designsystem.icon.TikStikIcons
import com.redrixdev.tikstik.core.designsystem.theme.BrandGreen
import com.redrixdev.tikstik.core.designsystem.theme.BrandViolet
import com.redrixdev.tikstik.core.designsystem.theme.LocalReduceMotion
import com.redrixdev.tikstik.core.designsystem.theme.Surface3
import com.redrixdev.tikstik.core.designsystem.theme.TextHigh
import com.redrixdev.tikstik.core.designsystem.theme.statusBarsStable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.abs

/** What a notice's button does; it only exists when it leads somewhere useful. */
enum class NoticeAction(@StringRes val label: Int) {
    CONNECT(R.string.action_connect),
    OPEN_WIRELESS_DEBUGGING(R.string.action_open_settings),
}

@Immutable
data class Notice(val text: UiText, val action: NoticeAction? = null, val error: Boolean = false)

/** One at a time, never repeating the one shown or waiting its turn; the timer pauses while touched. */
@Composable
fun NoticeHost(notices: Flow<Notice>, onAction: (NoticeAction) -> Unit, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val queue = remember { Channel<Pair<String, Notice>>(Channel.UNLIMITED) }
    var current by remember { mutableStateOf<Pair<String, Notice>?>(null) }
    var shown by remember { mutableIntStateOf(0) }
    val waiting = remember { mutableSetOf<String>() }
    LaunchedEffect(notices) {
        notices.collect { notice ->
            val text = notice.text.resolve(resources)
            if (text != current?.first && waiting.add(text)) queue.send(text to notice)
        }
    }
    LaunchedEffect(current) {
        if (current == null) {
            val next = queue.receive()
            waiting.remove(next.first)
            shown++
            current = next
        }
    }
    Box(
        // On the left, 16 dp minus the echoes, which live inside the notice's layer.
        modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBarsStable).padding(start = 16.dp - ECHO, end = 16.dp, top = 8.dp, bottom = 8.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        current?.let { (text, notice) ->
            key(shown) { Banner(text, notice, onAction, onGone = { current = null }) }
        }
    }
}

@Composable
private fun Banner(text: String, notice: Notice, onAction: (NoticeAction) -> Unit, onGone: () -> Unit) {
    val density = LocalDensity.current
    val reduce = LocalReduceMotion.current
    val hidden = with(density) { -140.dp.toPx() }
    val y = remember { Animatable(if (reduce) 0f else hidden) }
    val x = remember { Animatable(0f) }
    val alpha = remember { Animatable(if (reduce) 1f else 0f) }
    var touching by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val enter = spring<Float>(dampingRatio = 0.72f, stiffness = 420f)
    val scope = rememberCoroutineScope()
    val accessibility = LocalAccessibilityManager.current

    // Once it is leaving, drags no longer count: when they cut its exit short, it stayed stuck at the top.
    suspend fun leave(toX: Float = 0f, toY: Float = hidden) {
        if (leaving) return
        leaving = true
        try {
            coroutineScope {
                launch { alpha.animateTo(0f, spring(stiffness = 600f)) }
                launch { x.animateTo(toX, spring(stiffness = 500f)) }
                y.animateTo(toY, spring(stiffness = 500f))
            }
        } finally {
            onGone()
        }
    }

    LaunchedEffect(Unit) {
        // The entrance runs on its own: a drag that cuts it short no longer takes the closing timer down with it.
        launch { alpha.animateTo(1f, spring(stiffness = 500f)) }
        launch { y.animateTo(0f, enter) }
        val base = if (notice.action != null || notice.error) 4500L else 3000L
        // Whoever asked for more time in "Time to take action" gets it.
        var left = accessibility?.calculateRecommendedTimeoutMillis(base, containsIcons = true, containsText = true, containsControls = notice.action != null) ?: base
        while (left > 0) {
            delay(100)
            if (!touching) left -= 100
        }
        leave()
    }

    val swipeDismiss = with(density) { 56.dp.toPx() }
    val maxPull = with(density) { 24.dp.toPx() }
    Row(
        Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .graphicsLayer {
                translationX = x.value
                translationY = y.value
                this.alpha = alpha.value
                rotationZ = x.value / 60f
            }
            .pointerInput(Unit) {
                coroutineScope {
                    detectDragGestures(
                        onDragStart = { if (!leaving) touching = true },
                        onDragEnd = {
                            touching = false
                            if (!leaving) launch {
                                when {
                                    y.value < -swipeDismiss -> leave(toX = x.value)
                                    abs(x.value) > swipeDismiss * 2 -> leave(toX = x.value * 4f, toY = y.value)
                                    else -> {
                                        launch { x.animateTo(0f, enter) }
                                        y.animateTo(0f, enter)
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            touching = false
                            if (!leaving) launch {
                                launch { x.animateTo(0f, enter) }
                                y.animateTo(0f, enter)
                            }
                        },
                    ) { change, drag ->
                        change.consume()
                        if (!leaving) launch {
                            x.snapTo(x.value + drag.x)
                            // Pulling down resists (it stretches little); pulling up dismisses.
                            y.snapTo((y.value + if (drag.y > 0) drag.y * 0.25f else drag.y).coerceAtMost(maxPull))
                        }
                    }
                }
            }
            // The echoes live inside the layer: entering with transparency, it was painted clipped to the bubble's size.
            .padding(start = ECHO, bottom = ECHO)
            .drawBehind {
                // Logo echoes behind the bubble, as in the dock.
                val r = CornerRadius(22.dp.toPx())
                val step = 4.dp.toPx()
                drawRoundRect(BrandViolet.copy(alpha = 0.5f), topLeft = Offset(-step * 2, step * 2), size = size, cornerRadius = r)
                drawRoundRect(BrandGreen.copy(alpha = 0.55f), topLeft = Offset(-step, step), size = size, cornerRadius = r)
            }
            .clip(RoundedCornerShape(22.dp))
            .background(Surface3)
            .heightIn(min = 60.dp)
            .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (notice.error) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Icon(TikStikIcons.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) }
        } else {
            BrandMark(Modifier.size(36.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextHigh, modifier = Modifier.weight(1f).padding(end = 4.dp))
        notice.action?.let { action ->
            Box(
                Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button) {
                        onAction(action)
                        scope.launch { leave() }
                    }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(action.label), style = MaterialTheme.typography.labelLarge, color = BrandGreen) }
        }
    }
}

// How far the two echoes stick out from the bubble (two 4 dp steps).
private val ECHO = 8.dp
