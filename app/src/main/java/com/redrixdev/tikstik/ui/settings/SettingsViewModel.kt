// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.settings

import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redrixdev.tikstik.BuildConfig
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.access.AccessController
import com.redrixdev.tikstik.backup.BackupAccount
import com.redrixdev.tikstik.core.common.Logger
import com.redrixdev.tikstik.core.data.settings.SettingsRepository
import com.redrixdev.tikstik.core.ui.Notice
import com.redrixdev.tikstik.core.ui.Notices
import com.redrixdev.tikstik.core.ui.UiText
import com.redrixdev.tikstik.ui.packs.PackNotices
import com.redrixdev.tikstik.update.CheckResult
import com.redrixdev.tikstik.update.Updates
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val access: AccessController,
    private val settings: SettingsRepository,
    private val account: BackupAccount,
    private val notices: Notices,
    private val updates: Updates,
) : ViewModel() {

    /** Choosing the Google account or granting the permission: the route launches it and reports back through [onAuthResult]. */
    val authRequests: Flow<PendingIntent> = account.authRequests

    private val systemSettings = Channel<Unit>(Channel.CONFLATED)

    /** Android 8–10: the storage permission is withdrawn on TikStik's page in the system settings, which the route opens. */
    val openAppSettings: Flow<Unit> = systemSettings.receiveAsFlow()

    val state: StateFlow<SettingsUiState> = combine(access.status, settings.settings, account.state, updates.state) { status, saved, a, u ->
        SettingsUiState(
            account = a,
            tiktokConnected = status.ready,
            packBaseName = saved.packBaseName,
            updates = UpdatesUi(u.checkAutomatically, u.available?.versionName, BuildConfig.VERSION_NAME),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun refresh() = access.refresh()

    fun disconnectTikTok() {
        if (access.legacyStorage) systemSettings.trySend(Unit) else access.revoke()
    }

    fun signIn() = account.signIn()

    fun onAuthResult(ok: Boolean, data: Intent?) = account.onAuthResult(ok, data)

    fun backupNow() = account.backupNow()

    fun signOut() {
        account.signOut()
    }

    fun confirmMerge() = account.confirmMerge()

    fun cancelMerge() = account.cancelMerge()

    fun setAutoUpdate(enabled: Boolean) {
        updates.setCheckAutomatically(enabled)
    }

    fun showUpdate() {
        updates.show()
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            when (updates.check()) {
                CheckResult.FOUND -> updates.show()
                CheckResult.UP_TO_DATE -> notices.post(Notice(UiText.Res(R.string.update_none)))
                CheckResult.FAILED -> notices.post(Notice(UiText.Res(R.string.update_check_failed), error = true))
            }
        }
    }

    fun setPackBaseName(name: String?) {
        viewModelScope.launch {
            try {
                settings.setPackBaseName(name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A full disk must not crash the app.
                Logger.warn("Settings", "pack name not saved", e)
                notices.post(PackNotices.saveFailed)
            }
        }
    }
}
