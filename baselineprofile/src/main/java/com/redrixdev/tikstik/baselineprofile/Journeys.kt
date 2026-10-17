// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

// The release package, the one the plugin measures and profiles (no ".debug" suffix).
const val PACKAGE = "com.redrixdev.tikstik"
private const val WAIT_MS = 10_000L

// Fail if anything is missing: a journey that just carries on leaves the profile incomplete without warning.
private fun MacrobenchmarkScope.find(selector: BySelector, what: String): UiObject2 =
    checkNotNull(device.wait(Until.findObject(selector), WAIT_MS)) { "$what did not appear: does the release app have a collection and packs?" }

/** Starts the app and leaves Home visible; the first time it skips the welcome (the emulator runs in English). */
fun MacrobenchmarkScope.openHome() {
    pressHome()
    startActivityAndWait()
    device.wait(Until.findObject(By.text("Skip")), 3_000)?.click()
    find(By.desc("Settings"), "Home")
}

/** Everyday use beyond startup: scrolling the collection, Packs and a pack, and Settings. */
fun MacrobenchmarkScope.browse() {
    find(By.scrollable(true), "the Home grid").fling(Direction.DOWN)
    device.waitForIdle()
    find(By.desc("Packs"), "the Packs button").click()
    find(By.textStartsWith("TikStik"), "a pack").click()
    device.waitForIdle()
    device.pressBack()
    find(By.text("Packs"), "Packs")
    device.pressBack()
    find(By.desc("Settings"), "Settings").click()
    find(By.text("Settings"), "the Settings screen")
    device.pressBack()
    device.waitForIdle()
}
