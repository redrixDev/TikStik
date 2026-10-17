// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.welcome

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.core.designsystem.ScreenPreview
import com.redrixdev.tikstik.core.designsystem.brand.brandFullPath
import com.redrixdev.tikstik.core.designsystem.component.PrimaryButton
import com.redrixdev.tikstik.core.designsystem.component.QuietButton
import com.redrixdev.tikstik.core.designsystem.icon.TikStikIcons
import com.redrixdev.tikstik.core.designsystem.rememberHaptics
import com.redrixdev.tikstik.core.designsystem.theme.BrandGreen
import com.redrixdev.tikstik.core.designsystem.theme.BrandPaper
import com.redrixdev.tikstik.core.designsystem.theme.BrandViolet
import com.redrixdev.tikstik.core.designsystem.theme.Ink
import com.redrixdev.tikstik.core.designsystem.theme.LocalReduceMotion
import com.redrixdev.tikstik.core.designsystem.theme.Outline
import com.redrixdev.tikstik.core.designsystem.theme.Surface1
import com.redrixdev.tikstik.core.designsystem.theme.Surface2
import com.redrixdev.tikstik.core.designsystem.theme.Surface3
import com.redrixdev.tikstik.core.designsystem.theme.TextMid
import com.redrixdev.tikstik.core.designsystem.theme.TikStikTheme
import com.redrixdev.tikstik.core.designsystem.theme.Warning
import com.redrixdev.tikstik.core.designsystem.theme.motion
import com.redrixdev.tikstik.core.designsystem.theme.safeDrawingStable
import kotlinx.coroutines.launch

private const val PAGES = 2

// "Next" with the emphasized curve in 360 ms: at 480 its long tail looked slow.
private val PageSlide = tween<Float>(360, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))

