// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.packs

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.core.designsystem.ScreenPreview
import com.redrixdev.tikstik.core.designsystem.component.AppSheet
import com.redrixdev.tikstik.core.designsystem.component.BrandProgressBar
import com.redrixdev.tikstik.core.designsystem.component.ConfirmDialog
import com.redrixdev.tikstik.core.designsystem.component.PrimaryButton
import com.redrixdev.tikstik.core.designsystem.component.SecondaryButton
import com.redrixdev.tikstik.core.designsystem.component.StatusPill
import com.redrixdev.tikstik.core.designsystem.component.StickerThumb
import com.redrixdev.tikstik.core.designsystem.component.ThumbBadges
import com.redrixdev.tikstik.core.designsystem.component.TopBar
import com.redrixdev.tikstik.core.designsystem.component.TrayImage
import com.redrixdev.tikstik.core.designsystem.component.rememberTrayRequest
import com.redrixdev.tikstik.core.designsystem.icon.TikStikIcons
import com.redrixdev.tikstik.core.designsystem.theme.BrandGreen
import com.redrixdev.tikstik.core.designsystem.theme.Motion
import com.redrixdev.tikstik.core.designsystem.theme.Outline
import com.redrixdev.tikstik.core.designsystem.theme.Surface1
import com.redrixdev.tikstik.core.designsystem.theme.Surface2
import com.redrixdev.tikstik.core.designsystem.theme.TextHigh
import com.redrixdev.tikstik.core.designsystem.theme.TextMid
import com.redrixdev.tikstik.core.designsystem.theme.TikStikTheme
import com.redrixdev.tikstik.core.designsystem.theme.motion
import com.redrixdev.tikstik.core.designsystem.theme.navigationBarsStable
import com.redrixdev.tikstik.core.designsystem.theme.safeDrawingStable
import com.redrixdev.tikstik.core.ui.StickerUi
import com.redrixdev.tikstik.whatsapp.PackRules
import com.redrixdev.tikstik.whatsapp.WhatsAppApp
import java.io.File

/** Progress of "Add stickers" to a pack. */
@Immutable
data class AddingProgress(val done: Int, val total: Int)

/** A sticker inside the pack, in its order: the first one is the cover. */
@Immutable
data class PackStickerUi(val id: String, val file: File)

/** [offered]: in WhatsApp or being added, so it cannot drop below 3. */
@Immutable
data class PackDetailUi(
    val id: String,
    val name: String,
    val animated: Boolean,
    val stickers: List<PackStickerUi>,
    val tray: TrayImage,
    val offered: Boolean,
    val installed: List<WhatsAppApp>,
    val addedTo: Set<WhatsAppApp>,
    val addable: List<StickerUi>,
    val adding: AddingProgress? = null,
)

