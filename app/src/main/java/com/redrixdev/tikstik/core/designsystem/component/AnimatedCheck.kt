// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.redrixdev.tikstik.core.designsystem.theme.BrandGreen
import com.redrixdev.tikstik.core.designsystem.theme.LocalReduceMotion
import com.redrixdev.tikstik.core.designsystem.theme.Motion
import com.redrixdev.tikstik.core.designsystem.theme.OnGreen

/** Success check: the circle grows with a spring and then the stroke is drawn. */
@Composable
fun AnimatedCheck(modifier: Modifier = Modifier, size: Dp = 88.dp) {
    val reduce = LocalReduceMotion.current
    val haptics = LocalHapticFeedback.current
    val circle = remember { Animatable(if (reduce) 1f else 0f) }
    val stroke = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        if (reduce) return@LaunchedEffect
        circle.animateTo(1f, Motion.bouncy())
        stroke.animateTo(1f, tween(320))
    }
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        drawCircle(BrandGreen.copy(alpha = 0.18f), r * circle.value)
        drawCircle(BrandGreen, r * 0.72f * circle.value)
        val path = Path().apply {
            moveTo(r * 0.62f, r * 1.02f)
            lineTo(r * 0.9f, r * 1.3f)
            lineTo(r * 1.42f, r * 0.76f)
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val partial = Path()
        measure.getSegment(0f, measure.length * stroke.value, partial, true)
        drawPath(partial, OnGreen, style = Stroke(width = r * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
