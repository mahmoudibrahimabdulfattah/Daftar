package com.mk.daftar.core.domain.refresh

import com.mk.daftar.core.domain.repository.RefreshRejection
import com.mk.daftar.core.domain.repository.RefreshReport
import com.mk.daftar.core.domain.repository.RefreshStepResult
import kotlinx.coroutines.flow.StateFlow

/**
 * The session's price refresh, as seen by Home, Prices and pull-to-refresh. There is at most one refresh in flight
 * per open generation: a request made while one runs joins it instead of starting another fetch.
 */
sealed interface PriceRefreshStatus {
    /** The generation this status belongs to, or null when no session is open. */
    val generation: Long?

    /** No refresh has run for the open generation. Also the state after a lock. */
    data object Idle : PriceRefreshStatus {
        override val generation: Long? = null
    }

    data class Refreshing(override val generation: Long) : PriceRefreshStatus

    /** The last refresh of [generation] has finished with [outcome]. Last-success times are never changed by a failure. */
    data class Finished(override val generation: Long, val outcome: RefreshOutcome) : PriceRefreshStatus
}

enum class RefreshOutcome {
    /** Every provider step was accepted. */
    Succeeded,

    /** Some providers or karats were accepted and others kept their cached value. */
    PartiallyFailed,

    /** Nothing was accepted, or the refresh could not be stored. */
    Failed,
}

/**
 * Maps a finished refresh report to its outcome. Only provider failures count (see [isFailure]); a step that kept its
 * value because the provider's data was not newer is not a failure.
 */
fun RefreshReport.outcome(): RefreshOutcome {
    val steps = gold.values + fx.values
    val failures = steps.count { it.isFailure() }
    return when {
        failures == 0 -> RefreshOutcome.Succeeded
        failures == steps.size -> RefreshOutcome.Failed
        else -> RefreshOutcome.PartiallyFailed
    }
}

/** Whether a step failed to refresh: kept at its cached value for any reason but being older than what is stored. */
fun RefreshStepResult.isFailure(): Boolean =
    this is RefreshStepResult.Kept && reason != RefreshRejection.OlderThanStored

interface PriceRefreshController {
    val status: StateFlow<PriceRefreshStatus>

    /** Begins observing the vault session. Idempotent; startup neither fetches nor opens anything by itself. */
    fun start()

    /** Asks for a refresh of the open generation. Joins a refresh already in flight and does nothing without a session. */
    fun refresh()
}
