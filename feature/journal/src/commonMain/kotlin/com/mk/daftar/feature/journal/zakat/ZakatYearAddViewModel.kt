package com.mk.daftar.feature.journal.zakat

import androidx.lifecycle.viewModelScope
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.hijri.HijriCalendar
import com.mk.daftar.core.domain.repository.ZakatTrackingState
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.usecase.AddZakatYearUseCase
import com.mk.daftar.core.domain.usecase.ObserveZakatDayUseCase
import com.mk.daftar.core.domain.usecase.ObserveZakatTrackingUseCase
import com.mk.daftar.core.domain.zakat.ZakatYearRules
import com.mk.daftar.core.ui.mvi.MviViewModel
import com.mk.daftar.core.ui.mvi.UiIntent
import com.mk.daftar.core.ui.mvi.UiState
import com.mk.daftar.feature.journal.JournalEffect
import com.mk.daftar.feature.journal.JournalPhase
import com.mk.daftar.feature.journal.nonReadyPhase
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Why a typed year cannot be added. */
enum class ZakatYearError { Required, OutOfRange, Duplicate }

/**
 * The add-year form. [year] is the parsed Hijri year when the text is a whole number, and [gregorianYear] the year of its
 * Ramadan. [validation] is always current; [shownError] appears only after a save attempt, so typing is never flagged early.
 * [maxYear] is the last year the form accepts.
 */
data class ZakatYearAddUiState(
    val phase: JournalPhase = JournalPhase.Loading,
    val text: String = "",
    val year: Int? = null,
    val gregorianYear: Int? = null,
    val maxYear: Int? = null,
    val validation: ZakatYearError? = null,
    val attempted: Boolean = false,
    val saving: Boolean = false,
    val failed: Boolean = false,
) : UiState {
    val shownError: ZakatYearError? get() = validation.takeIf { attempted }
}

sealed interface ZakatYearAddIntent : UiIntent {
    data class TextChanged(val text: String) : ZakatYearAddIntent

    data object Save : ZakatYearAddIntent
}

class ZakatYearAddViewModel(
    observeTracking: ObserveZakatTrackingUseCase,
    observeDay: ObserveZakatDayUseCase,
    private val addYear: AddZakatYearUseCase,
) : MviViewModel<ZakatYearAddUiState, ZakatYearAddIntent, JournalEffect>(ZakatYearAddUiState()) {

    /** The tracking and day the form validates against, once both have been read. */
    private var basis: Pair<ZakatTrackingState, LocalDate>? = null

    init {
        viewModelScope.launch {
            combine(observeTracking(), observeDay()) { tracking, day -> tracking to day }.collect { (tracking, day) ->
                when (tracking) {
                    is VaultRead.Ready -> {
                        basis = tracking.value to day
                        reduce { revalidated(tracking.value, day).copy(phase = JournalPhase.Ready) }
                    }

                    else -> reduce { copy(phase = tracking.nonReadyPhase() ?: JournalPhase.Loading) }
                }
            }
        }
    }

    override fun onIntent(intent: ZakatYearAddIntent) {
        when (intent) {
            is ZakatYearAddIntent.TextChanged -> reduce { withText(intent.text) }
            ZakatYearAddIntent.Save -> save()
        }
    }

    private fun save() {
        val state = currentState
        if (state.saving || state.phase != JournalPhase.Ready) return
        reduce { copy(attempted = true, failed = false) }
        val year = currentState.year ?: return
        if (currentState.validation != null) return
        reduce { copy(saving = true) }
        viewModelScope.launch {
            when (addYear(year)) {
                is Outcome.Success -> {
                    reduce { copy(saving = false) }
                    emitEffect(JournalEffect.Completed)
                }

                is Outcome.Failure -> reduce { copy(saving = false, failed = true) }
            }
        }
    }

    private fun ZakatYearAddUiState.withText(newText: String): ZakatYearAddUiState {
        val typed = copy(text = newText)
        val (tracking, day) = basis ?: return typed
        return typed.revalidated(tracking, day)
    }

    private fun ZakatYearAddUiState.revalidated(tracking: ZakatTrackingState, today: LocalDate): ZakatYearAddUiState {
        val year = parseHijriYear(text)
        return copy(
            year = year,
            gregorianYear = year?.let { HijriCalendar.ramadanStart(it)?.year },
            maxYear = ZakatYearRules.preparationYear(today, tracking.years),
            validation = validate(text, year, today, tracking),
        )
    }

    private fun validate(text: String, year: Int?, today: LocalDate, tracking: ZakatTrackingState): ZakatYearError? =
        when {
            text.isBlank() -> ZakatYearError.Required
            year == null -> ZakatYearError.OutOfRange
            ZakatYearRules.canAddYear(year, today, tracking.years) -> null
            tracking.years.any { it.hijriYear == year } -> ZakatYearError.Duplicate
            else -> ZakatYearError.OutOfRange
        }
}
