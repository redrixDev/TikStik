// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.designsystem.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.core.designsystem.theme.BrandGreen
import com.redrixdev.tikstik.core.designsystem.theme.BrandPaper
import com.redrixdev.tikstik.core.designsystem.theme.BrandViolet
import com.redrixdev.tikstik.core.designsystem.theme.Ink
import com.redrixdev.tikstik.core.designsystem.theme.LocalReduceMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import com.redrixdev.tikstik.core.designsystem.brand.BrandGeometry as G

private val Lift = Color(0xFF1C1C22)

// The system draws the splash icon at 288 dp (108 canvas): the animation starts from that framing.
private val SplashIconSize = 288.dp
private val WordWidth = 172.dp
private val WordGap = 26.dp

/** Starts on the system splash's frame; without the system icon ([systemIconShown]), the sticker springs in. */
@Composable
fun BrandSplash(
    onReveal: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    systemIconShown: Boolean = true,
) {
    if (LocalReduceMotion.current) {
        LaunchedEffect(Unit) {
            onReveal()
            onFinished()
        }
        return
    }

    val shapes = remember { BrandShapes() }
    val m = remember { SplashMotion() }
    val scope = rememberCoroutineScope()
    val timeline = remember { arrayOfNulls<Job>(1) }

    // A tap cuts the timeline short; when it ends on its own, springs still settling carry on through the exit.
    fun exit(skip: Boolean) {
        if (m.exiting) return
        m.exiting = true
        if (skip) timeline[0]?.cancel()
        onReveal()
        scope.launch {
            m.exit.animateTo(1f, tween(900, easing = LinearEasing))
            onFinished()
        }
    }

    LaunchedEffect(systemIconShown) {
        if (!systemIconShown) {
            m.intro.snapTo(0f)
            m.intro.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 380f))
        }
    }
    LaunchedEffect(Unit) {
        timeline[0] = launch {
            m.play(this)
            exit(skip = false)
        }
    }

    val name = stringResource(R.string.app_name)
    Canvas(
        modifier
            .fillMaxSize()
            .semantics { contentDescription = name }
            .pointerInput(Unit) { detectTapGestures { exit(skip = true) } },
    ) {
        drawSplash(shapes, m)
    }
}

private class SplashMotion {
    val intro = Animatable(1f)
    val pop = Animatable(1f)
    val peel = Animatable(0f)
    val settle = Animatable(0f)
    val echo1 = Animatable(0f)
    val echo2 = Animatable(0f)
    val glow = Animatable(0f)
    val spark = Animatable(0f)
    val glint = Animatable(0f)
    val lift = Animatable(0f)
    val letters = List(G.LETTERS.size) { Animatable(0f) }
    val wordEcho = Animatable(0f)
    val exit = Animatable(0f)
    var exiting = false

    /** About 1.4 s until the exit; each piece is an independent spring. */
    suspend fun play(scope: CoroutineScope) = with(scope) {
        // Anticipation: squashes and bounces back.
        launch {
            pop.animateTo(0.92f, tween(130, easing = FastOutLinearInEasing))
            pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 520f))
        }
        delay(100)
        // The corner peels off and curls.
        launch { peel.animateTo(1f, tween(470, easing = CubicBezierEasing(0.3f, 0f, 0.12f, 1f))) }
        delay(230)
        // The echoes bounce out and the sticker backs off to its place in the composition.
        launch { settle.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 320f)) }
        launch { echo1.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 360f)) }
        launch { glow.animateTo(1f, tween(800, easing = LinearOutSlowInEasing)) }
        delay(70)
        launch { echo2.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 240f)) }
        delay(140)
        launch { spark.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 460f)) }
        launch { glint.animateTo(1f, tween(560, easing = LinearOutSlowInEasing)) }
        delay(110)
        // The mark rises and the wordmark enters letter by letter.
        launch { lift.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 210f)) }
        delay(80)
        letters.forEachIndexed { i, a ->
            launch {
                delay(i * 34L)
                a.animateTo(1f, spring(dampingRatio = 0.56f, stiffness = 430f))
            }
        }
        delay(34L * (letters.size - 1) + 130)
        launch { wordEcho.animateTo(1f, spring(dampingRatio = 0.48f, stiffness = 330f)) }
        delay(330)
    }

    fun pose() = MarkPose(
        settle = settle.value,
        scale = pop.value * intro.value,
        echo1 = echo1.value,
        echo2 = echo2.value,
        peel = peel.value,
        spark = spark.value,
        glint = glint.value,
    )
}

private val WaveEasing = CubicBezierEasing(0.4f, 0f, 0.3f, 1f)

