// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.redrixdev.tikstik.core.designsystem.theme.BrandGreen
import com.redrixdev.tikstik.core.designsystem.theme.LocalReduceMotion
import com.redrixdev.tikstik.core.designsystem.theme.Surface1

/** Shared clock for the new-sticker dot (0..1, looping); null when animations are off. */
@Composable
fun rememberNewGlow(): State<Float>? {
    if (LocalReduceMotion.current) return null
    return rememberInfiniteTransition("newDot").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
}

/** Green "new" dot at the top left, with a ripple; [offset] staggers each thumbnail. Read while drawing. */
fun Modifier.newGlow(enabled: Boolean, clock: State<Float>?, offset: Float): Modifier =
    if (!enabled) this else drawWithContent {
        drawContent()
        val c = Offset(14.dp.toPx(), 14.dp.toPx())
        val dot = 5.dp.toPx()
        // The ripple expands and fades during the first half of each cycle.
        if (clock != null) {
            val p = ((clock.value + offset) % 1f) / 0.55f
            if (p in 0f..1f) {
                val eased = 1f - (1f - p) * (1f - p)
                drawCircle(BrandGreen.copy(alpha = 0.55f * (1f - p)), radius = dot + 9.dp.toPx() * eased, center = c, style = Stroke(2.dp.toPx()))
            }
        }
        // Background-colored rim so the dot reads over any sticker.
        drawCircle(Surface1, radius = dot + 2.dp.toPx(), center = c)
        drawCircle(BrandGreen, radius = dot, center = c)
    }
