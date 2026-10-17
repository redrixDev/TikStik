// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.howto

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.core.designsystem.ScreenPreview
import com.redrixdev.tikstik.core.designsystem.brand.BrandMark
import com.redrixdev.tikstik.core.designsystem.component.PrimaryButton
import com.redrixdev.tikstik.core.designsystem.component.TopBar
import com.redrixdev.tikstik.core.designsystem.icon.TikStikIcons
import com.redrixdev.tikstik.core.designsystem.theme.BrandGreen
import com.redrixdev.tikstik.core.designsystem.theme.Ink
import com.redrixdev.tikstik.core.designsystem.theme.LocalReduceMotion
import com.redrixdev.tikstik.core.designsystem.theme.OnGreen
import com.redrixdev.tikstik.core.designsystem.theme.Surface1
import com.redrixdev.tikstik.core.designsystem.theme.Surface2
import com.redrixdev.tikstik.core.designsystem.theme.Surface3
import com.redrixdev.tikstik.core.designsystem.theme.TextHigh
import com.redrixdev.tikstik.core.designsystem.theme.TextMid
import com.redrixdev.tikstik.core.designsystem.theme.TikStikTheme
import com.redrixdev.tikstik.core.designsystem.theme.navigationBarsStable
import com.redrixdev.tikstik.core.designsystem.theme.safeDrawingStable

private const val LOOP_MS = 9000

// The loop: the sticker on TikTok, tapping Import and picking it in the review until it shows first in the collection.
private const val STEP_2 = 0.3f
private const val STEP_3 = 0.52f

private val steps = listOf(
    R.string.howto_1_title to R.string.howto_1_body,
    R.string.howto_2_title to R.string.howto_2_body,
    R.string.howto_3_title to R.string.howto_3_body,
)

/** How a sticker seen on TikTok reaches TikStik, with an animated phone and the three steps in sync; a null [onBack] hides the arrow. */
@Composable
fun HowToScreen(onBack: (() -> Unit)?, onDone: () -> Unit, onHelp: (() -> Unit)? = null) {
    val reduce = LocalReduceMotion.current
    val time = if (reduce) null else rememberInfiniteTransition("howto").animateFloat(
        0f, 1f, infiniteRepeatable(tween(LOOP_MS, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    // Read at draw time, not at composition; with animations off, frozen at the end: the sticker already in the collection.
    val t: () -> Float = { time?.value ?: 0.95f }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawingStable)) {
        // Top right, like "Restore" on Pro: the help video in the app's language.
        TopBar(null, onBack) {
            if (onHelp != null) {
                TextButton(onClick = onHelp, shape = CircleShape) {
                    Text(stringResource(R.string.howto_help), style = MaterialTheme.typography.labelMedium, color = TextMid)
                }
            }
        }
        // Everything on one screen, no scrolling: the phone takes the height the steps leave.
        Column(
            Modifier
                .weight(1f)
                .align(Alignment.CenterHorizontally)
                .widthIn(max = 560.dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (maxHeight >= 140.dp) {
                    // A real phone's proportions (19.5:9).
                    PhoneMock(t, Modifier.padding(vertical = 8.dp).heightIn(max = 320.dp).fillMaxHeight().aspectRatio(9f / 19.5f, matchHeightConstraintsFirst = true))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.howto_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(12.dp))
            val active by remember(time) { derivedStateOf { if (time == null) -1 else if (t() < STEP_2) 0 else if (t() < STEP_3) 1 else 2 } }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                steps.forEachIndexed { index, (title, body) -> Step(index + 1, title, body, active = index == active) }
            }
        }
        PrimaryButton(
            stringResource(R.string.howto_done),
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).align(Alignment.CenterHorizontally).windowInsetsPadding(WindowInsets.navigationBarsStable).padding(20.dp),
        )
    }
}

@Composable
private fun Step(number: Int, @StringRes title: Int, @StringRes body: Int, active: Boolean) {
    val background by animateColorAsState(if (active) Surface2 else Surface1, label = "step")
    val badge by animateColorAsState(if (active) BrandGreen else Surface3, label = "badge")
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(background).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(badge), contentAlignment = Alignment.Center) {
            Text("$number", style = MaterialTheme.typography.titleSmall, color = if (active) OnGreen else TextHigh)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = TextMid)
        }
    }
}

private fun seg(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

// Only changes when a threshold is crossed, so what appears or disappears recomposes once, not every frame.
@Composable
private fun whileAbove(t: () -> Float, value: (Float) -> Float): Boolean {
    val visible by remember { derivedStateOf { value(t()) > 0f } }
    return visible
}

// Decorative: TalkBack reads the steps below.
@Composable
private fun PhoneMock(t: () -> Float, modifier: Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .clip(shape)
            .background(Ink)
            .border(3.dp, Surface3, shape)
            .padding(horizontal = 8.dp, vertical = 12.dp)
            .clearAndSetSemantics { },
    ) {
        // One leaves and then the other enters: crossfaded, the sticker sat over the grid for a moment.
        val tiktok = { x: Float -> 1f - seg(x, STEP_2 - 0.04f, STEP_2 - 0.02f) }
        val app = { x: Float -> seg(x, STEP_2 - 0.02f, STEP_2) * (1f - seg(x, 0.97f, 1f)) }
        // Offscreen: with per-piece transparency, what lay behind each piece showed through.
        if (whileAbove(t, tiktok)) TikTokFrame(t, Modifier.graphicsLayer { alpha = tiktok(t()); compositingStrategy = CompositingStrategy.Offscreen })
        if (whileAbove(t, app)) TikStikFrame(t, Modifier.graphicsLayer { alpha = app(t()); compositingStrategy = CompositingStrategy.Offscreen })
    }
}

