// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.access

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Where the pairing run by [PairingService] stands, so the connect screen can show it. */
@Singleton
class PairingState @Inject constructor() {
    // TIMED_OUT: Android cut the wait short; the connect screen resumes it as soon as it is visible.
    enum class Phase { IDLE, WAITING_FOR_DIALOG, WAITING_FOR_CODE, PAIRING, PAIRED, TIMED_OUT }

    private val _phase = MutableStateFlow(Phase.IDLE)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    internal fun set(phase: Phase) {
        _phase.value = phase
    }
}
