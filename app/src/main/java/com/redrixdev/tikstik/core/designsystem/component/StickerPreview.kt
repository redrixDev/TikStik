// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.designsystem.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.core.designsystem.theme.Ink
import com.redrixdev.tikstik.core.designsystem.theme.LocalReduceMotion
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.hypot
import kotlin.math.min

/** Where a thumbnail is, measured only when the preview opens: measuring every tile on every frame made swipes stutter. */
class ThumbPlacement {
    var coordinates: LayoutCoordinates? = null

    fun bounds(): Rect? = coordinates?.takeIf { it.isAttached }?.boundsInRoot()
}

fun Modifier.placedAt(placement: ThumbPlacement): Modifier = onPlaced { placement.coordinates = it }

/** Flung, it leaves in that direction; tapping outside or pressing back returns it to its thumbnail ([origin], in root coordinates). */
@Composable
fun StickerPreview(file: File, origin: Rect?, onClosed: () -> Unit) {
    val reduce = LocalReduceMotion.current
    val open = remember { Animatable(if (reduce) 1f else 0f) }
    val drag = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val fade = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Where this layer sits on screen (inside a sheet it does not start at the top): nothing is drawn until known.
    var here by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(here != null) {
        // No bounce: at 0.8 it overshot slightly and a small readjustment showed at the end.
        if (here != null) open.animateTo(1f, spring(dampingRatio = 1f, stiffness = 420f))
    }

    fun close() {
        scope.launch {
            if (!reduce) {
                launch { drag.animateTo(Offset.Zero, spring(stiffness = 500f)) }
                open.animateTo(0f, spring(dampingRatio = 1f, stiffness = 520f))
            }
            onClosed()
        }
    }

    BackHandler { close() }

    // Gestures are read on this layer, which does not move: so the fling follows the finger's real direction.
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { here = it.positionInRoot() }
            .pointerInput(Unit) { detectTapGestures { close() } }
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                detectDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onDrag = { change, amount ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        scope.launch { drag.snapTo(drag.value + amount) }
                    },
                    onDragEnd = {
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val side = min(min(w, h) * 0.78f, 380.dp.toPx())
                        val v = tracker.calculateVelocity()
                        val speed = hypot(v.x, v.y)
                        val distance = hypot(drag.value.x, drag.value.y)
                        if (distance > side * 0.28f || speed > 1800f) {
                            // Leaves in a straight line towards where it was flung or dragged.
                            val direction = if (speed > 600f) Offset(v.x / speed, v.y / speed) else drag.value / distance
                            scope.launch {
                                launch { fade.animateTo(0f, tween(220)) }
                                drag.animateTo(drag.value + direction * maxOf(w, h), tween(260))
                                onClosed()
                            }
                        } else {
                            scope.launch { drag.animateTo(Offset.Zero, spring(dampingRatio = 0.6f, stiffness = 420f)) }
                        }
                    },
                )
            },
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val side = min(min(w, h) * 0.78f, with(density) { 380.dp.toPx() })
        val target = Rect(Offset((w - side) / 2f, (h - side) / 2f), Size(side, side))
        val start = origin?.translate(-(here ?: Offset.Zero)) ?: target.deflate(side * 0.3f)

        // The scrim darkens on opening and lightens while dragging.
        Canvas(Modifier.fillMaxSize()) {
            val away = (hypot(drag.value.x, drag.value.y) / (side * 1.2f)).coerceIn(0f, 1f)
            drawRect(Ink.copy(alpha = 0.88f * open.value.coerceIn(0f, 1f) * (1f - away) * fade.value))
        }

        val context = LocalContext.current
        val request = remember(file) { ImageRequest.Builder(context).data(file).build() }
        AsyncImage(
            model = request,
            contentDescription = stringResource(R.string.cd_sticker),
            modifier = Modifier
                .size(with(density) { side.toDp() })
                .graphicsLayer {
                    val p = open.value
                    val cx = lerp(start.center.x, target.center.x, p) + drag.value.x
                    val cy = lerp(start.center.y, target.center.y, p) + drag.value.y
                    val scale = lerp(start.width / side, 1f, p)
                    translationX = cx - side / 2f
                    translationY = cy - side / 2f
                    scaleX = scale
                    scaleY = scale
                    // While dragging it tilts a little towards where it goes, like a sticker peeling off.
                    rotationZ = drag.value.x / side * 12f
                    alpha = if (here == null) 0f else fade.value
                },
        )
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
