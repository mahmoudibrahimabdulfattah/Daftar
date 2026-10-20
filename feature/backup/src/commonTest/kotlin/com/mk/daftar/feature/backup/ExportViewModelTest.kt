@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mk.daftar.feature.backup

import com.mk.daftar.core.backup.BackupDocuments
import com.mk.daftar.core.backup.BackupEncryptor
import com.mk.daftar.core.backup.BackupError
import com.mk.daftar.core.backup.BackupExportController
import com.mk.daftar.core.backup.BackupExportState
import com.mk.daftar.core.backup.BackupFormat
import com.mk.daftar.core.backup.BackupPasswordProblem
import com.mk.daftar.core.backup.BackupPreferences
import com.mk.daftar.core.backup.BackupSnapshot
import com.mk.daftar.core.backup.BackupSnapshotSource
import com.mk.daftar.core.backup.BackupStaging
import com.mk.daftar.core.backup.PickedDestination
import com.mk.daftar.core.backup.StagedBackup
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.domain.repository.InactivityTimeout
import com.mk.daftar.core.domain.repository.ThemeMode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class ExportViewModelTest {

    private val dispatcher = StandardTestDispatcher(TestCoroutineScheduler())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeStaged : StagedBackup {
        override suspend fun discard() = Unit
    }

    private object FakeDestination : PickedDestination

    private class FakeStaging : BackupStaging {
        var staged = 0

        override suspend fun stage(encrypted: ByteArray): Outcome<StagedBackup, BackupError> {
            staged++
            return Outcome.Success(FakeStaged())
        }
    }

    private class FakeDocuments(
        var choice: Outcome<PickedDestination?, BackupError> = Outcome.Success(FakeDestination),
        var writeGate: CompletableDeferred<Unit>? = null,
        var writeResult: Outcome<Unit, BackupError> = Outcome.Success(Unit),
    ) : BackupDocuments {
        override suspend fun choose(
            staged: StagedBackup,
            suggestedName: String,
        ): Outcome<PickedDestination?, BackupError> = choice

        override suspend fun write(staged: StagedBackup, destination: PickedDestination): Outcome<Unit, BackupError> {
            writeGate?.await()
            return writeResult
        }

        override suspend fun discard(destination: PickedDestination) = Unit
    }

    private class FakeEncryptor(var failure: BackupError? = null) : BackupEncryptor {
        var calls = 0
        var seenPassword: ByteArray? = null

        override suspend fun encode(
            snapshot: BackupSnapshot,
            password: ByteArray,
            exportedAt: Instant,
            appVersion: String,
        ): Outcome<ByteArray, BackupError> {
            calls++
            seenPassword = password.copyOf()
            password.fill(0)
            failure?.let { return Outcome.Failure(it) }
            return Outcome.Success(ByteArray(120))
        }
    }

    private class FakeSource(private val gate: CompletableDeferred<Unit>? = null) : BackupSnapshotSource {
        override suspend fun read(): Outcome<BackupSnapshot, BackupError> {
            gate?.await()
            return Outcome.Success(
                BackupSnapshot(
                    preferences = BackupPreferences(ThemeMode.SYSTEM, AppLanguage.AR, InactivityTimeout.SECONDS_60),
                    goldHoldings = emptyList(),
                    banks = emptyList(),
                    bankAccounts = emptyList(),
                    cashBalances = emptyList(),
                    zakatPayments = emptyList(),
                    personalNotes = emptyList(),
                    remoteQuotes = emptyList(),
                    manualQuotes = emptyList(),
                ),
            )
        }
    }

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.parse("2026-10-12T09:45:00Z")
    }

    private class Harness(
        val viewModel: ExportViewModel,
        val controller: BackupExportController,
        val encryptor: FakeEncryptor,
        val staging: FakeStaging,
        val effects: MutableList<ExportEffect>,
    )

    private fun harness(
        source: BackupSnapshotSource = FakeSource(),
        documents: BackupDocuments = FakeDocuments(),
        encryptor: FakeEncryptor = FakeEncryptor(),
    ): Harness {
        val staging = FakeStaging()
        val controller = BackupExportController(
            source = source,
            encryptor = encryptor,
            staging = staging,
            documents = documents,
            appVersion = "1.0",
            scope = CoroutineScope(SupervisorJob() + dispatcher),
            clock = FixedClock,
            timeZone = TimeZone.UTC,
            workDispatcher = dispatcher,
        )
        val viewModel = ExportViewModel(controller)
        val effects = mutableListOf<ExportEffect>()
        CoroutineScope(dispatcher).launch { viewModel.effects.collect { effects += it } }
        return Harness(viewModel, controller, encryptor, staging, effects)
    }

    private fun settle() = dispatcher.scheduler.advanceUntilIdle()

    private val strong = "correct horse battery"

    private val exportFileName = BackupFormat.suggestedFileName(LocalDate(2026, 10, 12))

    private fun ExportViewModel.enterPassword(password: String, confirm: String = password) {
        onIntent(ExportIntent.Continue)
        onIntent(ExportIntent.PasswordChanged(password))
        onIntent(ExportIntent.ConfirmChanged(confirm))
    }

    @Test
    fun shortPasswordIsNeverStartedAndShowsTheRule() {
        val h = harness()
        h.viewModel.enterPassword("short")
        h.viewModel.onIntent(ExportIntent.Submit)
        settle()

        val state = h.viewModel.state.value
        assertTrue(state.submitAttempted)
        assertEquals(BackupPasswordProblem.TOO_SHORT, state.passwordProblem)
        assertTrue(state.showPasswordError)
        assertEquals(BackupExportState.Idle, h.controller.state.value)
        assertEquals(0, h.encryptor.calls)
    }

    @Test
    fun shortPasswordIsNotFlaggedBeforeASubmitAttempt() {
        val h = harness()
        h.viewModel.enterPassword("short")
        settle()
        assertFalse(h.viewModel.state.value.showPasswordError, "typing is not interrupted by a warning")
    }

    @Test
    fun confirmationMismatchBlocksSubmit() {
        val h = harness()
        h.viewModel.enterPassword(strong, confirm = "correct horse batter")
        settle()
        assertTrue(h.viewModel.state.value.confirmMismatch)
        assertFalse(h.viewModel.state.value.canSubmit)

        h.viewModel.onIntent(ExportIntent.Submit)
        settle()
        assertEquals(BackupExportState.Idle, h.controller.state.value)
        assertEquals(0, h.encryptor.calls)
    }

    @Test
    fun validSubmitHandsOverTheExactBytesClearsTheDraftAndSucceedsAfterTheWrite() {
        val gate = CompletableDeferred<Unit>()
        val h = harness(documents = FakeDocuments(writeGate = gate))
        h.viewModel.enterPassword(strong)
        h.viewModel.onIntent(ExportIntent.Submit)
        settle()

        assertEquals(BackupExportState.Writing, h.viewModel.state.value.operation)
        assertEquals("", h.viewModel.state.value.password, "the draft is cleared once the export has the bytes")
        assertContentEquals(strong.encodeToByteArray(), h.encryptor.seenPassword)
        assertTrue(h.effects.isEmpty())

        gate.complete(Unit)
        settle()
        assertEquals(BackupExportState.Complete("daftar-2026-10-12.dftar"), h.viewModel.state.value.operation)
    }

    @Test
    fun cancelWhilePreparingReturnsToTheEmptyPasswordForm() {
        val gate = CompletableDeferred<Unit>()
        val h = harness(source = FakeSource(gate = gate))
        h.viewModel.enterPassword(strong)
        h.viewModel.onIntent(ExportIntent.Submit)
        settle()
        assertEquals(BackupExportState.Preparing, h.viewModel.state.value.operation)

        h.viewModel.onIntent(ExportIntent.CancelPreparing)
        settle()
        assertEquals(BackupExportState.Idle, h.viewModel.state.value.operation)
        assertEquals(ExportStep.PASSWORD, h.viewModel.state.value.step)
        assertEquals(0, h.encryptor.calls)
        assertEquals(0, h.staging.staged)
    }

    @Test
    fun dismissingThePickerReturnsToThePasswordForm() {
        val h = harness(documents = FakeDocuments(choice = Outcome.Success(null)))
        h.viewModel.enterPassword(strong)
        h.viewModel.onIntent(ExportIntent.Submit)
        settle()
        assertEquals(BackupExportState.Idle, h.viewModel.state.value.operation)
        assertEquals(ExportStep.PASSWORD, h.viewModel.state.value.step)
        assertTrue(h.effects.isEmpty())
    }

    @Test
    fun writeFailureIsShownAndAcknowledgingKeepsTheScreenOpen() {
        val h = harness(documents = FakeDocuments(writeResult = Outcome.Failure(BackupError.WriteFailed)))
        h.viewModel.enterPassword(strong)
        h.viewModel.onIntent(ExportIntent.Submit)
        settle()
        assertEquals(BackupExportState.Failed(BackupError.WriteFailed), h.viewModel.state.value.operation)

        h.viewModel.onIntent(ExportIntent.Acknowledge)
        settle()
        assertEquals(BackupExportState.Idle, h.viewModel.state.value.operation)
        assertTrue(h.effects.isEmpty(), "a failed export never closes the screen by itself")
    }

    @Test
    fun leavingAFailedExportCloses() {
        val h = harness(documents = FakeDocuments(writeResult = Outcome.Failure(BackupError.WriteFailed)))
        h.viewModel.enterPassword(strong)
        h.viewModel.onIntent(ExportIntent.Submit)
        settle()

        h.viewModel.onIntent(ExportIntent.Back)
        settle()
        assertEquals(listOf<ExportEffect>(ExportEffect.Close), h.effects)
        assertEquals(BackupExportState.Idle, h.controller.state.value)
    }

    @Test
    fun acknowledgingASuccessfulExportClosesTheScreen() {
        val h = harness()
        h.viewModel.enterPassword(strong)
        h.viewModel.onIntent(ExportIntent.Submit)
        settle()
        assertTrue(h.viewModel.state.value.operation is BackupExportState.Complete)

        h.viewModel.onIntent(ExportIntent.Acknowledge)
        settle()
        assertEquals(listOf<ExportEffect>(ExportEffect.Close), h.effects)
        assertEquals(BackupExportState.Idle, h.controller.state.value)
    }

    @Test
    fun backFromThePasswordStepReturnsToTheIntroThenLeaves() {
        val h = harness()
        h.viewModel.onIntent(ExportIntent.Continue)
        h.viewModel.onIntent(ExportIntent.Back)
        settle()
        assertEquals(ExportStep.INTRO, h.viewModel.state.value.step)
        assertTrue(h.effects.isEmpty())

        h.viewModel.onIntent(ExportIntent.Back)
        settle()
        assertEquals(listOf<ExportEffect>(ExportEffect.Close), h.effects)
    }

    @Test
    fun aWriteThatFinishesWhileTheVaultIsLockedIsReportedWhenTheScreenIsBackAfterUnlock() {
        val writeGate = CompletableDeferred<Unit>()
        val h = harness(documents = FakeDocuments(writeGate = writeGate))
        h.viewModel.enterPassword(strong)
        h.viewModel.onIntent(ExportIntent.Submit)
        settle()
        assertEquals(BackupExportState.Writing, h.controller.state.value)

        // The screen goes away while the vault is locked. The controller keeps the write running on its own.
        writeGate.complete(Unit)
        settle()
        assertEquals(BackupExportState.Complete(exportFileName), h.controller.state.value)

        // After unlock a fresh screen shows the completion, and the next export is still refused until it is acknowledged.
        val fresh = ExportViewModel(h.controller)
        assertEquals(BackupExportState.Complete(exportFileName), fresh.state.value.operation)
        assertFalse(h.controller.start(strong.encodeToByteArray()))
        fresh.onIntent(ExportIntent.Acknowledge)
        settle()
        assertEquals(BackupExportState.Idle, h.controller.state.value)
    }

    @Test
    fun memoryShortageKeepsTheScreenAndTryAgainReturnsToAnEmptyPasswordStep() {
        val encryptor = FakeEncryptor(failure = BackupError.InsufficientMemory)
        val h = harness(encryptor = encryptor)
        h.viewModel.enterPassword(strong)
        h.viewModel.onIntent(ExportIntent.Submit)
        settle()
        assertEquals(BackupExportState.Failed(BackupError.InsufficientMemory), h.viewModel.state.value.operation)
        assertTrue(h.staging.staged == 0, "nothing was staged, so nothing is written")

        encryptor.failure = null
        h.viewModel.onIntent(ExportIntent.RetryAfterMemory)
        settle()
        assertEquals(BackupExportState.Idle, h.viewModel.state.value.operation)
        assertEquals(ExportStep.PASSWORD, h.viewModel.state.value.step)
        assertEquals("", h.viewModel.state.value.password)
        assertTrue(h.effects.isEmpty())

        h.viewModel.enterPassword(strong)
        h.viewModel.onIntent(ExportIntent.Submit)
        settle()
        assertEquals(2, encryptor.calls)
        assertEquals(BackupExportState.Complete(exportFileName), h.viewModel.state.value.operation)
    }

    @Test
    fun cancellingAMemoryShortageLeavesTheExport() {
        val h = harness(encryptor = FakeEncryptor(failure = BackupError.InsufficientMemory))
        h.viewModel.enterPassword(strong)
        h.viewModel.onIntent(ExportIntent.Submit)
        settle()
        h.viewModel.onIntent(ExportIntent.Back)
        settle()
        assertEquals(BackupExportState.Idle, h.viewModel.state.value.operation)
        assertEquals(listOf<ExportEffect>(ExportEffect.Close), h.effects)
    }
}
