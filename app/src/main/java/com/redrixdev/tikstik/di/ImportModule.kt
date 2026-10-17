// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.di

import android.content.Context
import com.redrixdev.tikstik.importing.AndroidStickerImages
import com.redrixdev.tikstik.importing.ImportMirror
import com.redrixdev.tikstik.importing.StickerImages
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ImportModule {

    @Binds
    abstract fun images(impl: AndroidStickerImages): StickerImages

    companion object {
        // In the app's cache: regenerated if the system clears it.
        @Provides
        @Singleton
        fun mirror(@ApplicationContext context: Context) = ImportMirror(File(context.cacheDir, "import_mirror"))
    }
}
