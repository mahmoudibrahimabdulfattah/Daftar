package com.mk.daftar.feature.cash

import com.mk.daftar.core.common.NumericInput
import com.mk.daftar.core.common.NumericInputPolicy
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.model.CashKind
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.EntryLimits
import com.mk.daftar.core.ui.mvi.UiIntent
import com.mk.daftar.core.ui.mvi.UiState

/** What the cash editor opens for: a new balance, or one recorded balance by its ID. */
sealed interface CashEditorTarget {
    data object New : CashEditorTarget

    data class Existing(val balanceId: String) : CashEditorTarget
}

/**
 * The text the user typed, with the kind and currency choices. Nothing here is a number until it is normalized. A new
 * balance starts as cash in EGP with no name, balance or note.
 */
data class CashDraft(
    val name: String = "",
    val kind: CashKind = CashKind.CASH,
    val currency: Currency = Currency.EGP,
    val balanceText: String = "",
    val noteText: String = "",
)

/** A draft in its normalized meaning. Two drafts with the same meaning are equal, so dirtiness is a value comparison. */
data class CashValues(
    val name: String,
    val kind: CashKind,
    val currency: Currency,
    val balance: String,
    val note: String?,
)

/** The balance a draft describes, or null while its text is not a valid zero-or-positive amount. */
internal fun CashDraft.balanceAmount(): Decimal? {
    val canonical = (NumericInput.normalize(balanceText, NumericInputPolicy.USER_ENTRY) as? Outcome.Success)?.data
    return canonical?.let { Decimal.parseCanonicalOrNull(it) }?.takeIf { EntryLimits.isValidBalance(it) }
}

internal fun CashDraft.values(): CashValues = CashValues(
    name = name.trim(),
    kind = kind,
    currency = currency,
    balance = when (val normalized = NumericInput.normalize(balanceText, NumericInputPolicy.USER_ENTRY)) {
        is Outcome.Success -> normalized.data
        is Outcome.Failure -> balanceText.trim()
    },
    note = noteText.trim().ifEmpty { null },
)

internal fun CashValues.toDraft(): CashDraft = CashDraft(
    name = name,
    kind = kind,
    currency = currency,
    balanceText = balance,
    noteText = note.orEmpty(),
)

enum class CashNameError { Required, TooLong }

enum class CashBalanceError { Required, Invalid }

enum class CashNoteError { TooLong }

data class CashErrors(val name: CashNameError?, val balance: CashBalanceError?, val note: CashNoteError?) {
    val any: Boolean get() = name != null || balance != null || note != null
}

/** Checks a draft against the model's limits. Zero is a valid balance; a negative or over-precise one is not. */
internal fun CashDraft.errors(): CashErrors {
    val name = when {
        name.isBlank() -> CashNameError.Required
        !EntryLimits.isValidName(name) -> CashNameError.TooLong
        else -> null
    }
    val balance = when {
        balanceText.isBlank() -> CashBalanceError.Required
        balanceAmount() == null -> CashBalanceError.Invalid
        else -> null
    }
    val note = CashNoteError.TooLong.takeIf { !EntryLimits.isValidNote(noteText) }
    return CashErrors(name, balance, note)
}

/** The confirmations an editor can raise. Delete and discard use the shared copy; reload follows a conflict. */
enum class CashConfirmation { Delete, Discard, Reload }

sealed interface CashEditorIntent : UiIntent {
    data class NameChanged(val text: String) : CashEditorIntent

    data class KindChanged(val kind: CashKind) : CashEditorIntent

    data class CurrencyChanged(val currency: Currency) : CashEditorIntent

    /** The user confirms that a relabelled balance keeps its intended amount. */
    data class CurrencyAcknowledged(val checked: Boolean) : CashEditorIntent

    data class BalanceChanged(val text: String) : CashEditorIntent

    data class NoteChanged(val text: String) : CashEditorIntent

    data object Save : CashEditorIntent

    data object DismissFailure : CashEditorIntent

    data object RequestDelete : CashEditorIntent

    data object ConfirmDelete : CashEditorIntent

    data object RequestReload : CashEditorIntent

    data object ConfirmReload : CashEditorIntent

    data object ConfirmDiscard : CashEditorIntent

    data object DismissConfirmation : CashEditorIntent

    data object RevealForm : CashEditorIntent

    /** Opens the explicit conversion of this saved entry into an investment. Offered only while the entry is clean. */
    data object ConvertToInvestment : CashEditorIntent
}

/**
 * The editor's state. Its raw [draft] is kept as typed; [baseline] holds the normalized values the record had when it
 * was last loaded. [errors] and [dirty] derive from those, so they can never disagree with the draft.
 */
data class CashEditorUiState(
    val phase: CashPhase = CashPhase.Loading,
    val isNew: Boolean = true,
    val draft: CashDraft = CashDraft(),
    val baseline: CashValues? = null,
    /** Errors appear only after the first save attempt, so intermediate typing such as `1.` is never flagged early. */
    val attempted: Boolean = false,
    val conflict: Boolean = false,
    val busy: CashBusy? = null,
    val failure: CashFailure? = null,
    val confirmation: CashConfirmation? = null,
    val concealed: Boolean = false,
    val completed: Boolean = false,
    val currencyAcknowledged: Boolean = false,
) : UiState {
    val errors: CashErrors
        get() = draft.errors()

    val dirty: Boolean
        get() = baseline?.let { draft.values() != it } ?: false

    /** An existing balance relabelled to another currency. Its amount is kept, so the user confirms it first. */
    val currencyChanged: Boolean
        get() = !isNew && baseline?.let { draft.currency != it.currency } == true

    val needsCurrencyAcknowledgement: Boolean
        get() = currencyChanged && !currencyAcknowledged
}