/** Step 1: a comment with the sticker, which appears and stays in TikTok's cache. */
@Composable
private fun TikTokFrame(t: () -> Float, modifier: Modifier) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.howto_mock_tiktok), style = MaterialTheme.typography.labelMedium, color = TextHigh, modifier = Modifier.padding(4.dp))
        Comment(lines = 2)
        Comment(lines = 1) {
            Sticker(Modifier.padding(top = 6.dp).fillMaxWidth(0.5f).aspectRatio(1f).popIn { seg(t(), 0.06f, 0.14f) })
        }
        Comment(lines = 2)
        Comment(lines = 1)
        Comment(lines = 2)
        Comment(lines = 1)
    }
}

@Composable
private fun Comment(lines: Int, extra: @Composable () -> Unit = {}) {
    Row {
        Box(Modifier.size(16.dp).clip(CircleShape).background(Surface3))
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.fillMaxWidth(0.4f).height(6.dp).clip(CircleShape).background(Surface3))
            repeat(lines) { Box(Modifier.fillMaxWidth(if (it == 0) 0.95f else 0.7f).height(6.dp).clip(CircleShape).background(Surface2)) }
            extra()
        }
    }
}

/** Steps 2 and 3: Import tapped; then the review slides up, the sticker is picked, added and shows up first. */
@Composable
private fun TikStikFrame(t: () -> Float, modifier: Modifier) {
    Box(modifier.fillMaxSize()) {
        Home(t)
        val shown = { x: Float -> seg(x, STEP_3, STEP_3 + 0.06f) * (1f - seg(x, 0.8f, 0.86f)) }
        if (whileAbove(t, shown)) ReviewSheet(t, Modifier.align(Alignment.BottomCenter).graphicsLayer { translationY = (1f - shown(t())) * size.height })
    }
}

@Composable
private fun Home(t: () -> Float) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandMark(Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelMedium, color = TextHigh)
        }
        Spacer(Modifier.height(8.dp))
        val arrive = { x: Float -> seg(x, 0.86f, 0.92f) }
        val arrived = whileAbove(t, arrive)
        Grid(rows = 4) { index ->
            if (index == 0 && arrived) Sticker(Modifier.fillMaxSize().popIn { arrive(t()) })
        }
        Spacer(Modifier.weight(1f))
        // The tap on Import: the button sinks and a circle appears.
        GreenButton(R.string.howto_mock_import, press = { seg(t(), 0.4f, 0.44f) * (1f - seg(t(), 0.47f, 0.5f)) }, icon = true)
    }
}

/** The import review: the sticker gets checked and Add is tapped. */
@Composable
private fun ReviewSheet(t: () -> Float, modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .fillMaxHeight(0.6f)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(Surface2)
            .padding(8.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).width(28.dp).height(3.dp).clip(CircleShape).background(Surface3))
        Text(stringResource(R.string.howto_mock_review), style = MaterialTheme.typography.labelMedium, color = TextHigh, modifier = Modifier.padding(vertical = 6.dp))
        val picked = whileAbove(t) { seg(it, 0.64f, 0.66f) }
        Grid(rows = 1) { index ->
            if (index == 0) {
                Sticker(Modifier.fillMaxSize())
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (picked) BrandGreen else Ink.copy(alpha = 0.5f))
                        .border(1.dp, if (picked) Color.Transparent else Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (picked) Icon(TikStikIcons.Check, null, tint = OnGreen, modifier = Modifier.size(9.dp)) }
                Tap { seg(t(), 0.62f, 0.64f) * (1f - seg(t(), 0.67f, 0.7f)) }
            }
        }
        Spacer(Modifier.weight(1f))
        GreenButton(R.string.howto_mock_add, press = { seg(t(), 0.74f, 0.77f) * (1f - seg(t(), 0.79f, 0.81f)) }, icon = false)
    }
}

// The green button being tapped: it sinks and a circle appears.
@Composable
private fun GreenButton(@StringRes label: Int, press: () -> Float, icon: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .graphicsLayer {
                scaleX = 1f - 0.05f * press()
                scaleY = 1f - 0.05f * press()
            }
            .clip(CircleShape)
            .background(BrandGreen),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon) {
                Icon(TikStikIcons.Download, null, tint = OnGreen, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = OnGreen, maxLines = 1)
        }
        Tap(press)
    }
}

@Composable
private fun Grid(rows: Int, cell: @Composable BoxScope.(Int) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(rows) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { column ->
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(Surface1)) { cell(row * 3 + column) }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.Tap(strength: () -> Float) {
    Canvas(Modifier.matchParentSize()) {
        val s = strength()
        if (s > 0f) drawCircle(Color.White.copy(alpha = 0.4f * s), 13.dp.toPx() * (0.6f + s), center = center)
    }
}

// Overshoots a little and settles, like a sticker being stuck on; in its own layer so it does not fade piece by piece.
private fun Modifier.popIn(progress: () -> Float) = graphicsLayer {
    val p = progress()
    val scale = if (p < 0.7f) p / 0.7f * 1.12f else 1.12f - 0.12f * ((p - 0.7f) / 0.3f)
    scaleX = scale
    scaleY = scale
    alpha = p
    compositingStrategy = CompositingStrategy.Offscreen
}

@Composable
private fun Sticker(modifier: Modifier) {
    AsyncImage(
        model = R.raw.howto_sticker,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(percent = 18)),
    )
}

@ScreenPreview
@Composable
private fun HowToPreview() {
    TikStikTheme { HowToScreen(onBack = {}, onDone = {}) }
}
