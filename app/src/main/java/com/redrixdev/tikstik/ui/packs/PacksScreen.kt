// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.packs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.core.designsystem.ScreenPreview
import com.redrixdev.tikstik.core.designsystem.component.EmptyState
import com.redrixdev.tikstik.core.designsystem.component.TopBar
import com.redrixdev.tikstik.core.designsystem.component.TrayImage
import com.redrixdev.tikstik.core.designsystem.component.WhatsAppMissing
import com.redrixdev.tikstik.core.designsystem.component.rememberTrayRequest
import com.redrixdev.tikstik.core.designsystem.icon.TikStikIcons
import com.redrixdev.tikstik.core.designsystem.theme.BrandGreen
import com.redrixdev.tikstik.core.designsystem.theme.BrandViolet
import com.redrixdev.tikstik.core.designsystem.theme.Danger
import com.redrixdev.tikstik.core.designsystem.theme.Motion
import com.redrixdev.tikstik.core.designsystem.theme.OnGreen
import com.redrixdev.tikstik.core.designsystem.theme.Surface1
import com.redrixdev.tikstik.core.designsystem.theme.Surface2
import com.redrixdev.tikstik.core.designsystem.theme.Surface3
import com.redrixdev.tikstik.core.designsystem.theme.TextHigh
import com.redrixdev.tikstik.core.designsystem.theme.TextLow
import com.redrixdev.tikstik.core.designsystem.theme.TextMid
import com.redrixdev.tikstik.core.designsystem.theme.TikStikTheme
import com.redrixdev.tikstik.core.designsystem.theme.Warning
import com.redrixdev.tikstik.core.designsystem.theme.motion
import com.redrixdev.tikstik.core.designsystem.theme.safeDrawingStable
import com.redrixdev.tikstik.core.ui.displayName
import com.redrixdev.tikstik.whatsapp.PackRules
import com.redrixdev.tikstik.whatsapp.WhatsAppApp
import kotlin.math.roundToInt

/** A pack in the list. */
@Immutable
data class PackCardUi(
    val id: String,
    val name: String,
    val size: Int,
    val animated: Boolean,
    val tray: TrayImage,
    val addedTo: Set<WhatsAppApp> = emptySet(),
)

@Immutable
data class PacksUiState(val packs: List<PackCardUi> = emptyList(), val installed: List<WhatsAppApp> = emptyList())

private enum class PackFilter(val label: Int) {
    ALL(R.string.packs_filter_all),
    ANIMATED(R.string.packs_filter_animated),
    STILL(R.string.packs_filter_still),
    IN_WHATSAPP(R.string.packs_filter_in),
    NOT_ADDED(R.string.packs_filter_out),
}

