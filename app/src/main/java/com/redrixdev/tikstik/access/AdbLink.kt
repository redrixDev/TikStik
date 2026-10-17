// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.access

import android.content.Context
import android.os.Build
import com.redrixdev.tikstik.core.common.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.AdbStream
import io.github.muntashirakon.adb.android.AdbMdns
import java.io.Closeable
import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A command's stream: raw reads, no PTY. */
interface ShellStream : Closeable {
    val isClosed: Boolean

    fun read(buffer: ByteArray, offset: Int, length: Int): Int
}

/** The ADB connection to the phone's own wireless debugging; replaced by a fake in tests. */
interface AdbLink {
    /** A single attempt; [PairingLostException] if adbd no longer recognizes the key. */
    fun connect(): Boolean

    fun open(command: String): ShellStream

    fun disconnect()

    fun pair(host: String, port: Int, code: String): Boolean

    /** After forgetting the identity: the next connection will use the new one. */
    fun reset()
}

class PairingLostException(cause: Throwable) : IOException(cause)

@Singleton
class LibAdbLink @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val store: AdbStore,
) : AdbLink {

    private var manager: Manager? = null

    @Synchronized
    private fun manager(): Manager = manager ?: Manager(store.identity()).also { manager = it }

    private val refused = ArrayDeque<Int>()

    override fun connect(): Boolean {
        val adb = manager()
        if (adb.isConnected) return true
        val (host, port) = discover() ?: return false
        return try {
            adb.connect(host, port)
        } catch (e: AdbPairingRequiredException) {
            throw PairingLostException(e)
        } catch (e: IOException) {
            if (generateSequence<Throwable>(e) { it.cause }.none { it is ConnectException }) throw e
            // mDNS may still announce the previous session's port: discard it and wait for another.
            if (refused.size == MAX_REFUSED) refused.removeFirst()
            refused.addLast(port)
            Logger.warn(AREA, "adbd refused port $port")
            false
        }
    }

    // Like libadb's connectTls, but without settling for the first announcement: it may belong to an adbd already stopped.
    private fun discover(): Pair<String, Int>? {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(CONNECT_TIMEOUT_MS)
        while (true) {
            // With no margin left, no new search is started: stopping it before it begins would leave it alive.
            if (deadline - System.nanoTime() < TimeUnit.MILLISECONDS.toNanos(MIN_SEARCH_MS)) return null
            val found = LinkedBlockingQueue<Pair<String, Int>>()
            val mdns = AdbMdns(context, AdbMdns.SERVICE_TYPE_TLS_CONNECT) { host: InetAddress?, port: Int ->
                if (host != null && port > 0) found += host.hostAddress.orEmpty() to port
            }
            mdns.start()
            try {
                val next = found.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS) ?: return null
                if (next.second !in refused) return next
            } finally {
                mdns.stop()
            }
            // Only the discarded port arrived: a fresh search resolves the service again.
            Thread.sleep(REDISCOVER_MS)
        }
    }

    override fun open(command: String): ShellStream = LibAdbStream(manager().openStream(command))

    override fun disconnect() {
        manager?.disconnect()
    }

    override fun pair(host: String, port: Int, code: String): Boolean = manager().pair(host, port, code)

    @Synchronized
    override fun reset() {
        runCatching { manager?.close() }
        manager = null
        refused.clear()
    }

    private class Manager(private val identity: AdbStore.Identity) : AbsAdbConnectionManager() {
        init {
            api = Build.VERSION.SDK_INT
            // Without a timeout, a peer that accepts TCP but never completes the handshake would block the session forever.
            setTimeout(HANDSHAKE_TIMEOUT_S, TimeUnit.SECONDS)
        }

        override fun getPrivateKey(): PrivateKey = identity.key

        override fun getCertificate(): Certificate = identity.certificate

        override fun getDeviceName(): String = "TikStik"
    }

    // Straight on AdbStream: AdbInputStream returns -1 as soon as the stream is marked closed and loses what was left.
    private class LibAdbStream(private val stream: AdbStream) : ShellStream {
        override val isClosed: Boolean get() = stream.isClosed

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = stream.read(buffer, offset, length)

        override fun close() = stream.close()
    }

    private companion object {
        const val AREA = "Access"
        const val CONNECT_TIMEOUT_MS = 6_000L
        const val MAX_REFUSED = 8
        const val REDISCOVER_MS = 500L
        const val HANDSHAKE_TIMEOUT_S = 15L
        const val MIN_SEARCH_MS = 1_000L
    }
}
