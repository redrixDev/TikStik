// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.importing

import com.google.common.truth.Truth.assertThat
import com.redrixdev.tikstik.access.CacheFile
import com.redrixdev.tikstik.media.Fingerprint
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImportMirrorTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val sticker = CacheFile("/c/sticker.cnt", 3, 100)
    private val avatar = CacheFile("/c/dm_avatar/a.cnt", 3, 100)

    @Test
    fun ofAReferenceOnlyTheFingerprintIsKept() {
        val mirror = ImportMirror(folder.root)
        val fingerprint = Fingerprint.parse("0".repeat(BITS_HEX) + ":100")!!
        mirror.put(sticker, byteArrayOf(1, 2, 3))
        // A full copy of a reference, as earlier versions used to leave behind.
        mirror.put(avatar, byteArrayOf(4, 5, 6))
        mirror.putReferenceFingerprint(avatar, fingerprint)

        mirror.keepOnly(listOf(sticker), references = listOf(avatar))

        assertThat(mirror.file(sticker)).isNotNull()
        assertThat(mirror.file(avatar)).isNull()
        assertThat(mirror.referenceFingerprint(avatar)).isEqualTo(fingerprint)
    }

    @Test
    fun aFileThatChangedIsReadAgainAndItsOldCopyGoes() {
        val mirror = ImportMirror(folder.root)
        mirror.put(sticker, byteArrayOf(1, 2, 3))
        val rewritten = sticker.copy(modifiedSec = 101)
        assertThat(mirror.file(sticker)).isNotNull()
        assertThat(mirror.file(sticker.copy(size = 4))).isNull()
        assertThat(mirror.file(rewritten)).isNull()

        mirror.keepOnly(listOf(rewritten), references = emptyList())
        assertThat(folder.root.listFiles()).isEmpty()
    }

    @Test
    fun aStickerKeepsItsFingerprintBesideItsCopy() {
        val mirror = ImportMirror(folder.root)
        val fingerprint = Fingerprint.parse("f".repeat(BITS_HEX) + ":80")!!
        mirror.put(sticker, byteArrayOf(1, 2, 3))
        val copy = mirror.file(sticker)!!
        mirror.putFingerprint(copy, fingerprint)

        mirror.keepOnly(listOf(sticker), references = emptyList())
        assertThat(mirror.fingerprint(copy)).isEqualTo(fingerprint)
    }

    @Test
    fun aReferenceThatCannotBeDecodedIsNotReadAgain() {
        val mirror = ImportMirror(folder.root)
        assertThat(mirror.hasReference(avatar)).isFalse()
        mirror.putReferenceFingerprint(avatar, null)

        mirror.keepOnly(listOf(sticker), references = listOf(avatar))
        assertThat(mirror.hasReference(avatar)).isTrue()
        assertThat(mirror.referenceFingerprint(avatar)).isNull()
    }

    private companion object {
        const val BITS_HEX = 64
    }
}