/** From 840 dp on, the detail goes beside the list ([detailPane]). */
@Composable
fun PacksScreen(
    state: PacksUiState,
    onBack: () -> Unit,
    onOpenPack: (String) -> Unit,
    detailPane: @Composable (packId: String, onClose: () -> Unit) -> Unit,
    onAboutRoom: () -> Unit = {},
) {
    val inWhatsApp = state.packs.count { it.addedTo.isNotEmpty() }
    val stats: @Composable () -> Unit = { Stats(inWhatsApp, PackRules.MAX_PACKS, onAboutRoom) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 840.dp) {
            var open by rememberSaveable { mutableStateOf<String?>(null) }
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(0.42f).fillMaxHeight()) {
                    PacksList(state, onBack, onOpenPack = { open = it }, selectedId = open, stats = stats)
                }
                Box(Modifier.weight(0.58f).fillMaxHeight().background(Surface1)) {
                    val id = open
                    if (id != null) {
                        detailPane(id) { open = null }
                    } else {
                        EmptyState(
                            title = stringResource(R.string.packs_pick_title),
                            body = stringResource(R.string.packs_pick_body),
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
        } else {
            PacksList(state, onBack, onOpenPack, selectedId = null, stats = stats)
        }
    }
}

@Composable
private fun PacksList(
    state: PacksUiState,
    onBack: () -> Unit,
    onOpenPack: (String) -> Unit,
    selectedId: String?,
    stats: @Composable () -> Unit,
) {
    val packs = state.packs
    var filter by rememberSaveable { mutableStateOf(PackFilter.ALL) }
    fun matches(p: PackCardUi) = when (filter) {
        PackFilter.ALL -> true
        PackFilter.ANIMATED -> p.animated
        PackFilter.STILL -> !p.animated
        PackFilter.IN_WHATSAPP -> p.addedTo.isNotEmpty()
        PackFilter.NOT_ADDED -> p.addedTo.isEmpty()
    }
    val anyShown = packs.any(::matches)
    val fade = motion<Float>()
    val size = motion(spring(dampingRatio = Motion.DAMPING, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntSize.VisibilityThreshold))
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawingStable)) {
        TopBar(stringResource(R.string.packs_title), onBack)
        if (packs.isEmpty()) {
            EmptyPacks(stats)
            return@Column
        }
        // Every item keeps its own spacing, so the ones a filter hides leave no gaps.
        val list = rememberLazyListState()
        val density = LocalDensity.current
        // What the header takes: "none with this filter" is centred in the height that remains.
        var statsHeight by remember { mutableIntStateOf(0) }
        var filters by remember { mutableIntStateOf(0) }
        val above = statsHeight
        // Only the visible height, not every scroll offset, or the list would recompose while moving.
        val viewport by remember { derivedStateOf { list.layoutInfo.viewportSize.height } }
        val free = with(density) { (viewport - above).toDp() - 24.dp }.coerceAtLeast(160.dp)
        // While scrolling (fling included), at the display's high refresh rate.
        LazyColumn(state = list, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            item(key = "stats") { Box(Modifier.onSizeChanged { statsHeight = it.height }.padding(bottom = 10.dp)) { stats() } }
            if (state.installed.isEmpty()) {
                item(key = "nowa") { Box(Modifier.padding(bottom = 10.dp)) { WhatsAppMissing(background = Surface1) } }
            }
            item(key = "filters") { Box(Modifier.onSizeChanged { filters = it.height }.padding(bottom = 12.dp)) { FilterChips(filter, onSelect = { filter = it }) } }
            // The filter only folds or unfolds each pack: nothing is recreated and no icon reloads.
            items(packs, key = { it.id }) { pack ->
                AnimatedVisibility(
                    visible = matches(pack),
                    enter = fadeIn(fade) + expandVertically(size),
                    exit = fadeOut(fade) + shrinkVertically(size),
                ) {
                    PackCard(pack, selected = pack.id == selectedId, onClick = { onOpenPack(pack.id) }, modifier = Modifier.padding(bottom = 10.dp))
                }
            }
            item(key = "none") {
                AnimatedVisibility(!anyShown, enter = fadeIn(fade), exit = fadeOut(fade)) {
                    Box(Modifier.height((free - with(density) { filters.toDp() }).coerceAtLeast(160.dp)).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            title = stringResource(R.string.packs_filter_none),
                            body = stringResource(R.string.packs_filter_none_body),
                            illustration = { Icon(TikStikIcons.FilterList, null, tint = BrandViolet, modifier = Modifier.size(56.dp)) },
                        )
                    }
                }
            }
        }
    }
}

/** No packs yet: the WhatsApp block and the empty state in the middle of what remains. */
@Composable
private fun ColumnScope.EmptyPacks(stats: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val viewport = constraints.maxHeight
        Layout(
            content = {
                Box(Modifier.padding(bottom = 10.dp)) { stats() }
                EmptyState(
                    title = stringResource(R.string.packs_empty_title),
                    body = stringResource(R.string.packs_empty_body),
                    illustration = { Icon(TikStikIcons.CollectionsBookmark, null, tint = BrandViolet, modifier = Modifier.size(56.dp)) },
                )
            },
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp),
        ) { measurables, constraints ->
            val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
            val (top, empty) = measurables.map { it.measure(loose) }
            val room = maxOf(viewport - top.height - EMPTY_BOTTOM.roundToPx(), empty.height)
            layout(constraints.maxWidth, top.height + room) {
                top.place(0, 0)
                // A little above the middle: dead centre looked low.
                empty.place((constraints.maxWidth - empty.width) / 2, top.height + ((room - empty.height) * EMPTY_BIAS).roundToInt())
            }
        }
    }
}