/** [legacyStorage]: Android 8-10 asks for one permission instead of pairing, and the second page says so. */
@Composable
fun WelcomeScreen(onStart: () -> Unit, legacyStorage: Boolean = false) {
    val pager = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PAGES - 1
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawingStable), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterEnd) {
            if (!last) QuietButton(stringResource(R.string.action_skip), onStart)
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page -> WelcomePage(page, pager, legacyStorage) }
        Dots(pager, Modifier.padding(vertical = 20.dp))
        Box(Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
            PrimaryButton(
                text = "",
                onClick = {
                    if (last) onStart() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1, animationSpec = PageSlide) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            // Only the text changes, with a fade: the button never changes size.
            AnimatedContent(
                last,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "cta",
                modifier = Modifier.align(Alignment.Center),
            ) { isLast ->
                Text(
                    stringResource(if (isLast) R.string.action_start else R.string.action_next),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun WelcomePage(page: Int, pager: PagerState, legacyStorage: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val compact = maxHeight < 560.dp
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The texts are measured first and the drawing takes the remaining height: on short screens it shrinks instead of overlapping them.
            Box(
                Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .heightIn(max = if (compact) 220.dp else 320.dp)
                    .aspectRatio(1.2f, matchHeightConstraintsFirst = true),
                contentAlignment = Alignment.Center,
            ) {
                if (page == 0) {
                    Column(Modifier.fillMaxSize()) {
                        TransferIllustration(Modifier.weight(1f).fillMaxWidth())
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            PanelLabel(stringResource(R.string.tiktok_name), Modifier.weight(0.4f))
                            Spacer(Modifier.weight(0.18f))
                            PanelLabel(stringResource(R.string.wa_consumer), Modifier.weight(0.42f))
                        }
                    }
                } else {
                    // Only vibrates while this page is in view and still.
                    SetupIllustration(Modifier.fillMaxSize(), active = pager.currentPage == page && !pager.isScrollInProgress, legacy = legacyStorage)
                }
            }
            Spacer(Modifier.height(if (compact) 20.dp else 36.dp))
            Text(
                stringResource(if (page == 0) R.string.welcome_1_title else R.string.welcome_2_title),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(
                    when {
                        page == 0 -> R.string.welcome_1_body
                        legacyStorage -> R.string.welcome_2_body_legacy
                        else -> R.string.welcome_2_body
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = TextMid,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PanelLabel(text: String, modifier: Modifier) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = TextMid, textAlign = TextAlign.Center, modifier = modifier)
}

@Composable
private fun loop(periodMs: Int): State<Float>? {
    if (LocalReduceMotion.current) return null
    return rememberInfiniteTransition("loop").animateFloat(
        0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
}

private fun seg(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)
private fun ease(x: Float) = 1f - (1f - x) * (1f - x) * (1f - x)

// Smooth start and stop (smootherstep).
private fun smooth(x: Float) = x * x * x * (x * (x * 6f - 15f) + 10f)

/** What the app does, without words: a sticker peels off the panel and flies with its trail of echoes into a chat. */
@Composable
private fun TransferIllustration(modifier: Modifier) {
    val bubble = remember { brandFullPath() }
    val bounds = remember(bubble) { bubble.getBounds() }
    val time = loop(3000)
    val description = stringResource(R.string.welcome_1_title)
    Canvas(modifier.semantics { contentDescription = description }) {
        val t = time?.value ?: 0.8f
        val w = size.width
        val h = size.height
        val panelW = w * 0.4f
        val panelH = h * 0.78f
        val top = (h - panelH) / 2f
        val r = CornerRadius(22.dp.toPx())
        // Sticker panel: a 2 × 3 grid.
        drawRoundRect(Surface1, Offset(0f, top), Size(panelW, panelH), r)
        val cell = panelW * 0.36f
        val gap = (panelW - cell * 2) / 3f
        val tileColors = listOf(BrandGreen, BrandViolet, BrandPaper, Warning, BrandViolet, BrandGreen)
        val tiles = (0 until 6).map { i -> Offset(gap + (i % 2) * (cell + gap), top + gap + (i / 2) * (cell + gap * 0.9f)) }
        tiles.forEachIndexed { i, o ->
            drawRoundRect(Surface2, o, Size(cell, cell), CornerRadius(12.dp.toPx()))
            if (i != 0) drawCircle(tileColors[i].copy(alpha = 0.85f), cell * 0.22f, o + Offset(cell / 2, cell / 2))
        }
        // Chat: two messages and the gap where the sticker lands.
        val chatX = w * 0.58f
        val chatW = w - chatX
        drawRoundRect(Surface1, Offset(chatX, top), Size(chatW, panelH), r)
        val pad = chatW * 0.1f
        drawRoundRect(Surface3, Offset(chatX + pad, top + pad), Size(chatW * 0.62f, cell * 0.42f), CornerRadius(cell * 0.21f))
        drawRoundRect(Surface3, Offset(chatX + pad, top + pad * 1.6f + cell * 0.42f), Size(chatW * 0.45f, cell * 0.42f), CornerRadius(cell * 0.21f))
        val land = Offset(chatX + chatW - pad - cell * 0.6f, top + panelH - pad - cell * 0.62f)
        val start = tiles[0] + Offset(cell / 2, cell / 2)

        // Lifts off (0.05–0.18), flies (0.18–0.64), lands (0.64–0.76) and waits.
        val lift = ease(seg(t, 0.05f, 0.18f))
        fun flyAt(time: Float) = smooth(seg(time, 0.18f, 0.64f))
        val fly = flyAt(t)
        val landed = seg(t, 0.64f, 0.76f)
        val fade = 1f - seg(t, 0.9f, 1f)
        val stickerSize = cell * (0.62f + 0.18f * lift - 0.12f * landed)
        fun at(p: Float): Offset {
            val c = Offset((start.x + land.x) / 2f, top - h * 0.08f)
            return start * ((1 - p) * (1 - p)) + c * (2 * (1 - p) * p) + land * (p * p)
        }
        // Each echo is the sticker a moment earlier; once landed, they settle behind it like the logo's layers.
        val relief = smooth(seg(t, 0.72f, 0.88f))
        val echoAlpha = seg(t, 0.18f, 0.26f) * fade
        val step = stickerSize * 0.075f
        fun echo(delay: Float, layers: Float) = at(flyAt(t - delay)) + Offset(-step, -step) * (layers * relief)
        drawSticker(bubble, bounds, echo(0.07f, 2f), stickerSize, BrandViolet.copy(alpha = 0.7f * echoAlpha), null)
        drawSticker(bubble, bounds, echo(0.035f, 1f), stickerSize, BrandGreen.copy(alpha = 0.8f * echoAlpha), null)
        drawSticker(bubble, bounds, at(fly), stickerSize, BrandPaper.copy(alpha = fade), BrandGreen.copy(alpha = fade))
    }
}

/** The brand's bubble sticker, centred on [c] with side [side]. */
private fun DrawScope.drawSticker(shape: Path, b: Rect, c: Offset, side: Float, fill: Color, outline: Color?) {
    val s = side / maxOf(b.width, b.height)
    translate(c.x - b.center.x * s, c.y - b.center.y * s) {
        scale(s, s, Offset.Zero) {
            if (outline != null) {
                drawPath(shape, outline)
                scale(0.86f, 0.86f, b.center) { drawPath(shape, fill) }
            } else {
                drawPath(shape, fill)
            }
        }
    }
}

/** The steps light up in turn and each one is felt in the hand: click, double click and celebration. */
@Composable
private fun SetupIllustration(modifier: Modifier, active: Boolean, legacy: Boolean) {
    val time = loop(3600)
    val haptics = rememberHaptics()
    val feel = if (legacy) listOf(haptics::activate, haptics::celebrate) else listOf(haptics::activate, haptics::link, haptics::celebrate)
    val steps = if (legacy) {
        listOf(TikStikIcons.Folder to R.string.welcome_2_step_allow, TikStikIcons.Check to R.string.welcome_2_step3)
    } else {
        listOf(
            TikStikIcons.DeveloperMode to R.string.welcome_2_step1,
            TikStikIcons.Link to R.string.welcome_2_step2,
            TikStikIcons.Check to R.string.welcome_2_step3,
        )
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(BrandViolet.copy(alpha = 0.18f), Color.Transparent)), size.minDimension / 2)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
            steps.forEachIndexed { i, (icon, label) ->
                val lit = time?.value?.let { it > 0.12f + i * 0.25f } ?: true
                StepNode(icon, stringResource(label), lit, last = i == steps.lastIndex)
                LaunchedEffect(lit) { if (lit && active && time != null) feel[i]() }
            }
        }
    }
}

@Composable
private fun StepNode(icon: ImageVector, label: String, lit: Boolean, last: Boolean) {
    val background by animateColorAsState(if (lit) (if (last) BrandGreen else BrandViolet) else Surface2, motion(), label = "node")
    val content by animateColorAsState(if (lit) Ink else TextMid, motion(), label = "nodeFg")
    val scale by animateFloatAsState(if (lit) 1f else 0.86f, motion(), label = "nodeScale")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .size(72.dp)
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = content, modifier = Modifier.size(32.dp)) }
        Spacer(Modifier.height(12.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** Page indicator: the current dot stretches with a spring. */
@Composable
private fun Dots(pager: PagerState, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.welcome_page, pager.currentPage + 1, PAGES)
    Row(modifier.semantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(PAGES) { i ->
            val current = pager.currentPage == i
            val width by animateDpAsState(if (current) 26.dp else 8.dp, motion(), label = "dot")
            val color by animateColorAsState(if (current) BrandGreen else Outline, label = "dotColor")
            Box(Modifier.size(width, 8.dp).clip(CircleShape).background(color))
        }
    }
}

@ScreenPreview
@Composable
private fun WelcomePreview() {
    TikStikTheme { WelcomeScreen(onStart = {}) }
}
