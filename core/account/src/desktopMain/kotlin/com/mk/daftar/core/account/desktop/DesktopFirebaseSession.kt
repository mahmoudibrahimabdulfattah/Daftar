package com.mk.daftar.core.account.desktop

import com.mk.daftar.core.domain.cloud.CloudAccount
import com.mk.daftar.core.domain.cloud.CloudAccountGateway
import com.mk.daftar.core.domain.cloud.CloudAuthOutcome
import com.mk.daftar.core.domain.cloud.CloudAvailability
import com.mk.daftar.core.domain.cloud.CloudProvider
import com.mk.daftar.core.domain.cloud.CloudSignInHandoff
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration as JavaDuration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The Firebase project the desktop app talks to. Every value is a public client identifier, the same kind Android reads
 * from `google-services.json`; the desktop OAuth "client secret" is not confidential for installed apps. Supplied by the
 * launcher from a local, git-ignored file; without it cloud backups are unavailable.
 */
data class DesktopCloudConfiguration(
    val apiKey: String,
    val projectId: String,
    val oauthClientId: String,
    val oauthClientSecret: String,
)

/** A signed-in Firebase user. Held in memory only: quitting the app signs out, and nothing is written to disk. */
private class Session(
    val uid: String,
    val email: String?,
    val idToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
)

/**
 * The desktop Firebase session: Google sign-in in the browser, exchanged for a Firebase session through the Auth REST
 * API, with ID tokens refreshed before they expire. Firestore reads the current token through [idToken].
 */
class DesktopFirebaseSession(private val configuration: DesktopCloudConfiguration) {
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(JavaDuration.ofSeconds(20)).build()
    private val browser = GoogleBrowserSignIn(
        clientId = configuration.oauthClientId,
        clientSecret = configuration.oauthClientSecret,
        http = http,
        timeout = 3.minutes,
    )
    private val session = MutableStateFlow<Session?>(null)
    private val refresh = Mutex()

    /** The Firebase project ID, for the Firestore REST paths. */
    val projectId: String get() = configuration.projectId

    internal val account: Flow<CloudAccount?> = session.map { it?.toAccount() }

    internal fun currentAccount(): CloudAccount? = session.value?.toAccount()

    /** A valid ID token for the signed-in user, refreshed when it is about to expire, or null when signed out. */
    suspend fun idToken(): String? = refresh.withLock {
        val current = session.value ?: return@withLock null
        if (current.expiresAtMillis - System.currentTimeMillis() >
            REFRESH_MARGIN_MILLIS
        ) {
            return@withLock current.idToken
        }
        val refreshed = post(
            "https://securetoken.googleapis.com/v1/token?key=${configuration.apiKey}",
            form("grant_type" to "refresh_token", "refresh_token" to current.refreshToken),
            "application/x-www-form-urlencoded",
        ) ?: return@withLock null
        val token = refreshed.text("id_token") ?: return@withLock null
        session.value = Session(
            uid = current.uid,
            email = current.email,
            idToken = token,
            refreshToken = refreshed.text("refresh_token") ?: current.refreshToken,
            expiresAtMillis = expiry(refreshed.text("expires_in")),
        )
        token
    }

    /** Signs in through the browser. With [sameUid], the session is replaced only when the same account answered. */
    internal suspend fun signIn(sameUid: String? = null): CloudAuthOutcome {
        val google = when (val result = browser.signIn()) {
            is BrowserSignInResult.Approved -> result.googleIdToken
            BrowserSignInResult.Declined -> return CloudAuthOutcome.Declined
            BrowserSignInResult.TimedOut -> return CloudAuthOutcome.TimedOut
            BrowserSignInResult.BrowserUnavailable -> return CloudAuthOutcome.BrowserUnavailable
            BrowserSignInResult.NoNetwork -> return CloudAuthOutcome.NoNetwork
            BrowserSignInResult.Failed -> return CloudAuthOutcome.Failed
        }
        val body = buildJsonObject {
            put("postBody", form("id_token" to google, "providerId" to "google.com"))
            put("requestUri", "http://127.0.0.1")
            put("returnSecureToken", true)
            put("returnIdpCredential", false)
        }.toString()
        val response = try {
            post(
                "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=${configuration.apiKey}",
                body,
                "application/json",
            )
        } catch (failure: IOException) {
            return CloudAuthOutcome.NoNetwork
        } ?: return CloudAuthOutcome.Failed
        val signedIn = Session(
            uid = response.text("localId") ?: return CloudAuthOutcome.Failed,
            email = response.text("email"),
            idToken = response.text("idToken") ?: return CloudAuthOutcome.Failed,
            refreshToken = response.text("refreshToken") ?: return CloudAuthOutcome.Failed,
            expiresAtMillis = expiry(response.text("expiresIn")),
        )
        // A re-authentication answered by another account leaves the original session untouched.
        if (sameUid == null || sameUid == signedIn.uid) session.value = signedIn
        return CloudAuthOutcome.SignedIn(signedIn.toAccount())
    }

