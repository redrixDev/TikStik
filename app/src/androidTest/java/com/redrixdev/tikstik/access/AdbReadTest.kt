// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.access

import android.provider.Settings
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.redrixdev.tikstik.MainActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.Security
import kotlin.random.Random

/** A real ADB read with the app's stored identity; needs the app paired and only runs with `-e adb real|hold|lost|manual`. */
@RunWith(AndroidJUnit4::class)
class AdbReadTest {

    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val mode = InstrumentationRegistry.getArguments().getString("adb")
    private val dir = File(app.getExternalFilesDir(null), "adb-read")
    private lateinit var access: AccessController

    // What used to confuse output splitting: head separators, the shell's end marker and line breaks.
    private val files = buildMap {
        put("a b.cnt", "RIFF\n==> x <==\n__TIKSTIK_END__\n".toByteArray() + ByteArray(256) { it.toByte() })
        put("it's.cnt", ByteArray(10) { 0x0A })
        put("empty.cnt", ByteArray(0))
        put("big.cnt", Random(7).nextBytes(3_000_000))
        repeat(45) { put("small_$it.cnt", Random(it).nextBytes(100 + it)) }
    }

    @Before
    fun setUp() {
        assumeTrue(mode != null)
        ActivityScenario.launch(MainActivity::class.java).use { scenario -> scenario.onActivity { access = it.access } }
        assumeTrue(access.status.value.ready)
        if (mode != "manual") assertThat(wirelessDebuggingOn()).isFalse()
        dir.deleteRecursively()
        dir.mkdirs()
        files.forEach { (name, bytes) -> File(dir, name).writeBytes(bytes) }
        File(dir, "not_a_cache.txt").writeText("no")
    }

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun readsRealFilesAndLeavesItOff() = runBlocking(Dispatchers.IO) {
        assumeTrue(mode == "real")
        var onDuringSession = false
        val started = System.nanoTime()
        val (listed, heads, whole) = access.withReader(onConnected = { onDuringSession = wirelessDebuggingOn() }) { reader ->
            val listed = reader.list(dir.path)
            Triple(listed, reader.heads(listed, 32), reader.read(listed))
        }
        val ms = (System.nanoTime() - started) / 1_000_000
        android.util.Log.i("AdbReadTest", "${listed.size} files, ${whole.values.sumOf { it.size }} bytes in $ms ms")
        assertThat(onDuringSession).isTrue()
        // libadb uses Conscrypt only in its own SSLContext: the rest of the app's TLS stays on the system provider.
        assertThat(Security.getProviders().map { it.name }).doesNotContain("Conscrypt")
        assertThat(listed.map { File(it.path).name }).containsExactlyElementsIn(files.keys)
        files.forEach { (name, bytes) ->
            val path = File(dir, name).path
            assertThat(heads.getValue(path)).isEqualTo(bytes.copyOf(minOf(32, bytes.size)))
            assertThat(whole.getValue(path)).isEqualTo(bytes)
        }
        assertOffAndUnowned()
    }

    // Each session starts adbd on another port and mDNS may keep announcing the previous one.
    @Test
    fun sessionsBackToBack() = runBlocking(Dispatchers.IO) {
        assumeTrue(mode == "real")
        repeat(8) { round ->
            val started = System.nanoTime()
            val listed = access.withReader { it.list(dir.path) }
            android.util.Log.i("AdbReadTest", "session $round: ${(System.nanoTime() - started) / 1_000_000} ms")
            assertThat(listed).hasSize(files.size)
            assertOffAndUnowned()
        }
    }

    @Test
    fun aFailingReadLeavesItOff() = runBlocking(Dispatchers.IO) {
        assumeTrue(mode == "real")
        val failure = runCatching { access.withReader { error("broken read") } }.exceptionOrNull()
        assertThat(failure).isInstanceOf(IllegalStateException::class.java)
        assertOffAndUnowned()
    }

    @Test
    fun aCancelledReadLeavesItOff() = runBlocking(Dispatchers.IO) {
        assumeTrue(mode == "real")
        val connected = CompletableDeferred<Unit>()
        val reading = async { access.withReader<Unit>(onConnected = { connected.complete(Unit) }) { awaitCancellation() } }
        withTimeout(60_000) { connected.await() }
        reading.cancel()
        runCatching { reading.await() }
        assertOffAndUnowned()
    }

    // Setup: `pm revoke` the permission, set the `manual` flag and turn debugging on by hand.
    @Test
    fun aManualReadRetriesTheGrantAndLeavesTheSwitchAsItWas() = runBlocking(Dispatchers.IO) {
        assumeTrue(mode == "manual")
        assertThat(access.status.value.step).isEqualTo(AccessStep.READY_MANUAL)
        assertThat(wirelessDebuggingOn()).isTrue()
        val listed = access.withReader { it.list(dir.path) }
        assertThat(listed).hasSize(files.size)
        assertThat(access.status.value.step).isEqualTo(AccessStep.READY)
        assertThat(File(app.noBackupFilesDir, "adb/manual").exists()).isFalse()
        assertThat(wirelessDebuggingOn()).isTrue()
    }

    // Setup: forget TikStik under the system's "Paired devices" first.
    @Test
    fun aForgottenPairingIsReportedAndLeavesItOff() = runBlocking(Dispatchers.IO) {
        assumeTrue(mode == "lost")
        val failure = runCatching { access.withReader { } }.exceptionOrNull()
        assertThat((failure as AccessException).error).isEqualTo(AccessError.PAIRING_LOST)
        assertThat(access.status.value.step).isEqualTo(AccessStep.NEEDS_PAIRING)
        assertOffAndUnowned()
    }

    // The process is killed from outside (`am force-stop`) with the session open; reopening the app must turn it off.
    @Test
    fun holdsTheSessionUntilTheProcessIsKilled() {
        assumeTrue(mode == "hold")
        runBlocking(Dispatchers.IO) {
            access.withReader<Unit>(onConnected = { android.util.Log.i("AdbReadTest", "session open") }) { awaitCancellation() }
        }
    }

    private fun assertOffAndUnowned() {
        assertThat(wirelessDebuggingOn()).isFalse()
        assertThat(File(app.noBackupFilesDir, "adb/wireless_debugging_on_by_tikstik").exists()).isFalse()
    }

    private fun wirelessDebuggingOn() = Settings.Global.getInt(app.contentResolver, "adb_wifi_enabled", 0) == 1
}
