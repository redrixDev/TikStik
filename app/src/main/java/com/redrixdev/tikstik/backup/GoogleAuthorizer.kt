// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.backup

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.redrixdev.tikstik.core.common.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/** What Google answers when asking for access to the TikStik folder in Drive. */
sealed interface AuthStep {
    data class Token(val token: String) : AuthStep

    /** An account must be chosen or the permission accepted: the activity launches [intent] and returns the result. */
    data class NeedsUser(val intent: PendingIntent) : AuthStep

    data object Failed : AuthStep

    /** A transient failure (network, internal error, timeout): retried later, without asking for the account again. */
    data object Unavailable : AuthStep
}

/** Google's authorization for Drive, with no accounts of its own. */
interface GoogleAuthorizer {
    /** With the permission already granted and the [email] account set, shows nothing: returns the token. */
    suspend fun authorize(email: String?): AuthStep

    fun fromIntent(data: Intent?): AuthStep

    /** An expired token is discarded so the next one is fresh. */
    suspend fun clearToken(token: String)

    /** On sign-out: TikStik loses the permission; the backup stays in the user's Drive. */
    suspend fun revoke(email: String)
}

/** `AuthorizationClient` with `drive.appdata`, the smallest permission: a hidden folder just for TikStik. */
class PlayAuthorizer @Inject constructor(@ApplicationContext context: Context) : GoogleAuthorizer {

    private val client: AuthorizationClient = Identity.getAuthorizationClient(context)

    override suspend fun authorize(email: String?): AuthStep = try {
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).apply { email?.let { setAccount(Account(it, ACCOUNT_TYPE)) } }.build()
        val result = client.authorize(request).await()
        val intent = result.pendingIntent
        val token = result.accessToken
        when {
            result.hasResolution() && intent != null -> AuthStep.NeedsUser(intent)
            token != null -> AuthStep.Token(token)
            else -> AuthStep.Failed
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiException) {
        Logger.warn(AREA, "google: ${e.statusCode}")
        if (e.statusCode in TRANSIENT) AuthStep.Unavailable else AuthStep.Failed
    } catch (e: Exception) {
        Logger.warn(AREA, "google: ${e.javaClass.simpleName}")
        AuthStep.Failed
    }

    override fun fromIntent(data: Intent?): AuthStep = try {
        client.getAuthorizationResultFromIntent(data).accessToken?.let(AuthStep::Token) ?: AuthStep.Failed
    } catch (e: ApiException) {
        Logger.warn(AREA, "authorization result: ${e.statusCode}")
        AuthStep.Failed
    }

    override suspend fun clearToken(token: String) {
        attempt { client.clearToken(ClearTokenRequest.builder().setToken(token).build()).await() }
    }

    override suspend fun revoke(email: String) {
        attempt {
            client.revokeAccess(RevokeAccessRequest.builder().setAccount(Account(email, ACCOUNT_TYPE)).setScopes(listOf(Scope(SCOPE))).build()).await()
        }
    }

    // Without an OAuth client ID or without Google Play, Google answers with an error: treated as "could not".
    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.warn(AREA, "google: ${(e as? ApiException)?.statusCode ?: e.javaClass.simpleName}")
        null
    }

    private companion object {
        const val AREA = "Account"
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        const val ACCOUNT_TYPE = "com.google"
        val TRANSIENT = setOf(CommonStatusCodes.NETWORK_ERROR, CommonStatusCodes.INTERNAL_ERROR, CommonStatusCodes.TIMEOUT)
    }
}
