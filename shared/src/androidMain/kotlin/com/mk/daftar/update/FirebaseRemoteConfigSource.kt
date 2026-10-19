package com.mk.daftar.update

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.mk.daftar.core.domain.update.RemoteConfigSource
import com.mk.daftar.core.domain.update.UpdatePolicy
import kotlin.coroutines.resume
import kotlin.time.Instant
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Android's Remote Config policy. Without a configured Firebase app there is no policy at all. [cached] reads what was
 * activated before, without touching the network; [fetch] refreshes it, and a failed fetch keeps the last activated
 * values with their last successful fetch time.
 */
internal class FirebaseRemoteConfigSource(private val context: Context, private val debugBuild: Boolean) :
    RemoteConfigSource {
    override suspend fun cached(): UpdatePolicy? {
        if (FirebaseApp.getApps(context).isEmpty()) return null
        return FirebaseRemoteConfig.getInstance().toUpdatePolicy(fetchSucceeded = false)
    }

    override suspend fun fetch(): UpdatePolicy? {
        if (FirebaseApp.getApps(context).isEmpty()) return null
        val config = configuredRemoteConfig()
        val fetched = config.fetchAndActivate().awaitOrNull() != null
        return config.toUpdatePolicy(fetchSucceeded = fetched)
    }

    override fun updates(): Flow<Unit> {
        if (FirebaseApp.getApps(context).isEmpty()) return emptyFlow()
        val config = FirebaseRemoteConfig.getInstance()
        return callbackFlow {
            val registration = config.addOnConfigUpdateListener(
                object : ConfigUpdateListener {
                    override fun onUpdate(update: ConfigUpdate) {
                        config.activate().addOnSuccessListener { trySend(Unit) }
                    }

                    override fun onError(error: FirebaseRemoteConfigException) = Unit
                },
            )
            awaitClose { registration.remove() }
        }
    }

    private fun FirebaseRemoteConfig.toUpdatePolicy(fetchSucceeded: Boolean): UpdatePolicy {
        // The fetch time moves only on a successful fetch, so a failed one never renews the policy's freshness.
        val fetchedAt = info.fetchTimeMillis.takeIf { it > 0 }?.let(Instant::fromEpochMilliseconds)
        return UpdatePolicy(
            minBuild = getLong(ANDROID_MIN_BUILD),
            latestBuild = getLong(ANDROID_LATEST_BUILD),
            forceEnabled = getBoolean(ANDROID_FORCE_ENABLED),
            latestName = getString(ANDROID_LATEST_NAME),
            fetchedAt = fetchedAt,
            storeUrl = UpdateStoreLinks.ANDROID_STORE_URL,
            storeFallbackUrl = UpdateStoreLinks.ANDROID_STORE_FALLBACK_URL,
            fetchSucceeded = fetchSucceeded,
        )
    }

    private suspend fun configuredRemoteConfig(): FirebaseRemoteConfig {
        val config = FirebaseRemoteConfig.getInstance()
        config.setConfigSettingsAsync(
            FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(
                    if (debugBuild) DEBUG_FETCH_INTERVAL_SECONDS else RELEASE_FETCH_INTERVAL_SECONDS,
                )
                .setFetchTimeoutInSeconds(FETCH_TIMEOUT_SECONDS)
                .build(),
        ).awaitOrNull()
        config.setDefaultsAsync(DEFAULTS).awaitOrNull()
        return config
    }

    private suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener
            continuation.resume(if (task.isSuccessful) task.result else null)
        }
    }

    private companion object {
        const val ANDROID_MIN_BUILD = "android_min_build"
        const val ANDROID_LATEST_BUILD = "android_latest_build"
        const val ANDROID_FORCE_ENABLED = "android_force_enabled"
        const val ANDROID_LATEST_NAME = "android_latest_name"
        const val RELEASE_FETCH_INTERVAL_SECONDS = 12L * 60L * 60L
        const val DEBUG_FETCH_INTERVAL_SECONDS = 0L
        const val FETCH_TIMEOUT_SECONDS = 10L

        /** Every default means no prompt: a build of 0, no force, and no version name. */
        val DEFAULTS: Map<String, Any> = mapOf(
            ANDROID_MIN_BUILD to 0L,
            ANDROID_LATEST_BUILD to 0L,
            ANDROID_FORCE_ENABLED to false,
            ANDROID_LATEST_NAME to "",
        )
    }
}
