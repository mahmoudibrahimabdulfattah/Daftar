package com.mk.daftar.di

import com.mk.daftar.core.domain.repository.JournalRepository
import com.mk.daftar.core.domain.repository.NoteRepository
import com.mk.daftar.core.domain.repository.ZakatRepository
import com.mk.daftar.core.domain.usecase.AddZakatYearUseCase
import com.mk.daftar.core.domain.usecase.ConvertCashToInvestmentUseCase
import com.mk.daftar.core.domain.usecase.CreateBankAccountUseCase
import com.mk.daftar.core.domain.usecase.CreateBankUseCase
import com.mk.daftar.core.domain.usecase.CreateCashBalanceUseCase
import com.mk.daftar.core.domain.usecase.CreateGoldHoldingUseCase
import com.mk.daftar.core.domain.usecase.CreateInvestmentUseCase
import com.mk.daftar.core.domain.usecase.CreatePersonalNoteUseCase
import com.mk.daftar.core.domain.usecase.CreateZakatPaymentUseCase
import com.mk.daftar.core.domain.usecase.DeleteBankAccountUseCase
import com.mk.daftar.core.domain.usecase.DeleteBankUseCase
import com.mk.daftar.core.domain.usecase.DeleteCashBalanceUseCase
import com.mk.daftar.core.domain.usecase.DeleteGoldHoldingUseCase
import com.mk.daftar.core.domain.usecase.DeleteInvestmentUseCase
import com.mk.daftar.core.domain.usecase.DeleteManualQuoteUseCase
import com.mk.daftar.core.domain.usecase.DeletePersonalNoteUseCase
import com.mk.daftar.core.domain.usecase.DeleteZakatPaymentUseCase
import com.mk.daftar.core.domain.usecase.EnsureZakatTrackingUseCase
import com.mk.daftar.core.domain.usecase.ObserveBankAccountUseCase
import com.mk.daftar.core.domain.usecase.ObserveBankAccountsUseCase
import com.mk.daftar.core.domain.usecase.ObserveBankDetailUseCase
import com.mk.daftar.core.domain.usecase.ObserveBankUseCase
import com.mk.daftar.core.domain.usecase.ObserveBanksUseCase
import com.mk.daftar.core.domain.usecase.ObserveCashBalanceUseCase
import com.mk.daftar.core.domain.usecase.ObserveCashBalancesUseCase
import com.mk.daftar.core.domain.usecase.ObserveGoldHoldingUseCase
import com.mk.daftar.core.domain.usecase.ObserveGoldHoldingsUseCase
import com.mk.daftar.core.domain.usecase.ObserveInvestmentUseCase
import com.mk.daftar.core.domain.usecase.ObserveInvestmentsUseCase
import com.mk.daftar.core.domain.usecase.ObservePersonalNoteUseCase
import com.mk.daftar.core.domain.usecase.ObservePersonalNotesUseCase
import com.mk.daftar.core.domain.usecase.ObservePortfolioValuationUseCase
import com.mk.daftar.core.domain.usecase.ObservePreferencesUseCase
import com.mk.daftar.core.domain.usecase.ObserveQuoteSnapshotUseCase
import com.mk.daftar.core.domain.usecase.ObserveSelectedQuotesUseCase
import com.mk.daftar.core.domain.usecase.ObserveZakatDayUseCase
import com.mk.daftar.core.domain.usecase.ObserveZakatEstimateUseCase
import com.mk.daftar.core.domain.usecase.ObserveZakatPaymentUseCase
import com.mk.daftar.core.domain.usecase.ObserveZakatPaymentsUseCase
import com.mk.daftar.core.domain.usecase.ObserveZakatTrackingUseCase
import com.mk.daftar.core.domain.usecase.RefreshPricesUseCase
import com.mk.daftar.core.domain.usecase.RemoveOverrideUseCase
import com.mk.daftar.core.domain.usecase.SetInactivityTimeoutUseCase
import com.mk.daftar.core.domain.usecase.SetLanguageUseCase
import com.mk.daftar.core.domain.usecase.SetManualQuoteUseCase
import com.mk.daftar.core.domain.usecase.SetThemeModeUseCase
import com.mk.daftar.core.domain.usecase.SetZakatDateAdjustmentUseCase
import com.mk.daftar.core.domain.usecase.SetZakatYearPaidUseCase
import com.mk.daftar.core.domain.usecase.UpdateBankAccountUseCase
import com.mk.daftar.core.domain.usecase.UpdateBankUseCase
import com.mk.daftar.core.domain.usecase.UpdateCashBalanceUseCase
import com.mk.daftar.core.domain.usecase.UpdateGoldHoldingUseCase
import com.mk.daftar.core.domain.usecase.UpdateInvestmentUseCase
import com.mk.daftar.core.domain.usecase.UpdatePersonalNoteUseCase
import com.mk.daftar.core.domain.usecase.UpdateZakatPaymentUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/** Domain use cases. Pure Kotlin in `core:domain`, so they are bound here rather than in a Koin-aware module. */
val domainModule = module {
    factory<ZakatRepository> { get<JournalRepository>().zakat }
    factory<NoteRepository> { get<JournalRepository>().notes }
    factoryOf(::RefreshPricesUseCase)
    factoryOf(::SetManualQuoteUseCase)
    factoryOf(::RemoveOverrideUseCase)
    factoryOf(::DeleteManualQuoteUseCase)
    factoryOf(::ObserveGoldHoldingsUseCase)
    factoryOf(::ObserveGoldHoldingUseCase)
    factoryOf(::CreateGoldHoldingUseCase)
    factoryOf(::UpdateGoldHoldingUseCase)
    factoryOf(::DeleteGoldHoldingUseCase)
    factoryOf(::ObserveBanksUseCase)
    factoryOf(::ObserveBankUseCase)
    factoryOf(::ObserveBankDetailUseCase)
    factoryOf(::ObserveBankAccountsUseCase)
    factoryOf(::ObserveBankAccountUseCase)
    factoryOf(::CreateBankUseCase)
    factoryOf(::UpdateBankUseCase)
    factoryOf(::DeleteBankUseCase)
    factoryOf(::CreateBankAccountUseCase)
    factoryOf(::UpdateBankAccountUseCase)
    factoryOf(::DeleteBankAccountUseCase)
    factoryOf(::ObserveCashBalancesUseCase)
    factoryOf(::ObserveInvestmentsUseCase)
    factoryOf(::ObserveInvestmentUseCase)
    factoryOf(::CreateInvestmentUseCase)
    factoryOf(::UpdateInvestmentUseCase)
    factoryOf(::DeleteInvestmentUseCase)
    factoryOf(::ConvertCashToInvestmentUseCase)
    factoryOf(::ObserveCashBalanceUseCase)
    factoryOf(::CreateCashBalanceUseCase)
    factoryOf(::UpdateCashBalanceUseCase)
    factoryOf(::DeleteCashBalanceUseCase)
    factoryOf(::ObserveZakatPaymentsUseCase)
    factoryOf(::ObserveZakatPaymentUseCase)
    factoryOf(::CreateZakatPaymentUseCase)
    factoryOf(::UpdateZakatPaymentUseCase)
    factoryOf(::DeleteZakatPaymentUseCase)
    factoryOf(::ObservePersonalNotesUseCase)
    factoryOf(::ObservePersonalNoteUseCase)
    factoryOf(::CreatePersonalNoteUseCase)
    factoryOf(::UpdatePersonalNoteUseCase)
    factoryOf(::DeletePersonalNoteUseCase)
    factoryOf(::ObserveSelectedQuotesUseCase)
    factoryOf(::ObserveQuoteSnapshotUseCase)
    factoryOf(::ObservePortfolioValuationUseCase)
    factoryOf(::ObserveZakatEstimateUseCase)
    factoryOf(::ObserveZakatTrackingUseCase)
    factoryOf(::ObserveZakatDayUseCase)
    factoryOf(::EnsureZakatTrackingUseCase)
    factoryOf(::SetZakatYearPaidUseCase)
    factoryOf(::SetZakatDateAdjustmentUseCase)
    factoryOf(::AddZakatYearUseCase)
    factoryOf(::ObservePreferencesUseCase)
    factoryOf(::SetThemeModeUseCase)
    factoryOf(::SetLanguageUseCase)
    factoryOf(::SetInactivityTimeoutUseCase)
}
