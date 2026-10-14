package com.mk.daftar.feature.investments

import androidx.lifecycle.viewModelScope
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.InvestmentConversion
import com.mk.daftar.core.domain.repository.InvestmentConversionFailure
import com.mk.daftar.core.domain.repository.InvestmentDraftIds
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.usecase.ConvertCashToInvestmentUseCase
import com.mk.daftar.core.domain.usecase.ObserveCashBalancesUseCase
import com.mk.daftar.core.model.CashBalance
import com.mk.daftar.core.model.CashBalanceId
import com.mk.daftar.core.model.InstitutionId
import com.mk.daftar.core.model.InvestmentAccount
import com.mk.daftar.core.ui.mvi.MviViewModel
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.launch

/**
 * Moves one EGP cash entry into Investments by explicit choice: the entry, then the broker, then a review that states the
 * total is unchanged. Nothing is preselected from a name, and nothing converts without Confirm. The draft ID is minted once
 * when the review opens, so a retry can never create a second investment. A change under the review asks for a new
 * review; an entry that vanished is reported as unavailable and never recreated.
 */
class InvestmentConvertViewModel(
    private val presetSourceId: CashBalanceId?,
    observeCash: ObserveCashBalancesUseCase,
    private val convert: ConvertCashToInvestmentUseCase,
    private val draftIds: InvestmentDraftIds,
    private val visibility: ValuesVisibility,
    private val clock: Clock,
) : MviViewModel<ConvertUiState, ConvertIntent, InvestmentEffect>(ConvertUiState()) {

    private var lockEpoch = 0
    private var presetApplied = false

    init {
        viewModelScope.launch {
            observeCash().collect { read ->
                if (read == VaultRead.Locked) {
                    onLocked()
                } else {
                    onCashRead(read)
                }
            }
        }
        viewModelScope.launch {
            visibility.hiddenFlow().collect { hidden ->
                reduce { copy(hidden = hidden, concealed = hidden) }
            }
        }
    }

    private fun onCashRead(read: VaultRead<List<Stored<CashBalance>>>) {
        val fresh = convertReadOf(read, hidden = currentState.hidden)
        reduce {
            val sourceRow = fresh.sources.firstOrNull { it.id == sourceId }
            // An empty list counts too: the entry is gone, not the list unavailable.
            val listed = fresh.phase == InvestmentPhase.Ready || fresh.phase == InvestmentPhase.Empty
            val vanished = sourceId != null && sourceRow == null && step != ConvertStep.Result && listed
            // A review stays a review of the revision it showed: any later change to the entry raises a conflict.
            val diverged = step == ConvertStep.Review && sourceRow != null && sourceRow.revision != reviewedRevision
            copy(
                phase = fresh.phase,
                sources = fresh.sources,
                conflict = conflict || diverged,
                step = if (vanished) ConvertStep.Result else step,
                outcome = if (vanished) ConvertOutcome.SourceUnavailable else outcome,
                sourceId = if (vanished) null else sourceId,
            )
        }
        if (!presetApplied && read is VaultRead.Ready) applyPreset()
    }

    /** A preselected entry: eligible entries go straight to the broker choice, ineligible ones stay visible and disabled. */
    private fun applyPreset() {
        presetApplied = true
        val id = presetSourceId ?: return
        val row = currentState.sources.firstOrNull { it.id == id }
        when {
            row == null -> reduce { copy(step = ConvertStep.Result, outcome = ConvertOutcome.SourceUnavailable) }
            row.eligible -> reduce { copy(sourceId = id, step = ConvertStep.Provider) }
            else -> reduce { copy(sourceId = null) }
        }
    }

    override fun onIntent(intent: ConvertIntent) {
        when (intent) {
            is ConvertIntent.SelectSource -> selectSource(intent.sourceId)

            is ConvertIntent.ChooseProvider -> chooseProvider(intent.providerId)

            ConvertIntent.BackToSources -> {
                if (!currentState.busy) reduce { copy(step = ConvertStep.Source, sourceId = null) }
            }

            ConvertIntent.BackToProviders -> {
                if (!currentState.busy) {
                    reduce {
                        copy(step = ConvertStep.Provider, conflict = false, dateInvalid = false)
                    }
                }
            }

            ConvertIntent.SetToNow -> edit { copy(asOf = ValueDateChoice.Now, dateInvalid = false) }

            is ConvertIntent.DateTimeChosen -> chooseDateTime(intent.instant)

            ConvertIntent.ReviewLatest -> reviewLatest()

            ConvertIntent.Confirm -> confirm()

            ConvertIntent.DismissFailure -> reduce { copy(failure = null) }

            ConvertIntent.RevealForm -> visibility.reveal()

            ConvertIntent.Finish -> emitEffect(InvestmentEffect.Completed)
        }
    }

    private fun selectSource(id: CashBalanceId) {
        val state = currentState
        if (state.busy || state.step != ConvertStep.Source) return
        val row = state.sources.firstOrNull { it.id == id } ?: return
        if (!row.eligible) return
        reduce { copy(sourceId = id, step = ConvertStep.Provider, providerChosen = false, providerId = null) }
    }

    private fun chooseProvider(providerId: InstitutionId?) {
        val state = currentState
        if (state.busy || state.step != ConvertStep.Provider) return
        val row = state.source ?: return
        // The draft ID is minted once, the first time the review opens; going back and choosing again keeps it.
        val draft = state.draftId ?: draftIds.newDraftId()
        reduce {
            copy(
                step = ConvertStep.Review,
                providerChosen = true,
                providerId = providerId,
                reviewedRevision = row.revision,
                draftId = draft,
                conflict = false,
                failure = null,
            )
        }
    }

    private fun reviewLatest() {
        val state = currentState
        if (state.busy || state.step != ConvertStep.Review) return
        val row = state.source ?: return
        reduce { copy(reviewedRevision = row.revision, conflict = false) }
    }

    private fun edit(change: ConvertUiState.() -> ConvertUiState) {
        val state = currentState
        if (state.busy || state.concealed || state.step != ConvertStep.Review) return
        reduce { change() }
    }

    private fun chooseDateTime(instant: Instant?) {
        val state = currentState
        if (state.busy || state.concealed || state.step != ConvertStep.Review) return
        val now = clock.now()
        val chosen = instant?.takeIf { it >= InvestmentAccount.EARLIEST_VALUE_AS_OF && it <= now }
        reduce {
            copy(
                dateInvalid = chosen == null,
                asOf = if (chosen != null) ValueDateChoice.At(chosen) else asOf,
            )
        }
    }

    private fun confirm() {
        val state = currentState
        if (state.busy || state.concealed || state.step != ConvertStep.Review || state.conflict) return
        val sourceId = state.sourceId ?: return
        val revision = state.reviewedRevision ?: return
        val draftId = state.draftId ?: return
        if (state.source == null) return
        val now = clock.now()
        val valueAsOf = when (val choice = state.asOf) {
            ValueDateChoice.Now -> now
            is ValueDateChoice.At -> choice.instant
        }
        if (valueAsOf < InvestmentAccount.EARLIEST_VALUE_AS_OF || valueAsOf > now) {
            reduce { copy(dateInvalid = true) }
            return
        }
        reduce { copy(busy = true, failure = null) }
        val epoch = lockEpoch
        viewModelScope.launch {
            val outcome = convert(sourceId, revision, draftId, state.providerId, valueAsOf)
            if (epoch != lockEpoch) return@launch
            onConverted(outcome)
        }
    }

    private fun onConverted(outcome: Outcome<InvestmentConversion, InvestmentConversionFailure>) {
        when (outcome) {
            is Outcome.Success -> reduce {
                copy(busy = false, step = ConvertStep.Result, outcome = outcome.data.toConvertOutcome())
            }

            is Outcome.Failure -> when (val failure = outcome.error) {
                is InvestmentConversionFailure.Storage -> when (failure.error) {
                    StorageError.Locked -> onLocked()
                    else -> reduce { copy(busy = false, failure = failure.error) }
                }

                InvestmentConversionFailure.SourceUnavailable -> reduce {
                    copy(busy = false, step = ConvertStep.Result, outcome = ConvertOutcome.SourceUnavailable)
                }

                InvestmentConversionFailure.SourceChanged -> reduce { copy(busy = false, conflict = true) }

                InvestmentConversionFailure.UnsupportedCurrency,
                InvestmentConversionFailure.AmountOutOfRange,
                -> reduce {
                    copy(busy = false, step = ConvertStep.Result, outcome = ConvertOutcome.Unsupported)
                }

                InvestmentConversionFailure.InvalidValueAsOf -> reduce { copy(busy = false, dateInvalid = true) }
            }
        }
    }

    /** A lock drops the selection, the draft and any running review without asking. */
    private fun onLocked() {
        lockEpoch++
        presetApplied = true
        reduce {
            ConvertUiState(phase = InvestmentPhase.Locked, hidden = hidden, concealed = concealed)
        }
    }
}
