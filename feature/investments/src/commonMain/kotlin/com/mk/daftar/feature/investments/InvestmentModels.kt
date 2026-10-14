package com.mk.daftar.feature.investments

import androidx.navigation3.runtime.NavKey
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.InstitutionId
import com.mk.daftar.core.model.InvestmentAccount
import com.mk.daftar.core.model.InvestmentAccountId
import com.mk.daftar.core.ui.mvi.UiEffect
import com.mk.daftar.core.ui.mvi.UiIntent
import com.mk.daftar.core.ui.mvi.UiState
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** How long a value may stand before the gentle review line appears: strictly more than 24 hours. */
internal val REVIEW_AFTER = 24.hours

/** Where an investment screen stands. [Locked] means no session is open, so nothing is shown and every draft is gone. */
sealed interface InvestmentPhase {
    data object Loading : InvestmentPhase

    data object Ready : InvestmentPhase

    data object Empty : InvestmentPhase

    data object Missing : InvestmentPhase

    data class Failed(val error: StorageError) : InvestmentPhase

    data object Locked : InvestmentPhase
}

/**
 * The reminder beside a value's date. [UpdateDue] is a quiet prompt to review the user's own balance, never a stale
 * market-price warning. [CheckDate] is neutral: a future or rolled-back date that still counts in the total.
 */
enum class ValueDateHint { None, UpdateDue, CheckDate }

/** The hint for a value dated [valueAsOf], judged at [now]. Exactly 24 hours is not yet due. */
internal fun valueDateHintOf(valueAsOf: Instant, now: Instant): ValueDateHint = when {
    valueAsOf > now -> ValueDateHint.CheckDate
    now - valueAsOf > REVIEW_AFTER -> ValueDateHint.UpdateDue
    else -> ValueDateHint.None
}

/** The next instant at which a hint for one of [valueDates] can change after [now], or null when none can. */
internal fun nextValueDateThreshold(valueDates: List<Instant>, now: Instant): Instant? =
    valueDates.flatMap { listOf(it, it + REVIEW_AFTER) }.filter { it > now }.minOrNull()

/** One account as the list and the detail show it. [amount] is the stored EGP value, shown only while not hidden. */
data class InvestmentRow(
    val id: InvestmentAccountId,
    val providerId: InstitutionId?,
    val name: String,
    val amount: Decimal,
    val valueAsOf: Instant,
    val note: String?,
    val hint: ValueDateHint,
)

data class InvestmentListUiState(
    val phase: InvestmentPhase = InvestmentPhase.Loading,
    val rows: List<InvestmentRow> = emptyList(),
    /** The sum of every account, exact before presentation rounding. Zero when there are none. */
    val total: Decimal = Decimal.ZERO,
    val hidden: Boolean = false,
    /** The screen the user asked for while values are hidden. It opens only after an explicit reveal. */
    val pendingRoute: NavKey? = null,
) : UiState

/**
 * The list state for one read at one instant. Accounts keep the repository's order; the total is the exact sum of every
 * stored value, so an old value still counts in full.
 */
internal fun investmentListStateOf(
    read: VaultRead<List<Stored<InvestmentAccount>>>,
    hidden: Boolean,
    now: Instant,
): InvestmentListUiState {
    val accounts = when (read) {
        is VaultRead.Ready -> read.value
        VaultRead.Loading -> return InvestmentListUiState(phase = InvestmentPhase.Loading, hidden = hidden)
        VaultRead.Locked -> return InvestmentListUiState(phase = InvestmentPhase.Locked, hidden = hidden)
        is VaultRead.Failed -> return InvestmentListUiState(phase = InvestmentPhase.Failed(read.error), hidden = hidden)
    }
    val rows = accounts.map { stored -> stored.value.toRow(now) }
    val total = rows.fold(Decimal.ZERO) { sum, row -> sum + row.amount }
    return InvestmentListUiState(
        phase = if (rows.isEmpty()) InvestmentPhase.Empty else InvestmentPhase.Ready,
        rows = rows,
        total = total,
        hidden = hidden,
    )
}

internal fun InvestmentAccount.toRow(now: Instant): InvestmentRow = InvestmentRow(
    id = id,
    providerId = providerId,
    name = name,
    amount = amount.amount,
    valueAsOf = valueAsOf,
    note = note,
    hint = valueDateHintOf(valueAsOf, now),
)

sealed interface InvestmentListIntent : UiIntent {
    data object OpenAdd : InvestmentListIntent

    data class OpenAccount(val investmentId: InvestmentAccountId) : InvestmentListIntent

    data object OpenConvert : InvestmentListIntent

    /** Shows the values, then opens the screen the user asked for while they were hidden. */
    data object RevealAndOpen : InvestmentListIntent

    data object CancelReveal : InvestmentListIntent

    data object ToggleHidden : InvestmentListIntent
}

/** One-shot effects. Navigation is the only effect; the screens pop their own stacks. */
sealed interface InvestmentEffect : UiEffect {
    data class Navigate(val route: NavKey) : InvestmentEffect

    /** A write or deletion is confirmed, so the screen that made it can close. */
    data object Completed : InvestmentEffect
}
