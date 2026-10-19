package com.mk.daftar.core.domain.update

import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Decides what an installed build should be offered. Forcing needs the installed build below the minimum, the force
 * switch on, and a successful fetch younger than 24 hours. Anything missing, unreadable or stale fails open.
 */
fun evaluateUpdateStatus(
    installedBuild: Long,
    policy: UpdatePolicy?,
    now: Instant,
    softSnooze: SoftUpdateSnooze?,
): UpdateStatus {
    if (installedBuild <= 0 || policy == null || policy.storeUrl == null) return UpdateStatus.UpToDate

    val forced = installedBuild < policy.minBuild &&
        policy.forceEnabled &&
        policy.fetchedAt != null &&
        now - policy.fetchedAt <= FORCE_POLICY_MAX_AGE
    if (forced) {
        return UpdateStatus.Force(policy.minBuild, policy.latestName, policy.storeUrl, policy.storeFallbackUrl)
    }

    val targetBuild = maxOf(policy.latestBuild, policy.minBuild)
    val snoozed = softSnooze != null && softSnooze.build >= targetBuild && now < softSnooze.until
    return if (installedBuild < targetBuild && !snoozed) {
        UpdateStatus.Soft(targetBuild, policy.latestName, policy.storeUrl, policy.storeFallbackUrl)
    } else {
        UpdateStatus.UpToDate
    }
}

private val FORCE_POLICY_MAX_AGE = 24.hours
