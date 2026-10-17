// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.data.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.redrixdev.tikstik.backup.BackupProblem
import com.redrixdev.tikstik.whatsapp.WhatsAppApp
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/** The app's settings in a single DataStore; every new field carries a default, so there are no migrations. */
@Serializable
data class AppSettings(
    val onboardingDone: Boolean = false,
    // "How it works" shows only the first time connecting finishes; afterwards, from Settings.
    val howToSeen: Boolean = false,
    // null: the default pack name.
    val packBaseName: String? = null,
    // The pack that did not fit in WhatsApp: it is added automatically on return once there is room.
    val pendingAdd: PendingAdd? = null,
    val backup: BackupInfo = BackupInfo(),
    // What each restored pack is missing: the backup keeps listing it so it is not deleted from Drive.
    val packGaps: Map<String, List<PackGap>> = emptyMap(),
    val updates: UpdateInfo = UpdateInfo(),
)

@Serializable
data class PackGap(val stickerId: String, val emoji: String)

/**
 * The account and the backup: [email] null when signed out; [userId] is `g_` plus the Drive UUID; [synced] lists accounts whose backup was merged since they last signed in;
 * [owner] is the last account this phone's packs went to, kept after signing out; [restoredAt] is when the backup was last merged here.
 */
@Serializable
data class BackupInfo(
    val email: String? = null,
    val userId: String? = null,
    val lastBackupAt: Long = 0,
    val lastHash: String? = null,
    val problem: BackupProblem? = null,
    val synced: List<String> = emptyList(),
    val owner: String? = null,
    val restoredAt: Long = 0,
)

@Serializable
data class PendingAdd(val packId: String, val app: WhatsAppApp = WhatsAppApp.CONSUMER)

/** New versions: [found] is what GitHub listed last; [skipped] the one answered with "later". */
@Serializable
data class UpdateInfo(
    val checkAutomatically: Boolean = true,
    val lastCheckAt: Long = 0,
    val skipped: String? = null,
    val found: FoundUpdate? = null,
)

@Serializable
data class FoundUpdate(val versionName: String, val notes: String, val apkUrl: String, val apkBytes: Long)

object AppSettingsSerializer : Serializer<AppSettings> {

    // An enum value unknown to this version falls back to its default instead of wiping every setting.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override val defaultValue = AppSettings()

    override suspend fun readFrom(input: InputStream): AppSettings = try {
        json.decodeFromString(AppSettings.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable settings", e)
    }

    override suspend fun writeTo(t: AppSettings, output: OutputStream) {
        output.write(json.encodeToString(AppSettings.serializer(), t).encodeToByteArray())
    }
}