private fun DrawScope.drawSplash(s: BrandShapes, m: SplashMotion) {
    val unit = SplashIconSize.toPx() / 108f
    val ex = m.exit.value
    val lift = m.lift.value

    // Final framing: the mark and the wordmark, centered together vertically.
    val wordScale = WordWidth.toPx() / (G.WORD_RIGHT - G.WORD_LEFT)
    val wordH = (G.WORD_BOTTOM - G.WORD_TOP) * wordScale
    val gap = WordGap.toPx()
    val compHalf = (G.COMP_BOTTOM - 54f) * unit
    val restMarkY = center.y - (2 * compHalf + gap + wordH) / 2 + compHalf
    val markY = lerp(center.y, restMarkY, lift)

    if (ex <= 0f) {
        drawScene(s, m, unit, markY, wordScale, compHalf, gap)
        return
    }

    // Exit: three waves (white, green and violet) rise from the bottom and reveal the app.
    val e = WaveEasing.transform(ex)
    val amp = 18.dp.toPx()
    val band = 26.dp.toPx()
    // Starts below the bottom edge and ends with all three bands out past the top.
    val level = (size.height + amp) * (1f - e) - (amp * 2 + band * 4) * e
    val phase = ex * 2f * PI.toFloat() * 1.4f
    wavePath(s.hole, level, amp, phase)
    clipPath(s.hole) { drawScene(s, m, unit, markY - 36.dp.toPx() * e, wordScale, compHalf, gap) }
    drawWaveBand(s.sweep, level, amp, phase + 1.3f, band * 3, BrandViolet)
    drawWaveBand(s.sweep, level, amp, phase + 0.65f, band * 2, BrandGreen)
    drawWaveBand(s.sweep, level, amp, phase, band * 0.55f, BrandPaper)
}

private fun DrawScope.drawScene(
    s: BrandShapes,
    m: SplashMotion,
    unit: Float,
    markY: Float,
    wordScale: Float,
    compHalf: Float,
    gap: Float,
) {
    drawBackdrop(m, unit, markY)
    withTransform({
        translate(center.x, markY)
        scale(unit, unit, Offset.Zero)
        translate(-54f, -54f)
    }) { drawMark(s, m.pose()) }

    val wordLeft = center.x - (G.WORD_RIGHT - G.WORD_LEFT) * wordScale / 2
    val wordTop = markY + compHalf + gap
    withTransform({
        translate(wordLeft, wordTop)
        scale(wordScale, wordScale, Offset.Zero)
        translate(-G.WORD_LEFT, -G.WORD_TOP)
    }) { drawWord(s, m) }
}

/** Wavy edge at height [level], closed at the top: what is left of the splash. */
private fun DrawScope.wavePath(path: Path, level: Float, amp: Float, phase: Float) {
    path.rewind()
    val step = 6.dp.toPx()
    var x = 0f
    path.moveTo(0f, level + amp * sin(phase))
    while (x <= size.width + step) {
        path.lineTo(x, level + amp * sin(x / size.width * 2.6f * PI.toFloat() + phase))
        x += step
    }
    path.lineTo(size.width + step, -amp)
    path.lineTo(0f, -amp)
    path.close()
}

/** Wave band between the edge, with its phase, and [depth] further down. */
private fun DrawScope.drawWaveBand(path: Path, level: Float, amp: Float, phase: Float, depth: Float, color: Color) {
    path.rewind()
    val step = 6.dp.toPx()
    var x = 0f
    path.moveTo(0f, level + amp * sin(phase))
    while (x <= size.width + step) {
        path.lineTo(x, level + amp * sin(x / size.width * 2.6f * PI.toFloat() + phase))
        x += step
    }
    var back = size.width + step
    while (back >= -step) {
        path.lineTo(back, level + depth + amp * 0.6f * sin(back / size.width * 2.6f * PI.toFloat() + phase + 0.8f))
        back -= step
    }
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawBackdrop(m: SplashMotion, unit: Float, markY: Float) {
    drawRect(Ink)
    val glow = m.glow.value
    if (glow > 0f) {
        val r = 64f * unit
        val c = Offset(center.x, markY - 6f * unit)
        drawCircle(Brush.radialGradient(listOf(Lift, Lift.copy(alpha = 0f)), c, r), r, c, alpha = glow)
    }
}

private fun DrawScope.drawWord(s: BrandShapes, m: SplashMotion) {
    val sAlpha = m.letters[G.S_INDEX].value.coerceIn(0f, 1f)
    val we = m.wordEcho.value
    if (we > 0.001f) {
        // The echoes come out from behind the "S".
        val k = (1f - we) * G.WORD_ECHO_STEP
        translate(2f * k, 2f * k) { drawPath(s.wordEcho2, BrandViolet, alpha = sAlpha) }
        translate(k, k) { drawPath(s.wordEcho1, BrandGreen, alpha = sAlpha) }
    }
    s.letters.forEachIndexed { i, path ->
        val a = m.letters[i].value
        if (a > 0.001f) translate(0f, (1f - a) * 26f) {
            drawPath(path, BrandPaper, alpha = a.coerceIn(0f, 1f))
        }
    }
}
