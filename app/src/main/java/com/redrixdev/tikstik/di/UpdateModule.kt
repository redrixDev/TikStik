// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.di

import android.content.Context
import com.redrixdev.tikstik.BuildConfig
import com.redrixdev.tikstik.core.common.Links
import com.redrixdev.tikstik.update.GitHubReleases
import com.redrixdev.tikstik.update.InstalledVersion
import com.redrixdev.tikstik.update.UpdateDir
import com.redrixdev.tikstik.update.UpdateSource
import com.redrixdev.tikstik.update.Version
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {

    @Provides
    fun source(): UpdateSource = GitHubReleases(Links.REPOSITORY)

    @Provides
    fun updateDir(@ApplicationContext context: Context): UpdateDir = UpdateDir(File(context.filesDir, "update"))

    @Provides
    fun installed(): InstalledVersion = InstalledVersion(Version.parse(BuildConfig.VERSION_NAME) ?: Version(0, 0, 0))
}
