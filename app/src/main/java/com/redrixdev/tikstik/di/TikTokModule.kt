// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.di

import com.redrixdev.tikstik.importing.InstalledTikTok
import com.redrixdev.tikstik.importing.TikTokSources
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TikTokModule {
    @Binds
    abstract fun sources(impl: InstalledTikTok): TikTokSources
}
