package com.mk.daftar.feature.gold

import androidx.navigation3.runtime.NavKey
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.ui.mvi.UiEffect
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Where a gold screen stands. Lists use [Empty]; editors use [Missing] when their holding no longer exists. [Locked]
 * means no session is open, so nothing is shown and every draft has already been dropped.
 */
sealed interface GoldPhase {
    data object Loading : GoldPhase

    data object Ready : GoldPhase

    data object Empty : GoldPhase

    data object Missing : GoldPhase

    data class Failed(val error: StorageError) : GoldPhase

    data object Locked : GoldPhase
}

/** The write an editor is running. While one runs, every other action on that editor is ignored. */
enum class GoldBusy { Saving, Deleting }

/** Which write failed. Its draft is kept, so the user can retry or go back to editing. */
enum class GoldAction { Save, Delete }

/** A failed write. [StorageError.SessionEnded] is an uncertain outcome: it offers no retry. */
data class GoldFailure(val action: GoldAction, val error: StorageError) {
    val uncertain: Boolean get() = error == StorageError.SessionEnded
}

/** One-shot effects of the gold ViewModels: navigation, and completion of an editor's write. */
sealed interface GoldEffect : UiEffect {
    data class Navigate(val route: NavKey) : GoldEffect

    /** A write has been confirmed, so the editor can close. */
    data object Completed : GoldEffect
}

/** The phase a read imposes, or null when its value is ready to show. */
internal fun VaultRead<*>.nonReadyPhase(): GoldPhase? = when (this) {
    VaultRead.Locked -> GoldPhase.Locked
    VaultRead.Loading -> GoldPhase.Loading
    is VaultRead.Failed -> GoldPhase.Failed(error)
    is VaultRead.Ready -> null
}

/** Whether gold values are concealed: the shared privacy state, read inverted. */
internal fun ValuesVisibility.hiddenFlow(): Flow<Boolean> = visible.map { !it }

internal fun ValuesVisibility.isHidden(): Boolean = !visible.value

/** Shows the values, the only way out of a concealed state. Showing is always an explicit call. */
internal fun ValuesVisibility.reveal() {
    if (!visible.value) toggle()
}
