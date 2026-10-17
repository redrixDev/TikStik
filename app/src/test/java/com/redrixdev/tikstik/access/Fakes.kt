// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.access

import java.io.ByteArrayInputStream
import java.io.IOException

class FakeDevice(
    override var devOptionsEnabled: Boolean = true,
    var on: Boolean = false,
    override var canToggleWirelessDebugging: Boolean = true,
    override var onWifi: Boolean = true,
    /** Unapproved Wi-Fi: Android turns debugging off as soon as it is turned on. */
    var networkApproved: Boolean = true,
) : DeviceState {
    val switches = mutableListOf<Boolean>()

    override val wirelessDebuggingOn: Boolean get() = on
    override val packageName = "com.redrixdev.tikstik"

    override fun setWirelessDebugging(on: Boolean): Boolean {
        switches += on
        this.on = on && networkApproved
        return true
    }
}

/** Simulated adbd: each command answers with whatever [respond] returns plus the end marker the shell asks for. */
class FakeLink(var respond: (String) -> ByteArray = { ByteArray(0) }) : AdbLink {
    var connectResult: () -> Boolean = { true }
    var connected = false
    val commands = mutableListOf<String>()
    var chunk = 7

    override fun connect(): Boolean = connectResult().also { connected = it }

    override fun open(command: String): ShellStream {
        check(connected) { "not connected" }
        val body = command.removePrefix("exec:").substringBeforeLast("; echo ")
        commands += body
        val marker = command.substringAfterLast("; echo ") + "\n"
        return FakeStream(respond(body) + marker.toByteArray(), chunk)
    }

    override fun disconnect() {
        connected = false
    }

    override fun pair(host: String, port: Int, code: String): Boolean = code == "123456"

    override fun reset() = Unit
}

class FakeStream(bytes: ByteArray, private val chunk: Int, private val failAfter: Int = Int.MAX_VALUE) : ShellStream {
    private val input = ByteArrayInputStream(bytes)
    private var served = 0
    override var isClosed = false

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (isClosed) throw IOException("Stream closed.")
        if (served >= failAfter) throw IOException("connection reset")
        val n = input.read(buffer, offset, minOf(length, chunk))
        if (n > 0) served += n
        return n
    }

    override fun close() {
        isClosed = true
    }
}