@Composable
fun PackDetailScreen(
    pack: PackDetailUi,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onRemove: (Set<String>) -> Unit,
    onAddStickers: (Set<String>) -> Unit,
    onAddToWhatsApp: (WhatsAppApp) -> Unit,
    onDelete: () -> Unit,
    showBack: Boolean = true,
) {
    var selection by rememberSaveable(pack.id) { mutableStateOf(setOf<String>()) }
    // Dialogs and sheets that survive the screen being recreated.
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var addingSheet by rememberSaveable { mutableStateOf(false) }
    BackHandler(selection.isNotEmpty()) { selection = emptySet() }

    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawingStable)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(title = null, onBack = if (showBack) onBack else null) {
                IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(48.dp)) {
                    Icon(TikStikIcons.DeleteOutline, contentDescription = stringResource(R.string.pack_delete), tint = MaterialTheme.colorScheme.error)
                }
            }
            // The cover follows the saved order: it does not jump while dragging; the new one gets its layers once settled.
            val coverPhase = rememberCoverPhase()
            val coverKey = pack.stickers.firstOrNull()?.id
            val appear = remember(coverKey) { Animatable(0f) }
            LaunchedEffect(coverKey) { appear.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 180f)) }
            ReorderableGrid(
                items = pack.stickers,
                key = { it.id },
                onReorder = { order -> onReorder(order.map { it.id }) },
                emptySlots = PackRules.MAX_STICKERS - pack.stickers.size,
                onEmptySlot = { addingSheet = true },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                header = {
                    PackHeader(
                        pack = pack,
                        coverPhase = coverPhase,
                        appear = { appear.value },
                        onAddToWhatsApp = onAddToWhatsApp,
                        onRename = { renaming = true },
                    )
                },
            ) { sticker, index, dragging, actions ->
                val selected = sticker.id in selection
                Box(Modifier.coverFrame(enabled = index == 0 && sticker.id == coverKey, phase = coverPhase, appear = { appear.value })) {
                    StickerThumb(
                        file = sticker.file,
                        selected = selected,
                        selectionMode = selection.isNotEmpty() && !dragging,
                        onClick = { selection = if (selected) selection - sticker.id else selection + sticker.id },
                        modifier = actions,
                        badges = ThumbBadges(),
                    )
                }
            }
        }

        // The remove bar keeps the last selection while it leaves: with the selection already empty it said "Remove 0".
        var shown by remember { mutableStateOf(selection) }
        if (selection.isNotEmpty()) shown = selection
        AnimatedVisibility(
            selection.isNotEmpty(),
            Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(motion(Motion.offset())) { it } + fadeIn(),
            exit = slideOutVertically(motion(Motion.offset())) { it } + fadeOut(),
        ) {
            val remaining = pack.stickers.size - shown.size
            val minimum = PackRules.minimumKept(pack.offered)
            Row(
                Modifier
                    .windowInsetsPadding(WindowInsets.navigationBarsStable)
                    .padding(12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(36.dp))
                    .background(Surface2)
                    .padding(start = 8.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { selection = emptySet() }, modifier = Modifier.size(48.dp)) {
                    Icon(TikStikIcons.Close, contentDescription = stringResource(R.string.action_clear_selection))
                }
                if (remaining < minimum) {
                    // Not allowed: the reason in one line instead of a disabled button.
                    Text(
                        stringResource(if (pack.offered) R.string.pack_remove_min else R.string.pack_remove_last),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMid,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                    PrimaryButton(
                        pluralStringResource(R.plurals.pack_remove, shown.size, shown.size),
                        onClick = {
                            onRemove(selection)
                            selection = emptySet()
                        },
                        icon = TikStikIcons.RemoveCircleOutline,
                    )
                }
            }
        }
    }

    if (renaming) {
        RenameDialog(pack.name, onDismiss = { renaming = false }) {
            onRename(it)
            renaming = false
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.pack_delete_title, pack.name),
            body = stringResource(R.string.pack_delete_body),
            confirm = stringResource(R.string.action_delete),
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
    if (addingSheet) {
        // Adding clears the selection to remove: the remove bar must not linger alongside new stickers.
        AddStickersSheet(
            pack,
            onAdd = { ids ->
                selection = emptySet()
                onAddStickers(ids)
            },
            onDismiss = { addingSheet = false },
        )
    }
}

