// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.packs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.core.designsystem.component.TrayImage
import com.redrixdev.tikstik.core.ui.Notice
import com.redrixdev.tikstik.core.ui.Notices
import com.redrixdev.tikstik.core.ui.UiText
import com.redrixdev.tikstik.packs.PackRepository
import com.redrixdev.tikstik.whatsapp.WhatsAppPacks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PacksViewModel @Inject constructor(
    packs: PackRepository,
    whatsApp: WhatsAppPacks,
    private val notices: Notices,
) : ViewModel() {

    /** null until the packs are read, so the empty state is not drawn too early. */
    val state: StateFlow<PacksUiState?> = combine(packs.packs, whatsApp.installed, whatsApp.added) { all, installed, added ->
        PacksUiState(
            packs = all.map {
                val pack = it.pack
                PackCardUi(pack.id, pack.name, it.stickerIds.size, pack.animated, TrayImage(packs.tray(pack.id), pack.imageDataVersion.toLong()), added[pack.id].orEmpty())
            },
            installed = installed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Why that maximum: WhatsApp's own rule. */
    fun aboutRoom() = notices.post(Notice(UiText.Res(R.string.packs_room_whatsapp)))
}
