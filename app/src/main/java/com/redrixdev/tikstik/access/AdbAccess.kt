// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.access

import com.redrixdev.tikstik.core.common.ApplicationScope
import com.redrixdev.tikstik.core.common.IoDispatcher
import com.redrixdev.tikstik.core.common.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Access through wireless debugging (Android 11+); whenever TikStik turns it on, it always turns it off again. */
@Singleton
class AdbAccess @Inject constructor(
    private val device: DeviceState,
    private val link: AdbLink,
    private val store: AdbStore,
    @param:ApplicationScope private val scope: CoroutineScope,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : AccessController {

    private val session = Mutex()
    // Only touched while holding the session, so a read that finishes cannot recreate it after "Disconnect".
    private var lateApprovalWatch: Job? = null
    private val _status = MutableStateFlow(read())

    override val legacyStorage = false
    override val status: StateFlow<AccessStatus> = _status.asStateFlow()

    init {
        // Unpaired, the old identity must not outlive this process: libadb pins its TLS per process.
        scope.launch(io) { store.forgetIdentityIfUnpaired() }
        refresh()
    }

    // Off the main thread: it reads files and settings, and the wizard asks for it every second.
    override fun refresh() {
        scope.launch(io) {
            restoreIfOwned()
            _status.value = read()
        }
    }

    private fun read(): AccessStatus {
        val step = when {
            !store.paired && !device.devOptionsEnabled -> AccessStep.NEEDS_DEV_OPTIONS
            !store.paired -> AccessStep.NEEDS_PAIRING
            device.canToggleWirelessDebugging -> AccessStep.READY
            store.manual -> AccessStep.READY_MANUAL
            else -> AccessStep.NEEDS_FIRST_CONNECTION
        }
        return AccessStatus(step, device.onWifi, device.wirelessDebuggingOn)
    }

    override suspend fun <T> withReader(onConnected: () -> Unit, onNetworkApproval: () -> Unit, block: suspend (CacheReader) -> T): T =
        withContext(io) {
            session.withLock {
                if (!store.paired) throw AccessException(AccessError.NOT_CONFIGURED)
                // Without Wi-Fi there is no wireless debugging: fail at once instead of retrying.
                if (!device.onWifi) throw AccessException(AccessError.NO_WIFI)
                var turnedOn = false
                var connected = false
                var answered = false
                try {
                    if (!device.wirelessDebuggingOn) {
                        if (!device.canToggleWirelessDebugging) throw AccessException(AccessError.WIRELESS_DEBUGGING_OFF)
                        // The flag goes first: if the process dies with debugging on, it gets turned off on the next start.
                        store.ownsWirelessDebugging = true
                        turnedOn = true
                        if (!device.setWirelessDebugging(true)) throw AccessException(AccessError.WIRELESS_DEBUGGING_OFF)
                        if (!awaitNetworkApproval(onNetworkApproval)) {
                            throw AccessException(AccessError.NETWORK_NOT_ALLOWED)
                        }
                    }
                    connect()
                    connected = true
                    // In manual mode try again: the user may have enabled the option that was missing.
                    if (!device.canToggleWirelessDebugging && grant()) _status.value = read()
                    onConnected()
                    val job = currentCoroutineContext().job
                    block(AdbCacheReader(AdbShell(link, cancelled = { !job.isActive })))
                } catch (e: AccessException) {
                    // adbd answered: the Wi-Fi was already approved and no system prompt is pending.
                    if (e.error == AccessError.PAIRING_LOST) answered = true
                    throw e
                } finally {
                    withContext(NonCancellable) {
                        runCatching { link.disconnect() }
                        // Without reaching adbd, a late approval would turn it on with no session: keep watching, except after losing the pairing.
                        if (turnedOn) restore(keepWatching = !connected && !answered)
                    }
                }
            }
        }

    /** First connection after pairing: TikStik grants itself the permission; if the phone refuses, it stays in manual mode. */
    suspend fun finishFirstConnection(): Boolean = withContext(io) {
        session.withLock {
            val granted = try {
                connect()
                grant()
            } finally {
                runCatching { link.disconnect() }
            }
            // From now on TikStik turns it on only to read and off when done.
            if (granted) device.setWirelessDebugging(false) else store.manual = true
            _status.value = read()
            granted
        }
    }

    // Checked afterwards: some OEM layers accept the command without granting anything (docs/FABRICANTES.md).
    private fun grant(): Boolean {
        AdbShell(link).run("pm grant ${device.packageName} android.permission.WRITE_SECURE_SETTINGS")
        val granted = device.canToggleWirelessDebugging
        if (granted) store.manual = false
        return granted
    }

    /** Pairs with the code from "Pair device with pairing code", then attempts the first connection. */
    suspend fun pair(host: String, port: Int, code: String): Boolean = withContext(io) {
        val paired = session.withLock {
            // Pairing needs debugging turned on by the user: the watch must not turn it off.
            stopWatching()
            link.pair(host, port, code)
        }
        if (paired) {
            store.paired = true
            try {
                finishFirstConnection()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Logger.warn(AREA, "first connection after pairing failed", e)
            }
        }
        _status.value = read()
        paired
    }

    override fun revoke() {
        scope.launch(io) {
            session.withLock {
                link.reset()
                store.forget()
                // After disconnecting, the user will pair again with debugging on.
                stopWatching()
            }
            _status.value = read()
        }
    }

    // Android keeps debugging on only on approved Wi-Fi networks; on a new one it turns it off at once and asks.
    private suspend fun awaitNetworkApproval(onNetworkApproval: () -> Unit): Boolean {
        repeat(APPROVAL_CHECKS) {
            delay(APPROVAL_CHECK_MS)
            if (!device.wirelessDebuggingOn) {
                onNetworkApproval()
                var waited = 0L
                while (waited < APPROVAL_WAIT_MS) {
                    delay(APPROVAL_POLL_MS)
                    waited += APPROVAL_POLL_MS
                    if (device.wirelessDebuggingOn) return true
                }
                return false
            }
        }
        return true
    }

    // adbd takes a few seconds to announce itself over mDNS after debugging is turned on.
    private suspend fun connect() {
        var last: Exception? = null
        repeat(CONNECT_ATTEMPTS) { attempt ->
            try {
                if (link.connect()) return
            } catch (e: PairingLostException) {
                store.paired = false
                _status.value = read()
                throw AccessException(AccessError.PAIRING_LOST, e)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                last = e
            }
            delay(CONNECT_BACKOFF_MS * (attempt + 1))
        }
        throw AccessException(AccessError.CONNECTION_FAILED, last)
    }

    // If the user approves the Wi-Fi late, Android would turn it on with no session: watch for a while and turn it off.
    private fun restore(keepWatching: Boolean) {
        device.setWirelessDebugging(false)
        if (!keepWatching) {
            store.ownsWirelessDebugging = false
            return
        }
        lateApprovalWatch?.cancel()
        lateApprovalWatch = scope.launch(io) {
            var waited = 0L
            while (isActive && waited < LATE_APPROVAL_WATCH_MS) {
                delay(APPROVAL_POLL_MS)
                waited += APPROVAL_POLL_MS
                whenIdle { if (device.wirelessDebuggingOn) device.setWirelessDebugging(false) }
            }
            whenIdle { store.ownsWirelessDebugging = false }
        }
    }

    private fun stopWatching() {
        lateApprovalWatch?.cancel()
        lateApprovalWatch = null
        store.ownsWirelessDebugging = false
    }

    // On start or resume: if TikStik left it on (process died midway), turn it off.
    private fun restoreIfOwned() {
        if (lateApprovalWatch?.isActive == true) return
        whenIdle {
            if (store.ownsWirelessDebugging) {
                if (device.wirelessDebuggingOn) device.setWirelessDebugging(false)
                store.ownsWirelessDebugging = false
            }
        }
    }

    // Only with no open session, and none can start meanwhile: a read's own debugging is never turned off.
    private inline fun whenIdle(block: () -> Unit) {
        if (!session.tryLock()) return
        try {
            block()
        } finally {
            session.unlock()
        }
    }

    private companion object {
        const val AREA = "Access"
        const val APPROVAL_CHECKS = 8
        const val APPROVAL_CHECK_MS = 250L
        const val APPROVAL_POLL_MS = 300L
        const val APPROVAL_WAIT_MS = 60_000L
        const val LATE_APPROVAL_WATCH_MS = 5 * 60_000L
        const val CONNECT_ATTEMPTS = 4
        const val CONNECT_BACKOFF_MS = 1_000L
    }
}
