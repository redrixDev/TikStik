// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.backup

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.StringRes
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.collection.CollectionRepository
import com.redrixdev.tikstik.core.common.ApplicationScope
import com.redrixdev.tikstik.core.common.Clock
import com.redrixdev.tikstik.core.common.IoDispatcher
import com.redrixdev.tikstik.core.common.Logger
import com.redrixdev.tikstik.core.data.settings.SettingsRepository
import com.redrixdev.tikstik.core.ui.Notice
import com.redrixdev.tikstik.core.ui.Notices
import com.redrixdev.tikstik.core.ui.UiText
import com.redrixdev.tikstik.packs.CopyPreparation
import com.redrixdev.tikstik.packs.PackLimits
import com.redrixdev.tikstik.packs.PackRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Progress of a backup or a restore. */
data class BackupProgress(val done: Int, val total: Int)

/** The approved text of each problem, for the notice and for the account alike. */
@StringRes
fun BackupProblem.message(): Int = when (this) {
    BackupProblem.NO_NETWORK -> R.string.backup_err_network
    BackupProblem.SIGN_IN -> R.string.backup_err_sign_in
    BackupProblem.DRIVE_FULL -> R.string.backup_err_full
    BackupProblem.NEWER_VERSION -> R.string.backup_err_newer
    BackupProblem.FAILED -> R.string.backup_err_failed
}

/** What Settings and the "Your account" sheet show; [mergeWith] is the account that waits for an answer before this phone's packs go to it. */
data class AccountState(
    val email: String? = null,
    val work: BackupWork? = null,
    val progress: BackupProgress? = null,
    val problem: BackupProblem? = null,
    val lastBackupAt: Long = 0,
    val mergeWith: String? = null,
)

fun interface DriveClients {
    fun forToken(token: String): DriveClient
}

/** The outcome of one Worker run. */
enum class WorkOutcome { DONE, RETRY, FAILED }

