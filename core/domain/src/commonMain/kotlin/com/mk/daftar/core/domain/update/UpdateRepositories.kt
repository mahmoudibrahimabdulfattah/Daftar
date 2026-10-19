package com.mk.daftar.core.domain.update

import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

/** The installed build number, compared with the Remote Config build numbers. Non-numeric values fail open. */
interface InstalledBuildProvider {
    val buildNumber: String
}

interface RemoteConfigSource {
    /** The last activated values, read without any network request. Null when nothing is configured. */
    suspend fun cached(): UpdatePolicy?

    /** Fetches and activates the values. A failed fetch still returns the last activated values, with [UpdatePolicy.fetchSucceeded] false. */
    suspend fun fetch(): UpdatePolicy?

    /** Emits each time new values are activated in real time. */
    fun updates(): Flow<Unit>
}

/** A soft prompt that was postponed for [build] until [until]. */
data class SoftUpdateSnooze(val build: Long, val until: Instant)

interface UpdateSnoozeRepository {
    suspend fun softSnooze(): SoftUpdateSnooze?
    suspend fun snoozeSoft(snooze: SoftUpdateSnooze)
}
