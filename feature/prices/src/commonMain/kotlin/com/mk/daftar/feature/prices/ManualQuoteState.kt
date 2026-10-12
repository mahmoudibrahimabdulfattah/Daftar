package com.mk.daftar.feature.prices

import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.valuation.SelectedQuote
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.ui.mvi.UiEffect
import com.mk.daftar.core.ui.mvi.UiIntent
import com.mk.daftar.core.ui.mvi.UiState
import kotlin.time.Instant

/**
 * The manual value the snapshot holds for the selected quote. [revision] is what a save, unpin or delete must match.
 * [pinned] means it overrides the source now.
 */
data class ManualSlotState(
    val value: Decimal,
    val revision: Long,
    val pinned: Boolean,
    val enteredAt: Instant,
    val note: String?,
)

/**
 * The manual editor. [key] is null while the quote is still to be chosen. [dirty] means the draft differs from what was
 * last loaded. [conflict] means the stored value changed under a dirty draft: the draft stays, and saving waits for a
 * reload. [closed] is set when the vault locks, after which nothing of the draft remains.
 */
data class ManualQuoteUiState(
    val key: QuoteKey? = null,
    val unknownQuote: Boolean = false,
    val choosing: Boolean = false,
    val loaded: Boolean = false,
    val readError: StorageError? = null,
    val draftValue: String = "",
    val draftNote: String = "",
    val validation: ManualValueProblem? = null,
    val dirty: Boolean = false,
    val busy: Boolean = false,
    val conflict: Boolean = false,
    val failure: StorageError? = null,
    val slot: ManualSlotState? = null,
    /** The remote price beneath the manual value, shown for comparison. Null when no remote price exists. */
    val source: SelectedQuote? = null,
    val confirmDelete: Boolean = false,
    val confirmDiscard: Boolean = false,
    val closed: Boolean = false,
) : UiState

sealed interface ManualQuoteIntent : UiIntent {
    data class DraftValueChanged(val text: String) : ManualQuoteIntent

    data class DraftNoteChanged(val text: String) : ManualQuoteIntent

    data object ToggleChoices : ManualQuoteIntent

    data class ChooseQuote(val key: QuoteKey) : ManualQuoteIntent

    /** Saves the draft as the pinned manual value of the chosen quote. */
    data object Save : ManualQuoteIntent

    /** Unpins the manual value. It stays as the last-resort candidate; nothing is deleted. */
    data object ReturnToSource : ManualQuoteIntent

    data object RequestDelete : ManualQuoteIntent

    data object ConfirmDelete : ManualQuoteIntent

    data object DismissDelete : ManualQuoteIntent

    data object RequestBack : ManualQuoteIntent

    data object ConfirmDiscard : ManualQuoteIntent

    data object KeepEditing : ManualQuoteIntent

    /** Loads the stored value after a conflict. The draft is kept for review. */
    data object Reload : ManualQuoteIntent
}

sealed interface ManualQuoteEffect : UiEffect {
    /** The editor is finished: saved, deleted or discarded. The screen returns to the prices screen. */
    data object Close : ManualQuoteEffect
}
