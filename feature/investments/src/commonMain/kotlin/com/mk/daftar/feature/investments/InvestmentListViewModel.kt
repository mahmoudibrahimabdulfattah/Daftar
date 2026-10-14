package com.mk.daftar.feature.investments

import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.usecase.ObserveInvestmentsUseCase
import com.mk.daftar.core.model.InvestmentAccount
import com.mk.daftar.core.navigation.ConvertCashRoute
import com.mk.daftar.core.navigation.InvestmentAddRoute
import com.mk.daftar.core.navigation.InvestmentDetailRoute
import com.mk.daftar.core.ui.mvi.MviViewModel
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * The investment account list. Values come only from the repository: nothing here refreshes prices or touches the
 * network. The review line is re-judged at the next threshold of the displayed values, never by polling. Opening an
 * account while values are hidden stores the request until an explicit reveal.
 */
class InvestmentListViewModel(
    observeInvestments: ObserveInvestmentsUseCase,
    private val visibility: ValuesVisibility,
    private val clock: Clock,
) : MviViewModel<InvestmentListUiState, InvestmentListIntent, InvestmentEffect>(InvestmentListUiState()) {

    init {
        viewModelScope.launch {
            listFrames(observeInvestments()).collect { next ->
                reduce { next.copy(pendingRoute = if (next.phase == InvestmentPhase.Locked) null else pendingRoute) }
            }
        }
    }

    /**
     * One state per read and per hint threshold. Between thresholds nothing is emitted, so the screen does no work
     * while the displayed values are unchanged.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun listFrames(reads: Flow<VaultRead<List<Stored<InvestmentAccount>>>>): Flow<InvestmentListUiState> =
        combine(reads, visibility.hiddenFlow()) { read, hidden -> read to hidden }
            .flatMapLatest { (read, hidden) ->
                flow {
                    while (true) {
                        val now = clock.now()
                        emit(investmentListStateOf(read, hidden, now))
                        val dates = (read as? VaultRead.Ready)?.value?.map { it.value.valueAsOf }.orEmpty()
                        val next = nextValueDateThreshold(dates, now) ?: break
                        delay(next - now)
                    }
                }
            }

    override fun onIntent(intent: InvestmentListIntent) {
        when (intent) {
            InvestmentListIntent.OpenAdd -> open(InvestmentAddRoute)

            is InvestmentListIntent.OpenAccount -> open(InvestmentDetailRoute(intent.investmentId.value))

            InvestmentListIntent.OpenConvert -> open(ConvertCashRoute())

            InvestmentListIntent.RevealAndOpen -> {
                val route = currentState.pendingRoute ?: return
                visibility.reveal()
                reduce { copy(pendingRoute = null) }
                emitEffect(InvestmentEffect.Navigate(route))
            }

            InvestmentListIntent.CancelReveal -> reduce { copy(pendingRoute = null) }

            InvestmentListIntent.ToggleHidden -> visibility.toggle()
        }
    }

    private fun open(route: NavKey) {
        if (visibility.isHidden()) {
            reduce { copy(pendingRoute = route) }
        } else {
            emitEffect(InvestmentEffect.Navigate(route))
        }
    }
}
