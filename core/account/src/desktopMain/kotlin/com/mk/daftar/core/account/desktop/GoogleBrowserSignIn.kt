package com.mk.daftar.core.account.desktop

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.awt.Desktop
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The result of one browser hand-off. Only [Approved] carries anything, and only the Google ID token. */
internal sealed interface BrowserSignInResult {
    class Approved(val googleIdToken: String) : BrowserSignInResult

    data object Declined : BrowserSignInResult

    data object TimedOut : BrowserSignInResult

    data object BrowserUnavailable : BrowserSignInResult

    data object NoNetwork : BrowserSignInResult

    data object Failed : BrowserSignInResult
}

/**
 * Google sign-in for an installed app (RFC 8252): the system browser shows Google's own page, and the answer comes back
 * to a one-time listener on 127.0.0.1 with PKCE (S256) and a random state. The app never sees the Google password. The
 * listener lives only for one attempt, accepts one matching answer, and stops on success, failure, timeout or cancel.
 */
internal class GoogleBrowserSignIn(
    private val clientId: String,
    private val clientSecret: String,
    private val http: HttpClient,
    private val timeout: Duration,
) {
    private val random = SecureRandom()

    suspend fun signIn(): BrowserSignInResult {
        val verifier = randomToken(VERIFIER_BYTES)
        val state = randomToken(STATE_BYTES)
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val answer = CompletableDeferred<Map<String, String>>()
        val server = try {
            HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        } catch (failure: IOException) {
            return BrowserSignInResult.Failed
        }
        server.createContext("/") { exchange -> answer(exchange, state, answer) }
        server.start()
        try {
            val redirect = "http://127.0.0.1:${server.address.port}"
            val consent = URI(
                AUTHORIZE + "?" + form(
                    "client_id" to clientId,
                    "redirect_uri" to redirect,
                    "response_type" to "code",
                    "scope" to "openid email",
                    "code_challenge" to challenge,
                    "code_challenge_method" to "S256",
                    "state" to state,
                    "prompt" to "select_account",
                ),
            )
            if (!openBrowser(consent)) return BrowserSignInResult.BrowserUnavailable
            val parameters = try {
                withTimeout(timeout) { answer.await() }
            } catch (expired: TimeoutCancellationException) {
                return BrowserSignInResult.TimedOut
            }
            if (parameters["error"] != null) {
                return if (parameters["error"] == "access_denied") {
                    BrowserSignInResult.Declined
                } else {
                    BrowserSignInResult.Failed
                }
            }
            val code = parameters["code"] ?: return BrowserSignInResult.Failed
            return exchange(code, verifier, redirect)
        } finally {
            server.stop(0)
        }
    }

    /** Accepts the first answer whose state matches; every other request gets the failed page and changes nothing. */
    private fun answer(exchange: HttpExchange, state: String, answer: CompletableDeferred<Map<String, String>>) {
        val parameters = exchange.requestURI.rawQuery.orEmpty().split('&').mapNotNull { pair ->
            val parts = pair.split('=', limit = 2)
            if (parts.size == 2) {
                URLDecoder.decode(parts[0], Charsets.UTF_8) to URLDecoder.decode(parts[1], Charsets.UTF_8)
            } else {
                null
            }
        }.toMap()
        val matches = exchange.requestURI.path == "/" && parameters["state"] == state && !answer.isCompleted
        val succeeded = matches && parameters["code"] != null
        if (matches) answer.complete(parameters)
        val body = ReturnPage.html(succeeded).toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        exchange.responseHeaders.add("Cache-Control", "no-store")
        exchange.responseHeaders.add("Referrer-Policy", "no-referrer")
        exchange.sendResponseHeaders(200, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    private suspend fun openBrowser(uri: URI): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                return@withContext false
            }
            Desktop.getDesktop().browse(uri)
            true
        } catch (failure: Exception) {
            false
        }
    }

    /** Exchanges the one-time code for Google's ID token, proving possession of the PKCE verifier. */
    private suspend fun exchange(code: String, verifier: String, redirect: String): BrowserSignInResult =
        withContext(Dispatchers.IO) {
            val request = HttpRequest.newBuilder(URI(TOKEN))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        form(
                            "code" to code,
                            "client_id" to clientId,
                            "client_secret" to clientSecret,
                            "redirect_uri" to redirect,
                            "grant_type" to "authorization_code",
                            "code_verifier" to verifier,
                        ),
                    ),
                )
                .build()
            try {
                val response = http.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() != 200) return@withContext BrowserSignInResult.Failed
                val token = Json.parseToJsonElement(response.body()).jsonObject["id_token"]?.jsonPrimitive?.content
                if (token == null) BrowserSignInResult.Failed else BrowserSignInResult.Approved(token)
            } catch (failure: IOException) {
                BrowserSignInResult.NoNetwork
            }
        }

    private fun randomToken(bytes: Int): String = base64Url(ByteArray(bytes).also { random.nextBytes(it) })

    private companion object {
        const val AUTHORIZE = "https://accounts.google.com/o/oauth2/v2/auth"
        const val TOKEN = "https://oauth2.googleapis.com/token"
        const val VERIFIER_BYTES = 48
        const val STATE_BYTES = 24
    }
}

internal fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

internal fun form(vararg pairs: Pair<String, String>): String =
    pairs.joinToString("&") { (key, value) -> key + "=" + URLEncoder.encode(value, Charsets.UTF_8) }

/**
 * The page the browser shows after Google returns: the brand, a heading and one sentence, in the app's language. It
 * says to return to the app, not that sign-in succeeded, because the token exchange still follows. Nothing in it comes
 * from the request.
 */
private object ReturnPage {
    fun html(succeeded: Boolean): String {
        val arabic = Locale.getDefault().language == "ar"
        val title = if (arabic) "دفتر" else "Daftar"
        val heading = when {
            succeeded && arabic -> "يمكنك العودة إلى دفتر"
            succeeded -> "You can return to Daftar"
            arabic -> "لم يكتمل تسجيل الدخول"
            else -> "Sign-in wasn’t completed"
        }
        val body = when {
            succeeded && arabic -> "أغلق علامة التبويب هذه وعُد إلى التطبيق للمتابعة."
            succeeded -> "Close this tab and return to the app to continue."
            arabic -> "أغلق علامة التبويب هذه وعُد إلى دفتر للمحاولة مرة أخرى."
            else -> "Close this tab and return to Daftar to try again."
        }
        val direction = if (arabic) "rtl" else "ltr"
        val language = if (arabic) "ar" else "en"
        return """
            <!doctype html><html lang="$language" dir="$direction"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1"><title>$title</title>
            <style>
            body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#F4F2EA;
            color:#262D24;font-family:-apple-system,"Segoe UI",system-ui,sans-serif;padding:16px;box-sizing:border-box}
            main{max-width:420px;text-align:center}h1{font-size:22px;margin:16px 0 8px}p{color:#5E665A;margin:0;line-height:1.5}
            @media (prefers-color-scheme: dark){body{background:#151A13;color:#EFF0E6}p{color:#A9B0A2}}
            </style></head><body><main>
            <svg width="56" height="56" viewBox="0 0 48 48" aria-hidden="true"><rect width="48" height="48" rx="12" fill="#45583D"/>
            <rect x="10" y="6" width="28" height="36" rx="5" fill="#FFFEF8"/><path d="M18 7V41" stroke="#45583D" stroke-width="2"/>
            <path d="M24 17H32M24 24H32" stroke="#45583D" stroke-width="2.5" stroke-linecap="round"/></svg>
            <h1>$heading</h1><p>$body</p></main></body></html>
        """.trimIndent()
    }
}
