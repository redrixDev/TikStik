// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.packs

import android.graphics.PathMeasure
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.core.designsystem.theme.BrandGreen
import com.redrixdev.tikstik.core.designsystem.theme.BrandViolet
import com.redrixdev.tikstik.core.designsystem.theme.LocalReduceMotion
import kotlin.math.PI
import kotlin.math.sin

private val SlotCorner = 20.dp
private val HoleFill = Color(0xFF060608)

/** A free slot in the pack: the hole is drawn by the grid background ([drawHole]); tapping it opens "Add stickers". */
@Composable
fun EmptySlot(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.slot_add)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(SlotCorner))
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
    )
}

/** A sunken slot: darker than the background, with an inner shadow at the top and left, a glint at the bottom and a fine texture. */
fun DrawScope.drawHole(topLeft: Offset, size: Size) {
    val r = CornerRadius(SlotCorner.toPx())
    val hole = Path().apply { addRoundRect(RoundRect(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height, r)) }
    clipPath(hole) {
        drawRect(HoleFill, topLeft, size)
        val step = 7.dp.toPx()
        val dot = 0.9.dp.toPx()
        var y = topLeft.y + step / 2
        var row = 0
        while (y < topLeft.y + size.height) {
            var x = topLeft.x + step / 2 + if (row % 2 == 0) 0f else step / 2
            while (x < topLeft.x + size.width) {
                drawCircle(Color.White.copy(alpha = 0.035f), dot, Offset(x, y))
                x += step
            }
            y += step
            row++
        }
        drawRect(
            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent), startY = topLeft.y, endY = topLeft.y + size.height * 0.28f),
            topLeft,
            size,
        )
        drawRect(
            Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), startX = topLeft.x, endX = topLeft.x + size.width * 0.18f),
            topLeft,
            size,
        )
    }
    // Light comes from above: a faint glint on the bottom edge.
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.08f)), startY = topLeft.y, endY = topLeft.y + size.height),
        topLeft,
        size,
        r,
        style = Stroke(1.dp.toPx()),
    )
}

/** Phase of the cover's waves (0..1, looping); null with animations off. */
@Composable
fun rememberCoverPhase(): State<Float>? {
    if (LocalReduceMotion.current) return null
    return rememberInfiniteTransition("cover").animateFloat(
        0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "waves",
    )
}

/** [appear] (0→1, with a bounce) is read at draw time, without recomposing. */
fun Modifier.coverFrame(
    enabled: Boolean,
    phase: State<Float>?,
    appear: () -> Float,
    corner: Dp = SlotCorner,
    step: Dp = 6.dp,
): Modifier =
    if (!enabled) this else drawWithCache {
        // The outline is sampled once per size: position and normal at each point.
        val r = corner.toPx()
        val base = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, r, r)) }
        val measure = PathMeasure(base.asAndroidPath(), true)
        val length = measure.length
        val n = 200
        val pos = FloatArray(2)
        val tan = FloatArray(2)
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        val nx = FloatArray(n)
        val ny = FloatArray(n)
        for (i in 0 until n) {
            measure.getPosTan(length * i / n, pos, tan)
            xs[i] = pos[0]
            ys[i] = pos[1]
            nx[i] = tan[1]
            ny[i] = -tan[0]
        }
        val waves = 5
        fun layer(dx: Float, dy: Float, amp: Float, shift: Float): Path = Path().apply {
            for (i in 0 until n) {
                val d = amp * sin(2f * PI.toFloat() * (i.toFloat() / n * waves - shift))
                val x = xs[i] + dx + nx[i] * d
                val y = ys[i] + dy + ny[i] * d
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        val stepPx = step.toPx()
        val amp = stepPx / 3f
        onDrawWithContent {
            val t = phase?.value ?: 0f
            val a = appear()
            val breathe = 1f + 0.18f * sin(2f * PI.toFloat() * t)
            val s = stepPx * a * breathe
            drawPath(layer(-s * 2, -s * 2, amp * a, t + 0.5f), BrandViolet.copy(alpha = 0.75f * a.coerceIn(0f, 1f)))
            drawPath(layer(-s, -s, amp * a, t), BrandGreen.copy(alpha = 0.8f * a.coerceIn(0f, 1f)))
            drawContent()
        }
    }
