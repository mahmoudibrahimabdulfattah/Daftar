package com.mk.daftar.feature.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PinMessageTest {
    @Test
    fun backoffOutranksEveryOtherMessage() {
        val state = AuthUiState(
            phase = AuthPhase.Unlock,
            retrySeconds = 30,
            error = AuthError.IncorrectPin,
            busy = AuthBusy.Unlocking,
            notice = AuthNotice.BiometricsLockedOut,
        )
        assertEquals(PinMessage.Backoff(30), pinMessageFor(state))
    }

    @Test
    fun errorOutranksBusyNoticeAndExplanation() {
        val state = AuthUiState(
            phase = AuthPhase.Confirm,
            error = AuthError.PinMismatch,
            busy = AuthBusy.Creating,
        )
        assertEquals(PinMessage.Failure(AuthError.PinMismatch), pinMessageFor(state))
        val unlock = AuthUiState(
            phase = AuthPhase.Unlock,
            error = AuthError.OpenFailed,
            notice = AuthNotice.BiometricsUnavailable,
        )
        assertEquals(PinMessage.Failure(AuthError.OpenFailed), pinMessageFor(unlock))
    }

    @Test
    fun busyCaptionOutranksNoticeAndExplanation() {
        val state = AuthUiState(
            phase = AuthPhase.Unlock,
            busy = AuthBusy.Unlocking,
            notice = AuthNotice.BiometricsNotEnabled,
        )
        assertEquals(PinMessage.Busy(AuthBusy.Unlocking), pinMessageFor(state))
    }

    @Test
    fun biometricPromptDoesNotReplaceTheLane() {
        val state = AuthUiState(phase = AuthPhase.Unlock, busy = AuthBusy.UnlockingBiometric)
        assertEquals(PinMessage.Explanation(AuthPhase.Unlock), pinMessageFor(state))
    }

    @Test
    fun noticeOutranksExplanationUntilDigitsAreEntered() {
        val idle = AuthUiState(phase = AuthPhase.Unlock, notice = AuthNotice.BiometricsLockedOut)
        assertEquals(PinMessage.Notice(AuthNotice.BiometricsLockedOut), pinMessageFor(idle))
        val typing = idle.copy(enteredCount = 2)
        assertEquals(PinMessage.Explanation(AuthPhase.Unlock), pinMessageFor(typing))
    }

    @Test
    fun explanationShowsTheCurrentPhase() {
        assertEquals(
            PinMessage.Explanation(AuthPhase.Create),
            pinMessageFor(AuthUiState(phase = AuthPhase.Create)),
        )
        assertEquals(
            PinMessage.Explanation(AuthPhase.Confirm),
            pinMessageFor(AuthUiState(phase = AuthPhase.Confirm)),
        )
    }

    @Test
    fun phasesWithoutThePinLaneHaveNoMessage() {
        assertNull(pinMessageFor(AuthUiState(phase = AuthPhase.Biometric)))
        assertNull(pinMessageFor(AuthUiState(phase = AuthPhase.Recovery)))
        assertNull(pinMessageFor(AuthUiState(phase = AuthPhase.Checking)))
    }
}
