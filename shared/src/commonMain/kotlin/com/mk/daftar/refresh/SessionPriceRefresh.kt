package com.mk.daftar.refresh

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.refresh.PriceRefreshController
import com.mk.daftar.core.domain.refresh.PriceRefreshStatus
import com.mk.daftar.core.domain.refresh.RefreshOutcome
import com.mk.daftar.core.domain.refresh.failedAssets
import com.mk.daftar.core.domain.refresh.outcome
import com.mk.daftar.core.domain.refresh.rejections
import com.mk.daftar.core.domain.repository.RefreshRejection
import com.mk.daftar.core.domain.session.VaultSession
import com.mk.daftar.core.domain.session.VaultSessionState
import com.mk.daftar.core.domain.usecase.RefreshPricesUseCase
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The price refresh of each open vault generation.
 *
 * Every newly opened generation starts one automatic refresh. It runs quietly: its status is
 * [PriceRefreshStatus.Refreshing] with `visible = false`, so no screen shows progress. When a provider cannot be
 * reached it is retried after [retryDelays] (plus up to 20% jitter), then the refresh stops until the next generation
 * or an explicit request. A rate limit, an invalid payload or a storage failure is never retried. Only the final
 * result of a quiet chain is reported, so the owner sees no failure while a retry is still pending.
 *
 * An explicit request joins the attempt in flight and makes it visible, or, while a quiet retry is waiting, runs the
 * next attempt now. Its result is reported at once; any retries after it are quiet again. Opening a new generation,
 * locking, or leaving the open state cancels the chain and resets the status, and a late report is applied only while
 * its own generation is still the open one. Everything runs on [scope]'s dispatcher, which confines the state.
 */
class SessionPriceRefresh(
    private val session: VaultSession,
    private val refreshPrices: RefreshPricesUseCase,
    private val scope: CoroutineScope,
    private val retryDelays: List<Duration> = DEFAULT_RETRY_DELAYS,
    private val jitter: () -> Double = { Random.nextDouble(0.0, MAX_JITTER) },
) : PriceRefreshController {

    private val mutable = MutableStateFlow<PriceRefreshStatus>(PriceRefreshStatus.Idle)
    override val status: StateFlow<PriceRefreshStatus> = mutable.asStateFlow()

    private var observing: Job? = null
    private var generation: Long? = null
    private var chain: Job? = null

    // Whether the chain's attempt is running (rather than waiting to retry), and whether the owner asked for it.
    private var attempting = false
    private var visible = false

    override fun start() {
        if (observing != null) return
        observing = scope.launch { session.state.collect { onSessionState(it) } }
    }

    override fun refresh() {
        val open = generation ?: return
        if (chain?.isActive == true && attempting) {
            // Join the attempt in flight; it now reports to the owner.
            visible = true
            mutable.value = PriceRefreshStatus.Refreshing(open, visible = true)
            return
        }
        cancelChain()
        startChain(open, visible = true)
    }

    private fun onSessionState(state: VaultSessionState) {
        val open = (state as? VaultSessionState.Open)?.generation
        if (open == generation) return
        cancelChain()
        generation = open
        mutable.value = PriceRefreshStatus.Idle
        if (open != null) startChain(open, visible = false)
    }

    private fun startChain(open: Long, visible: Boolean) {
        this.visible = visible
        chain = scope.launch {
            var retry = 0
            while (true) {
                attempting = true
                // A quiet attempt never replaces a result the owner has already seen.
                if (this@SessionPriceRefresh.visible || mutable.value !is PriceRefreshStatus.Finished) {
                    mutable.value = PriceRefreshStatus.Refreshing(open, this@SessionPriceRefresh.visible)
                }
                val finished = attempt(open)
                attempting = false
                if (generation != open) return@launch
                val retryable = finished.outcome != RefreshOutcome.Succeeded && finished.isTransient()
                val last = !retryable || retry >= retryDelays.size
                if (last || this@SessionPriceRefresh.visible) mutable.value = finished
                this@SessionPriceRefresh.visible = false
                if (last) return@launch
                delay(retryDelays[retry] * (1.0 + jitter()))
                retry++
            }
        }
    }

    private suspend fun attempt(open: Long): PriceRefreshStatus.Finished = when (val result = refreshPrices()) {
        is Outcome.Success -> PriceRefreshStatus.Finished(
            open,
            result.data.outcome(),
            result.data.rejections(),
            result.data.failedAssets(),
        )

        is Outcome.Failure -> PriceRefreshStatus.Finished(open, RefreshOutcome.Failed)
    }

    private fun cancelChain() {
        chain?.cancel()
        chain = null
        attempting = false
        visible = false
    }

    private companion object {
        val DEFAULT_RETRY_DELAYS = listOf(5.seconds, 15.seconds, 45.seconds, 120.seconds)
        const val MAX_JITTER = 0.2
    }
}

/** Only an unreachable provider is worth another try soon; a rate limit or a bad payload is not. */
private fun PriceRefreshStatus.Finished.isTransient(): Boolean =
    RefreshRejection.Unreachable in rejections && RefreshRejection.RateLimited !in rejections
