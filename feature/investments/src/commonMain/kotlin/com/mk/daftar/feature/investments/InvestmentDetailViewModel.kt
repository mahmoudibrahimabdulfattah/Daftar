package com.mk.daftar.feature.investments

import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.usecase.DeleteInvestmentUseCase
import com.mk.daftar.core.domain.usecase.ObserveInvestmentUseCase
import com.mk.daftar.core.model.InvestmentAccount
import com.mk.daftar.core.model.InvestmentAccountId
import com.mk.daftar.core.navigation.InvestmentEditRoute
import com.mk.daftar.core.ui.mvi.MviViewModel
import com.mk.daftar.core.ui.mvi.UiIntent
import com.mk.daftar.core.ui.mvi.UiState
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/** One account's detail. Its only primary action is Update value, which opens the same editor. */
data class InvestmentDetailUiState(
    val phase: InvestmentPhase = InvestmentPhase.Loading,
    val row: InvestmentRow? = null,
    val hidden: Boolean = false,
    /** The editor the user asked for while values are hidden. It opens only after an explicit reveal. */
    val pendingRoute: NavKey? = null,
    val confirmDelete: Boolean = false,
    val busy: Boolean = false,
    val failure: StorageError? = null,
) : UiState

sealed interface InvestmentDetailIntent : UiIntent {
    data object OpenEdit : InvestmentDetailIntent

    data object RevealAndOpen : InvestmentDetailIntent

    data object CancelReveal : InvestmentDetailIntent

    data object RequestDelete : InvestmentDetailIntent

    data object ConfirmDelete : InvestmentDetailIntent

    data object DismissConfirmation : InvestmentDetailIntent

    data object DismissFailure : InvestmentDetailIntent

    data object ToggleHidden : InvestmentDetailIntent
}

/** The detail of one account. Values are re-judged for the review line the same way the list judges them. */
@OptIn(ExperimentalCoroutinesApi::class)
class InvestmentDetailViewModel(
    investmentId: String,
    observeInvestment: ObserveInvestmentUseCase,
    private val deleteInvestment: DeleteInvestmentUseCase,
    private val visibility: ValuesVisibility,
    private val clock: Clock,
) : MviViewModel<InvestmentDetailUiState, InvestmentDetailIntent, InvestmentEffect>(InvestmentDetailUiState()) {

    private val accountId = InvestmentAccountId(investmentId)
    private var loadedRevision: Long? = null

    init {
        viewModelScope.launch {
            combine(observeInvestment(accountId), visibility.hiddenFlow()) { read, hidden -> read to hidden }
                .flatMapLatest { (read, hidden) ->
                    flow {
                        while (true) {
                            val now = clock.now()
                            val stored = (read as? VaultRead.Ready)?.value
                            loadedRevision = stored?.revision
                            emit(detailStateOf(read, hidden, now))
                            val next = stored?.value?.valueAsOf
                                ?.let { nextValueDateThreshold(listOf(it), now) } ?: break
                            delay(next - now)
                        }
                    }
                }
                .collect { next ->
                    reduce {
                        next.copy(
                            pendingRoute = if (next.phase == InvestmentPhase.Locked) null else pendingRoute,
                            confirmDelete = confirmDelete && next.row != null,
                            busy = busy,
                            failure = failure,
                        )
                    }
                }
        }
    }

    override fun onIntent(intent: InvestmentDetailIntent) {
        when (intent) {
            InvestmentDetailIntent.OpenEdit -> open(InvestmentEditRoute(accountId.value))

            InvestmentDetailIntent.RevealAndOpen -> {
                val route = currentState.pendingRoute ?: return
                visibility.reveal()
                reduce { copy(pendingRoute = null) }
                emitEffect(InvestmentEffect.Navigate(route))
            }

            InvestmentDetailIntent.CancelReveal -> reduce { copy(pendingRoute = null) }

            InvestmentDetailIntent.RequestDelete -> {
                if (currentState.row != null && !currentState.busy) reduce { copy(confirmDelete = true) }
            }

            InvestmentDetailIntent.ConfirmDelete -> confirmDelete()

            InvestmentDetailIntent.DismissConfirmation -> reduce { copy(confirmDelete = false) }

            InvestmentDetailIntent.DismissFailure -> reduce { copy(failure = null) }

            InvestmentDetailIntent.ToggleHidden -> visibility.toggle()
        }
    }

    private fun open(route: NavKey) {
        if (visibility.isHidden()) {
            reduce { copy(pendingRoute = route) }
        } else {
            emitEffect(InvestmentEffect.Navigate(route))
        }
    }

    private fun confirmDelete() {
        val state = currentState
        val revision = loadedRevision ?: return
        if (state.busy || !state.confirmDelete) return
        reduce { copy(confirmDelete = false, busy = true, failure = null) }
        viewModelScope.launch {
            when (val outcome = deleteInvestment(accountId, revision)) {
                is Outcome.Success -> {
                    reduce { copy(busy = false) }
                    emitEffect(InvestmentEffect.Completed)
                }

                is Outcome.Failure -> reduce { copy(busy = false, failure = outcome.error) }
            }
        }
    }
}

/** The detail state for one read at one instant. A missing account is [InvestmentPhase.Missing]. */
internal fun detailStateOf(
    read: VaultRead<Stored<InvestmentAccount>?>,
    hidden: Boolean,
    now: kotlin.time.Instant,
): InvestmentDetailUiState = when (read) {
    VaultRead.Loading -> InvestmentDetailUiState(phase = InvestmentPhase.Loading, hidden = hidden)

    VaultRead.Locked -> InvestmentDetailUiState(phase = InvestmentPhase.Locked, hidden = hidden)

    is VaultRead.Failed -> InvestmentDetailUiState(phase = InvestmentPhase.Failed(read.error), hidden = hidden)

    is VaultRead.Ready -> read.value?.let { stored ->
        InvestmentDetailUiState(
            phase = InvestmentPhase.Ready,
            row = stored.value.toRow(now),
            hidden = hidden,
        )
    } ?: InvestmentDetailUiState(phase = InvestmentPhase.Missing, hidden = hidden)
}