/** Header: the cover at full size with its layers, the name (tap to rename), the size and the WhatsApp actions. */
@Composable
private fun PackHeader(
    pack: PackDetailUi,
    coverPhase: State<Float>?,
    appear: () -> Float,
    onAddToWhatsApp: (WhatsAppApp) -> Unit,
    onRename: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(176.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .padding(start = 16.dp, top = 16.dp)
                    .size(128.dp)
                    .coverFrame(enabled = true, phase = coverPhase, appear = appear, corner = 32.dp, step = 8.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(Surface1)
                    .padding(18.dp),
            ) {
                AsyncImage(model = rememberTrayRequest(pack.tray), contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
        val renameLabel = stringResource(R.string.pack_rename)
        // The pencil shows the name can be changed; the whole row is one tap target.
        Row(
            Modifier
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClickLabel = renameLabel, onClick = onRename)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                pack.name,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Icon(TikStikIcons.DriveFileRenameOutline, contentDescription = null, tint = TextMid, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val size = pack.stickers.size
            Text(pluralStringResource(R.plurals.pack_count, size, size), style = MaterialTheme.typography.bodyLarge, color = TextMid)
            if (pack.animated) StatusPill(stringResource(R.string.pack_animated), positive = false, icon = TikStikIcons.PlayArrow)
        }
        Spacer(Modifier.height(20.dp))
        WhatsAppActions(pack.stickers.size, pack.installed, pack.addedTo, onAddToWhatsApp)
        Spacer(Modifier.height(20.dp))
        // Fixed height: the bar appears without moving the grid.
        Box(Modifier.fillMaxWidth().height(10.dp)) {
            pack.adding?.let { BrandProgressBar(if (it.total == 0) null else it.done / it.total.toFloat(), Modifier.fillMaxWidth(), height = 6.dp) }
        }
    }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        shape = MaterialTheme.shapes.large,
        title = { Text(stringResource(R.string.pack_rename_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(PackRules.MAX_NAME) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandGreen, cursorColor = BrandGreen, unfocusedBorderColor = Outline),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_save), color = BrandGreen, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = TextHigh, style = MaterialTheme.typography.labelLarge) }
        },
    )
}

/** Pick from the collection (same kind, not already inside) until the pack is full. */
@Composable
private fun AddStickersSheet(pack: PackDetailUi, onAdd: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    val room = PackRules.MAX_STICKERS - pack.stickers.size
    var chosen by rememberSaveable { mutableStateOf(setOf<String>()) }
    AppSheet(onDismissRequest = onDismiss, skipPartiallyExpanded = true) { close ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(
                stringResource(R.string.add_stickers_title, pack.name),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(pluralStringResource(R.plurals.add_stickers_room, room, room), style = MaterialTheme.typography.bodyMedium, color = TextMid)
            if (pack.addable.isEmpty()) {
                Text(
                    stringResource(if (pack.animated) R.string.add_stickers_none_animated else R.string.add_stickers_none_still),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 32.dp),
                )
            } else {
                val grid = rememberLazyGridState()
                // While scrolling (fling included), at the display's high refresh rate.
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    state = grid,
                    modifier = Modifier.fillMaxWidth().height(380.dp).padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(pack.addable, key = { it.id }) { sticker ->
                        val selected = sticker.id in chosen
                        StickerThumb(
                            file = sticker.file,
                            selected = selected,
                            selectionMode = true,
                            onClick = {
                                chosen = when {
                                    selected -> chosen - sticker.id
                                    chosen.size < room -> chosen + sticker.id
                                    else -> chosen
                                }
                            },
                            badges = ThumbBadges(animated = sticker.animated),
                        )
                    }
                }
            }
            Column(Modifier.windowInsetsPadding(WindowInsets.navigationBarsStable).padding(vertical = 12.dp)) {
                PrimaryButton(
                    pluralStringResource(R.plurals.add_stickers_action, chosen.size, chosen.size),
                    onClick = {
                        onAdd(chosen)
                        close(onDismiss)
                    },
                    enabled = chosen.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryButton(stringResource(R.string.action_cancel), { close(onDismiss) }, Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
    }
}

@ScreenPreview
@Composable
private fun PackDetailPreview() {
    TikStikTheme {
        PackDetailScreen(
            pack = PackDetailUi(
                id = "p",
                name = "TikStik 1",
                animated = false,
                stickers = emptyList(),
                tray = TrayImage(File("tray.png"), 0),
                offered = false,
                installed = listOf(WhatsAppApp.CONSUMER),
                addedTo = emptySet(),
                addable = emptyList(),
            ),
            onBack = {},
            onRename = {},
            onReorder = {},
            onRemove = {},
            onAddStickers = {},
            onAddToWhatsApp = {},
            onDelete = {},
        )
    }
}
