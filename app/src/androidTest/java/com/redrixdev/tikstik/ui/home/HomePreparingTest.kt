// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.home

import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.redrixdev.tikstik.MainActivity
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.collection.CollectionRepository
import com.redrixdev.tikstik.core.common.Clock
import com.redrixdev.tikstik.core.data.db.AppDatabase
import com.redrixdev.tikstik.core.data.db.StickerEntity
import com.redrixdev.tikstik.core.data.files.StickerFiles
import com.redrixdev.tikstik.media.MediaFixtures
import com.redrixdev.tikstik.media.MediaProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** With the debug build's real collection seeded with fixtures: the Home indicator follows the Worker until it ends. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class HomePreparingTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext
    private val files = StickerFiles(app.filesDir)
    private val out = File(app.getExternalFilesDir(null), "verification").apply { mkdirs() }
    private var seeded = emptyList<StickerEntity>()

    // Through another Room instance: the app's does not notice, but every new screen queries again.
    private fun seed() = withDatabase { db ->
        val collection = CollectionRepository(db, files, Clock { System.currentTimeMillis() }, Dispatchers.IO)
        seeded = MediaFixtures.paths.mapIndexed { i, path ->
            val bytes = MediaFixtures.bytes(path)
            val probe = MediaProbe.probe(bytes)
            val sticker = StickerEntity(
                MediaFixtures.sha256(bytes), probe.format.extension, probe.animated, probe.width ?: 0, probe.height ?: 0,
                probe.frames, probe.durationMs, bytes.size.toLong(), null, null, 0, null, i.toLong(), 0,
            )
            files.delete(files.whatsApp(sticker.id))
            collection.add(sticker, bytes)
        }
    }

    @After
    fun cleanUp() = withDatabase { db ->
        db.stickers().delete(seeded.map { it.id })
        seeded.forEach {
            files.delete(files.original(it.id, it.fileExt))
            files.delete(files.whatsApp(it.id))
        }
    }

    private fun withDatabase(block: suspend (AppDatabase) -> Unit) {
        val db = Room.databaseBuilder(app, AppDatabase::class.java, AppDatabase.NAME).build()
        try {
            runBlocking { block(db) }
        } finally {
            db.close()
        }
    }

    @Test
    fun theIndicatorFollowsThePreparationUntilItEnds() {
        // Onboarding is persisted: it is marked done through the app's DataStore and seeding comes after, so the preparation starts in view.
        var preparing = ""
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                runBlocking { it.settings.setOnboardingDone() }
                // From the activity: the instrumentation context does not carry the per-app language.
                preparing = it.getString(R.string.home_preparing, 0, 0).substringBefore(':')
            }
        }
        seed()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntilAtLeastOneExists(hasContentDescription(preparing, substring = true), 30_000)
            shot("home_preparing")
            compose.waitUntilDoesNotExist(hasContentDescription(preparing, substring = true), 120_000)
            shot("home_prepared")
        }
        seeded.forEach { check(files.whatsApp(it.id).exists()) { "a copy is missing" } }
    }

    private fun shot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
