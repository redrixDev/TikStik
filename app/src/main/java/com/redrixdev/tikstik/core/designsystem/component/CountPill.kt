// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redrixdev.tikstik.core.designsystem.theme.BrandGreen
import com.redrixdev.tikstik.core.designsystem.theme.OnGreen
import com.redrixdev.tikstik.core.designsystem.theme.Surface2
import com.redrixdev.tikstik.core.designsystem.theme.TextMid

/** Height of the dock pills and their buttons. */
val PillHeight = 60.dp

/** The "[3] Add" pill: dimmed with nothing to do and showing dots while [loading]. */
@Composable
fun CountPill(
    count: Int,
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    val content = if (enabled) OnGreen else TextMid
    Row(
        modifier
            .height(PillHeight)
            .clip(CircleShape)
            .background(if (enabled) BrandGreen else Surface2)
            .clickable(enabled = enabled && !loading) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            }
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The number shrinks to fit its box, however many digits.
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(content.copy(alpha = 0.16f)).padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                count,
                transitionSpec = { (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut()) },
                label = "count",
            ) { n ->
                BasicText(
                    "$n",
                    maxLines = 1,
                    style = MaterialTheme.typography.titleMedium.merge(TextStyle(color = content, textAlign = TextAlign.Center)),
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 17.sp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (loading) BouncingDots(content) else PillLabel(label, content)
        }
    }
}

/** Single-line pill text: shrinks so it never wraps. */
@Composable
fun PillLabel(text: String, color: Color, modifier: Modifier = Modifier) {
    BasicText(
        text,
        modifier = modifier,
        maxLines = 1,
        style = MaterialTheme.typography.labelLarge.merge(TextStyle(color = color, fontSize = 16.sp, textAlign = TextAlign.Center)),
        autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 16.sp),
    )
}
