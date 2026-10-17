// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.ui

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalResources

/** Unresolved UI text: ViewModels describe it and the UI resolves it with its resources. */
@Immutable
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = listOf(count)) : UiText
    data class Join(val parts: List<UiText>, val separator: UiText? = null) : UiText
}

fun UiText.resolve(resources: Resources): String = when (this) {
    is UiText.Res -> resources.getString(id, *args.toTypedArray())
    is UiText.Plural -> resources.getQuantityString(id, count, *args.toTypedArray())
    is UiText.Join -> parts.joinToString(separator?.resolve(resources).orEmpty()) { it.resolve(resources) }
}

@Composable
fun UiText.resolve(): String = resolve(LocalResources.current)
