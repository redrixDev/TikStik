// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.create

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.core.designsystem.component.AnimatedCheck
import com.redrixdev.tikstik.core.designsystem.component.AppSheet
import com.redrixdev.tikstik.core.designsystem.component.PrimaryButton
import com.redrixdev.tikstik.core.designsystem.component.QuietButton
import com.redrixdev.tikstik.core.designsystem.component.SecondaryButton
import com.redrixdev.tikstik.core.designsystem.component.WhatsAppMissing
import com.redrixdev.tikstik.core.designsystem.icon.TikStikIcons
import com.redrixdev.tikstik.core.designsystem.theme.BrandGreen
import com.redrixdev.tikstik.core.designsystem.theme.BrandViolet
import com.redrixdev.tikstik.core.designsystem.theme.Surface2
import com.redrixdev.tikstik.core.designsystem.theme.Surface3
import com.redrixdev.tikstik.core.designsystem.theme.TextMid
import com.redrixdev.tikstik.core.designsystem.theme.navigationBarsStable
import com.redrixdev.tikstik.core.ui.displayName
import com.redrixdev.tikstik.ui.packs.WhatsAppActions
import com.redrixdev.tikstik.whatsapp.PackRules
import com.redrixdev.tikstik.whatsapp.WhatsAppApp
import java.io.File

/** A pack about to be created: its kind and its stickers. */
@Immutable
data class NewPackUi(val animated: Boolean, val files: List<File>)

@Immutable
data class CreatedPackUi(val id: String, val name: String, val size: Int, val addedTo: Set<WhatsAppApp> = emptySet())

enum class SkipReason { CONVERT, TRAY }

/** Stickers left out of the packs, and why. */
@Immutable
data class SkipUi(val reason: SkipReason, val count: Int)

@Immutable
sealed interface CreateUi {
    /** [limit]: the maximum number of packs, when what was chosen does not fit entirely. */
    data class Review(val packs: List<NewPackUi>, val limit: Int? = null) : CreateUi
    data class Working(val done: Int, val total: Int, val finished: Boolean = false) : CreateUi
    data class Done(val created: List<CreatedPackUi>, val skipped: List<SkipUi>, val installed: List<WhatsAppApp>) : CreateUi
}

/** Creating packs: summary, progress and result. It cannot be dismissed while working. */
@Composable
fun CreateSheet(
    state: CreateUi,
    onCreate: () -> Unit,
    onAddToWhatsApp: (packId: String, app: WhatsAppApp) -> Unit,
    onViewPacks: () -> Unit,
    onDismiss: () -> Unit,
) {
    val busy = state is CreateUi.Working && !state.finished
    AppSheet(onDismissRequest = onDismiss, skipPartiallyExpanded = true, dismissible = !busy) { close ->
        // When adding to an existing pack there is no result to show: the sheet closes by itself.
        val finished = (state as? CreateUi.Working)?.finished == true
        LaunchedEffect(finished) { if (finished) close(onDismiss) }
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "create",
        ) { current ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBarsStable)
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            ) {
                when (current) {
                    is CreateUi.Review -> ReviewContent(current, onCreate)
                    is CreateUi.Working -> PreparingStatus(current.done, current.total)
                    is CreateUi.Done -> DoneContent(current, onAddToWhatsApp, { close(onViewPacks) }, { close(onDismiss) })
                }
            }
        }
    }
}

@Composable
private fun ReviewContent(review: CreateUi.Review, onCreate: () -> Unit) {
    Text(stringResource(R.string.create_title), style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(16.dp))
    review.packs.forEach { pack ->
        PackPreview(
            title = stringResource(if (pack.animated) R.string.create_new_pack_animated else R.string.create_new_pack),
            count = pluralStringResource(R.plurals.pack_count, pack.files.size, pack.files.size),
            animated = pack.animated,
            files = pack.files,
        )
    }
    review.limit?.let { limit -> LimitNote(limit, review.packs.isNotEmpty()) }
    Spacer(Modifier.height(16.dp))
    PrimaryButton(stringResource(R.string.create_action), onClick = onCreate, modifier = Modifier.fillMaxWidth(), enabled = review.packs.isNotEmpty())
}

