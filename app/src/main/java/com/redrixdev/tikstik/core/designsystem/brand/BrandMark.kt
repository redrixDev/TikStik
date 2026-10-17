// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.designsystem.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.redrixdev.tikstik.core.designsystem.brand.BrandGeometry as G
import com.redrixdev.tikstik.core.designsystem.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val COMP_HEIGHT = G.COMP_BOTTOM - G.COMP_TOP
private const val TWINKLE_FIRST_MS = 700L
private const val TWINKLE_EVERY_MS = 7_000L

/** The logo: [alive], springy echoes that breathe; [twinkle], a spin on arrival and every 7 s; on each change of [bounce], the echoes go in and out. */
@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    alive: Boolean = false,
    contentDescription: String? = null,
    twinkle: Boolean = false,
    bounce: Int = 0,
    onBounced: () -> Unit = {},
) {
    val shapes = remember { BrandShapes() }
    val reduce = LocalReduceMotion.current
    val animate = alive && !reduce
    val echo1 = remember { Animatable(if (animate) 0f else 1f) }
    val echo2 = remember { Animatable(if (animate) 0f else 1f) }
    val spark = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        launch { echo1.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f)) }
        delay(80)
        launch { echo2.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 220f)) }
        delay(160)
        spark.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 420f))
    }
    val spin = remember { Animatable(0f) }
    val glint = remember { Animatable(1f) }
    LaunchedEffect(twinkle, reduce) {
        if (!twinkle || reduce) return@LaunchedEffect
        // One spin on arrival, once it has come in, then every 7 s: not too often, not so rare it goes unnoticed.
        var wait = TWINKLE_FIRST_MS
        while (true) {
            delay(wait)
            wait = TWINKLE_EVERY_MS
            launch { glint.snapTo(0f); glint.animateTo(1f, tween(700)) }
            spin.snapTo(0f)
            spin.animateTo(360f, tween(900, easing = FastOutSlowInEasing))
        }
    }
    // Only changes of [bounce]: recomposing (e.g. coming back to Home) does not repeat it.
    val lastBounce = remember { intArrayOf(bounce) }
    LaunchedEffect(bounce) {
        if (bounce == lastBounce[0]) return@LaunchedEffect
        lastBounce[0] = bounce
        if (!reduce) {
            // The echoes tuck behind the bubble and spring back out, as when they first appear.
            launch { echo2.animateTo(0f, tween(140)) }
            echo1.animateTo(0f, tween(140))
            launch { echo1.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f)) }
            delay(80)
            echo2.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 220f))
        }
        onBounced()
    }
    // Read while drawing: repaints without recomposing every frame.
    val breatheState = if (animate) {
        rememberInfiniteTransition("breathe").animateFloat(
            initialValue = 1f, targetValue = 0.82f,
            animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "echo",
        )
    } else {
        null
    }
    Canvas(modifier.semantics { contentDescription?.let { this.contentDescription = it } }) {
        val breathe = breatheState?.value ?: 1f
        val unit = minOf(size.width, size.height) / (COMP_HEIGHT * 1.08f)
        translate(size.width / 2f, size.height / 2f) {
            scale(unit, unit, Offset.Zero) {
                translate(-54f, -54f) {
                    drawMark(
                        shapes,
                        MarkPose(
                            echo1 = echo1.value * (0.5f + 0.5f * breathe),
                            echo2 = echo2.value * breathe,
                            spark = spark.value,
                            glint = glint.value,
                            spin = spin.value,
                        ),
                    )
                }
            }
        }
    }
}
