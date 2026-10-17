// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.designsystem.theme

import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import java.util.WeakHashMap

object Motion {
    const val DAMPING = 0.82f

    fun <T> standard(): FiniteAnimationSpec<T> = spring(dampingRatio = DAMPING, stiffness = Spring.StiffnessMediumLow)
    fun <T> bouncy(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
    fun offset(): FiniteAnimationSpec<IntOffset> =
        spring(dampingRatio = DAMPING, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset(1, 1))
}

/** The requested spring, or a snap when the user turned off system animations. */
@Composable
@ReadOnlyComposable
fun <T> motion(spec: FiniteAnimationSpec<T> = Motion.standard()): FiniteAnimationSpec<T> =
    if (LocalReduceMotion.current) snap() else spec

/** Votes for the highest refresh rate while [animating]: with the adaptive rate (Android 15+) the app stayed at 60 Hz otherwise. */
fun Modifier.highFrameRateWhile(animating: Boolean): Modifier = this then HighFrameRateElement(animating)

private data class HighFrameRateElement(val animating: Boolean) : ModifierNodeElement<HighFrameRateNode>() {
    override fun create() = HighFrameRateNode(animating)

    override fun update(node: HighFrameRateNode) = node.update(animating)

    override fun InspectorInfo.inspectableProperties() {
        name = "highFrameRateWhile"
        properties["animating"] = animating
    }
}

// Requested on the view, not with preferredFrameRate: Compose only votes when redrawing the layer, and a fling does not redraw.
private class HighFrameRateNode(private var animating: Boolean) : Modifier.Node(), CompositionLocalConsumerModifierNode {
    private var boosted: View? = null

    override fun onAttach() {
        if (animating) start()
    }

    override fun onDetach() = stop()

    fun update(value: Boolean) {
        if (value == animating) return
        animating = value
        if (!isAttached) return
        if (value) start() else stop()
    }

    private fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        if (boosted != null) return
        val view = currentValueOf(LocalView)
        boosted = view
        change(view, 1)
    }

    private fun stop() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val view = boosted ?: return
        boosted = null
        change(view, -1)
    }

    companion object {
        private val boosts = WeakHashMap<View, Int>()

        // Several pieces may move at once (sheet and list): the view returns to normal when the last one stops.
        @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
        private fun change(view: View, delta: Int) {
            val count = (boosts[view] ?: 0) + delta
            if (count > 0) boosts[view] = count else boosts.remove(view)
            view.requestedFrameRate =
                if (count > 0) view.display?.supportedModes?.maxOfOrNull { it.refreshRate } ?: View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
                else View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT
        }
    }
}