// One quiet line with the limit.
@Composable
private fun LimitNote(limit: Int, someFit: Boolean) {
    Text(
        stringResource(if (someFit) R.string.create_limit else R.string.create_limit_full, limit),
        style = MaterialTheme.typography.bodySmall,
        color = TextMid,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

@Composable
private fun DoneContent(
    done: CreateUi.Done,
    onAddToWhatsApp: (String, WhatsAppApp) -> Unit,
    onViewPacks: () -> Unit,
    onDismiss: () -> Unit,
) {
    val anythingDone = done.created.isNotEmpty()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (anythingDone) {
            AnimatedCheck(Modifier.padding(top = 8.dp))
        } else {
            Box(
                Modifier.padding(top = 8.dp).size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Icon(TikStikIcons.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp)) }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(if (anythingDone) R.string.create_done_title else R.string.create_nothing),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        if (!anythingDone) {
            Text(stringResource(R.string.create_nothing_body), style = MaterialTheme.typography.bodyMedium, color = TextMid, textAlign = TextAlign.Center)
        }
    }
    Spacer(Modifier.height(16.dp))
    val sendable = done.created.filter { it.size >= PackRules.MIN_STICKERS }
    // With several packs, each gets its own button: WhatsApp asks to add them one at a time.
    val perPack = sendable.size > 1 && done.installed.isNotEmpty()
    done.created.forEach { pack ->
        PlanLine(TikStikIcons.Inventory2, BrandGreen, pack.name, pluralStringResource(R.plurals.pack_count, pack.size, pack.size))
        if (perPack) {
            WhatsAppActions(
                size = pack.size,
                installed = done.installed,
                addedTo = pack.addedTo,
                onAdd = { onAddToWhatsApp(pack.id, it) },
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
        }
    }
    done.skipped.forEach { SkipLine(it) }
    Spacer(Modifier.height(18.dp))
    // Only packs of 3 or more go to WhatsApp; a smaller one stays in TikStik until it is completed.
    if (sendable.isEmpty() && done.created.isNotEmpty()) {
        val missing = PackRules.MIN_STICKERS - done.created.maxOf { it.size }
        Text(pluralStringResource(R.plurals.pack_wa_needs, missing, missing), style = MaterialTheme.typography.bodyMedium, color = TextMid)
        Spacer(Modifier.height(12.dp))
    }
    if (sendable.isNotEmpty()) {
        if (done.installed.isEmpty()) {
            WhatsAppMissing(background = Surface2, modifier = Modifier.padding(vertical = 4.dp))
            Spacer(Modifier.height(12.dp))
        }
        if (!perPack) sendable.singleOrNull()?.let { pack ->
            done.installed.forEachIndexed { i, app ->
                val label = stringResource(R.string.create_done_add, app.displayName())
                if (i == 0) {
                    PrimaryButton(label, { onAddToWhatsApp(pack.id, app) }, Modifier.fillMaxWidth())
                } else {
                    SecondaryButton(label, { onAddToWhatsApp(pack.id, app) }, Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
        }
        QuietButton(stringResource(R.string.create_done_view), onClick = onViewPacks, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    } else {
        PrimaryButton(stringResource(R.string.action_done), onDismiss, Modifier.fillMaxWidth())
    }
}

/** A pack about to be created: its kind, how many stickers and the first thumbnails. */
@Composable
private fun PackPreview(title: String, count: String, animated: Boolean, files: List<File>) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(Surface2)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val tint = if (animated) BrandViolet else BrandGreen
            Box(Modifier.size(30.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(if (animated) TikStikIcons.PlayArrow else TikStikIcons.Image, null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1)
            Text(count, style = MaterialTheme.typography.labelLarge, color = TextMid)
        }
        Spacer(Modifier.height(10.dp))
        val shown = files.take(6)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            shown.forEach { file ->
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Surface3),
                )
            }
            if (files.size > shown.size) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Surface3), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.more_count, files.size - shown.size), style = MaterialTheme.typography.labelMedium, color = TextMid)
                }
            }
        }
    }
}

@Composable
private fun PlanLine(icon: ImageVector, tint: Color, title: String, trailing: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(Surface2)
            .heightIn(min = 56.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = 10.dp),
        )
        Text(trailing, style = MaterialTheme.typography.labelLarge, color = TextMid)
    }
}

/** What was left out: one more row in the same style. */
@Composable
private fun SkipLine(skip: SkipUi) {
    val title = when (skip.reason) {
        SkipReason.CONVERT -> stringResource(R.string.skip_convert)
        SkipReason.TRAY -> stringResource(R.string.skip_tray)
    }
    PlanLine(TikStikIcons.RemoveCircleOutline, TextMid, title, pluralStringResource(R.plurals.pack_count, skip.count, skip.count))
}
