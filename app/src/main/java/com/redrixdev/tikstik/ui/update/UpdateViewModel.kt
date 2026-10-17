// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redrixdev.tikstik.update.UpdateState
import com.redrixdev.tikstik.update.Updates
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(private val updates: Updates) : ViewModel() {

    val state: StateFlow<UpdateState> = updates.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpdateState())

    init {
        updates.checkIfDue()
    }

    fun download() {
        updates.download()
    }

    fun later() {
        updates.skip()
    }

    fun installerOpened() = updates.installerOpened()
}
