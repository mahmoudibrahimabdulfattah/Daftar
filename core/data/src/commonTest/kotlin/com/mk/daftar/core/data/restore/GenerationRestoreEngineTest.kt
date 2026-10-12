package com.mk.daftar.core.data.restore

import com.mk.daftar.core.backup.RestoreFailure
import com.mk.daftar.core.backup.StagedRestore
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.security.SecurityError
import com.mk.daftar.core.security.VaultGeneration
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class GenerationRestoreEngineTest {

    private val store = FakeGenerationStore()
    private val staging = FakeStaging()
    private val engine = GenerationRestoreEngine(
        store = store,
        staging = staging,
        lockSession = { store.events += "lock" },
        preferencesPayload = RestorePreferences::encode,
    )
    private val snapshot = emptySnapshot()

    private suspend fun stageOk(): StagedRestore =
        assertIs<Outcome.Success<StagedRestore>>(engine.stage(snapshot, testPin())).data

    @Test
    fun stagingRecordsTheGenerationBeforeItsKeysExistAndKeepsTheOriginal() = runTest {
        val staged = stageOk()
        val generation = staging.built.single()
        assertEquals(generation, store.pointer.staging)
        assertEquals(VaultGeneration.LEGACY, store.pointer.active)
        assertTrue(store.erased.isEmpty())
        assertIs<StagedRestore>(staged)
    }

    @Test
    fun commitMakesTheStagedGenerationActiveRetiresTheOriginalAndInstallsSettings() = runTest {
        val staged = stageOk()
        val generation = staging.built.single()

        assertEquals(Outcome.Success(Unit), engine.commit(staged))

        assertEquals(generation, store.pointer.active)
        assertNull(store.pointer.staging)
        // The commit wrote the retired vault and the settings together; finishing then cleared them.
        val committed = store.history.last()
        assertEquals(generation, committed.active)
        assertEquals(VaultGeneration.LEGACY, committed.previous)
        assertEquals(RestorePreferences.encode(snapshot.preferences), committed.preferences)
        assertEquals(1, store.events.count { it == "finish" })
    }

    @Test
    fun theSessionIsRevokedBeforeTheReplacementIsWritten() = runTest {
        val staged = stageOk()
        store.events.clear()
        engine.commit(staged)
        assertEquals(listOf("lock", "update", "finish"), store.events)
    }

    @Test
    fun aStagingFailureErasesTheGenerationAndLeavesTheOriginalActive() = runTest {
        staging.failure = RestoreFailure.Staging
        assertEquals(RestoreFailure.Staging, assertFailsOrFailure())
        assertEquals(staging.built.single(), store.erased.single())
        assertEquals(VaultGeneration.LEGACY, store.pointer.active)
        assertNull(store.pointer.staging)
    }

    @Test
    fun aVerificationFailureErasesTheGenerationAndLeavesTheOriginalActive() = runTest {
        staging.failure = RestoreFailure.Verification
        assertEquals(RestoreFailure.Verification, assertFailsOrFailure())
        assertEquals(VaultGeneration.LEGACY, store.pointer.active)
        assertEquals(1, store.erased.size)
    }

    @Test
    fun aCancelledStagingStillErasesWhatItBuilt() = runTest {
        staging.cancel = true
        assertFailsWith<CancellationException> { engine.stage(snapshot, testPin()) }
        assertEquals(staging.built.single(), store.erased.single())
        assertNull(store.pointer.staging)
        assertEquals(VaultGeneration.LEGACY, store.pointer.active)
    }

    @Test
    fun aStagingRecordThatCannotBeWrittenStopsBeforeAnyKeyIsMade() = runTest {
        store.writeFault = SecurityError.PersistenceFailure
        assertEquals(RestoreFailure.Staging, assertFailsOrFailure())
        assertTrue(staging.built.isEmpty())
    }

    @Test
    fun aCommitWithAForeignTokenIsNotReplaced() = runTest {
        val foreign = object : StagedRestore {}
        assertEquals(Outcome.Failure(RestoreFailure.NotReplaced), engine.commit(foreign))
        assertEquals(VaultGeneration.LEGACY, store.pointer.active)
    }

    @Test
    fun aCommitWhoseWriteIsRefusedKeepsTheOriginalAndErasesTheStaged() = runTest {
        val staged = stageOk()
        val generation = staging.built.single()
        store.writeFault = SecurityError.PersistenceFailure

        assertEquals(Outcome.Failure(RestoreFailure.NotReplaced), engine.commit(staged))

        assertEquals(VaultGeneration.LEGACY, store.pointer.active)
        assertNull(store.pointer.staging)
        assertEquals(generation, store.erased.last())
        assertTrue(store.events.none { it == "finish" })
    }

    @Test
    fun aCommitWhoseWriteLandedDespiteTheFailureIsReportedAsCommitted() = runTest {
        val staged = stageOk()
        val generation = staging.built.single()
        store.writeFault = SecurityError.PersistenceFailure
        store.landOnFault = true

        assertEquals(Outcome.Success(Unit), engine.commit(staged))

        assertEquals(generation, store.pointer.active)
        assertTrue(store.erased.none { it == generation })
    }

    @Test
    fun anUncertainCommitWithAnUnreadablePointerNeedsRecovery() = runTest {
        val staged = stageOk()
        store.writeFault = SecurityError.PersistenceFailure
        store.landOnFault = true
        store.readsFailAfterFault = SecurityError.PersistenceFailure

        assertEquals(Outcome.Failure(RestoreFailure.RecoveryRequired), engine.commit(staged))
        assertTrue(store.erased.isEmpty(), "an uncertain outcome must not erase anything")
    }

    @Test
    fun aCompletedCommitIsNotUndoneWhenFinishingFailsLater() = runTest {
        val staged = stageOk()
        val generation = staging.built.single()
        assertEquals(Outcome.Success(Unit), engine.commit(staged))
        // The pending work stays on the pointer and is completed at the next start.
        assertEquals(generation, store.pointer.active)
    }

    @Test
    fun discardErasesAStagedGenerationOnly() = runTest {
        val staged = stageOk()
        engine.discard(staged)
        assertEquals(staging.built.single(), store.erased.single())
        assertNull(store.pointer.staging)
        assertEquals(VaultGeneration.LEGACY, store.pointer.active)
    }

    @Test
    fun aSecondCommitOfTheSameTokenIsRefused() = runTest {
        val staged = stageOk()
        assertEquals(Outcome.Success(Unit), engine.commit(staged))
        assertEquals(Outcome.Failure(RestoreFailure.NotReplaced), engine.commit(staged))
    }

    private suspend fun assertFailsOrFailure(): RestoreFailure =
        assertIs<Outcome.Failure<RestoreFailure>>(engine.stage(snapshot, testPin())).error
}
