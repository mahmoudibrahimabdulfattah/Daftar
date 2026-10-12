@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mk.daftar.feature.backup

import com.mk.daftar.core.backup.BackupDecryptor
import com.mk.daftar.core.backup.BackupError
import com.mk.daftar.core.backup.BackupPreferences
import com.mk.daftar.core.backup.BackupSnapshot
import com.mk.daftar.core.backup.DecodedBackup
import com.mk.daftar.core.backup.RestoreEngine
import com.mk.daftar.core.backup.RestoreFailure
import com.mk.daftar.core.backup.RestoreFilePicker
import com.mk.daftar.core.backup.StagedCiphertext
import com.mk.daftar.core.backup.StagedRestore
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.domain.repository.InactivityTimeout
import com.mk.daftar.core.domain.repository.ThemeMode
import com.mk.daftar.core.model.Bank
import com.mk.daftar.core.model.BankId
import com.mk.daftar.core.security.SecretBytes
import com.mk.daftar.feature.backup.restore.RestoreDraftRegistry
import com.mk.daftar.feature.backup.restore.RestoreEffect
import com.mk.daftar.feature.backup.restore.RestoreIntent
import com.mk.daftar.feature.backup.restore.RestorePhase
import com.mk.daftar.feature.backup.restore.RestoreProblem
import com.mk.daftar.feature.backup.restore.RestoreViewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

class RestoreViewModelTest {

    private val dispatcher = StandardTestDispatcher(TestCoroutineScheduler())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeCiphertext : StagedCiphertext {
        override val size: Int = 64
        var discards = 0

        override suspend fun readAll(): ByteArray = ByteArray(size)

        override fun discard() {
            discards++
        }
    }

    private class FakePicker(private val result: Outcome<StagedCiphertext?, BackupError>) : RestoreFilePicker {
        override suspend fun pick(): Outcome<StagedCiphertext?, BackupError> = result
    }

    /** Answers the decryption with [result], and records the password each call received. */
    private class FakeDecryptor(private val result: Outcome<DecodedBackup, BackupError>) : BackupDecryptor {
        val passwords = mutableListOf<String>()

        override suspend fun decrypt(file: ByteArray, password: ByteArray): Outcome<DecodedBackup, BackupError> {
            passwords += password.decodeToString()
            return result
        }
    }

    private class FakeEngine : RestoreEngine {
        var stageResult: Outcome<StagedRestore, RestoreFailure> = Outcome.Success(object : StagedRestore {})
        var commitResult: Outcome<Unit, RestoreFailure> = Outcome.Success(Unit)
        var stageGate: CompletableDeferred<Unit>? = null
        var commitGate: CompletableDeferred<Unit>? = null
        var stageCalls = 0
        var commitCalls = 0
        var stageCancelled = false

        override suspend fun stage(snapshot: BackupSnapshot, pin: SecretBytes): Outcome<StagedRestore, RestoreFailure> {
            stageCalls++
            try {
                stageGate?.await()
            } catch (cancelled: CancellationException) {
                stageCancelled = true
                throw cancelled
            }
            return stageResult
        }

        override suspend fun commit(staged: StagedRestore): Outcome<Unit, RestoreFailure> {
            commitCalls++
            commitGate?.await()
            return commitResult
        }

        override suspend fun discard(staged: StagedRestore) = Unit
    }

    private class Harness(
        decrypt: Outcome<DecodedBackup, BackupError>,
        pick: Outcome<StagedCiphertext?, BackupError>,
        decodeDispatcher: CoroutineDispatcher,
    ) {
        val file = FakeCiphertext()
        val decryptor = FakeDecryptor(decrypt)
        val engine = FakeEngine()
        val drafts = RestoreDraftRegistry()
        val vm = RestoreViewModel(
            picker = FakePicker(
                when (pick) {
                    is Outcome.Success -> if (pick.data != null) Outcome.Success(file) else pick
                    is Outcome.Failure -> pick
                },
            ),
            decryptor = decryptor,
            engine = engine,
            drafts = drafts,
            decodeDispatcher = decodeDispatcher,
        )
    }

