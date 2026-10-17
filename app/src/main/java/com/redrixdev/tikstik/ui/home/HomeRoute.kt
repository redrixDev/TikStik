// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.home

import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HomeRoute(
    onOpenPacks: () -> Unit,
    onOpenSettings: () -> Unit,
    onConnect: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Startup ends when the collection is visible, not at the first frame (Baseline Profile and macrobenchmark).
    ReportDrawnWhen { state.loaded }
    // Coming back from Settings, access may have changed.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    HomeScreen(
        state = state,
        onAction = viewModel::onAction,
        onOpenPacks = onOpenPacks,
        onOpenSettings = onOpenSettings,
        onConnect = onConnect,
        scrollRequests = viewModel.scrollRequests,
    )
}