    /** Deletes the Firebase user. An old sign-in must be renewed first, as on Android. */
    internal suspend fun deleteUser(): CloudAuthOutcome {
        val token = idToken() ?: return CloudAuthOutcome.Deleted
        return withContext(Dispatchers.IO) {
            val request = HttpRequest.newBuilder(
                URI("https://identitytoolkit.googleapis.com/v1/accounts:delete?key=${configuration.apiKey}"),
            )
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildJsonObject { put("idToken", token) }.toString()))
                .build()
            try {
                val response = http.send(request, HttpResponse.BodyHandlers.ofString())
                when {
                    response.statusCode() == 200 -> {
                        session.value = null
                        CloudAuthOutcome.Deleted
                    }

                    response.body().contains("CREDENTIAL_TOO_OLD_LOGIN_AGAIN") -> CloudAuthOutcome.ReauthRequired

                    response.body().contains("USER_NOT_FOUND") -> {
                        session.value = null
                        CloudAuthOutcome.Deleted
                    }

                    else -> CloudAuthOutcome.Failed
                }
            } catch (failure: IOException) {
                CloudAuthOutcome.NoNetwork
            }
        }
    }

    internal fun signOut() {
        session.value = null
    }

    /** Posts [body] and returns the JSON object of a 200 answer, or null for any other status. */
    private suspend fun post(url: String, body: String, contentType: String): JsonObject? =
        withContext(Dispatchers.IO) {
            val request = HttpRequest.newBuilder(URI(url))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) null else Json.parseToJsonElement(response.body()).jsonObject
        }

    private fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.content

    private fun expiry(seconds: String?): Long =
        System.currentTimeMillis() + (seconds?.toLongOrNull() ?: DEFAULT_LIFETIME_SECONDS) * 1000

    private fun Session.toAccount() = CloudAccount(uid = uid, provider = CloudProvider.GOOGLE, email = email)

    private companion object {
        const val REFRESH_MARGIN_MILLIS = 60_000L
        const val DEFAULT_LIFETIME_SECONDS = 3600L
    }
}

/** The one desktop session of the process, or null when this build has no cloud configuration. */
class DesktopCloudSessionHolder(val session: DesktopFirebaseSession?)

/** The desktop account gateway over [DesktopFirebaseSession], or unavailable when the build has no configuration. */
internal class DesktopCloudAccountGateway(private val session: DesktopFirebaseSession?) : CloudAccountGateway {
    override val availability: CloudAvailability =
        if (session == null) CloudAvailability.UNAVAILABLE else CloudAvailability.AVAILABLE

    override val provider: CloudProvider = CloudProvider.GOOGLE

    override val signInHandoff: CloudSignInHandoff = CloudSignInHandoff.BROWSER

    override val account: Flow<CloudAccount?> = session?.account ?: kotlinx.coroutines.flow.flowOf(null)

    override fun currentAccount(): CloudAccount? = session?.currentAccount()

    override suspend fun signIn(): CloudAuthOutcome = session?.signIn() ?: CloudAuthOutcome.Unavailable

    override suspend fun reauthenticate(): CloudAuthOutcome {
        val active = session ?: return CloudAuthOutcome.Unavailable
        val uid = active.currentAccount()?.uid ?: return CloudAuthOutcome.Failed
        return active.signIn(sameUid = uid)
    }

    /** Google sign-in holds no token that needs revoking; the Firebase account is removed separately. */
    override suspend fun revokeProviderAuthorization(): Boolean = true

    override suspend fun deleteAuthAccount(): CloudAuthOutcome = session?.deleteUser() ?: CloudAuthOutcome.Unavailable

    override suspend fun signOut() {
        session?.signOut()
    }
}
