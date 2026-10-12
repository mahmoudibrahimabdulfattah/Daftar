package com.mk.daftar.feature.banks

import androidx.lifecycle.viewModelScope
import com.mk.daftar.core.domain.usecase.ObserveBankAccountsUseCase
import com.mk.daftar.core.domain.usecase.ObserveBanksUseCase
import com.mk.daftar.core.domain.usecase.ObserveSelectedQuotesUseCase
import com.mk.daftar.core.navigation.BankAddRoute
import com.mk.daftar.core.navigation.BankDetailRoute
import com.mk.daftar.core.ui.mvi.MviViewModel
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The bank list: every bank with its account count, currencies and value, and the aggregate account value. Banks,
 * accounts and quotes are observed together, so a row and the total always come from one session generation.
 */
class BanksListViewModel(
    observeBanks: ObserveBanksUseCase,
    observeAccounts: ObserveBankAccountsUseCase,
    observeQuotes: ObserveSelectedQuotesUseCase,
    private val visibility: ValuesVisibility,
) : MviViewModel<BankListUiState, BankListIntent, BankEffect>(BankListUiState()) {

    init {
        viewModelScope.launch {
            combine(
                observeBanks(),
                observeAccounts(),
                observeQuotes(),
                visibility.hiddenFlow(),
            ) { banks, accounts, quotes, hidden ->
                bankListStateOf(banks, accounts, quotes, hidden)
            }.collect { next -> reduce { next } }
        }
    }

    override fun onIntent(intent: BankListIntent) {
        when (intent) {
            is BankListIntent.OpenBank -> emitEffect(BankEffect.Navigate(BankDetailRoute(intent.bankId)))
            BankListIntent.AddBank -> emitEffect(BankEffect.Navigate(BankAddRoute))
            BankListIntent.ToggleValues -> visibility.toggle()
        }
    }
}
