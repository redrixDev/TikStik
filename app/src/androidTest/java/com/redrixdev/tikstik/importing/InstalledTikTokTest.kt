// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.importing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** With TikTok installed: it sees it (the manifest's <queries>) and points at its cache without reading anything from it. */
@RunWith(AndroidJUnit4::class)
class InstalledTikTokTest {

    @Test
    fun findsTheInstalledTikTokAndItsCache() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val installed = runCatching { context.packageManager.getPackageInfo(TikTokCache.PACKAGES.first(), 0) }.isSuccess
        assumeTrue(installed)
        val roots = InstalledTikTok(context).roots()
        assertThat(roots).contains("/storage/emulated/0/Android/data/${TikTokCache.PACKAGES.first()}/cache/picture")
        assertThat(roots.all { File(it).path.endsWith("/cache/picture") }).isTrue()
    }
}
