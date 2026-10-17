// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.media

import android.os.Debug
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/** Converts every fixture, validates with the official rules and writes timings and memory to media-report.tsv. */
@RunWith(AndroidJUnit4::class)
class ConversionTest {

    private val app = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun everyFixtureBecomesAValidStickerAndTray() {
        val report = StringBuilder("fixture\tinput_bytes\tframes\tduration_ms\tms\toutput_bytes\toutput_frames\toutput_ms\tpeak_native_mb\n")
        for (path in MediaFixtures.paths) {
            val original = MediaFixtures.bytes(path)
            val probe = MediaProbe.probe(original)
            val baseline = Debug.getNativeHeapAllocatedSize()
            val peak = AtomicLong(baseline)
            val sampler = thread {
                while (!Thread.currentThread().isInterrupted) {
                    peak.accumulateAndGet(Debug.getNativeHeapAllocatedSize(), ::maxOf)
                    try {
                        Thread.sleep(2)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
            val start = SystemClock.elapsedRealtime()
            val copy = WhatsAppConverter.sticker(original, probe.animated)
            val elapsed = SystemClock.elapsedRealtime() - start
            sampler.interrupt()
            sampler.join()
            val tray = WhatsAppConverter.tray(copy)

            assertWithMessage(path).that(StickerOutput.isValidSticker(copy, probe.animated)).isTrue()
            assertWithMessage(path).that(StickerOutput.isValidTray(tray)).isTrue()
            val out = MediaProbe.probe(copy)
            assertWithMessage(path).that(out.durationMs).isAtMost(StickerOutput.MAX_DURATION_MS)
            assertWithMessage(path).that(out.frames).isAtMost(80)
            report.append("$path\t${original.size}\t${probe.frames}\t${probe.durationMs}\t$elapsed\t${copy.size}\t${out.frames}\t${out.durationMs}\t")
                .append(String.format(Locale.ROOT, "%.1f\n", (peak.get() - baseline) / 1048576.0))
        }
        File(app.getExternalFilesDir(null), "media-report.tsv").writeText(report.toString())
    }

    @Test
    fun aCompliantStickerIsKeptByteForByte() {
        val compliant = MediaFixtures.paths.map(MediaFixtures::bytes).first { StickerOutput.isValidSticker(it, MediaProbe.probe(it).animated) }
        assertThat(WhatsAppConverter.sticker(compliant, MediaProbe.probe(compliant).animated)).isSameInstanceAs(compliant)
    }

    @Test
    fun longAnimationsAreCutAtTenSecondsKeepingTheirPace() {
        val long = MediaFixtures.bytes("media/webp_long_320_150f_100ms.webp")
        val out = MediaProbe.probe(WhatsAppConverter.sticker(long, animated = true))
        assertThat(out.durationMs).isIn(9_900..10_000)
    }

    @Test
    fun anAnimationThatBarelyMovesStaysAnimatedForItsWholeLength() {
        val almostStill = MediaFixtures.bytes("media/webp_almost_still_256_4f_100ms.webp")
        val out = MediaProbe.probe(WhatsAppConverter.sticker(almostStill, animated = true))
        assertThat(out.frames).isAtLeast(2)
        assertThat(out.durationMs).isEqualTo(400)
    }

    @Test
    fun brokenInputFailsWithAnIOException() {
        val broken = MediaFixtures.bytes("media/gif_busy_360_120f_40ms.gif").copyOf(2_000)
        val result = runCatching { WhatsAppConverter.sticker(broken, animated = true) }
        assertThat(result.exceptionOrNull()).isInstanceOf(java.io.IOException::class.java)
        assertThat(runCatching { WhatsAppConverter.sticker(ByteArray(10), animated = false) }.exceptionOrNull()).isInstanceOf(java.io.IOException::class.java)
    }
}