    private fun backup(banks: Int = 1): DecodedBackup {
        val created = Instant.parse("2026-10-10T09:00:00Z")
        val bank = Bank(BankId("bank-1"), "Bank", created, created)
        return DecodedBackup(
            snapshot = BackupSnapshot(
                preferences = BackupPreferences(ThemeMode.DARK, AppLanguage.EN, InactivityTimeout.SECONDS_300),
                goldHoldings = emptyList(),
                banks = List(banks) { index -> bank.copy(id = BankId("bank-${index + 1}")) },
                bankAccounts = emptyList(),
                cashBalances = emptyList(),
                zakatPayments = emptyList(),
                personalNotes = emptyList(),
                remoteQuotes = emptyList(),
                manualQuotes = emptyList(),
            ),
            exportedAt = Instant.parse("2026-10-12T09:45:00Z"),
            appVersion = "1.0",
        )
    }

    private fun harness(
        decrypt: Outcome<DecodedBackup, BackupError> = Outcome.Success(backup()),
        pick: Outcome<StagedCiphertext?, BackupError> = Outcome.Success(FakeCiphertext()),
    ) = Harness(decrypt, pick, dispatcher)

    private fun TestScope.settle() = advanceUntilIdle()

    private fun RestoreViewModel.phase(): RestorePhase = state.value.phase

    private fun RestoreViewModel.typePassword(text: String) = onIntent(RestoreIntent.PasswordChanged(text))

    private fun RestoreViewModel.typePin(text: String) {
        text.forEach { onIntent(RestoreIntent.Digit(it.code)) }
    }

    /** Runs the wizard to the review of a validated backup. */
    private fun TestScope.reviewReached(h: Harness) {
        h.vm.onIntent(RestoreIntent.ChooseFile)
        settle()
        h.vm.typePassword("correct horse battery")
        h.vm.onIntent(RestoreIntent.SubmitPassword)
        settle()
    }

    /** Runs through the PIN pair to the explicit confirmation. */
    private fun TestScope.confirmationReached(h: Harness, pin: String = "123456") {
        reviewReached(h)
        h.vm.onIntent(RestoreIntent.ContinueToPin)
        h.vm.typePin(pin)
        h.vm.onIntent(RestoreIntent.SubmitPin)
        h.vm.typePin(pin)
        h.vm.onIntent(RestoreIntent.SubmitPin)
        settle()
    }

    @Test
    fun choosingAFileCopiesItAndAsksForThePassword() = runTest(dispatcher) {
        val h = harness()
        h.vm.onIntent(RestoreIntent.ChooseFile)
        settle()
        assertEquals(RestorePhase.Password, h.vm.phase())
        assertEquals(0, h.file.discards)
    }

    @Test
    fun cancellingThePickerChangesNothing() = runTest(dispatcher) {
        val h = harness(pick = Outcome.Success(null))
        h.vm.onIntent(RestoreIntent.ChooseFile)
        settle()
        assertEquals(RestorePhase.ChooseFile, h.vm.phase())
    }

    @Test
    fun aPickerFailureIsFileUnavailable() = runTest(dispatcher) {
        val h = harness(pick = Outcome.Failure(BackupError.WriteFailed))
        h.vm.onIntent(RestoreIntent.ChooseFile)
        settle()
        assertEquals(RestorePhase.Failed(RestoreProblem.FileUnavailable), h.vm.phase())
    }

    @Test
    fun aValidBackupReviewShowsCountsAndDropsTheCopyAndThePassword() = runTest(dispatcher) {
        val h = harness(decrypt = Outcome.Success(backup(banks = 3)))
        reviewReached(h)
        assertEquals(RestorePhase.Review, h.vm.phase())
        assertEquals(1, h.file.discards)
        assertEquals(listOf("correct horse battery"), h.decryptor.passwords)
        assertEquals("", h.vm.state.value.password)
        assertEquals(3, h.vm.state.value.summary?.banks)
    }

    @Test
    fun aWrongPasswordAndACorruptFileShareOneProblem() = runTest(dispatcher) {
        for (error in listOf(BackupError.AuthenticationFailed, BackupError.CorruptPayload, BackupError.NotABackup)) {
            val h = harness(decrypt = Outcome.Failure(error))
            reviewReached(h)
            assertEquals(RestorePhase.Failed(RestoreProblem.Unreadable), h.vm.phase(), "for $error")
            assertEquals(1, h.file.discards)
        }
    }

