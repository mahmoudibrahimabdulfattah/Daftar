package com.mk.daftar.core.domain.update

import kotlin.time.Instant

/**
 * The Remote Config values for the installed platform. [fetchedAt] is the time of the last successful fetch, which a
 * failed fetch never moves forward. [fetchSucceeded] is true only for a policy read from a fetch that just finished.
 */
data class UpdatePolicy(
    val minBuild: Long,
    val latestBuild: Long,
    val forceEnabled: Boolean,
    val latestName: String,
    val fetchedAt: Instant?,
    val storeUrl: String?,
    val storeFallbackUrl: String? = null,
    val fetchSucceeded: Boolean = false,
)

sealed interface UpdateStatus {
    data object UpToDate : UpdateStatus

    data class Soft(
        val targetBuild: Long,
        val latestName: String,
        val storeUrl: String,
        val storeFallbackUrl: String? = null,
    ) : UpdateStatus

    data class Force(
        val targetBuild: Long,
        val latestName: String,
        val storeUrl: String,
        val storeFallbackUrl: String? = null,
    ) : UpdateStatus
}
