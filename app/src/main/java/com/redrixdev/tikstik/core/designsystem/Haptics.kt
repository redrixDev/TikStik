// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.designsystem

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Meaningful vibrations: haptic primitives on Android 12+ or an equivalent pattern; they honor the system's touch feedback setting. */
class Haptics(context: Context) {

    private val resolver = context.contentResolver

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    /** "Activate": one click, like a switch. */
    fun activate() = play(
        primitives = listOf(Primitive(Kind.CLICK, 0.8f)),
        fallback = longArrayOf(0, 20) to intArrayOf(0, 200),
    )

    /** "Link": a double click, like two pieces snapping together. */
    fun link() = play(
        primitives = listOf(Primitive(Kind.CLICK, 0.7f), Primitive(Kind.CLICK, 1f, 90)),
        fallback = longArrayOf(0, 18, 90, 26) to intArrayOf(0, 160, 0, 255),
    )

    /** "Done": rises, lands and bounces. */
    fun celebrate() = play(
        primitives = listOf(
            Primitive(Kind.QUICK_RISE, 0.7f),
            Primitive(Kind.CLICK, 1f, 30),
            Primitive(Kind.TICK, 0.7f, 110),
            Primitive(Kind.TICK, 0.5f, 70),
            Primitive(Kind.TICK, 0.3f, 70),
        ),
        fallback = longArrayOf(0, 60, 40, 30, 90, 12, 60, 10, 60, 8) to intArrayOf(0, 120, 0, 255, 0, 160, 0, 110, 0, 70),
    )

    /** The "Searching…" segment hits an edge: a short tick. */
    fun edge() = play(
        primitives = listOf(Primitive(Kind.TICK, 0.8f)),
        fallback = longArrayOf(0, 8) to intArrayOf(0, 120),
    )

    private enum class Kind { CLICK, QUICK_RISE, TICK }

    private class Primitive(val kind: Kind, val scale: Float, val delayMs: Int = 0)

    private fun play(primitives: List<Primitive>, fallback: Pair<LongArray, IntArray>) {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        if (touchFeedbackOff()) return
        if (Build.VERSION.SDK_INT >= 31 && v.supportsOurPrimitives()) {
            v.vibrateTouch(compose(primitives))
        } else if (v.hasAmplitudeControl()) {
            v.vibrateTouch(VibrationEffect.createWaveform(fallback.first, fallback.second, -1))
        } else {
            v.vibrateTouch(VibrationEffect.createWaveform(fallback.first, -1))
        }
    }

    // Android 8 does not apply "Vibrate on tap" to apps (AOSP 8.0/8.1): checked by hand; deprecated only since API 33.
    @Suppress("DEPRECATION")
    private fun touchFeedbackOff() =
        Build.VERSION.SDK_INT < 28 && Settings.System.getInt(resolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0

    @RequiresApi(31)
    private fun compose(primitives: List<Primitive>): VibrationEffect {
        val composition = VibrationEffect.startComposition()
        primitives.forEach { composition.addPrimitive(it.kind.id(), it.scale, it.delayMs) }
        return composition.compose()
    }

    @RequiresApi(31)
    private fun Kind.id() = when (this) {
        Kind.CLICK -> VibrationEffect.Composition.PRIMITIVE_CLICK
        Kind.QUICK_RISE -> VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
        Kind.TICK -> VibrationEffect.Composition.PRIMITIVE_TICK
    }

    @RequiresApi(31)
    private fun Vibrator.supportsOurPrimitives() = areAllPrimitivesSupported(
        VibrationEffect.Composition.PRIMITIVE_CLICK,
        VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
        VibrationEffect.Composition.PRIMITIVE_TICK,
    )

    // As touch feedback: the system mutes it if the user turned off vibration on tap.
    private fun Vibrator.vibrateTouch(effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= 33) {
            vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            vibrate(effect)
        }
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val context = LocalContext.current
    return remember(context) { Haptics(context.applicationContext) }
}
