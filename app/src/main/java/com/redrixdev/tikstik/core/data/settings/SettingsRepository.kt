// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.data.settings

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(private val store: DataStore<AppSettings>) {

    val settings: Flow<AppSettings> = store.data

    suspend fun setOnboardingDone() {
        store.updateData { it.copy(onboardingDone = true) }
    }

    suspend fun setHowToSeen() {
        store.updateData { it.copy(howToSeen = true) }
    }

    /** Empty: back to the default name. */
    suspend fun setPackBaseName(name: String?) {
        store.updateData { it.copy(packBaseName = name?.trim()?.take(MAX_BASE_NAME)?.ifEmpty { null }) }
    }

    suspend fun setPendingAdd(pending: PendingAdd?) {
        store.updateData { it.copy(pendingAdd = pending) }
    }

    suspend fun updateBackup(change: (BackupInfo) -> BackupInfo) {
        store.updateData { it.copy(backup = change(it.backup)) }
    }

    suspend fun updatePackGaps(change: (Map<String, List<PackGap>>) -> Map<String, List<PackGap>>) {
        store.updateData { it.copy(packGaps = change(it.packGaps)) }
    }

    suspend fun updateUpdates(change: (UpdateInfo) -> UpdateInfo) {
        store.updateData { it.copy(updates = change(it.updates)) }
    }

    companion object {
        const val MAX_BASE_NAME = 60
    }
}
