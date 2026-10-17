// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.data

import androidx.datastore.core.CorruptionException
import com.google.common.truth.Truth.assertThat
import com.redrixdev.tikstik.core.data.settings.AppSettings
import com.redrixdev.tikstik.core.data.settings.AppSettingsSerializer
import com.redrixdev.tikstik.core.data.settings.PendingAdd
import com.redrixdev.tikstik.whatsapp.WhatsAppApp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class AppSettingsSerializerTest {

    @Test
    fun roundTrip() = runTest {
        val settings = AppSettings(onboardingDone = true, packBaseName = "Fiesta", pendingAdd = PendingAdd("pack_3_1", WhatsAppApp.BUSINESS))
        val out = ByteArrayOutputStream()
        AppSettingsSerializer.writeTo(settings, out)
        assertThat(AppSettingsSerializer.readFrom(ByteArrayInputStream(out.toByteArray()))).isEqualTo(settings)
    }

    @Test
    fun unknownAndMissingFieldsKeepDefaults() = runTest {
        val json = """{"onboardingDone":true,"fieldFromTheFuture":42}"""
        assertThat(AppSettingsSerializer.readFrom(json.byteInputStream())).isEqualTo(AppSettings(onboardingDone = true))
    }

    @Test
    fun unknownEnumValuesKeepTheRestOfTheSettings() = runTest {
        val json = """{"onboardingDone":true,"pendingAdd":{"packId":"pack_1","app":"FROM_THE_FUTURE"},"backup":{"email":"a@b.c","problem":"FROM_THE_FUTURE"}}"""
        val settings = AppSettingsSerializer.readFrom(json.byteInputStream())
        assertThat(settings.onboardingDone).isTrue()
        assertThat(settings.pendingAdd).isEqualTo(PendingAdd("pack_1", WhatsAppApp.CONSUMER))
        assertThat(settings.backup.email).isEqualTo("a@b.c")
        assertThat(settings.backup.problem).isNull()
    }

    @Test
    fun damagedFileIsReportedAsCorruption() {
        assertThrows(CorruptionException::class.java) {
            kotlinx.coroutines.runBlocking { AppSettingsSerializer.readFrom("{onboarding".byteInputStream()) }
        }
    }
}
