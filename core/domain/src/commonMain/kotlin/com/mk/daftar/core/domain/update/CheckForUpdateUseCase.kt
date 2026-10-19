package com.mk.daftar.core.domain.update

import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.Flow

/** The outcome of a fetch: what to offer, and whether the Remote Config values were refreshed by this check. */
data class UpdateCheck(val status: UpdateStatus, val fetchSucceeded: Boolean)

class CheckForUpdateUseCase(
    private val installedBuildProvider: InstalledBuildProvider,
    private val remoteConfigSource: RemoteConfigSource,
    private val snoozeRepository: UpdateSnoozeRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(): UpdateStatus = check().status

    suspend fun check(): UpdateCheck {
        val policy = remoteConfigSource.fetch()
        return UpdateCheck(status = evaluate(policy), fetchSucceeded = policy?.fetchSucceeded == true)
    }

    /** The status from the last activated values, with no network request. Used at launch, before the fetch returns. */
    suspend fun cached(): UpdateStatus = evaluate(remoteConfigSource.cached())

    private suspend fun evaluate(policy: UpdatePolicy?): UpdateStatus = evaluateUpdateStatus(
        installedBuild = installedBuildProvider.buildNumber.toLongOrNull() ?: 0,
        policy = policy,
        now = clock.now(),
        softSnooze = snoozeRepository.softSnooze(),
    )
}

class ObserveRemoteConfigUpdatesUseCase(private val remoteConfigSource: RemoteConfigSource) {
    operator fun invoke(): Flow<Unit> = remoteConfigSource.updates()
}

class SnoozeSoftUpdateUseCase(private val snoozeRepository: UpdateSnoozeRepository, private val clock: Clock) {
    suspend operator fun invoke(build: Long) {
        snoozeRepository.snoozeSoft(SoftUpdateSnooze(build, clock.now() + SNOOZE_DURATION))
    }
}

private val SNOOZE_DURATION = 7.days
