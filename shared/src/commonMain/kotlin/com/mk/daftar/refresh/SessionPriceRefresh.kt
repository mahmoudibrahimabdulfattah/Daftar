package com.mk.daftar.refresh

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.refresh.PriceRefreshController
import com.mk.daftar.core.domain.refresh.PriceRefreshStatus
import com.mk.daftar.core.domain.refresh.RefreshOutcome
import com.mk.daftar.core.domain.refresh.outcome
import com.mk.daftar.core.domain.refresh.rejections
import com.mk.daftar.core.domain.session.VaultSession
import com.mk.daftar.core.domain.session.VaultSessionState
import com.mk.daftar.core.domain.usecase.RefreshPricesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The one price refresh of each open vault generation.
 *
 * Every newly opened generation starts one automatic refresh, and nothing else starts one by itself: there is no
 * polling and no retry loop. Explicit requests join the refresh in flight. Opening a new generation, locking, or
 * leaving the open state cancels the refresh in flight and resets the status, and a late report is applied only while
 * its own generation is still the open one. Everything runs on [scope]'s dispatcher, which confines the state.
 */
class SessionPriceRefresh(
    private val session: VaultSession,
    private val refreshPrices: RefreshPricesUseCase,
    private val scope: CoroutineScope,
) : PriceRefreshController {

    private val mutable = MutableStateFlow<PriceRefreshStatus>(PriceRefreshStatus.Idle)
    override val status: StateFlow<PriceRefreshStatus> = mutable.asStateFlow()

    private var observing: Job? = null
    private var generation: Long? = null
    private var inFlight: Job? = null

    override fun start() {
        if (observing != null) return
        observing = scope.launch { session.state.collect { onSessionState(it) } }
    }

    override fun refresh() {
        val open = generation ?: return
        if (inFlight?.isActive == true) return
        startRefresh(open)
    }

    private fun onSessionState(state: VaultSessionState) {
        val open = (state as? VaultSessionState.Open)?.generation
        if (open == generation) return
        cancelInFlight()
        generation = open
        mutable.value = PriceRefreshStatus.Idle
        if (open != null) startRefresh(open)
    }

    private fun startRefresh(open: Long) {
        mutable.value = PriceRefreshStatus.Refreshing(open)
        inFlight = scope.launch {
            val finished = when (val result = refreshPrices()) {
                is Outcome.Success -> PriceRefreshStatus.Finished(open, result.data.outcome(), result.data.rejections())
                is Outcome.Failure -> PriceRefreshStatus.Finished(open, RefreshOutcome.Failed)
            }
            if (generation == open) mutable.value = finished
        }
    }

    private fun cancelInFlight() {
        inFlight?.cancel()
        inFlight = null
    }
}
