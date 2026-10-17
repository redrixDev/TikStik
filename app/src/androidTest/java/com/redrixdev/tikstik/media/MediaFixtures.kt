// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.media

import androidx.test.platform.app.InstrumentationRegistry
import java.security.MessageDigest

/** The synthetic fixtures in the test APK: the media ones (hard cases) and the gallery ones. */
object MediaFixtures {
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    val paths: List<String> = listOf("media", "gallery").flatMap { dir -> assets.list(dir)!!.sorted().map { "$dir/$it" } }

    fun bytes(path: String): ByteArray = assets.open(path).use { it.readBytes() }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
