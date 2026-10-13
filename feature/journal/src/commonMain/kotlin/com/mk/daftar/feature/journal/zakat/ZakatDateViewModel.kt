package com.mk.daftar.feature.journal.zakat

import androidx.lifecycle.viewModelScope
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.hijri.HijriCalendar
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.usecase.ObserveZakatTrackingUseCase
import com.mk.daftar.core.domain.usecase.SetZakatDateAdjustmentUseCase
import com.mk.daftar.core.ui.mvi.MviViewModel
import com.mk.daftar.core.ui.mvi.UiIntent
import com.mk.daftar.core.ui.mvi.UiState
import com.mk.daftar.feature.journal.JournalEffect
import com.mk.daftar.feature.journal.JournalPhase
import com.mk.daftar.feature.journal.nonReadyPhase
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** One of the three concrete Gregorian options for a year's Ramadan: one day earlier, calculated, or one day later. */
data class ZakatDateOption(val overrideDays: Int, val date: LocalDate)

/**
 * Adjusts one year's Ramadan date. [selected] is the draft choice; nothing changes until [ZakatDateIntent.Save]. The
 * declaration is never touched by a date change.
 */
data class ZakatDateUiState(
    val phase: JournalPhase = JournalPhase.Loading,
    val hijriYear: Int,
    val options: List<ZakatDateOption> = emptyList(),
    val committed: Int = 0,
    val selected: Int = 0,
    val saving: Boolean = false,
    val failed: Boolean = false,
) : UiState {
    /** Whether the draft differs from the saved date. */
    val dirty: Boolean get() = selected != committed
}

sealed interface ZakatDateIntent : UiIntent {
    data class Select(val overrideDays: Int) : ZakatDateIntent

    data object Save : ZakatDateIntent

    /** Returns to the calculated date and saves it at once. */
    data object Restore : ZakatDateIntent
}

class ZakatDateViewModel(
    private val hijriYear: Int,
    observeTracking: ObserveZakatTrackingUseCase,
    private val setAdjustment: SetZakatDateAdjustmentUseCase,
) : MviViewModel<ZakatDateUiState, ZakatDateIntent, JournalEffect>(ZakatDateUiState(hijriYear = hijriYear)) {

    init {
        viewModelScope.launch {
            observeTracking().collect { read ->
                when (read) {
                    is VaultRead.Ready -> {
                        val declared = read.value.years.firstOrNull { it.hijriYear == hijriYear }
                        if (declared == null) {
                            reduce { copy(phase = JournalPhase.Missing) }
                        } else {
                            val saved = declared.overrideDays
                            reduce {
                                copy(
                                    phase = JournalPhase.Ready,
                                    options = optionsOf(hijriYear),
                                    committed = if (saving) committed else saved,
                                    selected = if (saving || dirty) selected else saved,
                                )
                            }
                        }
                    }

                    else -> reduce { copy(phase = read.nonReadyPhase() ?: JournalPhase.Loading) }
                }
            }
        }
    }

    override fun onIntent(intent: ZakatDateIntent) {
        when (intent) {
            is ZakatDateIntent.Select -> reduce { if (saving) this else copy(selected = intent.overrideDays) }
            ZakatDateIntent.Save -> save(currentState.selected)
            ZakatDateIntent.Restore -> save(0)
        }
    }

    private fun save(overrideDays: Int) {
        val state = currentState
        if (state.phase != JournalPhase.Ready || state.saving) return
        reduce { copy(saving = true, failed = false, selected = overrideDays) }
        viewModelScope.launch {
            when (val outcome = setAdjustment(hijriYear, overrideDays)) {
                is Outcome.Success -> {
                    reduce { copy(saving = false, committed = overrideDays) }
                    emitEffect(JournalEffect.Completed)
                }

                is Outcome.Failure -> reduce { copy(saving = false, failed = true, selected = committed) }
            }
        }
    }
}

/** The three options for [hijriYear], earliest first. The calculated date is the middle one. */
internal fun optionsOf(hijriYear: Int): List<ZakatDateOption> = listOf(-1, 0, 1).map { days ->
    ZakatDateOption(days, checkNotNull(HijriCalendar.ramadanStart(hijriYear, days)))
}
