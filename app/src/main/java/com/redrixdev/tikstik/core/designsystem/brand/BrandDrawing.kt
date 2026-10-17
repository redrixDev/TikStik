// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.designsystem.brand

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import com.redrixdev.tikstik.core.designsystem.theme.BrandGreen
import com.redrixdev.tikstik.core.designsystem.theme.BrandPaper
import com.redrixdev.tikstik.core.designsystem.theme.BrandViolet
import com.redrixdev.tikstik.core.designsystem.theme.Ink
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import com.redrixdev.tikstik.core.designsystem.brand.BrandGeometry as G

private val CurlLight = Color(0xFFCFF9DE)

internal fun String.toPath(): Path = PathParser().parsePathString(this).toPath()

/** Full silhouette of the bubble sticker (108 canvas), for brand clips and fills. */
fun brandFullPath(): Path = G.FULL.toPath()

/** The brand paths already parsed, reusable across frames. */
internal class BrandShapes {
    val full = G.FULL.toPath()
    val keyline = G.FULL_KEYLINE.toPath()
    val bodyFace = G.BODY_FACE.toPath()
    val tailFace = G.TAIL_FACE.toPath()
    val flap = G.FLAP.toPath()
    val foldShade = G.FOLD_SHADE.toPath()
    val spark = G.SPARK.toPath()
    val letters = G.LETTERS.map { it.toPath() }
    val wordEcho1 = G.WORD_ECHO_1.toPath()
    val wordEcho2 = G.WORD_ECHO_2.toPath()

    // Rewritten on every frame.
    val sweep = Path()
    val band = Path()
    val hole = Path()
}

/** The mark's pose: what the splash animates and, in its final state, the static logo. */
internal data class MarkPose(
    val settle: Float = 1f,
    val scale: Float = 1f,
    val echo1: Float = 1f,
    val echo2: Float = 1f,
    val peel: Float = 1f,
    val spark: Float = 1f,
    val glint: Float = 1f,
    // Extra spark turns, in degrees, for the Settings flourish.
    val spin: Float = 0f,
)

private fun smooth(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

internal fun DrawScope.drawMark(s: BrandShapes, pose: MarkPose) {
    val settle = pose.settle
    // At first the sticker is centered, as in the system splash; it moves back into place as the echoes appear.
    translate((54f - G.STICKER_CX) * (1f - settle), (54f - G.STICKER_CY) * (1f - settle)) {
        scale(pose.scale, Offset(G.STICKER_CX, G.STICKER_CY)) {
            val e2 = pose.echo2
            if (e2 > 0.001f) translate(G.ECHO_2_X * e2, G.ECHO_2_Y * e2) {
                drawPath(s.full, BrandViolet)
            }
            val e1 = pose.echo1
            if (e1 > 0.001f) translate(G.ECHO_1_X * e1, G.ECHO_1_Y * e1) {
                drawPath(s.keyline, Ink)
                drawPath(s.full, BrandGreen)
            }
            drawPath(s.keyline, Ink)
            drawPath(s.full, BrandGreen)
            drawPath(s.bodyFace, BrandPaper)
            val p = pose.peel
            val tail = 1f - smooth(0.8f, 1f, p)
            if (tail > 0f) drawPath(s.tailFace, BrandPaper, alpha = tail)
            if (p > 0f) drawPeel(s, p)
        }
        drawSpark(s, pose)
    }
}

/** The curl: the green back advances from the tip to the fold, with a shine at the front. */
private fun DrawScope.drawPeel(s: BrandShapes, p: Float) {
    val front = lerp(G.FLAP_MAX_PROJ + 0.6f, -1.8f, p)
    val bulge = 1f + 0.09f * sin(PI * p).toFloat()
    scale(bulge, Offset(G.FOLD_MX, G.FOLD_MY)) {
        halfPlane(s.sweep, front, front + 60f)
        clipPath(s.sweep) {
            drawPath(s.flap, BrandGreen)
            val shine = sin(PI * p).toFloat()
            if (shine > 0.02f) {
                halfPlane(s.band, front, front + 1.7f)
                clipPath(s.band) { drawPath(s.flap, CurlLight, alpha = 0.9f * shine) }
            }
        }
        val shade = smooth(0.5f, 1f, p)
        if (shade > 0f) {
            val start = Offset(G.FOLD_MX - G.FOLD_NX * 1.2f, G.FOLD_MY - G.FOLD_NY * 1.2f)
            val end = Offset(G.FOLD_MX + G.FOLD_NX * G.FOLD_SHADE_DEPTH, G.FOLD_MY + G.FOLD_NY * G.FOLD_SHADE_DEPTH)
            drawPath(s.foldShade, Brush.linearGradient(listOf(Color.Black.copy(alpha = 0.42f), Color.Transparent), start, end), alpha = shade)
        }
    }
}

/** The band between the lines parallel to the fold at distances [d0] and [d1], towards the tip. */
private fun halfPlane(path: Path, d0: Float, d1: Float) {
    val nx = G.FOLD_NX
    val ny = G.FOLD_NY
    val tx = -ny * 80f
    val ty = nx * 80f
    path.rewind()
    path.moveTo(G.FOLD_MX + nx * d0 - tx, G.FOLD_MY + ny * d0 - ty)
    path.lineTo(G.FOLD_MX + nx * d0 + tx, G.FOLD_MY + ny * d0 + ty)
    path.lineTo(G.FOLD_MX + nx * d1 + tx, G.FOLD_MY + ny * d1 + ty)
    path.lineTo(G.FOLD_MX + nx * d1 - tx, G.FOLD_MY + ny * d1 - ty)
    path.close()
}

private val GlintAngles = floatArrayOf(-62f, -8f, 46f, 104f)
private val GlintColors = arrayOf(BrandPaper, BrandGreen, BrandViolet, BrandGreen)

private fun DrawScope.drawSpark(s: BrandShapes, pose: MarkPose) {
    val c = Offset(G.SPARK_CX, G.SPARK_CY)
    val g = pose.glint
    if (g > 0f && g < 1f) {
        for (i in GlintAngles.indices) {
            val a = GlintAngles[i] * (PI / 180f).toFloat()
            val dist = 3.5f + 8.5f * g
            drawCircle(
                GlintColors[i], radius = 1.15f * (1f - g * 0.7f),
                center = Offset(c.x + cos(a) * dist, c.y + sin(a) * dist), alpha = 1f - g,
            )
        }
    }
    val sp = pose.spark
    if (sp > 0.001f) {
        withTransform({
            rotate((1f - sp.coerceAtMost(1f)) * -120f + pose.spin, c)
            scale(sp, sp, c)
        }) { drawPath(s.spark, BrandPaper) }
    }
}
