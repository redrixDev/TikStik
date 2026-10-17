// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.redrixdev.tikstik.core.data.db.AppDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The exported v1 opens as is: the base for testing each future migration with its own test. */
@RunWith(AndroidJUnit4::class)
class SchemaTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        file = instrumentation.targetContext.getDatabasePath(DB),
        driver = AndroidSQLiteDriver(),
        databaseClass = AppDatabase::class,
    )

    @Test
    fun version1CanBeCreatedFromTheExportedSchema() {
        instrumentation.targetContext.deleteDatabase(DB)
        val connection = helper.createDatabase(1)
        connection.prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'stickers'").use {
            assertThat(it.step()).isTrue()
        }
        connection.close()
        instrumentation.targetContext.deleteDatabase(DB)
    }

    private companion object {
        const val DB = "schema-test.db"
    }
}
