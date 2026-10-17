// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.packs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PacksRoute(onBack: () -> Unit, onOpenPack: (String) -> Unit, viewModel: PacksViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    state?.let {
        PacksScreen(
            state = it,
            onBack = onBack,
            onAboutRoom = viewModel::aboutRoom,
            onOpenPack = onOpenPack,
            detailPane = { packId, onClose -> PackDetailRoute(packId, onClose, showBack = false) },
        )
    }
}
