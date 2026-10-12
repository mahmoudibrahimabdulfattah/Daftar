package com.mk.daftar.feature.banks

import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import com.mk.daftar.core.domain.usecase.ObserveBankDetailUseCase
import com.mk.daftar.core.domain.usecase.ObserveSelectedQuotesUseCase
import com.mk.daftar.core.model.BankId
import com.mk.daftar.core.navigation.AccountAddRoute
import com.mk.daftar.core.navigation.AccountEditRoute
import com.mk.daftar.core.navigation.BankEditRoute
import com.mk.daftar.core.ui.mvi.MviViewModel
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * One bank and its accounts. Account balances and notes are sensitive: opening an account or adding one while values
 * are hidden stores the request and waits for an explicit reveal. Editing the bank's name is not sensitive.
 */
class BankDetailViewModel(
    private val bankId: String,
    observeDetail: ObserveBankDetailUseCase,
    observeQuotes: ObserveSelectedQuotesUseCase,
    private val visibility: ValuesVisibility,
) : MviViewModel<BankDetailUiState, BankDetailIntent, BankEffect>(BankDetailUiState(bankId = bankId)) {

    init {
        viewModelScope.launch {
            combine(
                observeDetail(BankId(bankId)),
                observeQuotes(),
                visibility.hiddenFlow(),
            ) { detail, quotes, hidden ->
                bankDetailStateOf(bankId, detail, quotes, hidden)
            }.collect { next ->
                reduce { next.copy(pendingRoute = if (next.phase == BankPhase.Locked) null else pendingRoute) }
            }
        }
    }

    override fun onIntent(intent: BankDetailIntent) {
        when (intent) {
            is BankDetailIntent.OpenAccount -> open(AccountEditRoute(intent.accountId))

            BankDetailIntent.AddAccount -> open(AccountAddRoute(bankId))

            BankDetailIntent.RevealAndOpen -> {
                val route = currentState.pendingRoute ?: return
                visibility.reveal()
                reduce { copy(pendingRoute = null) }
                emitEffect(BankEffect.Navigate(route))
            }

            BankDetailIntent.CancelReveal -> reduce { copy(pendingRoute = null) }

            BankDetailIntent.ToggleValues -> visibility.toggle()

            BankDetailIntent.EditBank -> emitEffect(BankEffect.Navigate(BankEditRoute(bankId)))
        }
    }

    private fun open(route: NavKey) {
        if (visibility.isHidden()) {
            reduce { copy(pendingRoute = route) }
        } else {
            emitEffect(BankEffect.Navigate(route))
        }
    }
}
