// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.importing

import com.redrixdev.tikstik.access.CacheFile
import com.redrixdev.tikstik.core.common.writeAtomically
import com.redrixdev.tikstik.media.Fingerprint
import java.io.File
import java.security.MessageDigest

/** A regenerable copy of what was read from TikTok: only new or changed files are read again, and the fingerprint is computed once. */
class ImportMirror(private val dir: File) {

    // No cache names on disk: the path is hashed, with size and date to notice changes.
    private fun name(file: CacheFile): String =
        MessageDigest.getInstance("SHA-1").digest(file.path.toByteArray()).toHexString() + "_" + file.size + "_" + file.modifiedSec

    fun file(file: CacheFile): File? = File(dir, name(file)).takeIf { it.length() == file.size }

    fun put(file: CacheFile, bytes: ByteArray) = File(dir, name(file)).writeAtomically(bytes)

    fun fingerprint(copy: File): Fingerprint? = runCatching { Fingerprint.parse(fingerprintFile(copy).readText()) }.getOrNull()

    fun putFingerprint(copy: File, fingerprint: Fingerprint) = fingerprintFile(copy).writeAtomically(fingerprint.encode().toByteArray())

    // Of references (images that are not stickers, like avatars) only the fingerprint is kept, never the image.
    fun referenceFingerprint(file: CacheFile): Fingerprint? = fingerprint(File(dir, name(file)))

    fun hasReference(file: CacheFile): Boolean = fingerprintFile(File(dir, name(file))).exists()

    // One the system cannot decode is noted without a fingerprint, so it is not read again on every import.
    fun putReferenceFingerprint(file: CacheFile, fingerprint: Fingerprint?) =
        fingerprintFile(File(dir, name(file))).writeAtomically((fingerprint?.encode() ?: UNDECODABLE).toByteArray())

    /** Drops what is no longer in TikTok's cache or changed; of [references], only the fingerprint stays. */
    fun keepOnly(files: Collection<CacheFile>, references: Collection<CacheFile>) {
        val keep = files.mapTo(HashSet(), ::name)
        val fingerprintsOnly = references.mapTo(HashSet(), ::name)
        dir.listFiles()?.forEach {
            val key = it.name.substringBefore('.')
            val isFingerprint = it.name.endsWith(".fp")
            if (key !in keep && !(isFingerprint && key in fingerprintsOnly)) it.delete()
        }
    }

    private fun fingerprintFile(copy: File) = File(copy.path + ".fp")

    private companion object {
        const val UNDECODABLE = "-"
    }
}
