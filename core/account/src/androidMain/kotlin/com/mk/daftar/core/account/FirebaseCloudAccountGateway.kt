package com.mk.daftar.core.account

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.mk.daftar.core.domain.cloud.CloudAccount
import com.mk.daftar.core.domain.cloud.CloudAccountGateway
import com.mk.daftar.core.domain.cloud.CloudAuthOutcome
import com.mk.daftar.core.domain.cloud.CloudAvailability
import com.mk.daftar.core.domain.cloud.CloudProvider
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Google sign-in for Android: Credential Manager presents the account sheet, and the ID token is exchanged for a
 * Firebase session. Every call checks that Firebase is configured first, so a build without `google-services.json`
 * reports the feature as unavailable instead of throwing.
 */
internal class FirebaseCloudAccountGateway(private val context: Context) : CloudAccountGateway {

    private val credentialManager by lazy { CredentialManager.create(context) }

    override val availability: CloudAvailability
        get() = if (configured()) CloudAvailability.AVAILABLE else CloudAvailability.UNAVAILABLE

    override val provider: CloudProvider = CloudProvider.GOOGLE

    override val account: Flow<CloudAccount?> = flow {
        if (!configured()) {
            emit(null)
            return@flow
        }
        emitAll(
            callbackFlow {
                val auth = FirebaseAuth.getInstance()
                val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toAccount()) }
                auth.addAuthStateListener(listener)
                awaitClose { auth.removeAuthStateListener(listener) }
            },
        )
    }

    override fun currentAccount(): CloudAccount? {
        if (!configured()) return null
        return FirebaseAuth.getInstance().currentUser?.toAccount()
    }

    override suspend fun signIn(): CloudAuthOutcome = attempt {
        val activity = CloudActivity.current ?: return@attempt CloudAuthOutcome.Unavailable
        val credential = googleCredential(activity) ?: return@attempt CloudAuthOutcome.Unavailable
        val result = FirebaseAuth.getInstance().signInWithCredential(credential).awaitTask()
        val user = result.user ?: return@attempt CloudAuthOutcome.Failed
        CloudAuthOutcome.SignedIn(CloudAccount(user.uid, provider))
    }

    override suspend fun reauthenticate(): CloudAuthOutcome = attempt {
        val activity = CloudActivity.current ?: return@attempt CloudAuthOutcome.Unavailable
        val user = FirebaseAuth.getInstance().currentUser ?: return@attempt CloudAuthOutcome.Failed
        val credential = googleCredential(activity) ?: return@attempt CloudAuthOutcome.Unavailable
        user.reauthenticate(credential).awaitTask()
        CloudAuthOutcome.SignedIn(CloudAccount(user.uid, provider))
    }

    /** Google sign-in holds no token that needs revoking; the Firebase account is removed separately. */
    override suspend fun revokeProviderAuthorization(): Boolean = true

    override suspend fun deleteAuthAccount(): CloudAuthOutcome = attempt {
        val user = FirebaseAuth.getInstance().currentUser ?: return@attempt CloudAuthOutcome.Deleted
        try {
            user.delete().awaitTask()
        } catch (_: FirebaseAuthRecentLoginRequiredException) {
            return@attempt CloudAuthOutcome.ReauthRequired
        }
        CloudAuthOutcome.Deleted
    }

    override suspend fun signOut() {
        if (!configured()) return
        FirebaseAuth.getInstance().signOut()
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
    }

    private fun configured(): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    private fun FirebaseUser.toAccount(): CloudAccount = CloudAccount(uid, provider)

    /**
     * The OAuth web client ID that the google-services plugin generates as `default_web_client_id`. It is looked up by
     * name so this module needs no generated resource class. Null when the configuration lacks a web client.
     */
    private fun webClientId(): String? {
        val identifier = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (identifier == 0) return null
        return context.getString(identifier).takeIf { it.isNotBlank() }
    }

    private suspend fun googleCredential(activity: android.app.Activity): AuthCredential? {
        val clientId = webClientId() ?: return null
        val option = GetSignInWithGoogleOption.Builder(clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = credentialManager.getCredential(activity, request).credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return null
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        return GoogleAuthProvider.getCredential(idToken, null)
    }

    /** Runs [block], turning every failure into a typed outcome. Cancellation is a normal result, not an error. */
    private suspend fun attempt(block: suspend () -> CloudAuthOutcome): CloudAuthOutcome {
        if (!configured()) return CloudAuthOutcome.Unavailable
        return try {
            block()
        } catch (_: GetCredentialCancellationException) {
            CloudAuthOutcome.Cancelled
        } catch (_: FirebaseNetworkException) {
            CloudAuthOutcome.NoNetwork
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: FirebaseAuthRecentLoginRequiredException) {
            CloudAuthOutcome.ReauthRequired
        } catch (_: Exception) {
            CloudAuthOutcome.Failed
        }
    }
}

/** Waits for a Firebase [Task] without the `-ktx` artifact, which the project does not use. */
internal suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        if (task.isSuccessful) {
            continuation.resume(task.result)
        } else {
            continuation.resumeWithException(task.exception ?: IllegalStateException("Firebase task failed"))
        }
    }
}
