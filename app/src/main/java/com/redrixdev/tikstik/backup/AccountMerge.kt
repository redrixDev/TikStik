// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.backup

import com.redrixdev.tikstik.core.data.settings.BackupInfo

/** The account just signed in: its backup counts as merged into this phone only when it holds nothing to bring. */
fun BackupInfo.signedIn(email: String, userId: String, remote: BackupManifest?): BackupInfo {
    val toBring = remote != null && remote.stickers.isNotEmpty()
    return copy(
        email = email,
        userId = userId,
        problem = null,
        // The time of the backup already on Drive: right after signing in it is not "none yet".
        lastBackupAt = remote?.savedAt?.takeIf { it > 0 } ?: lastBackupAt,
        // Every sign-in merges again: what reached Drive from another phone, or while signed out, comes back too.
        synced = if (toBring) synced - userId else (synced + userId).distinct(),
        // From now on this phone's packs go to this account's backup.
        owner = userId,
    )
}

/** Whether this phone still has to merge the signed-in account's backup. */
val BackupInfo.needsRestore: Boolean
    get() = email != null && userId != null && userId !in synced

/** Whether signing in with [userId] asks first: the packs on this phone went to another account, and they would go to this one too. */
fun BackupInfo.asksToMerge(userId: String, hasPacks: Boolean): Boolean = hasPacks && owner != null && owner != userId
