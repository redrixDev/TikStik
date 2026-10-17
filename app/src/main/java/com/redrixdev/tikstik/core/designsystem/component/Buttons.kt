// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.redrixdev.tikstik.core.designsystem.theme.LocalReduceMotion
import com.redrixdev.tikstik.core.designsystem.theme.Surface2
import com.redrixdev.tikstik.core.designsystem.theme.TextHigh
import com.redrixdev.tikstik.core.designsystem.theme.TextMid
import kotlin.math.PI
import kotlin.math.sin

/** The screen's main action: a 56 dp green pill. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.heightIn(min = 56.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = Surface2,
            disabledContentColor = TextMid,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    ) {
        ButtonContent(text, icon, loading)
    }
}

/** Secondary action: a 52 dp raised-surface pill. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    contentColor: Color = TextHigh,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = CircleShape,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Surface2, contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        ButtonContent(text, icon, loading = false)
    }
}

/** Quiet action: text only, with a 48 dp touch target. */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = TextMid) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), shape = CircleShape) {
        Text(text, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun RowScope.ButtonContent(text: String, icon: ImageVector?, loading: Boolean) {
    if (loading) {
        BouncingDots(LocalContentColor.current)
        Spacer(Modifier.width(10.dp))
    } else if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
    }
    Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 2, textAlign = TextAlign.Center)
}

/** Three dots bouncing in turn: loading inside a button. */
@Composable
fun BouncingDots(color: Color, modifier: Modifier = Modifier) {
    val t = if (LocalReduceMotion.current) null else rememberInfiniteTransition("dots").animateFloat(
        0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "t",
    )
    Canvas(modifier.size(28.dp, 12.dp)) {
        val r = size.height / 4f
        for (i in 0..2) {
            val phase = ((t?.value ?: 0f) - i * 0.15f + 1f) % 1f
            val lift = if (phase < 0.5f) sin(phase * 2f * PI.toFloat()) else 0f
            drawCircle(color, r, Offset(r + i * (size.width - 2 * r) / 2f, size.height - r - lift * (size.height - 2 * r)))
        }
    }
}
