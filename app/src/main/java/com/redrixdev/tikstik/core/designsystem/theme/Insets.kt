// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

@file:OptIn(ExperimentalLayoutApi::class)

package com.redrixdev.tikstik.core.designsystem.theme

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable

// The bars count even when hidden: coming back from a full-screen ad they took a second to return and everything jumped.

val WindowInsets.Companion.statusBarsStable: WindowInsets @Composable get() = statusBarsIgnoringVisibility

val WindowInsets.Companion.navigationBarsStable: WindowInsets @Composable get() = navigationBarsIgnoringVisibility

/** Like `safeDrawing` (bars, display cutout and keyboard), but counting the bars even when they are not visible. */
val WindowInsets.Companion.safeDrawingStable: WindowInsets @Composable get() = systemBarsIgnoringVisibility.union(displayCutout).union(ime)
