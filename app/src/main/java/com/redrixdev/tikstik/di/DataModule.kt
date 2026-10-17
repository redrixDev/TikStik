// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.room.Room
import com.redrixdev.tikstik.core.common.IoDispatcher
import com.redrixdev.tikstik.core.common.Logger
import com.redrixdev.tikstik.core.data.db.AppDatabase
import com.redrixdev.tikstik.core.data.files.StickerFiles
import com.redrixdev.tikstik.core.data.settings.AppSettings
import com.redrixdev.tikstik.core.data.settings.AppSettingsSerializer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides
    @Singleton
    fun files(@ApplicationContext context: Context): StickerFiles = StickerFiles(context.filesDir)

    @Provides
    @Singleton
    fun settings(@ApplicationContext context: Context, @IoDispatcher io: CoroutineDispatcher): DataStore<AppSettings> =
        DataStoreFactory.create(
            serializer = AppSettingsSerializer,
            // A corrupted file does not block the app: it falls back to the defaults and gets logged.
            corruptionHandler = ReplaceFileCorruptionHandler { e ->
                Logger.warn("Settings", "corrupted settings replaced with defaults", e)
                AppSettings()
            },
            scope = CoroutineScope(io + SupervisorJob()),
            produceFile = { context.dataStoreFile("settings.json") },
        )
}
