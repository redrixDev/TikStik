// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.access

import android.app.Notification
import android.app.NotificationManager
import android.os.ParcelFileDescriptor
import androidx.core.content.getSystemService
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.redrixdev.tikstik.MainActivity
import com.redrixdev.tikstik.R
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The shortService deadline (about 3 min on Android 14+) runs out without a pairing dialog: the timed-out notice remains; only with `-e pairing timeout`. */
@RunWith(AndroidJUnit4::class)
class PairingTimeoutTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext

    @Test
    fun theShortServiceTimeoutStopsItCleanly() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("pairing") == "timeout")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var timedOut = ""
            scenario.onActivity {
                // From the activity: the instrumentation context does not carry the per-app language.
                timedOut = it.getString(R.string.pair_timeout_title)
                PairingService.start(it)
            }
            Thread.sleep(2_000)
            assertThat(services()).contains("PairingService")
            assertThat(pairingTitle()).isNotEqualTo(timedOut)
            Thread.sleep(WAIT_MS)
            assertThat(services()).doesNotContain("PairingService")
            assertThat(pairingTitle()).isEqualTo(timedOut)
        }
    }

    private fun pairingTitle(): String? = app.getSystemService<NotificationManager>()!!.activeNotifications
        .firstOrNull { it.id == PairingNotifications.ID }
        ?.notification?.extras?.getString(Notification.EXTRA_TITLE)

    private fun services(): String {
        val pipe = instrumentation.uiAutomation.executeShellCommand("dumpsys activity services ${app.packageName}")
        return ParcelFileDescriptor.AutoCloseInputStream(pipe).bufferedReader().readText()
    }

    private companion object {
        const val WAIT_MS = 200_000L
    }
}
