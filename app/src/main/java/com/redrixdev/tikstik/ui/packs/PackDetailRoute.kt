// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.packs

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** A pack's detail; the arrow, the back gesture and delete all leave through the same path. */
@Composable
fun PackDetailRoute(packId: String, onClose: () -> Unit, showBack: Boolean = true) {
    val viewModel = hiltViewModel<PackDetailViewModel, PackDetailViewModel.Factory>(key = packId) { it.create(packId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val close by rememberUpdatedState(onClose)
    LaunchedEffect(viewModel) { viewModel.exits.collect { close() } }
    // Beside the list there is nothing to pop: back only closes the pane.
    BackHandler(enabled = !showBack) { viewModel.close() }
    state?.let { pack ->
        PackDetailScreen(
            pack = pack,
            onBack = viewModel::close,
            onRename = viewModel::rename,
            onReorder = viewModel::reorder,
            onRemove = viewModel::remove,
            onAddStickers = viewModel::addStickers,
            onAddToWhatsApp = viewModel::addToWhatsApp,
            onDelete = viewModel::delete,
            showBack = showBack,
        )
    }
}
