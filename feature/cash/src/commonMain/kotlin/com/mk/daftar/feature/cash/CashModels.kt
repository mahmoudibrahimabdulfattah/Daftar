package com.mk.daftar.feature.cash

import androidx.navigation3.runtime.NavKey
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.valuation.AssetRef
import com.mk.daftar.core.domain.valuation.AssetValuation
import com.mk.daftar.core.domain.valuation.PortfolioValuation
import com.mk.daftar.core.domain.valuation.QuoteWarning
import com.mk.daftar.core.model.CashBalance
import com.mk.daftar.core.model.CashBalanceId
import com.mk.daftar.core.model.CashKind
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.ui.mvi.UiEffect
import com.mk.daftar.core.ui.mvi.UiIntent
import com.mk.daftar.core.ui.mvi.UiState

/**
 * Where a cash screen stands. Lists use [Empty]; editors use [Missing] when their record no longer exists. [Locked]
 * means no session is open, so nothing is shown and every draft has already been dropped.
 */
sealed interface CashPhase {
    data object Loading : CashPhase

    data object Ready : CashPhase

    data object Empty : CashPhase

    data object Missing : CashPhase

    data class Failed(val error: StorageError) : CashPhase

    data object Locked : CashPhase
}

/** The write an editor is running. While one runs, every other action on that editor is ignored. */
enum class CashBusy { Saving, Deleting }

/** Which write failed. Its draft is kept, so the user can retry or go back to editing. */
enum class CashAction { Save, Delete }

/** A failed write. [StorageError.SessionEnded] is an uncertain outcome: it offers no retry. */
data class CashFailure(val action: CashAction, val error: StorageError) {
    val uncertain: Boolean get() = error == StorageError.SessionEnded
}

/** One-shot effects of the cash ViewModels: navigation, and completion of an editor's write. */
sealed interface CashEffect : UiEffect {
    data class Navigate(val route: NavKey) : CashEffect

    /** A write has been confirmed, so the editor can close. */
    data object Completed : CashEffect
}

/**
 * One balance as the list shows it. [amount] and [currency] are the stored values. [egp] is the exact EGP valuation, or
 * null while no usable quote exists for it. [warnings] carry the quote's manual or stale state.
 */
data class CashRow(
    val id: CashBalanceId,
    val name: String,
    val kind: CashKind,
    val currency: Currency,
    val amount: Decimal,
    val note: String?,
    val egp: Decimal?,
    val warnings: List<QuoteWarning>,
)

data class CashListUiState(
    val phase: CashPhase = CashPhase.Loading,
    val rows: List<CashRow> = emptyList(),
    /** The complete total in EGP. Present only when every balance is valued. */
    val totalEgp: Decimal? = null,
    /** The sum of the valued balances. Shown as available when [totalEgp] is missing. */
    val availableEgp: Decimal? = null,
    val hidden: Boolean = false,
    /** The editor the user asked for while values are hidden. It opens only after an explicit reveal. */
    val pendingRoute: NavKey? = null,
) : UiState {
    /** True when a balance has no usable quote, so the total cannot be stated. */
    val incomplete: Boolean get() = phase == CashPhase.Ready && totalEgp == null
}

sealed interface CashListIntent : UiIntent {
    data object OpenAdd : CashListIntent

    data class OpenBalance(val balanceId: CashBalanceId) : CashListIntent

    /** Shows the values, then opens the editor the user asked for while they were hidden. */
    data object RevealAndOpen : CashListIntent

    data object CancelReveal : CashListIntent

    data object ToggleHidden : CashListIntent
}

/**
 * The list state for one combination of reads. Balances define the rows and their phase; the valuation only adds EGP
 * figures, joined by typed ID within the same generation. A missing or pending valuation leaves those figures blank
 * rather than hiding the balances.
 */
internal fun cashListStateOf(
    balances: VaultRead<List<Stored<CashBalance>>>,
    valuation: VaultRead<PortfolioValuation>,
    hidden: Boolean,
): CashListUiState {
    val list = when (balances) {
        is VaultRead.Ready -> balances
        VaultRead.Loading -> return CashListUiState(phase = CashPhase.Loading, hidden = hidden)
        VaultRead.Locked -> return CashListUiState(phase = CashPhase.Locked, hidden = hidden)
        is VaultRead.Failed -> return CashListUiState(phase = CashPhase.Failed(balances.error), hidden = hidden)
    }
    if (valuation is VaultRead.Failed) {
        return CashListUiState(phase = CashPhase.Failed(valuation.error), hidden = hidden)
    }
    val priced = (valuation as? VaultRead.Ready)?.takeIf { it.generation == list.generation }?.value
    val valuedById: Map<CashBalanceId, AssetValuation> = priced?.cash?.assets.orEmpty()
        .mapNotNull { entry -> (entry.asset as? AssetRef.Cash)?.let { it.id to entry } }
        .toMap()
    val rows = list.value.map { stored ->
        val balance = stored.value
        val valued = valuedById[balance.id] as? AssetValuation.Valued
        CashRow(
            id = balance.id,
            name = balance.name,
            kind = balance.kind,
            currency = balance.balance.currency,
            amount = balance.balance.amount,
            note = balance.note,
            egp = valued?.exactEgp,
            warnings = valued?.selectedQuote?.warnings.orEmpty(),
        )
    }
    return CashListUiState(
        phase = if (rows.isEmpty()) CashPhase.Empty else CashPhase.Ready,
        rows = rows,
        totalEgp = priced?.cash?.totalEgp,
        availableEgp = priced?.cash?.subtotalEgp,
        hidden = hidden,
    )
}
