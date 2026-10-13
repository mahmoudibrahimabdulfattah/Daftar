package com.mk.daftar.feature.home

import androidx.navigation3.runtime.NavKey
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.ui.mvi.UiEffect
import com.mk.daftar.core.ui.mvi.UiIntent
import com.mk.daftar.core.ui.mvi.UiState

/** What the Home screen has to show: nothing yet, a storage failure, or the figures of one generation. */
sealed interface HomeLoad {
    data object Loading : HomeLoad

    /** The records could not be read. This is an error, never an empty or incomplete valuation. */
    data class Failed(val error: StorageError) : HomeLoad

    data class Ready(val figures: HomeFigures) : HomeLoad
}

/**
 * The Home state. [valuesHidden] comes from the session's privacy state. [refreshing] and [refreshFailed] describe the
 * refresh of the generation whose figures are shown; they never change those figures.
 */
data class HomeUiState(
    val load: HomeLoad = HomeLoad.Loading,
    val valuesHidden: Boolean = false,
    val refreshing: Boolean = false,
    val refreshFailed: Boolean = false,
    /** The declared years awaiting confirmation, oldest first. Empty when none is due or tracking is not read. */
    val zakatDue: List<ZakatDueYear> = emptyList(),
) : UiState

/** A year awaiting confirmation: its Hijri key and the Gregorian year of its Ramadan. Never an amount. */
data class ZakatDueYear(val hijriYear: Int, val gregorianYear: Int)

sealed interface HomeIntent : UiIntent {
    data object ToggleValues : HomeIntent

    data object Refresh : HomeIntent

    data class OpenCategory(val category: HomeCategory) : HomeIntent

    data object OpenPrices : HomeIntent

    /** The empty state's action: Assets, where the first record is added. */
    data object AddFirstAsset : HomeIntent

    /** The neutral zakat entry: the calculator. */
    data object OpenZakatCalculator : HomeIntent

    /** The reminder's action: one due year, or the yearly status when several are due. */
    data object OpenZakatReview : HomeIntent
}

sealed interface HomeEffect : UiEffect {
    /** Navigate to a typed route. The screen performs it through the navigator. */
    data class Open(val route: NavKey) : HomeEffect
}

/** The asset categories Home links to. */
enum class HomeCategory { GOLD, BANKS, CASH }
