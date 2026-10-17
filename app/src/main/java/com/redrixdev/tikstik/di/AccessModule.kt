// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.di

import android.content.Context
import android.os.Build
import com.redrixdev.tikstik.access.AccessController
import com.redrixdev.tikstik.access.AdbAccess
import com.redrixdev.tikstik.access.AdbLink
import com.redrixdev.tikstik.access.AdbStore
import com.redrixdev.tikstik.access.AndroidDeviceState
import com.redrixdev.tikstik.access.DeviceState
import com.redrixdev.tikstik.access.LibAdbLink
import com.redrixdev.tikstik.access.StorageAccess
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AccessModule {

    @Binds
    abstract fun deviceState(impl: AndroidDeviceState): DeviceState

    @Binds
    abstract fun adbLink(impl: LibAdbLink): AdbLink

    companion object {
        @Provides
        @Singleton
        fun adbStore(@ApplicationContext context: Context): AdbStore = AdbStore(File(context.noBackupFilesDir, "adb"))

        // Wireless debugging arrives in Android 11; before that, the cache is read with the storage permission.
        @Provides
        @Singleton
        fun access(adb: Provider<AdbAccess>, storage: Provider<StorageAccess>): AccessController =
            if (Build.VERSION.SDK_INT >= 30) adb.get() else storage.get()
    }
}
