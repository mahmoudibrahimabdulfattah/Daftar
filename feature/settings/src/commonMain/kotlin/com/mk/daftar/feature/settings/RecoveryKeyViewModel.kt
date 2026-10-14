package com.mk.daftar.feature.settings

import androidx.lifecycle.viewModelScope
import com.mk.daftar.core.common.MonotonicTime
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.security.PinEntryBuffer
import com.mk.daftar.core.ui.mvi.MviViewModel
import com.mk.daftar.core.ui.mvi.UiIntent
import com.mk.daftar.core.ui.mvi.UiState
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** The steps of creating a recovery key. */
sealed interface RecoveryKeyStage {
    /** What the key is for, and whether one exists. */
    data object Intro : RecoveryKeyStage

    /** The current PIN, checked with the same penalty as unlock. */
    data object Verify : RecoveryKeyStage

    /** The new key, shown once, to write down. */
    data class Show(val key: String) : RecoveryKeyStage

    /** Typing the key's last group back, so a key that was not written down is noticed now. */
    data class Check(val key: String) : RecoveryKeyStage

    /** The key is saved and active. */
    data object Done : RecoveryKeyStage
}

data class RecoveryKeyUiState(
    val stage: RecoveryKeyStage = RecoveryKeyStage.Intro,
    /** Null until read. */
    val active: Boolean? = null,
    val enteredCount: Int = 0,
    val busy: Boolean = false,
    val failure: SecurityActionFailure? = null,
    val retrySeconds: Int? = null,
    /** The typed last group did not match. */
    val checkMismatch: Boolean = false,
) : UiState {
    val inputEnabled: Boolean
        get() = stage == RecoveryKeyStage.Verify && !busy && retrySeconds == null

    val submitEnabled: Boolean
        get() = inputEnabled && enteredCount in MIN_PIN_DIGITS..MAX_PIN_DIGITS

    private companion object {
        const val MIN_PIN_DIGITS = 6
        const val MAX_PIN_DIGITS = 16
    }
}

sealed interface RecoveryKeyIntent : UiIntent {
    data object Start : RecoveryKeyIntent

    data class Digit(val codePoint: Int) : RecoveryKeyIntent

    data object Erase : RecoveryKeyIntent

    data object Submit : RecoveryKeyIntent

    /** "I've written it down": move on to the check. */
    data object Saved : RecoveryKeyIntent

    /** Back from the check to the key. */
    data object ShowAgain : RecoveryKeyIntent

    data class CheckGroup(val text: String) : RecoveryKeyIntent

    data object PrivacyInvalidated : RecoveryKeyIntent
}

typealias RecoveryKeyEffect = Nothing

/**
 * Creating a recovery key: explain, verify the current PIN, show the new key once, then ask for its last group so a key
 * that was never written down is caught before the owner relies on it. The key is never stored: it lives in this state
 * only while it is on screen, and a privacy event or leaving the screen drops it. A new key replaces the previous one.
 */