    @Test
    fun anOversizedFileIsUnreadable() = runTest(dispatcher) {
        val h = harness(decrypt = Outcome.Failure(BackupError.TooLarge))
        reviewReached(h)
        assertEquals(RestorePhase.Failed(RestoreProblem.Unreadable), h.vm.phase())
    }

    @Test
    fun aNewerFormatHasItsOwnProblem() = runTest(dispatcher) {
        val h = harness(decrypt = Outcome.Failure(BackupError.UnsupportedFormat))
        reviewReached(h)
        assertEquals(RestorePhase.Failed(RestoreProblem.NewerFormat), h.vm.phase())
    }

    @Test
    fun aMismatchedConfirmationKeepsTheFirstPinAndAsksAgain() = runTest(dispatcher) {
        val h = harness()
        reviewReached(h)
        h.vm.onIntent(RestoreIntent.ContinueToPin)
        h.vm.typePin("123456")
        h.vm.onIntent(RestoreIntent.SubmitPin)
        h.vm.typePin("654321")
        h.vm.onIntent(RestoreIntent.SubmitPin)
        settle()
        assertEquals(RestorePhase.NewPin(confirming = true), h.vm.phase())
        assertTrue(h.vm.state.value.pinMismatch)
        h.vm.typePin("123456")
        h.vm.onIntent(RestoreIntent.SubmitPin)
        settle()
        assertEquals(RestorePhase.ConfirmReplacement, h.vm.phase())
    }

    @Test
    fun aPinShorterThanSixDigitsCannotBeSubmitted() = runTest(dispatcher) {
        val h = harness()
        reviewReached(h)
        h.vm.onIntent(RestoreIntent.ContinueToPin)
        h.vm.typePin("12345")
        h.vm.onIntent(RestoreIntent.SubmitPin)
        settle()
        assertEquals(RestorePhase.NewPin(confirming = false), h.vm.phase())
    }

    @Test
    fun theReplacementNeedsItsOwnConfirmationAndCancelReturnsToReview() = runTest(dispatcher) {
        val h = harness()
        confirmationReached(h)
        assertEquals(RestorePhase.ConfirmReplacement, h.vm.phase())
        assertEquals(0, h.engine.stageCalls)
        h.vm.onIntent(RestoreIntent.CancelReplacement)
        settle()
        assertEquals(RestorePhase.Review, h.vm.phase())
        assertEquals(0, h.engine.stageCalls)
    }

    @Test
    fun aConfirmedReplacementStagesThenCommitsAndCompletes() = runTest(dispatcher) {
        val h = harness()
        confirmationReached(h)
        h.vm.onIntent(RestoreIntent.ConfirmReplacement)
        settle()
        assertEquals(RestorePhase.Complete, h.vm.phase())
        assertEquals(1, h.engine.stageCalls)
        assertEquals(1, h.engine.commitCalls)
    }

    @Test
    fun aStagingFailureLeavesTheOriginalAndNeverCommits() = runTest(dispatcher) {
        val h = harness()
        h.engine.stageResult = Outcome.Failure(RestoreFailure.Verification)
        confirmationReached(h)
        h.vm.onIntent(RestoreIntent.ConfirmReplacement)
        settle()
        assertEquals(RestorePhase.Failed(RestoreProblem.NotReplaced), h.vm.phase())
        assertEquals(0, h.engine.commitCalls)
    }

    @Test
    fun anUncertainCommitIsReportedAsRecoveryRequired() = runTest(dispatcher) {
        val h = harness()
        h.engine.commitResult = Outcome.Failure(RestoreFailure.RecoveryRequired)
        confirmationReached(h)
        h.vm.onIntent(RestoreIntent.ConfirmReplacement)
        settle()
        assertEquals(RestorePhase.Failed(RestoreProblem.RecoveryRequired), h.vm.phase())
    }

