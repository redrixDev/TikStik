// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.redrixdev.tikstik.core.designsystem.brand.brandFullPath
import com.redrixdev.tikstik.core.designsystem.theme.BrandGreen
import com.redrixdev.tikstik.core.designsystem.theme.BrandPaper
import com.redrixdev.tikstik.core.designsystem.theme.BrandViolet
import com.redrixdev.tikstik.core.designsystem.theme.LocalReduceMotion
import com.redrixdev.tikstik.core.designsystem.theme.Surface3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val GreenLight = Color(0xFF7BEDA6)

/** Continuous phase (0..1) for shines and waves; read while drawing, so it does not recompose per frame. */
@Composable
private fun rememberPhase(periodMs: Int): State<Float>? {
    if (LocalReduceMotion.current) return null
    return rememberInfiniteTransition("phase").animateFloat(
        0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart), label = "p",
    )
}

/** With [progress] null it is indeterminate; [onEdge] fires every time the segment touches an edge. */
@Composable
fun BrandProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
    track: Color = Surface3,
    onEdge: (() -> Unit)? = null,
) {
    val target = progress?.coerceIn(0f, 1f) ?: 0f
    val animated = animateFloatAsState(target, spring(dampingRatio = 0.9f, stiffness = 120f), label = "progress")
    val phase = rememberPhase(if (progress == null) 1400 else 1600)
    if (progress == null && onEdge != null && phase != null) {
        val edge by rememberUpdatedState(onEdge)
        LaunchedEffect(phase) {
            // Phase 0: left edge; 0.5: right edge.
            var last = phase.value
            snapshotFlow { phase.value }.collect { v ->
                if ((last < 0.5f && v >= 0.5f) || v < last) edge()
                last = v
            }
        }
    }
    Canvas(
        modifier
            .height(height)
            .semantics { if (progress != null) progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f) },
    ) {
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(track, cornerRadius = r)
        val t = phase?.value ?: 0f
        if (progress == null) {
            // Back and forth, faster in the middle and slower at the edges; the trail stretches with the speed.
            val p = (1f - cos(t * 2f * PI.toFloat())) / 2f
            val w = size.width * 0.34f
            val x = (size.width - w) * p
            val velocity = sin(t * 2f * PI.toFloat())
            drawEchoSegment(x, w, r, behind = -velocity, shine = t)
        } else {
            drawEchoFill(animated.value, r, t)
        }
    }
}

/** Fill with the echoes peeking out ahead of it and a shine running across it. */
fun DrawScope.drawEchoFill(fraction: Float, r: CornerRadius, phase: Float) {
    if (fraction <= 0f) return
    val w = (size.width * fraction).coerceAtLeast(size.height)
    val step = size.height * 0.42f
    // The echoes tuck in as the fill reaches the end, instead of vanishing at once.
    val reach = step * ((1f - fraction) / 0.08f).coerceIn(0f, 1f)
    if (reach > 0f) {
        drawRoundRect(BrandViolet, size = Size((w + reach * 2).coerceAtMost(size.width), size.height), cornerRadius = r)
        drawRoundRect(BrandPaper, size = Size((w + reach).coerceAtMost(size.width), size.height), cornerRadius = r)
    }
    drawRoundRect(Brush.horizontalGradient(listOf(BrandGreen, GreenLight), endX = w), size = Size(w, size.height), cornerRadius = r)
    shine(0f, w, r, phase)
}

/** [behind]: which way the trail lies and how far (-1..1; 0, tucked under the segment). */
private fun DrawScope.drawEchoSegment(x: Float, w: Float, r: CornerRadius, behind: Float, shine: Float) {
    val step = size.height * 0.5f
    translate(left = x + behind * step * 2) { drawRoundRect(BrandViolet.copy(alpha = 0.85f), size = Size(w, size.height), cornerRadius = r) }
    translate(left = x + behind * step) { drawRoundRect(BrandPaper, size = Size(w, size.height), cornerRadius = r) }
    translate(left = x) {
        drawRoundRect(Brush.horizontalGradient(listOf(BrandGreen, GreenLight), endX = w), size = Size(w, size.height), cornerRadius = r)
    }
    shine(x, w, r, shine)
}

/** Diagonal reflection running across the fill. */
private fun DrawScope.shine(x: Float, w: Float, r: CornerRadius, phase: Float) {
    val band = size.height * 3f
    val cx = x - band + (w + band * 2) * phase
    val clip = Path().apply { addRoundRect(RoundRect(x, 0f, x + w, size.height, r)) }
    clipPath(clip) {
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                start = Offset(cx - band / 2, 0f),
                end = Offset(cx + band / 2, size.height),
            ),
        )
    }
}

/** The logo bubble fills with wavy liquid; with [progress] null, the level slowly rises and falls. */
@Composable
fun BubbleFillProgress(progress: Float?, modifier: Modifier = Modifier) {
    val shape = remember { brandFullPath() }
    val bounds = remember(shape) { shape.getBounds() }
    val target = progress?.coerceIn(0f, 1f)
    val level = animateFloatAsState(target ?: 0.5f, spring(dampingRatio = 0.85f, stiffness = 90f), label = "level")
    val phase = rememberPhase(1800)
    val breathe = if (target == null) phase else null
    Canvas(modifier.semantics { if (target != null) progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f) }) {
        val s = minOf(size.width / bounds.width, size.height / bounds.height) * 0.92f
        val t = phase?.value ?: 0f
        translate((size.width - bounds.width * s) / 2f - bounds.left * s, (size.height - bounds.height * s) / 2f - bounds.top * s) {
            scale(s, s, Offset.Zero) {
                drawPath(shape, Surface3)
                clipPath(shape) {
                    val fill = if (breathe != null) 0.35f + 0.3f * sin(breathe.value * 2f * PI.toFloat()) else level.value
                    val top = bounds.bottom - (bounds.height + 6f) * fill
                    drawWave(bounds.left, bounds.right, top - 2.2f, 2.4f, t + 0.35f, BrandViolet)
                    drawWave(bounds.left, bounds.right, top, 2.2f, t, BrandGreen)
                }
            }
        }
    }
}

private fun DrawScope.drawWave(left: Float, right: Float, top: Float, amp: Float, phase: Float, color: Color) {
    val path = Path()
    val bottom = top + 200f
    path.moveTo(left - 2f, bottom)
    var x = left - 2f
    while (x <= right + 2f) {
        path.lineTo(x, top + amp * sin(x / 6f + phase * 2f * PI.toFloat()))
        x += 1f
    }
    path.lineTo(right + 2f, bottom)
    path.close()
    drawPath(path, color)
}