/** The Google account and the Drive backup, which runs in WorkManager so it finishes with the app closed. */
@Singleton
class BackupAccount @Inject constructor(
    private val authorizer: GoogleAuthorizer,
    private val drives: DriveClients,
    private val sync: BackupSync,
    private val settings: SettingsRepository,
    private val workManager: WorkManager,
    private val copies: CopyPreparation,
    private val notices: Notices,
    private val clock: Clock,
    @param:IoDispatcher private val io: CoroutineDispatcher,
    @param:ApplicationScope private val scope: CoroutineScope,
    collection: CollectionRepository,
    private val packs: PackRepository,
) {
    // Connecting, or the last backup before signing out: neither is a WorkManager job.
    private val busy = MutableStateFlow<BackupWork?>(null)
    private val pending = MutableStateFlow<SignIn?>(null)
    private val authIntents = Channel<PendingIntent>(Channel.CONFLATED)

    /** Choosing the account or granting the permission: the screen launches it and reports back through [onAuthResult]. */
    val authRequests: Flow<PendingIntent> = authIntents.receiveAsFlow()

    val state: Flow<AccountState> = combine(
        settings.settings.map { it.backup },
        busy,
        workManager.getWorkInfosForUniqueWorkFlow(BACKUP),
        workManager.getWorkInfosForUniqueWorkFlow(RESTORE),
        pending,
    ) { info, busyNow, backups, restores, waiting ->
        val restoring = restores.firstOrNull { it.state == WorkInfo.State.RUNNING }
        val backingUp = backups.firstOrNull { it.state == WorkInfo.State.RUNNING }
        AccountState(
            email = info.email,
            work = when {
                busyNow == BackupWork.CONNECTING -> BackupWork.CONNECTING
                restoring != null -> BackupWork.RESTORING
                backingUp != null -> BackupWork.BACKING_UP
                else -> busyNow
            },
            progress = (restoring ?: backingUp)?.let(::progressOf),
            problem = info.problem,
            lastBackupAt = info.lastBackupAt,
            mergeWith = waiting?.email,
        )
    }

    init {
        // Shortly after every change, when signed in: REPLACE with a delay works as a debounce.
        scope.launch {
            // The state at start-up is not a change, and `offered` does not count: WhatsApp does not change the backup.
            val packsForBackup = packs.packs.map { list -> list.map { it.copy(pack = it.pack.copy(offered = false)) } }
            combine(collection.collection.distinctUntilChanged(), packsForBackup.distinctUntilChanged()) { _, _ -> }.drop(1).collect {
                keepAlive { scheduleIfSignedIn() }
            }
        }
    }

    /** On opening the app: a restore that did not finish (no network, a 5xx), and once a day what other phones backed up and the packs that waited for room. */
    fun restoreIfDue() = scope.launch {
        val info = settings.settings.first().backup
        // A failure that retrying does not fix is not repeated (nor announced) at every start; a full Drive or a newer backup only stop backups.
        val stuck = info.problem == BackupProblem.SIGN_IN || info.problem == BackupProblem.FAILED
        val due = info.email != null && clock.now() - info.restoredAt !in 0..DAY_MS
        if ((info.needsRestore || due) && !stuck) enqueueRestore()
    }

    fun signIn() {
        if (!busy.compareAndSet(null, BackupWork.CONNECTING)) return
        scope.launch {
            when (val step = authorizer.authorize(null)) {
                is AuthStep.Token -> finishSignIn(step.token)
                is AuthStep.NeedsUser -> authIntents.send(step.intent)
                AuthStep.Failed, AuthStep.Unavailable -> signInFailed()
            }
        }
    }

    fun onAuthResult(ok: Boolean, data: Intent?) {
        if (!ok) {
            busy.value = null
            return
        }
        scope.launch {
            when (val step = authorizer.fromIntent(data)) {
                is AuthStep.Token -> finishSignIn(step.token)
                else -> signInFailed()
            }
        }
    }

    /** The packs on this phone go to the account that asked: [AccountState.mergeWith]. */
    fun confirmMerge() {
        val signIn = pending.getAndUpdate { null } ?: return
        scope.launch {
            try {
                accept(signIn)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.warn(AREA, "sign in failed: ${e.javaClass.simpleName}")
                signInFailed()
            }
        }
    }

    /** Nothing was saved for that account: only the permission it just granted is withdrawn. */
    fun cancelMerge() {
        val signIn = pending.getAndUpdate { null } ?: return
        scope.launch { authorizer.revoke(signIn.email) }
    }

    fun signOut() = scope.launch {
        if (!busy.compareAndSet(null, BackupWork.BACKING_UP)) return@launch
        try {
            signOutNow()
        } finally {
            busy.value = null
        }
    }

    private suspend fun signOutNow() {
        val email = settings.settings.first().backup.email ?: return
        // A restore halfway would hold the backup back; what it did not bring stays in Drive.
        workManager.cancelUniqueWork(RESTORE)
        // The changes of the last seconds reach Drive before the account is forgotten; without a connection, they stay on the phone.
        if (runBackup(manual = false) { _, _ -> } != WorkOutcome.DONE) {
            notices.post(Notice(UiText.Res(R.string.backup_sign_out_failed), error = true))
        }
        workManager.cancelUniqueWork(BACKUP)
        authorizer.revoke(email)
        // The backup stays in their Drive; only the account is forgotten here (an install signed in before `owner` existed learns it now).
        settings.updateBackup { it.copy(email = null, userId = null, problem = null, lastBackupAt = 0, lastHash = null, owner = it.owner ?: it.userId) }
    }

    fun backupNow() = schedule(manual = true)

    /** Run by [BackupWorker]. */
    suspend fun runBackup(manual: Boolean, onProgress: suspend (Int, Int) -> Unit): WorkOutcome {
        val info = settings.settings.first().backup
        val email = info.email ?: return WorkOutcome.DONE
        return try {
            val outcome = withDrive(email) { drive -> sync.backup(drive, clock.now(), info.lastHash, info.userId?.removePrefix(USER_PREFIX), onProgress) }
            settings.updateBackup {
                it.copy(
                    lastBackupAt = if (outcome.changed || manual) clock.now() else it.lastBackupAt,
                    lastHash = outcome.hash,
                    problem = null,
                )
            }
            if (manual) notices.post(Notice(UiText.Res(R.string.backup_done)))
            WorkOutcome.DONE
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed(e, manual)
        }
    }

    /** Run by [RestoreWorker]: after signing in with an account that has a backup, and once a day while signed in. */
    suspend fun runRestore(onProgress: suspend (Int, Int) -> Unit): WorkOutcome {
        val info = settings.settings.first().backup
        val email = info.email ?: return WorkOutcome.DONE
        val userId = info.userId ?: return WorkOutcome.DONE
        return try {
            val outcome = withDrive(email) { drive -> sync.restore(drive, onProgress) }
            // Only a backup clears a full Drive or a newer backup.
            settings.updateBackup { info ->
                info.copy(
                    synced = (info.synced + userId).distinct(),
                    problem = info.problem.takeIf { it == BackupProblem.DRIVE_FULL || it == BackupProblem.NEWER_VERSION },
                    restoredAt = clock.now(),
                )
            }
            if (outcome.stickers > 0) copies.start()
            restoredNotice(outcome)?.let(notices::post)
            // Said once, after signing in; the daily merges bring them quietly as room is made.
            if (outcome.notFit > 0 && info.needsRestore) {
                notices.post(Notice(UiText.Plural(R.plurals.backup_not_fit, outcome.notFit, listOf(outcome.notFit, PackLimits.MAX))))
            }
            WorkOutcome.DONE
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(AREA, "restore failed: ${e.javaClass.simpleName}")
            settings.updateBackup { it.copy(problem = problemOf(e)) }
            if (retries(e)) return WorkOutcome.RETRY
            // Only once, on giving up: not on every retry.
            notices.post(Notice(UiText.Res(R.string.restore_failed), error = true))
            WorkOutcome.FAILED
        }
    }

    private suspend fun finishSignIn(token: String) {
        try {
            val drive = drives.forToken(token)
            // Three independent Drive calls at once: one after another they were most of the wait after choosing the account.
            val (user, remote, userId) = coroutineScope {
                val user = async(io) { drive.user() }
                val remote = async { sync.read(drive) }
                // A random id stored in their Drive, not derived from data others can see.
                val userId = async { USER_PREFIX + sync.accountId(drive) }
                Triple(user.await(), remote.await(), userId.await())
            }
            // Without an email the account cannot be pinned for the following backups.
            if (user.emailAddress.isBlank()) throw IOException("no email for the account")
            val signIn = SignIn(user.emailAddress, userId, remote)
            // Packs that went to another account are not taken to this one without asking.
            if (settings.settings.first().backup.asksToMerge(userId, packs.packs.first().isNotEmpty())) pending.value = signIn else accept(signIn)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(AREA, "sign in failed: ${e.javaClass.simpleName}")
            signInFailed()
            return
        }
        busy.value = null
    }

    private suspend fun accept(signIn: SignIn) {
        // The gaps belong to the backup the packs went to. Kept while signed out: without them, the next backup to that account would drop them from Drive.
        if (settings.settings.first().backup.owner.let { it != null && it != signIn.userId }) settings.updatePackGaps { emptyMap() }
        settings.updateBackup { it.signedIn(signIn.email, signIn.userId, signIn.remote) }
        if (settings.settings.first().backup.needsRestore) enqueueRestore()
        schedule(manual = false)
    }

    private fun signInFailed() {
        busy.value = null
        notices.post(Notice(UiText.Res(R.string.account_connect_failed), error = true))
    }

    /** A token without showing anything; if it expired, another one is requested once. */
    private suspend fun <T> withDrive(email: String, block: suspend (DriveClient) -> T): T {
        repeat(2) { attempt ->
            val token = when (val step = authorizer.authorize(email)) {
                is AuthStep.Token -> step.token
                AuthStep.Unavailable -> throw AuthUnavailable()
                else -> throw SignInNeeded()
            }
            try {
                return block(drives.forToken(token))
            } catch (e: DriveException) {
                if (!e.unauthorized || attempt == 1) throw e
                authorizer.clearToken(token)
            }
        }
        throw SignInNeeded()
    }

    private suspend fun failed(e: Exception, manual: Boolean): WorkOutcome {
        val problem = problemOf(e)
        Logger.warn(AREA, "backup failed: $problem")
        settings.updateBackup { it.copy(problem = problem) }
        if (manual) notices.post(Notice(UiText.Res(problem.message()), error = true))
        return if (retries(e)) WorkOutcome.RETRY else WorkOutcome.FAILED
    }

    private fun retries(e: Exception) = problemOf(e) == BackupProblem.NO_NETWORK || (e is DriveException && e.transient)

    // A failure while scheduling must not stop the collector that schedules backups for good.
    private suspend fun keepAlive(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(AREA, "backup not scheduled", e)
        }
    }

    private suspend fun scheduleIfSignedIn() {
        if (settings.settings.first().backup.email != null) schedule(manual = false)
    }

    private fun schedule(manual: Boolean) {
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_S, TimeUnit.SECONDS)
            .setInputData(workDataOf(BackupWorker.MANUAL to manual))
            .apply { if (!manual) setInitialDelay(AUTO_DELAY_S, TimeUnit.SECONDS) }
            .build()
        workManager.enqueueUniqueWork(BACKUP, ExistingWorkPolicy.REPLACE, request)
    }

    private fun enqueueRestore() {
        val request = OneTimeWorkRequestBuilder<RestoreWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_S, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(RESTORE, ExistingWorkPolicy.KEEP, request)
    }

    private fun restoredNotice(outcome: RestoreOutcome): Notice? = when {
        outcome.stickers == 0 && outcome.packs == 0 -> null
        outcome.stickers == 0 -> Notice(UiText.Plural(R.plurals.backup_restored_packs, outcome.packs))
        outcome.packs == 0 -> Notice(UiText.Plural(R.plurals.backup_restored, outcome.stickers))
        else -> Notice(UiText.Join(listOf(UiText.Plural(R.plurals.backup_restored, outcome.stickers), UiText.Plural(R.plurals.backup_packs, outcome.packs)), UiText.Res(R.string.separator)))
    }

    private fun problemOf(e: Exception) = when {
        e is SignInNeeded -> BackupProblem.SIGN_IN
        e is DriveException && e.unauthorized -> BackupProblem.SIGN_IN
        e is DriveException && e.full -> BackupProblem.DRIVE_FULL
        e is DriveException -> BackupProblem.FAILED
        // Only what never reached Drive or Google counts as "no connection"; a full disk or a missing file does not.
        e is DriveNetworkException || e is AuthUnavailable -> BackupProblem.NO_NETWORK
        // Retrying never helps: only a newer TikStik can write over that backup.
        e is NewerBackupException -> BackupProblem.NEWER_VERSION
        else -> BackupProblem.FAILED
    }

    private fun progressOf(info: WorkInfo): BackupProgress? {
        val total = info.progress.getInt(TOTAL, 0)
        return if (total == 0) null else BackupProgress(info.progress.getInt(DONE, 0), total)
    }

    /** An account chosen but not stored yet: only in memory, so closing the app just means signing in again. */
    private class SignIn(val email: String, val userId: String, val remote: BackupManifest?)

    private class SignInNeeded : IOException("sign in needed")

    private class AuthUnavailable : IOException("google temporarily unavailable")

    companion object {
        const val DONE = "done"
        const val TOTAL = "total"
        private const val AREA = "Account"
        private const val USER_PREFIX = "g_"
        private const val BACKUP = "backup"
        private const val RESTORE = "restore"
        private const val AUTO_DELAY_S = 20L
        private const val BACKOFF_S = 30L
        private const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
