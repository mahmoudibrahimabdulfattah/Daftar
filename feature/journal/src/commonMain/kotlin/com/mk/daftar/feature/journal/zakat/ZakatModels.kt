package com.mk.daftar.feature.journal.zakat

import androidx.navigation3.runtime.NavKey
import com.mk.daftar.core.common.NumericInput
import com.mk.daftar.core.common.NumericInputPolicy
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.EntryLimits
import com.mk.daftar.core.model.ZakatPayment
import com.mk.daftar.core.model.ZakatPaymentId
import com.mk.daftar.core.ui.mvi.UiIntent
import com.mk.daftar.core.ui.mvi.UiState
import com.mk.daftar.feature.journal.JournalBusy
import com.mk.daftar.feature.journal.JournalFailure
import com.mk.daftar.feature.journal.JournalPhase
import com.mk.daftar.feature.journal.isoText
import com.mk.daftar.feature.journal.parseIsoDate
import kotlinx.datetime.LocalDate

/** One recorded payment as the list shows it. */
data class ZakatPaymentRow(val id: ZakatPaymentId, val paidOn: LocalDate, val amount: Decimal, val note: String?)

/** The payments of one calendar year, newest first, with their exact sum. */
data class ZakatYear(val year: Int, val total: Decimal, val payments: List<ZakatPaymentRow>)

/** Groups payments by calendar year. The repository order is kept, so each year's rows and the years stay in that order. */
internal fun groupByYear(payments: List<ZakatPayment>): List<ZakatYear> =
    payments.groupBy { it.paidOn.year }.map { (year, entries) ->
        ZakatYear(
            year = year,
            total = entries.fold(Decimal.ZERO) { sum, payment -> sum + payment.amount.amount },
            payments = entries.map { ZakatPaymentRow(it.id, it.paidOn, it.amount.amount, it.note) },
        )
    }

/** What the zakat editor opens for: a new payment, or one recorded payment by its ID. */
sealed interface ZakatEditorTarget {
    data object New : ZakatEditorTarget

    data class Existing(val paymentId: String) : ZakatEditorTarget
}

/** The text the user typed. Nothing here is a number until it is normalized. */
data class ZakatDraft(val dateText: String = "", val amountText: String = "", val noteText: String = "")

/** A draft in its normalized meaning. Two drafts with the same meaning are equal, so dirtiness is a value comparison. */
data class ZakatValues(val date: String, val amount: String, val note: String?)

internal fun ZakatDraft.values(): ZakatValues = ZakatValues(
    date = parseIsoDate(dateText)?.isoText() ?: dateText.trim(),
    amount = when (val normalized = NumericInput.normalize(amountText, NumericInputPolicy.USER_ENTRY)) {
        is Outcome.Success -> normalized.data
        is Outcome.Failure -> amountText.trim()
    },
    note = noteText.trim().ifEmpty { null },
)

/** The payment a draft describes, or null while its amount is not a valid payment. */
internal fun ZakatDraft.paymentAmount(): Decimal? {
    val canonical = (NumericInput.normalize(amountText, NumericInputPolicy.USER_ENTRY) as? Outcome.Success)?.data
    val amount = canonical?.let { Decimal.parseCanonicalOrNull(it) } ?: return null
    return amount.takeIf { EntryLimits.isValidPayment(it) }
}

internal fun ZakatValues.toDraft(): ZakatDraft = ZakatDraft(date, amount, note.orEmpty())

enum class ZakatDateError { Required, Invalid, Future }

enum class ZakatAmountError { Required, Invalid }

enum class ZakatNoteError { TooLong }

data class ZakatErrors(val date: ZakatDateError?, val amount: ZakatAmountError?, val note: ZakatNoteError?) {
    val any: Boolean get() = date != null || amount != null || note != null
}

/** Checks a draft against the model's limits. A date in the future is rejected: a paid log never records one. */
internal fun ZakatDraft.errors(today: LocalDate): ZakatErrors {
    val parsed = parseIsoDate(dateText)
    val date = when {
        dateText.isBlank() -> ZakatDateError.Required
        parsed == null -> ZakatDateError.Invalid
        parsed > today -> ZakatDateError.Future
        else -> null
    }
    val amount = when {
        amountText.isBlank() -> ZakatAmountError.Required
        paymentAmount() == null -> ZakatAmountError.Invalid
        else -> null
    }
    val note = ZakatNoteError.TooLong.takeIf { !EntryLimits.isValidNote(noteText) }
    return ZakatErrors(date, amount, note)
}

/** The confirmations an editor can raise. Delete and discard use the shared copy; reload follows a conflict. */
enum class ZakatConfirmation { Delete, Discard, Reload }

sealed interface ZakatListIntent : UiIntent {
    data object OpenAdd : ZakatListIntent

    data class OpenPayment(val paymentId: ZakatPaymentId) : ZakatListIntent

    /** Shows the values, then opens the editor the user asked for while they were hidden. */
    data object RevealAndOpen : ZakatListIntent

    data object CancelReveal : ZakatListIntent

    data object ToggleHidden : ZakatListIntent
}

data class ZakatListUiState(
    val phase: JournalPhase = JournalPhase.Loading,
    val years: List<ZakatYear> = emptyList(),
    val hidden: Boolean = false,
    /** The editor the user asked for while values are hidden. It opens only after an explicit reveal. */
    val pendingRoute: NavKey? = null,
) : UiState

sealed interface ZakatEditorIntent : UiIntent {
    data class DateChanged(val text: String) : ZakatEditorIntent

    data class AmountChanged(val text: String) : ZakatEditorIntent

    data class NoteChanged(val text: String) : ZakatEditorIntent

    data object Save : ZakatEditorIntent

    data object DismissFailure : ZakatEditorIntent

    data object RequestDelete : ZakatEditorIntent

    data object ConfirmDelete : ZakatEditorIntent

    data object RequestReload : ZakatEditorIntent

    data object ConfirmReload : ZakatEditorIntent

    data object ConfirmDiscard : ZakatEditorIntent

    data object DismissConfirmation : ZakatEditorIntent

    data object RevealForm : ZakatEditorIntent
}

/**
 * The editor's state. Its raw [draft] is kept as typed; [baseline] holds the normalized values the record had when it
 * was last loaded. [errors] and [dirty] are derived from those, so they can never disagree with the draft.
 */
data class ZakatEditorUiState(
    val phase: JournalPhase = JournalPhase.Loading,
    val isNew: Boolean = true,
    val draft: ZakatDraft = ZakatDraft(),
    val baseline: ZakatValues? = null,
    val today: LocalDate? = null,
    /** Errors appear only after the first save attempt, so intermediate typing such as `1.` is never flagged early. */
    val attempted: Boolean = false,
    val conflict: Boolean = false,
    val busy: JournalBusy? = null,
    val failure: JournalFailure? = null,
    val confirmation: ZakatConfirmation? = null,
    val concealed: Boolean = false,
    val completed: Boolean = false,
) : UiState {
    val errors: ZakatErrors
        get() = today?.let { draft.errors(it) } ?: ZakatErrors(null, null, null)

    val dirty: Boolean
        get() = baseline?.let { draft.values() != it } ?: false
}
