// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.di

import android.content.Context
import com.redrixdev.tikstik.backup.DriveClients
import com.redrixdev.tikstik.backup.GoogleAuthorizer
import com.redrixdev.tikstik.backup.HttpDriveClient
import com.redrixdev.tikstik.backup.InstallId
import com.redrixdev.tikstik.backup.PlayAuthorizer
import com.redrixdev.tikstik.core.common.writeAtomically
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.UUID
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {

    @Binds
    abstract fun authorizer(impl: PlayAuthorizer): GoogleAuthorizer

    companion object {
        @Provides
        fun drives(): DriveClients = DriveClients(::HttpDriveClient)

        // In noBackupFilesDir: two phones never share an id even when the app is transferred from one to the other.
        @Provides
        @Singleton
        fun installId(@ApplicationContext context: Context): InstallId {
            val file = File(context.noBackupFilesDir, "install_id")
            val saved = file.takeIf { it.isFile }?.readText()?.trim()
            return InstallId(saved ?: UUID.randomUUID().toString().also { file.writeAtomically(it.encodeToByteArray()) })
        }
    }
}
