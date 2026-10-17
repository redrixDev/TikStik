// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.create

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.core.designsystem.component.BubbleFillProgress
import com.redrixdev.tikstik.core.designsystem.theme.TextMid
import com.redrixdev.tikstik.core.designsystem.theme.motion
import kotlinx.coroutines.delay
import kotlin.random.Random

private const val ROTATE_MS = 5_000L
private const val ALMOST = 0.85f

/** The filling bubble, "n/total" and a light-hearted line that changes every 5 s. */
@Composable
fun PreparingStatus(done: Int, total: Int, modifier: Modifier = Modifier) {
    val progress = if (total == 0) null else done / total.toFloat()
    Column(modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BubbleFillProgress(progress, Modifier.size(170.dp))
        Spacer(Modifier.height(16.dp))
        // Until the first progress update the count is unknown: the space is kept so nothing jumps.
        Text(if (total > 0) stringResource(R.string.fraction, done, total) else "", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        PreparingMessage(progress)
    }
}

// With many stickers the wait is long: a fixed text looked hung even while the bubble filled.
@Composable
private fun PreparingMessage(progress: Float?) {
    val lines = stringArrayResource(R.array.prep_fun)
    // Each wait starts at a different line, so the same few are not always the ones seen.
    var index by remember { mutableIntStateOf(Random.nextInt(lines.size)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(ROTATE_MS)
            index = (index + 1) % lines.size
        }
    }
    val text = if (progress != null && progress >= ALMOST) stringResource(R.string.prep_almost) else lines[index % lines.size]
    val fade = motion(tween<Float>(250))
    AnimatedContent(text, transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) }, label = "prep") { line ->
        Text(line, style = MaterialTheme.typography.bodyMedium, color = TextMid, textAlign = TextAlign.Center)
    }
}
