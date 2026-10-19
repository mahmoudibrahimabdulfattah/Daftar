package com.mk.daftar.core.domain.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest

class CheckForUpdateUseCaseTest {
    private val now = Instant.parse("2026-10-19T12:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = this@CheckForUpdateUseCaseTest.now
    }

    @Test
    fun checkUsesInstalledBuildRemotePolicyAndSnooze() = runTest {
        val source = FakeRemoteConfigSource(policy(latestBuild = 8))
        val snooze = FakeUpdateSnoozeRepository(softSnooze = SoftUpdateSnooze(build = 8, until = now + 1.days))
        val useCase = CheckForUpdateUseCase(FakeInstalledBuildProvider("7"), source, snooze, clock)

        assertEquals(UpdateStatus.UpToDate, useCase())
    }

    @Test
    fun checkFailsOpenWhenInstalledBuildIsNotANumber() = runTest {
        val useCase = CheckForUpdateUseCase(
            FakeInstalledBuildProvider("abc"),
            FakeRemoteConfigSource(policy(latestBuild = 8)),
            FakeUpdateSnoozeRepository(),
            clock,
        )

        assertEquals(UpdateStatus.UpToDate, useCase())
    }

    @Test
    fun cachedReadsTheActivatedValuesWithoutFetching() = runTest {
        val source = FakeRemoteConfigSource(
            cachedPolicy = policy(minBuild = 9, forceEnabled = true, fetchedAt = now - 1.hours),
            fetchedPolicy = null,
        )
        val useCase =
            CheckForUpdateUseCase(FakeInstalledBuildProvider("7"), source, FakeUpdateSnoozeRepository(), clock)

        assertEquals(UpdateStatus.Force(9, "1.2.0", STORE, FALLBACK), useCase.cached())
        assertEquals(0, source.fetchCount)
    }

    @Test
    fun checkReportsWhetherTheFetchSucceeded() = runTest {
        val useCase = CheckForUpdateUseCase(
            FakeInstalledBuildProvider("7"),
            FakeRemoteConfigSource(fetchedPolicy = policy(latestBuild = 8, fetchSucceeded = false)),
            FakeUpdateSnoozeRepository(),
            clock,
        )

        val result = useCase.check()

        assertEquals(UpdateStatus.Soft(8, "1.2.0", STORE, FALLBACK), result.status)
        assertEquals(false, result.fetchSucceeded)
    }

    @Test
    fun snoozeStoresSevenDaysFromNow() = runTest {
        val repo = FakeUpdateSnoozeRepository()

        SnoozeSoftUpdateUseCase(repo, clock)(build = 8)

        assertEquals(SoftUpdateSnooze(build = 8, until = now + 7.days), repo.softSnooze())
    }

    @Test
    fun snoozeLapsesAfterSevenDaysAndNewerBuildBypassesIt() = runTest {
        val repo = FakeUpdateSnoozeRepository()
        SnoozeSoftUpdateUseCase(repo, clock)(build = 8)
        val source = FakeRemoteConfigSource(fetchedPolicy = policy(latestBuild = 8))
        fun checkAt(instant: Instant) = CheckForUpdateUseCase(
            FakeInstalledBuildProvider("7"),
            source,
            repo,
            object : Clock {
                override fun now(): Instant = instant
            },
        )

        assertEquals(UpdateStatus.UpToDate, checkAt(now + 7.days - 1.minutes)())
        assertEquals(UpdateStatus.Soft(8, "1.2.0", STORE, FALLBACK), checkAt(now + 7.days)())

        source.fetchedPolicy = policy(latestBuild = 9)
        assertEquals(UpdateStatus.Soft(9, "1.2.0", STORE, FALLBACK), checkAt(now + 1.days)())
    }

    @Test
    fun staleFetchNeverForcesEvenWhenMinimumIsHigher() = runTest {
        val useCase = CheckForUpdateUseCase(
            FakeInstalledBuildProvider("7"),
            FakeRemoteConfigSource(
                fetchedPolicy = policy(minBuild = 9, forceEnabled = true, fetchedAt = now - 25.hours),
            ),
            FakeUpdateSnoozeRepository(),
            clock,
        )

        assertEquals(UpdateStatus.Soft(9, "1.2.0", STORE, FALLBACK), useCase())
    }

    private fun policy(
        minBuild: Long = 0,
        latestBuild: Long = 8,
        forceEnabled: Boolean = false,
        fetchedAt: Instant? = now,
        fetchSucceeded: Boolean = true,
    ) = UpdatePolicy(
        minBuild = minBuild,
        latestBuild = latestBuild,
        forceEnabled = forceEnabled,
        latestName = "1.2.0",
        fetchedAt = fetchedAt,
        storeUrl = STORE,
        storeFallbackUrl = FALLBACK,
        fetchSucceeded = fetchSucceeded,
    )

    private companion object {
        const val STORE = "market://details?id=com.mk.daftar"
        const val FALLBACK = "https://play.google.com/store/apps/details?id=com.mk.daftar"
    }
}

private class FakeInstalledBuildProvider(override val buildNumber: String) : InstalledBuildProvider

private class FakeRemoteConfigSource(
    private val cachedPolicy: UpdatePolicy? = null,
    var fetchedPolicy: UpdatePolicy? = null,
) : RemoteConfigSource {
    var fetchCount = 0
        private set

    override suspend fun cached(): UpdatePolicy? = cachedPolicy

    override suspend fun fetch(): UpdatePolicy? {
        fetchCount++
        return fetchedPolicy
    }

    override fun updates(): Flow<Unit> = emptyFlow()
}

private class FakeUpdateSnoozeRepository(private var softSnooze: SoftUpdateSnooze? = null) : UpdateSnoozeRepository {
    override suspend fun softSnooze(): SoftUpdateSnooze? = softSnooze
    override suspend fun snoozeSoft(snooze: SoftUpdateSnooze) {
        softSnooze = snooze
    }
}
