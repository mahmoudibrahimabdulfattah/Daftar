@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mk.daftar.core.backup

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.storage.StorageError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone

class BackupExportControllerTest {

    private class FakeStaged : StagedBackup {
        var discarded = false

        override suspend fun discard() {
            discarded = true
        }
    }

    private object FakeDestination : PickedDestination

    private class FakeStaging(private val failure: BackupError? = null) : BackupStaging {
        val staged = mutableListOf<FakeStaged>()

        override suspend fun stage(encrypted: ByteArray): Outcome<StagedBackup, BackupError> {
            failure?.let { return Outcome.Failure(it) }
            return FakeStaged().also { staged += it }.let { Outcome.Success(it) }
        }
    }

    private class FakeDocuments(
        var choice: Outcome<PickedDestination?, BackupError> = Outcome.Success(FakeDestination),
        var writeGate: CompletableDeferred<Unit>? = null,
        var writeResult: Outcome<Unit, BackupError> = Outcome.Success(Unit),
    ) : BackupDocuments {
        var suggestedName: String? = null
        var writes = 0
        val discarded = mutableListOf<PickedDestination>()

        override suspend fun choose(
            staged: StagedBackup,
            suggestedName: String,
        ): Outcome<PickedDestination?, BackupError> {
            this.suggestedName = suggestedName
            return choice
        }

        override suspend fun write(staged: StagedBackup, destination: PickedDestination): Outcome<Unit, BackupError> {
            writes++
            writeGate?.await()
            return writeResult
        }

        override suspend fun discard(destination: PickedDestination) {
            discarded += destination
        }
    }

    private class FakeEncryptor(private val result: Outcome<ByteArray, BackupError> = Outcome.Success(ByteArray(120))) :
        BackupEncryptor {
        var calls = 0

        override suspend fun encode(
            snapshot: BackupSnapshot,
            password: ByteArray,
            exportedAt: Instant,
            appVersion: String,
        ): Outcome<ByteArray, BackupError> {
            calls++
            password.fill(0)
            return result
        }
    }

    private class FakeSource(
        private val gate: CompletableDeferred<Unit>? = null,
        private val result: Outcome<BackupSnapshot, BackupError> = Outcome.Success(BackupFixtures.minimalSnapshot()),
    ) : BackupSnapshotSource {
        override suspend fun read(): Outcome<BackupSnapshot, BackupError> {
            gate?.await()
            return result
        }
    }

    private object FixedClock : Clock {
        override fun now(): Instant = BackupFixtures.exportedAt
    }

    private fun TestScope.controller(
        source: BackupSnapshotSource = FakeSource(),
        encryptor: BackupEncryptor = FakeEncryptor(),
        staging: BackupStaging = FakeStaging(),
        documents: BackupDocuments = FakeDocuments(),
        dispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
    ): BackupExportController = BackupExportController(
        source = source,
        encryptor = encryptor,
        staging = staging,
        documents = documents,
        appVersion = "1.0",
        scope = this,
        clock = FixedClock,
        timeZone = TimeZone.UTC,
        workDispatcher = dispatcher,
    )

    private fun password(): ByteArray = "correct horse battery".encodeToByteArray()

    @Test
    fun successIsReportedOnlyAfterTheWriteReturns() = runTest {
        val gate = CompletableDeferred<Unit>()
        val documents = FakeDocuments(writeGate = gate)
        val staging = FakeStaging()
        val controller = controller(staging = staging, documents = documents)

        assertTrue(controller.start(password()))
        runCurrent()
        assertEquals(BackupExportState.Writing, controller.state.value)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(BackupExportState.Complete("daftar-2026-10-12.dftar"), controller.state.value)
        assertEquals("daftar-2026-10-12.dftar", documents.suggestedName)
        assertTrue(staging.staged.single().discarded, "the staged file goes once the export has finished")
        assertTrue(documents.discarded.isEmpty())
    }

    @Test
    fun writeFailureIsReportedAndTheDestinationIsRemoved() = runTest {
        val documents = FakeDocuments(writeResult = Outcome.Failure(BackupError.WriteFailed))
        val staging = FakeStaging()
        val controller = controller(staging = staging, documents = documents)

        controller.start(password())
        advanceUntilIdle()
        assertEquals(BackupExportState.Failed(BackupError.WriteFailed), controller.state.value)
        assertEquals(listOf<PickedDestination>(FakeDestination), documents.discarded)
        assertTrue(staging.staged.single().discarded)
    }

    @Test
    fun cancelWhilePreparingLeavesNothingBehind() = runTest {
        val gate = CompletableDeferred<Unit>()
        val encryptor = FakeEncryptor()
        val staging = FakeStaging()
        val controller = controller(source = FakeSource(gate = gate), encryptor = encryptor, staging = staging)
        val bytes = password()

        controller.start(bytes)
        runCurrent()
        assertEquals(BackupExportState.Preparing, controller.state.value)
        controller.cancel()
        advanceUntilIdle()

        assertEquals(BackupExportState.Idle, controller.state.value)
        assertEquals(0, encryptor.calls)
        assertTrue(staging.staged.isEmpty())
        assertTrue(bytes.all { it == 0.toByte() }, "a cancelled export still wipes the password")
    }

    @Test
    fun dismissingThePickerReturnsToIdleWithoutWriting() = runTest {
        val documents = FakeDocuments(choice = Outcome.Success(null))
        val staging = FakeStaging()
        val controller = controller(staging = staging, documents = documents)

        controller.start(password())
        advanceUntilIdle()
        assertEquals(BackupExportState.Idle, controller.state.value)
        assertEquals(0, documents.writes)
        assertTrue(staging.staged.single().discarded)
    }

    @Test
    fun aSecondExportIsRefusedWhileOneRunsAndWipesItsPassword() = runTest {
        val gate = CompletableDeferred<Unit>()
        val controller = controller(source = FakeSource(gate = gate))
        assertTrue(controller.start(password()))
        runCurrent()

        val second = password()
        assertFalse(controller.start(second))
        assertTrue(second.all { it == 0.toByte() })
        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun acknowledgingAFinishedExportAllowsTheNextOne() = runTest {
        val controller = controller()
        controller.start(password())
        advanceUntilIdle()
        assertTrue(controller.state.value is BackupExportState.Complete)

        assertFalse(controller.start(password()), "a finished export holds its state until it is acknowledged")
        controller.acknowledge()
        assertEquals(BackupExportState.Idle, controller.state.value)
        assertTrue(controller.start(password()))
        advanceUntilIdle()
    }

    @Test
    fun aVaultThatIsNotReadableFailsWithoutStaging() = runTest {
        val staging = FakeStaging()
        val controller = controller(
            source = FakeSource(result = Outcome.Failure(BackupError.Storage(StorageError.Locked))),
            staging = staging,
        )
        controller.start(password())
        advanceUntilIdle()
        assertEquals(BackupExportState.Failed(BackupError.Storage(StorageError.Locked)), controller.state.value)
        assertTrue(staging.staged.isEmpty())
    }

    @Test
    fun anEncryptionFailureIsReportedAndStagesNothing() = runTest {
        val staging = FakeStaging()
        val controller = controller(
            encryptor = FakeEncryptor(Outcome.Failure(BackupError.TooLarge)),
            staging = staging,
        )
        controller.start(password())
        advanceUntilIdle()
        assertEquals(BackupExportState.Failed(BackupError.TooLarge), controller.state.value)
        assertTrue(staging.staged.isEmpty())
    }
}
