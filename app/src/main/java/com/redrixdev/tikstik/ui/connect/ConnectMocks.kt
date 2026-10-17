// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.connect

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.core.designsystem.brand.BrandMark
import com.redrixdev.tikstik.core.designsystem.icon.TikStikIcons
import com.redrixdev.tikstik.core.designsystem.theme.BrandGreen
import com.redrixdev.tikstik.core.designsystem.theme.Ink
import com.redrixdev.tikstik.core.designsystem.theme.LocalReduceMotion
import com.redrixdev.tikstik.core.designsystem.theme.OnGreen
import com.redrixdev.tikstik.core.designsystem.theme.Outline
import com.redrixdev.tikstik.core.designsystem.theme.Surface1
import com.redrixdev.tikstik.core.designsystem.theme.Surface2
import com.redrixdev.tikstik.core.designsystem.theme.Surface3
import com.redrixdev.tikstik.core.designsystem.theme.TextHigh
import com.redrixdev.tikstik.core.designsystem.theme.TextMid

// Decorative illustrations of the steps: they show what Settings will look like; TalkBack reads the step's text.

// Sample code for the Android dialog: it is part of the drawing.
private const val SAMPLE_CODE = "482913"

@Composable
private fun loop(periodMs: Int): State<Float>? {
    if (LocalReduceMotion.current) return null
    return rememberInfiniteTransition("mock").animateFloat(
        0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
}

private fun seg(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

@Composable
private fun Phone(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Box(
        modifier
            .width(250.dp)
            .height(210.dp)
            .clip(shape)
            .background(Ink)
            .border(3.dp, Surface3, shape)
            .padding(10.dp)
            .clearAndSetSemantics { },
    ) { content() }
}

/** Step 1: "Build number" tapped 7 times. */
@Composable
fun DevOptionsMock(modifier: Modifier = Modifier) {
    val time = loop(3600)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        Phone {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                repeat(2) { FakeRow(null, false) }
                val t = time?.value ?: 0.95f
                val taps = (seg(t, 0.08f, 0.72f) * 7).toInt().coerceAtMost(7)
                FakeRow(stringResource(R.string.mock_build_number), highlight = true, counter = if (t < 0.08f) 0 else taps)
                FakeRow(null, false)
            }
            val t = time?.value ?: 0.95f
            val toast = seg(t, 0.74f, 0.8f) * (1f - seg(t, 0.94f, 1f))
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        alpha = toast
                        translationY = (1f - toast) * 12.dp.toPx()
                    }
                    .padding(bottom = 8.dp)
                    .clip(CircleShape)
                    .background(Surface3)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) { Text(stringResource(R.string.mock_dev_toast), style = MaterialTheme.typography.labelSmall, color = TextHigh, maxLines = 1) }
        }
    }
}

@Composable
private fun FakeRow(label: String?, highlight: Boolean, counter: Int = 0) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlight) Surface2 else Surface1)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (label == null) {
            Box(Modifier.width(90.dp).height(8.dp).clip(CircleShape).background(Surface3))
        } else {
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
            if (counter > 0) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(if (counter == 7) BrandGreen else Outline),
                    contentAlignment = Alignment.Center,
                ) {
                    if (counter == 7) {
                        Icon(TikStikIcons.Check, null, tint = OnGreen, modifier = Modifier.size(14.dp))
                    } else {
                        Text("$counter", style = MaterialTheme.typography.labelSmall, color = TextHigh)
                    }
                }
            }
        }
    }
}

/** Step 2: Settings shows the code and our notice drops over it to type it in, just as it will happen. */
@Composable
fun PairingMock(modifier: Modifier = Modifier) {
    val time = loop(4200)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        Phone {
            val t = time?.value ?: 0.86f
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(top = 40.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface2)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.mock_pair_dialog), style = MaterialTheme.typography.labelSmall, color = TextMid, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text("${SAMPLE_CODE.take(3)} ${SAMPLE_CODE.drop(3)}", style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 2.sp))
            }
            // Our notice drops down with the field and rises back up when done.
            val drop = seg(t, 0.18f, 0.3f) * (1f - seg(t, 0.9f, 0.99f))
            val typed = (seg(t, 0.38f, 0.74f) * SAMPLE_CODE.length).toInt()
            val ok = t > 0.8f
            Row(
                Modifier
                    .graphicsLayer {
                        translationY = (drop - 1f) * 80.dp.toPx()
                        alpha = drop
                    }
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Surface3)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandMark(Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(26.dp)
                        .clip(CircleShape)
                        .background(if (ok) BrandGreen else Ink)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    when {
                        ok -> Icon(TikStikIcons.Check, null, tint = OnGreen, modifier = Modifier.size(16.dp).align(Alignment.Center))
                        typed == 0 -> Text(stringResource(R.string.mock_pair_field), style = MaterialTheme.typography.labelSmall, color = TextMid, maxLines = 1)
                        else -> Text(SAMPLE_CODE.take(typed), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp), color = TextHigh)
                    }
                }
            }
            // The tap ripple on the notice.
            val tap = seg(t, 0.3f, 0.36f) * (1f - seg(t, 0.4f, 0.46f))
            Canvas(Modifier.fillMaxSize()) {
                if (tap > 0f) drawCircle(Color.White.copy(alpha = 0.35f * tap), 14.dp.toPx() * (0.6f + tap), center = Offset(size.width * 0.55f, 22.dp.toPx()))
            }
        }
    }
}
