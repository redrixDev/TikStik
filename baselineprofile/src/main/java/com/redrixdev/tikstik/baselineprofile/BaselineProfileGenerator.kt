// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Generates the Baseline Profile (and the startup one) with `./gradlew :app:generateBaselineProfile`, against a release build with a collection. */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    // Only startup goes into the startup profile; the other journeys go into the general one (Baseline Profiles guide).
    @Test
    fun startup() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) { openHome() }

    @Test
    fun journeys() = rule.collect(packageName = PACKAGE) {
        openHome()
        browse()
    }
}
