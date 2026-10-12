package com.mk.daftar.feature.settings

import androidx.lifecycle.viewModelScope
import com.mk.daftar.core.common.MonotonicTime
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.PreferencesRead
import com.mk.daftar.core.domain.usecase.ObservePreferencesUseCase
import com.mk.daftar.core.security.PinEntryBuffer
import com.mk.daftar.core.ui.mvi.MviViewModel
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * The Security section: the biometric switch and its PIN prompt. Turning biometrics on asks for the current PIN and then
 * the platform prompt, through the security gateway. Nothing is shown as on until the gateway has committed it, and a
 * cancelled prompt keeps the PIN working with biometrics still off. Turning them off needs no PIN.
 *
 * The PIN prompt holds its digits in [PinEntryBuffer]. Privacy events and leaving the screen wipe them.
 */
class SettingsViewModel(
    observePreferences: ObservePreferencesUseCase,
    private val gateway: SettingsSecurityGateway,
    private val clock: MonotonicTime,
    private val drafts: SettingsDraftRegistry,
) : MviViewModel<SettingsUiState, SettingsIntent, SettingsEffect>(SettingsUiState()) {

    private var entry: PinEntryBuffer? = null
    private var work: Job? = null
    private var countdown: Job? = null
    private var preferenceEnabled: Boolean = false
    private var facts: BiometricFacts? = null
    private val unregister: () -> Unit = drafts.register { onIntent(SettingsIntent.PrivacyInvalidated) }

    init {
        viewModelScope.launch {
            observePreferences().collect { read -> onRead(read) }
        }
        refreshFacts()
    }

    override fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SetBiometricEnabled -> setBiometricEnabled(intent.enabled)
            is SettingsIntent.Digit -> digit(intent.codePoint)
            SettingsIntent.Erase -> erase()
            SettingsIntent.Submit -> submit()
            SettingsIntent.DismissBiometricSheet -> dismissSheet()
            SettingsIntent.Refresh -> refreshFacts()
            SettingsIntent.PrivacyInvalidated -> invalidate()
        }
    }

    override fun onCleared() {
        unregister()
        work?.cancel()
        countdown?.cancel()
        entry?.wipe()
        entry = null
        super.onCleared()
    }

    private fun onRead(read: PreferencesRead) {
        when (read) {
            is PreferencesRead.Ready -> {
                preferenceEnabled = read.preferences.biometricEnabled
                reduce { copy(preferences = read.preferences, unreadable = false) }
            }

            is PreferencesRead.Failed -> {
                preferenceEnabled = false
                reduce { copy(unreadable = true) }
            }
        }
        recompute()
    }

    /** Reads the platform and the stored key again, then shows what they say together with the preference. */
    private fun refreshFacts() {
        viewModelScope.launch {
            facts = gateway.biometricFacts()
            recompute()
        }
    }

    private fun recompute() {
        val current = facts ?: return
        reduce { copy(biometric = biometricSettingsFor(preferenceEnabled, current)) }
    }

    private fun setBiometricEnabled(enabled: Boolean) {
        val state = currentState
        if (state.biometricBusy || state.biometricSheet != null) return
        val settings = state.biometric ?: return
        if (!enabled) {
            if (!settings.enabled) return
            reduce { copy(biometricBusy = true, biometricFailure = null) }
            work = viewModelScope.launch {
                val outcome = gateway.disableBiometrics()
                refreshAfterAction(outcome)
            }
            return
        }
        if (!settings.canEnable) return
        entry?.wipe()
        entry = PinEntryBuffer()
        reduce { copy(biometricSheet = BiometricPinSheet(), biometricFailure = null) }
    }

    private fun digit(codePoint: Int) {
        val sheet = currentState.biometricSheet ?: return
        if (!sheet.inputEnabled) return
        val buffer = entry ?: return
        if (!buffer.append(codePoint)) return
        reduce { copy(biometricSheet = sheet.copy(enteredCount = buffer.size, failure = null)) }
    }

    private fun erase() {
        val sheet = currentState.biometricSheet ?: return
        if (!sheet.inputEnabled) return
        val buffer = entry ?: return
        if (!buffer.erase()) return
        reduce { copy(biometricSheet = sheet.copy(enteredCount = buffer.size)) }
    }

    private fun submit() {
        val state = currentState
        val sheet = state.biometricSheet ?: return
        if (!sheet.submitEnabled) return
        val buffer = entry ?: return
        val pin = buffer.snapshot() ?: return
        reduce { copy(biometricSheet = sheet.copy(busy = true, failure = null)) }
        work = viewModelScope.launch {
            val outcome = try {
                gateway.reenrollBiometrics(pin)
            } finally {
                pin.wipe()
            }
            coroutineContext.ensureActive()
            onEnrolled(outcome)
        }
    }

    private suspend fun onEnrolled(outcome: Outcome<Unit, SecurityActionFailure>) {
        val buffer = entry
        when (outcome) {
            is Outcome.Success -> {
                closeSheet()
                facts = gateway.biometricFacts()
                recompute()
                reduce { copy(biometricBusy = false, biometricFailure = null) }
            }

            is Outcome.Failure -> when (val failure = outcome.error) {
                SecurityActionFailure.WrongPin -> {
                    buffer?.clear()
                    reduce {
                        copy(biometricSheet = biometricSheet?.copy(enteredCount = 0, failure = failure, busy = false))
                    }
                }

                is SecurityActionFailure.Throttled -> {
                    buffer?.clear()
                    reduce {
                        copy(biometricSheet = biometricSheet?.copy(enteredCount = 0, failure = null, busy = false))
                    }
                    startCountdown(failure.retryAfter)
                }

                else -> {
                    closeSheet()
                    refreshAfterAction(outcome)
                }
            }
        }
    }

    /** After a disable, or a finished enable that is not a PIN problem: the platform is read again and the result shown. */
    private suspend fun refreshAfterAction(outcome: Outcome<Unit, SecurityActionFailure>) {
        facts = gateway.biometricFacts()
        recompute()
        val failure = (outcome as? Outcome.Failure)?.error
        reduce { copy(biometricBusy = false, biometricFailure = failure) }
    }

    private fun dismissSheet() {
        if (currentState.biometricSheet?.busy == true) return
        closeSheet()
    }

    private fun closeSheet() {
        countdown?.cancel()
        countdown = null
        entry?.wipe()
        entry = null
        reduce { copy(biometricSheet = null, biometricBusy = false) }
    }

    /**
     * Privacy: drop the digits and the sheet. A prompt that is on screen keeps running, because it deactivates the app;
     * an actual lock ends the session, and the gateway's result then reports that.
     */
    private fun invalidate() {
        entry?.clear()
        val sheet = currentState.biometricSheet ?: return
        if (sheet.busy) {
            reduce { copy(biometricSheet = sheet.copy(enteredCount = 0)) }
            return
        }
        closeSheet()
    }

    private fun startCountdown(retryAfter: Duration) {
        countdown?.cancel()
        val deadline = clock.now() + retryAfter
        countdown = viewModelScope.launch {
            while (true) {
                val remaining = deadline - clock.now()
                if (remaining <= Duration.ZERO) break
                reduce { copy(biometricSheet = biometricSheet?.copy(retrySeconds = ceilSeconds(remaining))) }
                delay(minOf(remaining, ONE_SECOND))
            }
            reduce { copy(biometricSheet = biometricSheet?.copy(retrySeconds = null)) }
        }
    }

    companion object {
        private val ONE_SECOND: Duration = 1000.milliseconds

        /** Whole seconds left, rounded up, so the countdown never shows zero while a penalty still runs. */
        fun ceilSeconds(remaining: Duration): Int = ((remaining.inWholeMilliseconds + 999) / 1000).toInt()
    }
}
