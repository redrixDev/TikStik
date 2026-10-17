// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.importing

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The TikTok caches to read; empty without any TikTok app (or without mounted storage). */
fun interface TikTokSources {
    fun roots(): List<String>
}

class InstalledTikTok @Inject constructor(@param:ApplicationContext private val context: Context) : TikTokSources {

    // Since Android 11 they are only visible if the manifest declares them in <queries>.
    override fun roots(): List<String> {
        val androidData = context.getExternalFilesDir(null)?.parentFile?.parentFile?.path ?: return emptyList()
        return TikTokCache.PACKAGES.filter(::installed).map { TikTokCache.root(androidData, it) }
    }

    private fun installed(pkg: String) = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
