// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.create

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.core.designsystem.component.AppSheet
import com.redrixdev.tikstik.core.designsystem.component.TrayImage
import com.redrixdev.tikstik.core.designsystem.component.rememberTrayRequest
import com.redrixdev.tikstik.core.designsystem.icon.TikStikIcons
import com.redrixdev.tikstik.core.designsystem.theme.BrandGreen
import com.redrixdev.tikstik.core.designsystem.theme.BrandViolet
import com.redrixdev.tikstik.core.designsystem.theme.OnGreen
import com.redrixdev.tikstik.core.designsystem.theme.Surface2
import com.redrixdev.tikstik.core.designsystem.theme.Surface3
import com.redrixdev.tikstik.core.designsystem.theme.TextMid
import com.redrixdev.tikstik.core.designsystem.theme.Warning
import com.redrixdev.tikstik.core.designsystem.theme.navigationBarsStable

/** A pack the selection could go to: [wanted] are those of its kind it does not have yet and [fits], those that fit. */
@Immutable
data class PackFitUi(
    val id: String,
    val name: String,
    val tray: TrayImage,
    val animated: Boolean,
    val size: Int,
    val wanted: Int,
    val fits: Int,
)

/** "Which pack?": [newPacks] is how many new packs would come out; [packs], the existing ones of the same kind with room. */
@Immutable
data class PackChoiceUi(val animated: Int, val still: Int, val newPacks: Int, val packs: List<PackFitUi>)

/** The selection goes to a new pack or to one you already have; what does not fit stays selected to go to another. */
@Composable
fun PackChoiceSheet(choice: PackChoiceUi, onNew: () -> Unit, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AppSheet(onDismissRequest = onDismiss, skipPartiallyExpanded = true) { close ->
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBarsStable).padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
            Text(stringResource(R.string.pack_choice_title), style = MaterialTheme.typography.headlineSmall)
            val parts = buildList {
                if (choice.animated > 0) add(pluralStringResource(R.plurals.sel_animated, choice.animated, choice.animated))
                if (choice.still > 0) add(pluralStringResource(R.plurals.sel_still, choice.still, choice.still))
            }
            Text(parts.joinToString(stringResource(R.string.separator)), style = MaterialTheme.typography.bodyMedium, color = TextMid, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(16.dp))
            LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "new") {
                    ChoiceRow(enabled = choice.newPacks > 0, onClick = { close(onNew) }) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(BrandGreen), contentAlignment = Alignment.Center) {
                            Icon(TikStikIcons.Add, null, tint = OnGreen)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.pack_choice_new), style = MaterialTheme.typography.titleMedium)
                            Text(
                                pluralStringResource(R.plurals.pack_choice_new_count, choice.newPacks, choice.newPacks),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMid,
                            )
                        }
                    }
                }
                items(choice.packs, key = { it.id }) { fit -> FitRow(fit) { id -> close { onPick(id) } } }
            }
        }
    }
}

@Composable
private fun FitRow(fit: PackFitUi, onPick: (String) -> Unit) {
    ChoiceRow(enabled = fit.fits > 0, onClick = { onPick(fit.id) }) {
        AsyncImage(
            model = rememberTrayRequest(fit.tray),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Surface3),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(fit.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            val (status, color) = when {
                fit.wanted == 0 -> stringResource(R.string.pack_choice_has_them) to TextMid
                fit.fits < fit.wanted -> pluralStringResource(R.plurals.pack_choice_partial, fit.fits, fit.fits, fit.wanted) to Warning
                else -> pluralStringResource(R.plurals.pack_choice_fits, fit.fits, fit.fits) to BrandGreen
            }
            Row {
                Text(status, style = MaterialTheme.typography.bodySmall, color = color)
                Text(stringResource(R.string.separator) + stringResource(R.string.pack_choice_room, fit.size), style = MaterialTheme.typography.bodySmall, color = TextMid)
            }
        }
        if (fit.animated) Icon(TikStikIcons.PlayArrow, null, tint = BrandViolet, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ChoiceRow(enabled: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .clip(MaterialTheme.shapes.medium)
            .background(Surface2)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
