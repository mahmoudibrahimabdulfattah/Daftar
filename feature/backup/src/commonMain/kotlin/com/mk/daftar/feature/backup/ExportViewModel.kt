package com.mk.daftar.feature.backup

import androidx.lifecycle.viewModelScope
import com.mk.daftar.core.backup.BackupExportController
import com.mk.daftar.core.backup.BackupExportState
import com.mk.daftar.core.backup.BackupPasswordPolicy
import com.mk.daftar.core.ui.mvi.MviViewModel
import kotlinx.coroutines.launch

/**
 * The export screen. It collects the password, starts one export in the process-scoped controller and mirrors that
 * controller's state. The controller owns the snapshot, the staged file and the picker wait, so a locked vault or a
 * destroyed screen never leaves an export half-finished.
 *
 * The typed password is handed over as bytes and the draft is cleared at once. The controller wipes those bytes.
 */
class ExportViewModel(private val controller: BackupExportController) :
    MviViewModel<ExportUiState, ExportIntent, ExportEffect>(ExportUiState(operation = controller.state.value)) {

    init {
        viewModelScope.launch {
            controller.state.collect { operation -> reduce { copy(operation = operation) } }
        }
    }

    override fun onIntent(intent: ExportIntent) {
        when (intent) {
            ExportIntent.Continue -> reduce { copy(step = ExportStep.PASSWORD) }

            is ExportIntent.PasswordChanged -> reduce { copy(password = intent.text) }

            is ExportIntent.ConfirmChanged -> reduce { copy(confirm = intent.text) }

            ExportIntent.Submit -> submit()

            ExportIntent.Back -> when (currentState.operation) {
                // A finished or failed export is left, and its state is cleared so the next visit starts fresh.
                is BackupExportState.Complete, is BackupExportState.Failed -> {
                    controller.acknowledge()
                    emitEffect(ExportEffect.Close)
                }

                else -> when (currentState.step) {
                    ExportStep.PASSWORD -> reduce { copy(step = ExportStep.INTRO, password = "", confirm = "") }
                    ExportStep.INTRO -> emitEffect(ExportEffect.Close)
                }
            }

            ExportIntent.CancelPreparing -> controller.cancel()

            ExportIntent.Acknowledge -> {
                val finished = currentState.operation
                controller.acknowledge()
                if (finished is BackupExportState.Complete) emitEffect(ExportEffect.Close)
            }
        }
    }

    private fun submit() {
        val state = currentState
        if (!state.canSubmit) {
            reduce { copy(submitAttempted = true) }
            return
        }
        // The controller takes ownership of these bytes and wipes them; the draft is cleared at once.
        controller.start(BackupPasswordPolicy.utf8(state.password))
        reduce { copy(password = "", confirm = "", submitAttempted = false) }
    }
}
