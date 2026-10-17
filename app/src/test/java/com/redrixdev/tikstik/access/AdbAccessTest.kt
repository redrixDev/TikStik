// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.access

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// Every path must leave wireless debugging as it was.
@OptIn(ExperimentalCoroutinesApi::class)
class AdbAccessTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val device = FakeDevice()
    private val link = FakeLink()
    private val store by lazy { AdbStore(folder.newFolder("adb")).apply { paired = true } }

    private fun TestScope.access() = AdbAccess(device, link, store, backgroundScope, StandardTestDispatcher(testScheduler))

    private fun TestScope.failure(block: suspend () -> Unit): AccessError {
        val result = async { runCatching { block() }.exceptionOrNull() }
        advanceUntilIdle()
        return (result.getCompleted() as AccessException).error
    }

    @Test
    fun alreadyOnIsNeverTouched() = runTest {
        device.on = true
        access().withReader { }
        assertThat(device.switches).isEmpty()
        assertThat(device.on).isTrue()
    }

    @Test
    fun turnedOnForTheSessionAndOffAfterIt() = runTest {
        var duringSession = false
        access().withReader { duringSession = device.on }
        assertThat(duringSession).isTrue()
        assertThat(device.switches).containsExactly(true, false).inOrder()
        assertThat(store.ownsWirelessDebugging).isFalse()
    }

    @Test
    fun offWhenTheReadingFails() = runTest {
        val access = access()
        val result = async { runCatching { access.withReader { error("broken read") } }.exceptionOrNull() }
        advanceUntilIdle()
        assertThat(result.getCompleted()).isInstanceOf(IllegalStateException::class.java)
        assertThat(device.on).isFalse()
        assertThat(store.ownsWirelessDebugging).isFalse()
    }

    @Test
    fun offWhenCancelledWhileWaitingForTheNetwork() = runTest {
        device.networkApproved = false
        val reading = async { access().withReader { } }
        advanceTimeBy(5_000)
        reading.cancel()
        advanceUntilIdle()
        assertThat(device.switches.last()).isFalse()
        assertThat(device.on).isFalse()
        // Still watching: if the user approves the Wi-Fi now, Android would turn it on with no session running.
        assertThat(store.ownsWirelessDebugging).isTrue()
    }

    @Test
    fun aNewNetworkThatIsNeverApprovedEndsOff() = runTest {
        device.networkApproved = false
        var asked = false
        val access = access()
        assertThat(failure { access.withReader(onNetworkApproval = { asked = true }) { } }).isEqualTo(AccessError.NETWORK_NOT_ALLOWED)
        assertThat(asked).isTrue()
        assertThat(device.on).isFalse()
    }

    @Test
    fun aLateApprovalIsTurnedOffAgain() = runTest {
        device.networkApproved = false
        val access = access()
        val reading = async { runCatching { access.withReader { } } }
        advanceTimeBy(65_000)
        runCurrent()
        assertThat(reading.isCompleted).isTrue()
        // The user accepts Android's prompt after TikStik has already given up: Android turns it on.
        device.networkApproved = true
        device.on = true
        advanceTimeBy(2_000)
        assertThat(device.on).isFalse()
        advanceTimeBy(10 * 60_000)
        assertThat(store.ownsWirelessDebugging).isFalse()
    }

    @Test
    fun offWhenTheConnectionFails() = runTest {
        link.connectResult = { false }
        val access = access()
        assertThat(failure { access.withReader { } }).isEqualTo(AccessError.CONNECTION_FAILED)
        assertThat(device.on).isFalse()
    }

    @Test
    fun aLostPairingIsForgottenAndLeavesItOff() = runTest {
        link.connectResult = { throw PairingLostException(Exception("unknown key")) }
        val access = access()
        assertThat(failure { access.withReader { } }).isEqualTo(AccessError.PAIRING_LOST)
        assertThat(device.on).isFalse()
        assertThat(store.paired).isFalse()
        assertThat(access.status.value.step).isEqualTo(AccessStep.NEEDS_PAIRING)
    }

    @Test
    fun afterALostPairingTheUserCanTurnItOnToPairAgain() = runTest {
        link.connectResult = { throw PairingLostException(Exception("unknown key")) }
        val access = access()
        assertThat(failure { access.withReader { } }).isEqualTo(AccessError.PAIRING_LOST)
        // To pair again the user turns it on: TikStik must not turn it off on them.
        device.on = true
        advanceTimeBy(2_000)
        assertThat(device.on).isTrue()
    }

    @Test
    fun aLateApprovalAfterCancellingIsTurnedOffToo() = runTest {
        device.networkApproved = false
        val reading = async { access().withReader { } }
        advanceTimeBy(5_000)
        reading.cancel()
        runCurrent()
        // The user accepts Android's prompt after cancelling: Android turns it on and TikStik turns it off.
        device.networkApproved = true
        device.on = true
        advanceTimeBy(2_000)
        assertThat(device.on).isFalse()
    }

    @Test
    fun revokingDuringAReadLeavesNoWatchBehind() = runTest {
        device.networkApproved = false
        val access = access()
        val reading = async { runCatching { access.withReader { } } }
        advanceTimeBy(5_000)
        // "Disconnect" while the read waits for the Wi-Fi: it waits for the session and stops the watch the read leaves behind.
        access.revoke()
        reading.cancel()
        runCurrent()
        reading.join()
        runCurrent()
        // To pair again the user turns it on: TikStik must not turn it off on them.
        device.networkApproved = true
        device.on = true
        advanceTimeBy(2_000)
        assertThat(device.on).isTrue()
        assertThat(store.paired).isFalse()
    }

    @Test
    fun ifTheProcessDiedWithItOnItIsTurnedOffOnStart() = runTest {
        store.ownsWirelessDebugging = true
        device.on = true
        access()
        runCurrent()
        assertThat(device.on).isFalse()
        assertThat(store.ownsWirelessDebugging).isFalse()
    }

    @Test
    fun revokingForgetsThePairingButKeepsTheIdentity() = runTest {
        val identity = store.identity()
        val access = access()
        assertThat(access.status.value.step).isEqualTo(AccessStep.READY)
        access.revoke()
        runCurrent()
        assertThat(store.paired).isFalse()
        // The same key: libadb cannot change its own without restarting the app.
        assertThat(store.identity().certificate).isEqualTo(identity.certificate)
        assertThat(access.status.value.step).isEqualTo(AccessStep.NEEDS_PAIRING)
    }

    @Test
    fun withoutPermissionItAsksTheUserAndTouchesNothing() = runTest {
        device.canToggleWirelessDebugging = false
        val access = access()
        assertThat(failure { access.withReader { } }).isEqualTo(AccessError.WIRELESS_DEBUGGING_OFF)
        assertThat(device.switches).isEmpty()
    }

    @Test
    fun noWifiOrNoPairingFailFast() = runTest {
        device.onWifi = false
        val access = access()
        assertThat(failure { access.withReader { } }).isEqualTo(AccessError.NO_WIFI)
        store.paired = false
        assertThat(failure { access.withReader { } }).isEqualTo(AccessError.NOT_CONFIGURED)
        assertThat(device.switches).isEmpty()
    }

    @Test
    fun theFirstConnectionGrantsThePermissionAndTurnsItOff() = runTest {
        device.canToggleWirelessDebugging = false
        device.on = true
        link.respond = { command ->
            if (command.startsWith("pm grant com.redrixdev.tikstik android.permission.WRITE_SECURE_SETTINGS")) device.canToggleWirelessDebugging = true
            ByteArray(0)
        }
        val access = access()
        assertThat(access.status.value.step).isEqualTo(AccessStep.NEEDS_FIRST_CONNECTION)
        val granted = async { access.finishFirstConnection() }
        advanceUntilIdle()
        assertThat(granted.getCompleted()).isTrue()
        assertThat(device.on).isFalse()
        assertThat(access.status.value.step).isEqualTo(AccessStep.READY)
    }

    @Test
    fun aGrantThatDoesNotStickLeavesTheManualMode() = runTest {
        device.canToggleWirelessDebugging = false
        device.on = true
        val access = access()
        val granted = async { access.finishFirstConnection() }
        advanceUntilIdle()
        assertThat(granted.getCompleted()).isFalse()
        assertThat(store.manual).isTrue()
        assertThat(access.status.value.step).isEqualTo(AccessStep.READY_MANUAL)
        assertThat(device.on).isTrue()
    }

    @Test
    fun aManualReadRetriesTheGrantAndNeverTouchesTheSwitch() = runTest {
        store.manual = true
        device.canToggleWirelessDebugging = false
        device.on = true
        link.respond = { command ->
            if (command.startsWith("pm grant")) device.canToggleWirelessDebugging = true
            ByteArray(0)
        }
        val access = access()
        assertThat(access.status.value.step).isEqualTo(AccessStep.READY_MANUAL)
        access.withReader { }
        assertThat(store.manual).isFalse()
        assertThat(access.status.value.step).isEqualTo(AccessStep.READY)
        assertThat(device.switches).isEmpty()
        assertThat(device.on).isTrue()
    }

    @Test
    fun pairingStoresItAndFinishesTheFirstConnection() = runTest {
        store.paired = false
        device.on = true
        device.canToggleWirelessDebugging = false
        link.respond = { device.canToggleWirelessDebugging = true; ByteArray(0) }
        val access = access()
        assertThat(access.status.value.step).isEqualTo(AccessStep.NEEDS_PAIRING)
        val wrong = async { access.pair("127.0.0.1", 37000, "000000") }
        advanceUntilIdle()
        assertThat(wrong.getCompleted()).isFalse()
        assertThat(store.paired).isFalse()
        val right = async { access.pair("127.0.0.1", 37000, "123456") }
        advanceUntilIdle()
        assertThat(right.getCompleted()).isTrue()
        assertThat(access.status.value.step).isEqualTo(AccessStep.READY)
    }
}
