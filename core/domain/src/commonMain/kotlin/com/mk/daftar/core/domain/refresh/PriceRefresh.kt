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

    /**
     * A refresh of [generation] is running. [visible] is true only for a refresh the owner asked for; automatic
     * refreshes and their retries run quietly, so no screen shows progress for them.
     */
    data class Refreshing(override val generation: Long, val visible: Boolean = true) : PriceRefreshStatus

    /**
     * The last refresh of [generation] has finished with [outcome]. Last-success times are never changed by a failure.
     * [rejections] are the reasons of the failed steps, so a rate limit, an invalid payload and an unreachable source
     * can be told apart. Empty when nothing failed, or when the refresh could not be stored at all.
     */
    data class Finished(
        override val generation: Long,
        val outcome: RefreshOutcome,
        val rejections: Set<RefreshRejection> = emptySet(),
    ) : PriceRefreshStatus
}

/** Whether a screen should show progress for this status: only for a refresh the owner asked for. */
val PriceRefreshStatus.showsProgress: Boolean
    get() = this is PriceRefreshStatus.Refreshing && visible

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

/** The reasons of every failed step in [RefreshReport], without the steps that are merely older than what is stored. */
fun RefreshReport.rejections(): Set<RefreshRejection> = (gold.values + fx.values)
    .filter { it.isFailure() }
    .mapNotNull { (it as? RefreshStepResult.Kept)?.reason }
    .toSet()

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