private val EMPTY_BOTTOM = 24.dp
private const val EMPTY_BIAS = 0.4f

@Composable
private fun FilterChips(selected: PackFilter, onSelect: (PackFilter) -> Unit) {
    // Tabs of one group, like the selector on Home.
    LazyRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(PackFilter.entries) { f ->
            val on = f == selected
            val background by animateColorAsState(if (on) BrandGreen else Surface1, label = "chipBg")
            val content by animateColorAsState(if (on) OnGreen else TextHigh, label = "chipFg")
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(background)
                    .selectable(selected = on, onClick = { onSelect(f) }, role = Role.Tab)
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(f.label), style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1) }
        }
    }
}

/** How many packs are in WhatsApp out of the [room] that fit there, one bar per slot. */
@Composable
private fun Stats(inWhatsApp: Int, room: Int, onClick: () -> Unit) {
    val label = stringResource(R.string.packs_room_info)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Surface1)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.packs_in_whatsapp_of, inWhatsApp, room), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            Icon(TikStikIcons.Info, null, tint = TextLow, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(10.dp))
        val full = inWhatsApp >= room
        val busy = inWhatsApp > room / 2
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            val gap = 6.dp.toPx()
            val unit = (size.width - gap * (room - 1)) / room
            for (i in 0 until room) {
                val color = when {
                    i < inWhatsApp && full -> Danger
                    i < inWhatsApp && busy -> Warning
                    i < inWhatsApp -> BrandGreen
                    else -> Surface3
                }
                drawRoundRect(color, topLeft = Offset(i * (unit + gap), 0f), size = Size(unit, size.height), cornerRadius = CornerRadius(size.height / 2f))
            }
        }
    }
}

/** A pack in the list: always the same height, with its state as icons (play if animated, WhatsApp if added). */
@Composable
private fun PackCard(pack: PackCardUi, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(MaterialTheme.shapes.large)
            .background(if (selected) Surface2 else Surface1)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp).clip(MaterialTheme.shapes.medium).background(Surface2), contentAlignment = Alignment.Center) {
            AsyncImage(model = rememberTrayRequest(pack.tray), contentDescription = null, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(pack.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(pluralStringResource(R.plurals.pack_count, pack.size, pack.size), style = MaterialTheme.typography.bodySmall, color = TextMid)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pack.addedTo.isNotEmpty()) {
                val description = stringResource(R.string.pack_status_in, WhatsAppApp.entries.filter { it in pack.addedTo }.map { it.displayName() }.joinToString())
                Image(
                    painterResource(R.drawable.ic_whatsapp),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp).semantics { contentDescription = description }.padding(2.dp),
                )
            }
            if (pack.animated) {
                val description = stringResource(R.string.pack_animated)
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(BrandViolet.copy(alpha = 0.18f)).semantics { contentDescription = description },
                    contentAlignment = Alignment.Center,
                ) { Icon(TikStikIcons.PlayArrow, null, tint = BrandViolet, modifier = Modifier.size(18.dp)) }
            }
        }
        Spacer(Modifier.width(6.dp))
        Icon(TikStikIcons.KeyboardArrowRight, contentDescription = null, tint = TextMid)
    }
}

@ScreenPreview
@Composable
private fun PacksEmptyPreview() {
    TikStikTheme { PacksScreen(PacksUiState(installed = listOf(WhatsAppApp.CONSUMER)), {}, {}, detailPane = { _, _ -> }) }
}
