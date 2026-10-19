package com.mk.daftar.core.domain.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class UpdateRuleTest {
    private val now = Instant.parse("2026-10-19T12:00:00Z")
    private val store = "market://details?id=com.mk.daftar"
    private val fallback = "https://play.google.com/store/apps/details?id=com.mk.daftar"

    @Test
    fun unparsableOrZeroInstalledBuildFailsOpen() {
        assertEquals(UpdateStatus.UpToDate, status(installedBuild = 0))
    }

    @Test
    fun missingPolicyFailsOpen() {
        assertEquals(UpdateStatus.UpToDate, evaluateUpdateStatus(1, policy = null, now = now, softSnooze = null))
    }

    @Test
    fun missingStoreUrlFailsOpen() {
        assertEquals(UpdateStatus.UpToDate, status(policy = policy(storeUrl = null)))
    }

    @Test
    fun forceShowsWhenInstalledIsBelowMinForceIsEnabledAndFetchIsFresh() {
        assertEquals(
            UpdateStatus.Force(5, "1.2.0", store, fallback),
            status(installedBuild = 4, policy = policy(minBuild = 5, forceEnabled = true, fetchedAt = now - 24.hours)),
        )
    }

    @Test
    fun forceBoundaryExpiresAfterTwentyFourHours() {
        assertEquals(
            UpdateStatus.Soft(5, "1.2.0", store, fallback),
            status(
                installedBuild = 4,
                policy = policy(minBuild = 5, forceEnabled = true, fetchedAt = now - 24.hours - 1.hours),
            ),
        )
    }

    @Test
    fun staleFetchStopsForcingButStillOffersSoftUpdate() {
        assertEquals(
            UpdateStatus.Soft(5, "1.2.0", store, fallback),
            status(
                installedBuild = 4,
                policy = policy(minBuild = 5, latestBuild = 5, forceEnabled = true, fetchedAt = now - 2.hours * 13),
            ),
        )
    }

    @Test
    fun forceDisabledFallsBackToSoft() {
        assertEquals(
            UpdateStatus.Soft(5, "1.2.0", store, fallback),
            status(installedBuild = 4, policy = policy(minBuild = 5, forceEnabled = false, fetchedAt = now)),
        )
    }

    @Test
    fun forceWithoutFetchTimeFallsBackToSoft() {
        assertEquals(
            UpdateStatus.Soft(5, "1.2.0", store, fallback),
            status(installedBuild = 4, policy = policy(minBuild = 5, forceEnabled = true, fetchedAt = null)),
        )
    }

    @Test
    fun softUsesTheGreaterOfLatestAndMinBuild() {
        assertEquals(
            UpdateStatus.Soft(10, "1.2.0", store, fallback),
            status(installedBuild = 9, policy = policy(minBuild = 5, latestBuild = 10)),
        )
        assertEquals(
            UpdateStatus.Soft(10, "1.2.0", store, fallback),
            status(installedBuild = 9, policy = policy(minBuild = 10, latestBuild = 5)),
        )
    }

    @Test
    fun snoozedSameBuildIsSuppressed() {
        assertEquals(UpdateStatus.UpToDate, status(softSnooze = SoftUpdateSnooze(build = 5, until = now + 1.hours)))
    }

    @Test
    fun higherBuildAfterSnoozeShowsSoftUpdate() {
        assertEquals(
            UpdateStatus.Soft(6, "1.2.0", store, fallback),
            status(policy = policy(latestBuild = 6), softSnooze = SoftUpdateSnooze(build = 5, until = now + 1.hours)),
        )
    }

    @Test
    fun expiredSnoozeShowsSoftUpdate() {
        assertEquals(
            UpdateStatus.Soft(5, "1.2.0", store, fallback),
            status(softSnooze = SoftUpdateSnooze(build = 5, until = now)),
        )
    }

    @Test
    fun upToDateWhenInstalledBuildMeetsTarget() {
        assertEquals(
            UpdateStatus.UpToDate,
            status(installedBuild = 10, policy = policy(minBuild = 5, latestBuild = 10)),
        )
    }

    private fun status(
        installedBuild: Long = 4,
        policy: UpdatePolicy? = policy(),
        softSnooze: SoftUpdateSnooze? = null,
    ) = evaluateUpdateStatus(installedBuild, policy, now, softSnooze)

    private fun policy(
        minBuild: Long = 0,
        latestBuild: Long = 5,
        forceEnabled: Boolean = false,
        latestName: String = "1.2.0",
        fetchedAt: Instant? = now,
        storeUrl: String? = "market://details?id=com.mk.daftar",
        storeFallbackUrl: String? = "https://play.google.com/store/apps/details?id=com.mk.daftar",
    ) = UpdatePolicy(minBuild, latestBuild, forceEnabled, latestName, fetchedAt, storeUrl, storeFallbackUrl)
}
