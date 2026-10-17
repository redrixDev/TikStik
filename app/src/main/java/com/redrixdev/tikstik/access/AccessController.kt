// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.access

import kotlinx.coroutines.flow.StateFlow

// READY_MANUAL: paired, but the phone refuses to grant the permission; the user turns debugging on.
enum class AccessStep { NEEDS_STORAGE_PERMISSION, NEEDS_DEV_OPTIONS, NEEDS_PAIRING, NEEDS_FIRST_CONNECTION, READY_MANUAL, READY }

data class AccessStatus(val step: AccessStep, val onWifi: Boolean = true, val wirelessDebuggingOn: Boolean = false) {
    val ready: Boolean get() = step == AccessStep.READY || step == AccessStep.READY_MANUAL
}

/** Access to TikTok's cache: through ADB from Android 11 and with the storage permission before that. */
interface AccessController {
    /** Android 8–10: the wizard only has the permission step. */
    val legacyStorage: Boolean
    val status: StateFlow<AccessStatus>

    /** Re-reads the state (on returning to the app or during the wizard). */
    fun refresh()

    /** One read session at a time; on exit the system is left as it was. Errors: [AccessException]. */
    suspend fun <T> withReader(
        onConnected: () -> Unit = {},
        onNetworkApproval: () -> Unit = {},
        block: suspend (CacheReader) -> T,
    ): T

    /** Forgets the pairing and the identity. */
    fun revoke()
}
