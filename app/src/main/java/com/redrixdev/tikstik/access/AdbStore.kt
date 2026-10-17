// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.access

import androidx.core.util.AtomicFile
import com.redrixdev.tikstik.core.common.writeAtomically
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DERBitString
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.x509.Time
import org.bouncycastle.asn1.x509.V3TBSCertificateGenerator
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

/** TikStik's ADB identity and its marks, in `noBackupFilesDir/adb`: they never travel in backups or transfers. */
class AdbStore(private val dir: File) {

    class Identity(val key: PrivateKey, val certificate: Certificate)

    private val identityPath = File(dir, "identity")
    private val identityFile = AtomicFile(identityPath)
    private val pairedMark = File(dir, "paired")
    private val ownedMark = File(dir, "wireless_debugging_on_by_tikstik")
    private val manualMark = File(dir, "manual")

    var paired: Boolean
        get() = pairedMark.exists()
        set(value) = mark(pairedMark, value)

    // TikStik turned debugging on and still has to turn it off; survives process death.
    var ownsWirelessDebugging: Boolean
        get() = ownedMark.exists()
        set(value) = mark(ownedMark, value)

    // The `pm grant` had no effect: the user turns debugging on themselves.
    var manual: Boolean
        get() = manualMark.exists()
        set(value) = mark(manualMark, value)

    /** The key and the certificate, created the first time; kept together in a single atomic file. */
    @Synchronized
    fun identity(): Identity = load() ?: create().also(::save)

    // The identity is kept: libadb pins the TLS of the first one and another key would fail until a reboot.
    @Synchronized
    fun forget() {
        paired = false
        manual = false
    }

    /** At process start: when unpaired, the previous key is no longer needed and is not kept. */
    @Synchronized
    fun forgetIdentityIfUnpaired() {
        if (!paired) identityFile.delete()
    }

    private fun mark(file: File, on: Boolean) {
        if (on) {
            dir.mkdirs()
            file.createNewFile()
        } else {
            file.delete()
        }
    }

    private fun load(): Identity? = runCatching {
        DataInputStream(ByteArrayInputStream(identityFile.readFully())).use { input ->
            val key = ByteArray(input.readInt()).also(input::readFully)
            val certificate = input.readBytes()
            Identity(
                KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(key)),
                CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(certificate)),
            )
        }
    }.getOrNull()

    private fun save(identity: Identity) {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).run {
            writeInt(identity.key.encoded.size)
            write(identity.key.encoded)
            write(identity.certificate.encoded)
            flush()
        }
        identityPath.writeAtomically(bytes.toByteArray())
    }

    // adbd only looks at the certificate's public key; a long-lived self-signed one is enough.
    private fun create(): Identity {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048, SecureRandom()) }.generateKeyPair()
        val name = X500Name("CN=TikStik")
        val algorithm = AlgorithmIdentifier(PKCSObjectIdentifiers.sha256WithRSAEncryption, DERNull.INSTANCE)
        val now = System.currentTimeMillis()
        val tbs = V3TBSCertificateGenerator().apply {
            setSerialNumber(ASN1Integer(BigInteger(63, SecureRandom())))
            setIssuer(name)
            setSubject(name)
            setStartDate(Time(Date(now)))
            setEndDate(Time(Date(now + VALIDITY_MS)))
            setSignature(algorithm)
            setSubjectPublicKeyInfo(SubjectPublicKeyInfo.getInstance(pair.public.encoded))
        }.generateTBSCertificate()
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(pair.private)
            update(tbs.encoded)
            sign()
        }
        val certificate = DERSequence(ASN1EncodableVector().apply { add(tbs); add(algorithm); add(DERBitString(signature)) })
        return Identity(pair.private, CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(certificate.encoded)))
    }

    private companion object {
        const val VALIDITY_MS = 30L * 365 * 24 * 60 * 60 * 1000
    }
}
