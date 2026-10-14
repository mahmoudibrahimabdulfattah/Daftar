package com.mk.daftar.feature.investments

import com.mk.daftar.core.domain.repository.InvestmentConversion
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.model.CashBalance
import com.mk.daftar.core.model.CashBalanceId
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.InstitutionId
import com.mk.daftar.core.model.InvestmentAccount
import com.mk.daftar.core.model.InvestmentAccountId
import com.mk.daftar.core.ui.mvi.UiIntent
import com.mk.daftar.core.ui.mvi.UiState
import kotlin.time.Instant

/** The steps of the conversion: choose the cash entry, choose the broker, then review and confirm. */
enum class ConvertStep { Source, Provider, Review, Result }

/** How a conversion ended, when the user has to be told. Each one leaves the source entry untouched. */
enum class ConvertOutcome {
    Converted,
    AlreadyConverted,
    SourceUnavailable,
    Unsupported,
}

/** One cash entry as the picker shows it. An entry that cannot convert is shown disabled, with the reason beside it. */
data class ConvertSourceRow(
    val id: CashBalanceId,
    val name: String,
    val note: String?,
    val amount: Decimal,
    val currency: Currency,
    val revision: Long,
) {
    val eligible: Boolean get() = currency == Currency.EGP && InvestmentAccount.isValidAmount(amount)
}

data class ConvertUiState(
    val phase: InvestmentPhase = InvestmentPhase.Loading,
    val step: ConvertStep = ConvertStep.Source,
    val sources: List<ConvertSourceRow> = emptyList(),
    /** The selected entry, read from the latest data. Null when none is selected or it has disappeared. */
    val sourceId: CashBalanceId? = null,
    /** The revision the review shows. A change in the entry requires a new review before confirming. */
    val reviewedRevision: Long? = null,
    val providerChosen: Boolean = false,
    val providerId: InstitutionId? = null,
    val asOf: ValueDateChoice = ValueDateChoice.Now,
    /** The new investment's ID, minted once per draft and reused on every retry. */
    val draftId: InvestmentAccountId? = null,
    val conflict: Boolean = false,
    val dateInvalid: Boolean = false,
    val busy: Boolean = false,
    val failure: StorageError? = null,
    val outcome: ConvertOutcome? = null,
    val hidden: Boolean = false,
    val concealed: Boolean = false,
) : UiState {
    val source: ConvertSourceRow?
        get() = sourceId?.let { id -> sources.firstOrNull { it.id == id } }
}

sealed interface ConvertIntent : UiIntent {
    data class SelectSource(val sourceId: CashBalanceId) : ConvertIntent

    data class ChooseProvider(val providerId: InstitutionId?) : ConvertIntent

    data object BackToSources : ConvertIntent

    data object BackToProviders : ConvertIntent

    data object SetToNow : ConvertIntent

    data class DateTimeChosen(val instant: Instant?) : ConvertIntent

    /** Accepts the entry's latest revision after it changed under the review. */
    data object ReviewLatest : ConvertIntent

    data object Confirm : ConvertIntent

    data object DismissFailure : ConvertIntent

    data object RevealForm : ConvertIntent

    data object Finish : ConvertIntent
}

/** The cash entries as the picker reads them, with the same eligibility as the conversion. */
internal fun List<Stored<CashBalance>>.toConvertRows(): List<ConvertSourceRow> = map { stored ->
    val balance = stored.value
    ConvertSourceRow(
        id = balance.id,
        name = balance.name,
        note = balance.note,
        amount = balance.balance.amount,
        currency = balance.balance.currency,
        revision = stored.revision,
    )
}

/** The list state for the conversion's cash source, judged by the same rules as the picker. */
internal fun convertReadOf(read: VaultRead<List<Stored<CashBalance>>>, hidden: Boolean): ConvertUiState = when (read) {
    VaultRead.Loading -> ConvertUiState(phase = InvestmentPhase.Loading, hidden = hidden)

    VaultRead.Locked -> ConvertUiState(phase = InvestmentPhase.Locked, hidden = hidden)

    is VaultRead.Failed -> ConvertUiState(phase = InvestmentPhase.Failed(read.error), hidden = hidden)

    is VaultRead.Ready -> {
        val rows = read.value.toConvertRows()
        ConvertUiState(
            phase = if (rows.isEmpty()) InvestmentPhase.Empty else InvestmentPhase.Ready,
            sources = rows,
            hidden = hidden,
        )
    }
}

/** The outcome a successful conversion call reports to the screen. */
internal fun InvestmentConversion.toConvertOutcome(): ConvertOutcome = when (this) {
    is InvestmentConversion.Converted -> ConvertOutcome.Converted
    is InvestmentConversion.AlreadyConverted -> ConvertOutcome.AlreadyConverted
}