class RecoveryKeyViewModel(
    private val gateway: SettingsSecurityGateway,
    private val clock: MonotonicTime,
    private val drafts: SettingsDraftRegistry,
) : MviViewModel<RecoveryKeyUiState, RecoveryKeyIntent, RecoveryKeyEffect>(RecoveryKeyUiState()) {

    private val entry = PinEntryBuffer()
    private var work: Job? = null
    private var countdown: Job? = null
    private val unregister: () -> Unit = drafts.register { onIntent(RecoveryKeyIntent.PrivacyInvalidated) }

    init {
        viewModelScope.launch {
            val active = gateway.recoveryKeyActive()
            reduce { copy(active = active) }
        }
    }

    override fun onIntent(intent: RecoveryKeyIntent) {
        when (intent) {
            RecoveryKeyIntent.Start -> start()
            is RecoveryKeyIntent.Digit -> digit(intent.codePoint)
            RecoveryKeyIntent.Erase -> erase()
            RecoveryKeyIntent.Submit -> verifyAndCreate()
            RecoveryKeyIntent.Saved -> saved()
            RecoveryKeyIntent.ShowAgain -> showAgain()
            is RecoveryKeyIntent.CheckGroup -> check(intent.text)
            RecoveryKeyIntent.PrivacyInvalidated -> invalidate()
        }
    }

    override fun onCleared() {
        unregister()
        work?.cancel()
        countdown?.cancel()
        entry.wipe()
        super.onCleared()
    }

    private fun start() {
        if (currentState.stage != RecoveryKeyStage.Intro) return
        entry.clear()
        reduce { copy(stage = RecoveryKeyStage.Verify, enteredCount = 0, failure = null) }
    }

    private fun digit(codePoint: Int) {
        if (!currentState.inputEnabled || !entry.append(codePoint)) return
        reduce { copy(enteredCount = entry.size, failure = null) }
    }

    private fun erase() {
        if (!currentState.inputEnabled || !entry.erase()) return
        reduce { copy(enteredCount = entry.size) }
    }

    private fun verifyAndCreate() {
        if (!currentState.submitEnabled) return
        val pin = entry.snapshot() ?: return
        entry.clear()
        reduce { copy(busy = true, enteredCount = 0, failure = null) }
        work = viewModelScope.launch {
            val outcome = try {
                gateway.createRecoveryKey(pin)
            } finally {
                pin.wipe()
            }
            coroutineContext.ensureActive()
            when (outcome) {
                is Outcome.Success -> reduce {
                    copy(stage = RecoveryKeyStage.Show(outcome.data), busy = false, active = true)
                }

                is Outcome.Failure -> {
                    reduce { copy(busy = false) }
                    when (val failure = outcome.error) {
                        is SecurityActionFailure.Throttled -> startCountdown(failure.retryAfter)
                        SecurityActionFailure.SessionEnded -> Unit
                        else -> reduce { copy(failure = failure) }
                    }
                }
            }
        }
    }

    private fun saved() {
        val stage = currentState.stage as? RecoveryKeyStage.Show ?: return
        reduce { copy(stage = RecoveryKeyStage.Check(stage.key), checkMismatch = false) }
    }

    private fun showAgain() {
        val stage = currentState.stage as? RecoveryKeyStage.Check ?: return
        reduce { copy(stage = RecoveryKeyStage.Show(stage.key), checkMismatch = false) }
    }

    private fun check(text: String) {
        val stage = currentState.stage as? RecoveryKeyStage.Check ?: return
        val typed = text.filter { it.isLetterOrDigit() }.uppercase()
        val expected = stage.key.substringAfterLast('-')
        when {
            typed.length < expected.length -> reduce { copy(checkMismatch = false) }
            typed == expected -> reduce { copy(stage = RecoveryKeyStage.Done, checkMismatch = false) }
            else -> reduce { copy(checkMismatch = true) }
        }
    }

    /** A privacy event drops the key from the screen. A key already created stays active; a new one can be made. */
    private fun invalidate() {
        work?.cancel()
        entry.clear()
        val keyShown = currentState.stage is RecoveryKeyStage.Show || currentState.stage is RecoveryKeyStage.Check
        reduce {
            copy(
                stage = RecoveryKeyStage.Intro,
                enteredCount = 0,
                busy = false,
                failure = null,
                checkMismatch = false,
                active = if (keyShown) true else active,
            )
        }
    }

    private fun startCountdown(retryAfter: Duration) {
        countdown?.cancel()
        val deadline = clock.now() + retryAfter
        countdown = viewModelScope.launch {
            while (true) {
                val remaining = deadline - clock.now()
                if (remaining <= Duration.ZERO) break
                reduce { copy(retrySeconds = ((remaining.inWholeMilliseconds + 999) / 1000).toInt()) }
                delay(minOf(remaining, 1000.milliseconds))
            }
            reduce { copy(retrySeconds = null) }
        }
    }
}
