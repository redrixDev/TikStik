// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.nav

import com.redrixdev.tikstik.ui.info.InfoPage
import kotlinx.serialization.Serializable

@Serializable data object WelcomeDestination
@Serializable data object ConnectDestination
/** [onboarding]: just connected, no back arrow; from Settings, with it. */
@Serializable data class HowToDestination(val onboarding: Boolean = false)
@Serializable data object HomeDestination
@Serializable data object PacksDestination
@Serializable data class PackDestination(val packId: String)
@Serializable data object SettingsDestination
@Serializable data class InfoDestination(val page: InfoPage)
