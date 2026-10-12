package com.mk.daftar.feature.settings

import androidx.lifecycle.viewModelScope
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.domain.repository.InactivityTimeout
import com.mk.daftar.core.domain.repository.PreferencesRead
import com.mk.daftar.core.domain.repository.ThemeMode
import com.mk.daftar.core.domain.usecase.ObservePreferencesUseCase
import com.mk.daftar.core.domain.usecase.SetInactivityTimeoutUseCase
import com.mk.daftar.core.domain.usecase.SetLanguageUseCase
import com.mk.daftar.core.domain.usecase.SetThemeModeUseCase
import com.mk.daftar.core.ui.mvi.MviViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Theme, language and the inactivity timeout. Every choice is one write, queued and performed strictly in order, so
 * rapid changes cannot commit out of order. The screen shows only what the repository has committed: nothing is shown
 * as applied before it is stored, and a failed write leaves the previous choice in effect.
 */
class PreferencesViewModel(
    observePreferences: ObservePreferencesUseCase,
    private val setThemeMode: SetThemeModeUseCase,
    private val setLanguage: SetLanguageUseCase,
    private val setInactivityTimeout: SetInactivityTimeoutUseCase,
) : MviViewModel<PreferencesUiState, PreferencesIntent, PreferencesEffect>(PreferencesUiState()) {

    private val writes = Channel<PreferenceWrite>(Channel.UNLIMITED)

    /** Writes queued and not yet finished. Touched only on the main thread, where the view model runs. */
    private var pending = 0

    init {
        viewModelScope.launch {
            observePreferences().collect { read -> onRead(read) }
        }
        viewModelScope.launch {
            for (write in writes) perform(write)
        }
    }

    override fun onIntent(intent: PreferencesIntent) {
        when (intent) {
            is PreferencesIntent.OpenSheet -> reduce { copy(sheet = intent.sheet) }
            PreferencesIntent.CloseSheet -> reduce { copy(sheet = null) }
            is PreferencesIntent.SelectTheme -> enqueue(PreferenceWrite.Theme(intent.mode))
            is PreferencesIntent.SelectLanguage -> enqueue(PreferenceWrite.Language(intent.language))
            is PreferencesIntent.SelectTimeout -> enqueue(PreferenceWrite.Timeout(intent.timeout))
            PreferencesIntent.DismissSaveFailure -> reduce { copy(saveFailed = false) }
        }
    }

    override fun onCleared() {
        writes.close()
        super.onCleared()
    }

    private fun onRead(read: PreferencesRead) {
        when (read) {
            is PreferencesRead.Ready -> reduce {
                copy(preferences = read.preferences, unreadable = false)
            }

            is PreferencesRead.Failed -> reduce { copy(unreadable = true) }
        }
    }

    private fun enqueue(write: PreferenceWrite) {
        reduce { copy(sheet = null, saving = true, saveFailed = false) }
        pending++
        writes.trySend(write)
    }

    private suspend fun perform(write: PreferenceWrite) {
        val outcome = when (write) {
            is PreferenceWrite.Theme -> setThemeMode(write.mode)
            is PreferenceWrite.Language -> setLanguage(write.language)
            is PreferenceWrite.Timeout -> setInactivityTimeout(write.timeout)
        }
        pending--
        // The latest finished write decides the message. A failure never changes the committed value shown above.
        reduce { copy(saving = pending > 0, saveFailed = outcome is Outcome.Failure) }
    }
}

/** One queued preference write. Each carries its own value, so a later choice never rewrites an earlier one. */
private sealed interface PreferenceWrite {
    data class Theme(val mode: ThemeMode) : PreferenceWrite

    data class Language(val language: AppLanguage) : PreferenceWrite

    data class Timeout(val timeout: InactivityTimeout) : PreferenceWrite
}