    @Test
    fun cancellingWhilePreparingStopsTheStagingAndNeverCommits() = runTest(dispatcher) {
        val h = harness()
        h.engine.stageGate = CompletableDeferred()
        confirmationReached(h)
        h.vm.onIntent(RestoreIntent.ConfirmReplacement)
        settle()
        assertEquals(RestorePhase.Preparing, h.vm.phase())

        h.vm.onIntent(RestoreIntent.CancelPreparing)
        settle()
        assertEquals(RestorePhase.ChooseFile, h.vm.phase())
        assertTrue(h.engine.stageCancelled)
        assertEquals(0, h.engine.commitCalls)
    }

    @Test
    fun privacyDuringReviewDropsTheDecodedBackupAndReturnsToTheStart() = runTest(dispatcher) {
        val h = harness()
        reviewReached(h)
        h.drafts.invalidateAll()
        settle()
        assertEquals(RestorePhase.ChooseFile, h.vm.phase())
        assertNull(h.vm.state.value.summary)
    }

    @Test
    fun privacyDuringPreparingCancelsAndNeverCommits() = runTest(dispatcher) {
        val h = harness()
        h.engine.stageGate = CompletableDeferred()
        confirmationReached(h)
        h.vm.onIntent(RestoreIntent.ConfirmReplacement)
        settle()
        h.drafts.invalidateAll()
        settle()
        assertEquals(RestorePhase.ChooseFile, h.vm.phase())
        assertTrue(h.engine.stageCancelled)
        assertEquals(0, h.engine.commitCalls)
    }

    @Test
    fun privacyDuringCommitDoesNotInterruptIt() = runTest(dispatcher) {
        val h = harness()
        h.engine.commitGate = CompletableDeferred()
        confirmationReached(h)
        h.vm.onIntent(RestoreIntent.ConfirmReplacement)
        settle()
        assertEquals(RestorePhase.Committing, h.vm.phase())

        h.drafts.invalidateAll()
        settle()
        assertEquals(RestorePhase.Committing, h.vm.phase())

        h.engine.commitGate?.complete(Unit)
        settle()
        assertEquals(RestorePhase.Complete, h.vm.phase())
        assertEquals(1, h.engine.commitCalls)
    }

    @Test
    fun closeIsRefusedWhileTheReplacementIsCommitting() = runTest(dispatcher) {
        val h = harness()
        h.engine.commitGate = CompletableDeferred()
        confirmationReached(h)
        h.vm.onIntent(RestoreIntent.ConfirmReplacement)
        settle()
        h.vm.onIntent(RestoreIntent.Close)
        settle()
        assertEquals(RestorePhase.Committing, h.vm.phase())

        h.engine.commitGate?.complete(Unit)
        settle()
        assertEquals(RestorePhase.Complete, h.vm.phase())
    }

    @Test
    fun backFromTheNewPinGoesToTheReviewAndForgetsTheFirstPin() = runTest(dispatcher) {
        val h = harness()
        reviewReached(h)
        h.vm.onIntent(RestoreIntent.ContinueToPin)
        h.vm.typePin("123456")
        h.vm.onIntent(RestoreIntent.SubmitPin)
        settle()
        h.vm.onIntent(RestoreIntent.Back)
        settle()
        assertEquals(RestorePhase.NewPin(confirming = false), h.vm.phase())
        h.vm.onIntent(RestoreIntent.Back)
        settle()
        assertEquals(RestorePhase.Review, h.vm.phase())
    }

    @Test
    fun closingLeavesTheWizardAndSignalsTheGate() = runTest(dispatcher) {
        val h = harness()
        reviewReached(h)
        h.vm.onIntent(RestoreIntent.Close)
        assertEquals(RestoreEffect.Close, h.vm.effects.first())
    }

    @Test
    fun aFinishedRestoreLeavesWhenAcknowledged() = runTest(dispatcher) {
        val h = harness()
        confirmationReached(h)
        h.vm.onIntent(RestoreIntent.ConfirmReplacement)
        settle()
        h.vm.onIntent(RestoreIntent.Acknowledge)
        assertEquals(RestoreEffect.Close, h.vm.effects.first())
    }

    @Test
    fun theDraftRegistryFollowsTheWizardLifetime() {
        val drafts = RestoreDraftRegistry()
        var invalidated = 0
        val unregister = drafts.register { invalidated++ }
        drafts.invalidateAll()
        assertEquals(1, invalidated)
        unregister()
        drafts.invalidateAll()
        assertEquals(1, invalidated)
    }
}
