package com.mk.daftar.feature.investments

import com.mk.daftar.core.common.NumericInput
import com.mk.daftar.core.common.NumericInputPolicy
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.EntryLimits
import com.mk.daftar.core.model.InstitutionId
import com.mk.daftar.core.model.InvestmentAccount
import com.mk.daftar.core.ui.mvi.UiIntent
import com.mk.daftar.core.ui.mvi.UiState
import kotlin.time.Instant

/** What the editor opens for: a new account, or one recorded account by its ID. */
sealed interface InvestmentEditorTarget {
    data object New : InvestmentEditorTarget

    data class Existing(val investmentId: String) : InvestmentEditorTarget
}

/** The effective time of the amount: the moment of saving, or a date and time the user chose or kept. */
sealed interface ValueDateChoice {
    /** Resolved to the save moment, so a new value is never dated in advance. */
    data object Now : ValueDateChoice

    data class At(val instant: Instant) : ValueDateChoice
}

/**
 * The text the user typed, with the broker choice. Nothing here is a number until it is normalized. A new account starts
 * with no broker, name, value or note, and its value date is Now.
 */
data class InvestmentDraft(
    val providerChosen: Boolean = false,
    val providerId: InstitutionId? = null,
    /** The name the app last suggested for the chosen broker. It is replaced only while the user has not typed another. */
    val suggestedName: String? = null,
    val name: String = "",
    val amountText: String = "",
    val noteText: String = "",
    val asOf: ValueDateChoice = ValueDateChoice.Now,
)

/** A draft in its normalized meaning. Two drafts with the same meaning are equal, so dirtiness is a value comparison. */
data class InvestmentValues(
    val providerId: InstitutionId?,
    val name: String,
    val amount: String,
    val note: String?,
    val asOf: ValueDateChoice,
)

/** The canonical amount text of [text], or the trimmed text while it is not yet a valid number. */
internal fun canonicalAmountText(text: String): String =
    when (val normalized = NumericInput.normalize(text, NumericInputPolicy.USER_ENTRY)) {
        is Outcome.Success -> normalized.data
        is Outcome.Failure -> text.trim()
    }

/** The amount a draft describes, or null while its text is not a valid value within the account bounds. */
internal fun InvestmentDraft.amountValue(): Decimal? {
    val canonical = (NumericInput.normalize(amountText, NumericInputPolicy.USER_ENTRY) as? Outcome.Success)?.data
    return canonical?.let { Decimal.parseCanonicalOrNull(it) }?.takeIf { InvestmentAccount.isValidAmount(it) }
}

internal fun InvestmentDraft.values(): InvestmentValues = InvestmentValues(
    providerId = providerId,
    name = name.trim(),
    amount = canonicalAmountText(amountText),
    note = noteText.trim().ifEmpty { null },
    asOf = asOf,
)

internal fun InvestmentValues.toDraft(): InvestmentDraft = InvestmentDraft(
    providerChosen = true,
    providerId = providerId,
    name = name,
    amountText = amount,
    noteText = note.orEmpty(),
    asOf = asOf,
)

enum class InvestmentNameError { Required, TooLong }

/** The field errors of a draft. The amount error covers both a blank amount and one outside the account bounds. */
data class InvestmentErrors(val name: InvestmentNameError?, val amount: Boolean, val note: Boolean) {
    val any: Boolean get() = name != null || amount || note
}

internal fun InvestmentDraft.errors(): InvestmentErrors {
    val name = when {
        name.isBlank() -> InvestmentNameError.Required
        !EntryLimits.isValidName(name) -> InvestmentNameError.TooLong
        else -> null
    }
    return InvestmentErrors(
        name = name,
        amount = amountValue() == null,
        note = !EntryLimits.isValidNote(noteText),
    )
}

/** The confirmations an editor can raise. */
enum class InvestmentConfirmation { Delete, Discard, Reload }

/** The write an editor is running. While one runs, every other action on that editor is ignored. */
enum class InvestmentBusy { Saving, Deleting }

/** Which write failed. Its draft is kept, so the user can retry or go back to editing. */
enum class InvestmentAction { Save, Delete }

/** A failed write. [StorageError.SessionEnded] is an uncertain outcome: it offers no retry. */
data class InvestmentFailure(val action: InvestmentAction, val error: StorageError) {
    val uncertain: Boolean get() = error == StorageError.SessionEnded
}

sealed interface InvestmentEditorIntent : UiIntent {
    /** The broker choice. [suggestedName] is the chosen broker's name in the app's language, or null for Other. */
    data class ProviderPicked(val providerId: InstitutionId?, val suggestedName: String?) : InvestmentEditorIntent

    data object ReopenPicker : InvestmentEditorIntent

    /** Leaves the picker for an existing account, keeping its form. A new account leaves the screen instead. */
    data object ClosePicker : InvestmentEditorIntent

    data class NameChanged(val text: String) : InvestmentEditorIntent

    data class AmountChanged(val text: String) : InvestmentEditorIntent

    data class NoteChanged(val text: String) : InvestmentEditorIntent

    data object SetToNow : InvestmentEditorIntent

    /** The date and time the user picked in Cairo time, or null when the choice does not exist there. */
    data class DateTimeChosen(val instant: Instant?) : InvestmentEditorIntent

    data object Save : InvestmentEditorIntent

    data object DismissFailure : InvestmentEditorIntent

    data object RequestDelete : InvestmentEditorIntent

    data object ConfirmDelete : InvestmentEditorIntent

    data object RequestReload : InvestmentEditorIntent

    data object ConfirmReload : InvestmentEditorIntent

    data object ConfirmDiscard : InvestmentEditorIntent

    data object DismissConfirmation : InvestmentEditorIntent

    data object RevealForm : InvestmentEditorIntent
}

/**
 * The editor's state. [draft] is kept as typed; [baseline] holds the normalized values the record had when it was last
 * loaded. [picking] shows the broker picker before the form. [errors] and [dirty] derive from the draft.
 */
data class InvestmentEditorUiState(
    val phase: InvestmentPhase = InvestmentPhase.Loading,
    val isNew: Boolean = true,
    val picking: Boolean = true,
    val draft: InvestmentDraft = InvestmentDraft(),
    val baseline: InvestmentValues? = null,
    /** Errors appear only after the first save attempt, so intermediate typing such as `1.` is never flagged early. */
    val attempted: Boolean = false,
    /** A chosen date that is outside 1 January 1900 through now, or does not exist in Cairo time. */
    val dateInvalid: Boolean = false,
    val conflict: Boolean = false,
    val busy: InvestmentBusy? = null,
    val failure: InvestmentFailure? = null,
    val confirmation: InvestmentConfirmation? = null,
    val concealed: Boolean = false,
    val completed: Boolean = false,
) : UiState {
    val errors: InvestmentErrors
        get() = draft.errors()

    val dirty: Boolean
        get() = if (isNew) {
            draft.providerChosen || draft.name.isNotEmpty() || draft.amountText.isNotEmpty() ||
                draft.noteText.isNotEmpty()
        } else {
            baseline?.let { draft.values() != it } ?: false
        }
}
