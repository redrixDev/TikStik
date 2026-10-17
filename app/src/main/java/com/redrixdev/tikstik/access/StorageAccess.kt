// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.access

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.redrixdev.tikstik.core.common.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Android 8–10: TikTok's cache is read directly with the storage permission. */
@Singleton
class StorageAccess @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : AccessController {

    private val session = Mutex()
    private val _status = MutableStateFlow(read())

    override val legacyStorage = true
    override val status: StateFlow<AccessStatus> = _status.asStateFlow()

    private val granted: Boolean
        get() = context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun read() = AccessStatus(if (granted) AccessStep.READY else AccessStep.NEEDS_STORAGE_PERMISSION)

    override fun refresh() {
        _status.value = read()
    }

    override suspend fun <T> withReader(onConnected: () -> Unit, onNetworkApproval: () -> Unit, block: suspend (CacheReader) -> T): T =
        withContext(io) {
            session.withLock {
                if (!granted) throw AccessException(AccessError.NOT_CONFIGURED)
                onConnected()
                block(StorageCacheReader())
            }
        }

    // The user withdraws the permission in system Settings; there is nothing to forget here.
    override fun revoke() = refresh()
}
