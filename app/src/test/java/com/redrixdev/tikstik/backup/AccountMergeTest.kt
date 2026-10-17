// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.backup

import com.google.common.truth.Truth.assertThat
import com.redrixdev.tikstik.core.data.settings.BackupInfo
import org.junit.Test

class AccountMergeTest {

    private val sticker = BackupSticker("s1", "webp", false, 512, 512, 1, 0, 3, addedAt = 10)
    private val withBackup = BackupManifest(savedAt = 500, device = "other", stickers = listOf(sticker), packs = emptyList())
    private val empty = BackupManifest(savedAt = 500, device = "other", stickers = emptyList(), packs = emptyList())

    @Test
    fun theFirstSignInWithABackupMergesIt() {
        assertThat(BackupInfo().signedIn("me@example.com", "g_1", withBackup).needsRestore).isTrue()
    }

    @Test
    fun signingInAgainMergesAgain() {
        val mergedBefore = BackupInfo(synced = listOf("g_1"))
        assertThat(mergedBefore.signedIn("me@example.com", "g_1", withBackup).needsRestore).isTrue()
    }

    @Test
    fun anAccountWithNothingToBringCountsAsMerged() {
        assertThat(BackupInfo().signedIn("me@example.com", "g_1", null).needsRestore).isFalse()
        assertThat(BackupInfo().signedIn("me@example.com", "g_1", empty).needsRestore).isFalse()
    }

    @Test
    fun otherAccountsKeepTheirState() {
        val info = BackupInfo(synced = listOf("g_2")).signedIn("me@example.com", "g_1", withBackup)
        assertThat(info.synced).containsExactly("g_2")
    }

    @Test
    fun signedOutNothingIsMerged() {
        assertThat(BackupInfo(synced = emptyList()).needsRestore).isFalse()
    }

    @Test
    fun theBackupTimeComesFromDrive() {
        assertThat(BackupInfo(lastBackupAt = 100).signedIn("me@example.com", "g_1", withBackup).lastBackupAt).isEqualTo(500)
        assertThat(BackupInfo(lastBackupAt = 100).signedIn("me@example.com", "g_1", null).lastBackupAt).isEqualTo(100)
    }

    @Test
    fun theAccountSignedInTakesThePacksOfThisPhone() {
        assertThat(BackupInfo(owner = "g_2").signedIn("me@example.com", "g_1", withBackup).owner).isEqualTo("g_1")
        assertThat(BackupInfo().signedIn("me@example.com", "g_1", null).owner).isEqualTo("g_1")
    }

    @Test
    fun packsOfAnotherAccountAskBeforeMerging() {
        assertThat(BackupInfo(owner = "g_2").asksToMerge("g_1", hasPacks = true)).isTrue()
    }

    @Test
    fun theSameAccountPacksMadeWithoutOneOrNoPacksDoNotAsk() {
        assertThat(BackupInfo(owner = "g_1").asksToMerge("g_1", hasPacks = true)).isFalse()
        assertThat(BackupInfo(owner = null).asksToMerge("g_1", hasPacks = true)).isFalse()
        assertThat(BackupInfo(owner = "g_2").asksToMerge("g_1", hasPacks = false)).isFalse()
    }
}
