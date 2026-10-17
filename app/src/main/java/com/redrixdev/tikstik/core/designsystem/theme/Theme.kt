// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.designsystem.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val Scheme = darkColorScheme(
    primary = BrandGreen,
    onPrimary = OnGreen,
    primaryContainer = GreenContainer,
    onPrimaryContainer = OnGreenContainer,
    secondary = BrandViolet,
    onSecondary = Ink,
    secondaryContainer = VioletContainer,
    onSecondaryContainer = OnVioletContainer,
    tertiary = BrandPaper,
    onTertiary = Ink,
    background = Ink,
    onBackground = TextHigh,
    surface = Ink,
    onSurface = TextHigh,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextMid,
    surfaceDim = Ink,
    surfaceBright = Surface3,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Surface1,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface3,
    inverseSurface = BrandPaper,
    inverseOnSurface = Ink,
    inversePrimary = GreenContainer,
    outline = Outline,
    outlineVariant = OutlineSoft,
    error = Danger,
    onError = Ink,
    errorContainer = DangerContainer,
    onErrorContainer = Danger,
    scrim = Ink,
)

private val TikStikShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** true when the user turned off system animations: everything becomes instant. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Fixed dark theme, no dynamic colour: the brand always looks the same. */
@Composable
fun TikStikTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalReduceMotion provides rememberReduceMotion()) {
        MaterialTheme(colorScheme = Scheme, typography = TikStikTypography, shapes = TikStikShapes) {
            // No root Surface: otherwise the default text colour would be black.
            CompositionLocalProvider(LocalContentColor provides TextHigh, content = content)
        }
    }
}

// "Remove animations" can change while the app is open: it is observed rather than read once.
@Composable
private fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduce by remember { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduce = read()
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduce
}
