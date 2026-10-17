// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.core.designsystem.icon.TikStikIcons
import com.redrixdev.tikstik.core.designsystem.theme.BrandGreen
import com.redrixdev.tikstik.core.designsystem.theme.Ink
import com.redrixdev.tikstik.core.designsystem.theme.LocalReduceMotion
import com.redrixdev.tikstik.core.designsystem.theme.Motion
import com.redrixdev.tikstik.core.designsystem.theme.OnGreen
import com.redrixdev.tikstik.core.designsystem.theme.Surface1
import com.redrixdev.tikstik.core.designsystem.theme.TextHigh
import com.redrixdev.tikstik.core.designsystem.theme.motion
import java.io.File

/** A thumbnail's badges: animated, already in a pack, or a short note at the bottom. */
data class ThumbBadges(
    val animated: Boolean = false,
    val inPack: Boolean = false,
    val note: String? = null,
)

/** Sticker thumbnail. When selected it shrinks with a bounce and the green border and the check dot appear. */
@Composable
fun StickerThumb(
    file: File,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    badges: ThumbBadges = ThumbBadges(),
    imageModifier: Modifier = Modifier,
    corner: Dp = 20.dp,
) {
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (selected) 0.84f else 1f, motion(Motion.bouncy()), label = "thumb")
    val shape = RoundedCornerShape(corner)
    val selectedText = stringResource(R.string.cd_selected)
    val notSelectedText = stringResource(R.string.cd_not_selected)
    val description = buildList {
        add(stringResource(R.string.cd_sticker))
        if (badges.animated) add(stringResource(R.string.badge_animated))
        if (badges.inPack) add(stringResource(R.string.badge_in_pack))
        badges.note?.let(::add)
    }.joinToString(", ")
    Box(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Surface1)
            .then(if (selected) Modifier.border(2.5.dp, BrandGreen, shape) else Modifier)
            .combinedClickable(
                onClick = {
                    if (selectionMode) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onClick()
                },
                onLongClick = onLongClick?.let { longClick ->
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        longClick()
                    }
                },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = description
                if (selectionMode) {
                    role = Role.Checkbox
                    this.selected = selected
                    stateDescription = if (selected) selectedText else notSelectedText
                }
            },
    ) {
        val context = LocalContext.current
        // Coil's crossfade runs on its own clock: with "Remove animations" it is left out.
        val reduce = LocalReduceMotion.current
        val request = remember(file, reduce) { ImageRequest.Builder(context).data(file).apply { if (!reduce) crossfade(150) }.build() }
        AsyncImage(
            model = request,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().padding(10.dp).scale(scale).then(imageModifier),
        )
        if (badges.note == null && badges.animated) {
            Row(Modifier.align(Alignment.BottomStart).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MiniBadge(TikStikIcons.PlayArrow, TextHigh)
            }
        }
        if (badges.inPack && !selectionMode) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(TikStikIcons.Check, null, tint = BrandGreen, modifier = Modifier.size(12.dp)) }
        }
        badges.note?.let { note ->
            Text(
                note,
                style = MaterialTheme.typography.labelSmall,
                color = TextHigh,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Ink.copy(alpha = 0.78f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        SelectionDot(selected = selected, visible = selectionMode, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
    }
}

@Composable
private fun MiniBadge(icon: ImageVector, tint: Color) {
    Box(
        Modifier.size(20.dp).clip(CircleShape).background(Ink.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(13.dp)) }
}

@Composable
fun SelectionDot(selected: Boolean, visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible, modifier, enter = scaleIn(motion()) + fadeIn(), exit = scaleOut(motion()) + fadeOut()) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (selected) BrandGreen else Ink.copy(alpha = 0.55f))
                .then(if (selected) Modifier else Modifier.border(2.dp, TextHigh.copy(alpha = 0.9f), CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(selected, enter = scaleIn(motion(Motion.bouncy())), exit = scaleOut(motion())) {
                Icon(TikStikIcons.Check, null, tint = OnGreen, modifier = Modifier.size(16.dp))
            }
        }
    }
}
