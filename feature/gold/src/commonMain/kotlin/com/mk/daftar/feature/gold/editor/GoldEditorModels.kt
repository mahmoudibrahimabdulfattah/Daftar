package com.mk.daftar.feature.gold.editor

import com.mk.daftar.core.common.NumericInput
import com.mk.daftar.core.common.NumericInputPolicy
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.valuation.SelectedQuote
import com.mk.daftar.core.domain.valuation.SelectedQuotes
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.EntryLimits
import com.mk.daftar.core.model.GoldForm
import com.mk.daftar.core.model.GoldHolding
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.GoldWeight
import com.mk.daftar.core.model.MerchantSide
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.ui.mvi.UiIntent
import com.mk.daftar.core.ui.mvi.UiState
import com.mk.daftar.feature.gold.GoldBusy
import com.mk.daftar.feature.gold.GoldFailure
import com.mk.daftar.feature.gold.GoldPhase

/** What the gold editor opens for: a new holding, or one recorded holding by its ID. */
sealed interface GoldEditorTarget {
    data object New : GoldEditorTarget

    data class Existing(val holdingId: String) : GoldEditorTarget
}

/** The text the user typed. Nothing here is a number until it is normalized. */
data class GoldDraft(
    val karat: GoldKarat = GoldKarat.K21,
    val form: GoldForm = GoldForm.BAR,
    val weightText: String = "",
    val noteText: String = "",
)

/** A draft in its normalized meaning. Two drafts with the same meaning are equal, so dirtiness is a value comparison. */
data class GoldValues(val karat: GoldKarat, val form: GoldForm, val weight: String, val note: String?)

/** The default selection for a new holding: 21K, bars, no weight, no note. */
internal val NEW_GOLD_VALUES: GoldValues = GoldValues(GoldKarat.K21, GoldForm.BAR, "", null)

/**
 * The weight the draft describes, or null while it is not a valid gold weight. Arabic-Indic digits and decimal separators
 * are normalized; signs, exponents and more than two decimals are rejected. The model constructor stays authoritative.
 */
internal fun GoldDraft.weightOrNull(): GoldWeight? {
    val canonical = (NumericInput.normalize(weightText, NumericInputPolicy.USER_ENTRY) as? Outcome.Success)?.data
        ?: return null
    val grams = Decimal.parseCanonicalOrNull(canonical) ?: return null
    return if (EntryLimits.isValidGoldWeight(grams)) GoldWeight(grams) else null
}

internal fun GoldDraft.values(): GoldValues = GoldValues(
    karat = karat,
    form = form,
    weight = when (val normalized = NumericInput.normalize(weightText, NumericInputPolicy.USER_ENTRY)) {
        is Outcome.Success -> normalized.data
        is Outcome.Failure -> weightText.trim()
    },
    note = noteText.trim().ifEmpty { null },
)

internal fun GoldValues.toDraft(): GoldDraft = GoldDraft(karat, form, weight, note.orEmpty())

internal fun GoldHolding.toValues(): GoldValues = GoldValues(
    karat = karat,
    form = form,
    weight = weight.grams.toPlainString(),
    note = note,
)

/** Blank and invalid weights share one message: the weight must be greater than zero with two decimals at most. */
enum class GoldWeightError { Invalid, }

enum class GoldNoteError { TooLong }

data class GoldErrors(val weight: GoldWeightError?, val note: GoldNoteError?) {
    val any: Boolean get() = weight != null || note != null
}

internal fun GoldDraft.errors(): GoldErrors = GoldErrors(
    weight = GoldWeightError.Invalid.takeIf { weightOrNull() == null },
    note = GoldNoteError.TooLong.takeIf { !EntryLimits.isValidNote(noteText) },
)

/**
 * The live value of the draft at its karat's merchant sell price. [quote] is null when no sell quote exists for the karat,
 * which is an unavailable preview, never a reason to block saving. [egp] is null until the weight is valid.
 */
data class GoldPreview(val quote: SelectedQuote?, val egp: Decimal?)

internal fun previewOf(draft: GoldDraft, quotes: SelectedQuotes): GoldPreview {
    val quote = quotes[QuoteKey.Gold(draft.karat, MerchantSide.SELLS_TO_CUSTOMER)]
    val weight = draft.weightOrNull()
    return GoldPreview(quote, if (quote != null && weight != null) weight.grams * quote.value else null)
}

/** The confirmations an editor can raise. Delete and discard use the shared copy; reload follows a conflict. */
enum class GoldConfirmation { Delete, Discard, Reload }

sealed interface GoldEditorIntent : UiIntent {
    data class KaratChanged(val karat: GoldKarat) : GoldEditorIntent

    data class FormChanged(val form: GoldForm) : GoldEditorIntent

    data class WeightChanged(val text: String) : GoldEditorIntent

    data class NoteChanged(val text: String) : GoldEditorIntent

    data object Save : GoldEditorIntent

    data object DismissFailure : GoldEditorIntent

    data object RequestDelete : GoldEditorIntent

    data object ConfirmDelete : GoldEditorIntent

    data object RequestReload : GoldEditorIntent

    data object ConfirmReload : GoldEditorIntent

    data object ConfirmDiscard : GoldEditorIntent

    data object DismissConfirmation : GoldEditorIntent

    data object RevealForm : GoldEditorIntent
}

/**
 * The editor's state. Its raw [draft] is kept as typed; [baseline] holds the normalized values the record had when it was
 * last loaded. [errors], [dirty] and [preview] derive from those, so they never disagree with the draft.
 */
data class GoldEditorUiState(
    val phase: GoldPhase = GoldPhase.Loading,
    val isNew: Boolean = true,
    val draft: GoldDraft = GoldDraft(),
    val baseline: GoldValues? = null,
    /** Errors appear only after the first save attempt, so intermediate typing such as `1.` is never flagged early. */
    val attempted: Boolean = false,
    val conflict: Boolean = false,
    val busy: GoldBusy? = null,
    val failure: GoldFailure? = null,
    val confirmation: GoldConfirmation? = null,
    val concealed: Boolean = false,
    val completed: Boolean = false,
    /** The current selected quotes. Missing ones only make the preview unavailable. */
    val quotes: SelectedQuotes = SelectedQuotes.EMPTY,
    /** True for a new holding while the vault has never saved gold. The supporting hint shows under the heading. */
    val goldHint: Boolean = false,
) : UiState {
    val errors: GoldErrors
        get() = draft.errors()

    val dirty: Boolean
        get() = baseline?.let { draft.values() != it } ?: false

    val preview: GoldPreview
        get() = previewOf(draft, quotes)
}
