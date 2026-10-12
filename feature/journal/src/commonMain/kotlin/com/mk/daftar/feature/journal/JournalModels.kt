package com.mk.daftar.feature.journal

import androidx.navigation3.runtime.NavKey
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.ui.mvi.UiEffect

/**
 * Where a journal screen stands. Lists use [Empty]; editors use [Missing] when their record no longer exists. [Locked]
 * means no session is open, so nothing is shown and every draft has already been dropped.
 */
sealed interface JournalPhase {
    data object Loading : JournalPhase

    data object Ready : JournalPhase

    data object Empty : JournalPhase

    data object Missing : JournalPhase

    data class Failed(val error: StorageError) : JournalPhase

    data object Locked : JournalPhase
}

/** The write an editor is running. While one runs, every other action on that editor is ignored. */
enum class JournalBusy { Saving, Deleting }

/** Which write failed. Its draft is kept, so the user can retry or go back to editing. */
enum class JournalAction { Save, Delete }

/** A failed write. [StorageError.SessionEnded] is an uncertain outcome: it offers no retry. */
data class JournalFailure(val action: JournalAction, val error: StorageError) {
    val uncertain: Boolean get() = error == StorageError.SessionEnded
}

/** One-shot effects of the journal's ViewModels: navigation, and completion of an editor's write. */
sealed interface JournalEffect : UiEffect {
    data class Navigate(val route: NavKey) : JournalEffect

    /** A write has been confirmed, so the editor can close. */
    data object Completed : JournalEffect
}
