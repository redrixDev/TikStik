// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.connect

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.access.AccessController
import com.redrixdev.tikstik.access.AccessStep
import com.redrixdev.tikstik.access.AdbAccess
import com.redrixdev.tikstik.access.DeviceBrand
import com.redrixdev.tikstik.access.PairingState
import com.redrixdev.tikstik.access.PairingState.Phase
import com.redrixdev.tikstik.core.common.ApplicationScope
import com.redrixdev.tikstik.core.common.Logger
import com.redrixdev.tikstik.core.ui.Notice
import com.redrixdev.tikstik.core.ui.NoticeAction
import com.redrixdev.tikstik.core.ui.Notices
import com.redrixdev.tikstik.core.ui.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val access: AccessController,
    private val adb: Provider<AdbAccess>,
    private val pairing: PairingState,
    private val notices: Notices,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val finishing = MutableStateFlow(false)
    private val notificationsEnabled = MutableStateFlow(false)
    private val storageBlocked = MutableStateFlow(false)
    private val brand = DeviceBrand.of(Build.MANUFACTURER)

    val pairingPhase: StateFlow<Phase> = pairing.phase

    val state: StateFlow<ConnectUiState> =
        combine(access.status, pairing.phase, finishing, notificationsEnabled, storageBlocked) { status, phase, finishing, notifications, blocked ->
            ConnectUiState(
                step = status.step.toConnectStep(),
                legacyStorage = access.legacyStorage,
                onWifi = status.onWifi,
                waitingForCode = phase != Phase.IDLE && phase != Phase.PAIRED,
                finishing = finishing,
                notificationsEnabled = notifications,
                manual = status.step == AccessStep.READY_MANUAL,
                brand = brand,
                storageBlocked = blocked,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectUiState(access.status.value.step.toConnectStep(), access.legacyStorage, brand = brand))

    /** While the wizard is visible: it advances by itself on returning from Settings. */
    fun refresh(notificationsEnabled: Boolean) {
        access.refresh()
        this.notificationsEnabled.value = notificationsEnabled
    }

    /** Android 8–10: without the permission and unable to ask again, the button opens the app's settings page. */
    fun onStorageResult(granted: Boolean, canAskAgain: Boolean, notificationsEnabled: Boolean) {
        storageBlocked.value = !granted && !canAskAgain
        refresh(notificationsEnabled)
    }

    fun onNotificationsDenied() {
        notices.post(Notice(UiText.Res(R.string.connect_notifications_denied), error = true))
    }

    // In the app scope: leaving the wizard must not cut the grant off halfway.
    fun finish() {
        if (finishing.value) return
        if (!access.status.value.wirelessDebuggingOn) {
            notices.post(Notice(UiText.Res(R.string.err_wireless_off_first), NoticeAction.OPEN_WIRELESS_DEBUGGING, error = true))
            return
        }
        finishing.value = true
        appScope.launch {
            // null: never connected; false: connected, but the phone does not allow granting the permission.
            val granted = try {
                adb.get().finishFirstConnection()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Logger.warn("Access", "first connection failed", e)
                null
            }
            finishing.value = false
            access.refresh()
            when (granted) {
                true -> Unit
                false -> notices.post(Notice(UiText.Res(R.string.connect_manual_notice)))
                null -> notices.post(Notice(UiText.Res(R.string.err_grant), error = true))
            }
        }
    }

    private fun AccessStep.toConnectStep(): ConnectStep = when (this) {
        AccessStep.NEEDS_STORAGE_PERMISSION -> ConnectStep.STORAGE
        AccessStep.NEEDS_DEV_OPTIONS -> ConnectStep.DEV_OPTIONS
        AccessStep.NEEDS_PAIRING -> ConnectStep.PAIRING
        AccessStep.NEEDS_FIRST_CONNECTION -> ConnectStep.FINISH
        AccessStep.READY, AccessStep.READY_MANUAL -> ConnectStep.DONE
    }
}
