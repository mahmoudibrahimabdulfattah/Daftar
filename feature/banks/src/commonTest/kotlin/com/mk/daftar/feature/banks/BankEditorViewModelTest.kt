package com.mk.daftar.feature.banks

import com.mk.daftar.core.domain.session.VaultSessionState
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.usecase.CreateBankUseCase
import com.mk.daftar.core.domain.usecase.DeleteBankUseCase
import com.mk.daftar.core.domain.usecase.ObserveBankDetailUseCase
import com.mk.daftar.core.domain.usecase.ObserveBanksUseCase
import com.mk.daftar.core.domain.usecase.UpdateBankUseCase
import com.mk.daftar.core.navigation.LeaveRequest
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class BankEditorViewModelTest {

    private val dispatcher = StandardTestDispatcher(TestCoroutineScheduler())

    private fun settle() = dispatcher.scheduler.runCurrent()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Rig(
        val repository: FakeBankRepository,
        val session: FakeSession,
        val visibility: ValuesVisibility,
        val model: BankEditorViewModel,
    )

    private fun rig(target: BankEditorTarget): Rig {
        val repository = FakeBankRepository()
        val session = FakeSession()
        val visibility = ValuesVisibility()
        val model = BankEditorViewModel(
            target = target,
            observeBanks = ObserveBanksUseCase(repository),
            observeDetail = ObserveBankDetailUseCase(repository),
            createBank = CreateBankUseCase(repository),
            updateBank = UpdateBankUseCase(repository),
            deleteBank = DeleteBankUseCase(repository),
            visibility = visibility,
        )
        return Rig(repository, session, visibility, model)
    }

    private fun Rig.open() {
        session.set(VaultSessionState.Open(1))
        settle()
    }

    private fun collectEffects(rig: Rig): MutableList<BankEffect> {
        val effects = mutableListOf<BankEffect>()
        CoroutineScope(dispatcher).launch { rig.model.effects.toList(effects) }
        settle()
        return effects
    }

    @Test
    fun aBlankNameIsRefusedWithoutAnyWrite() {
        val rig = rig(BankEditorTarget.New)
        rig.open()

        rig.model.onIntent(BankEditorIntent.NameChanged("   "))
        rig.model.onIntent(BankEditorIntent.Save)
        settle()

        assertEquals(BankNameError.Required, rig.model.state.value.nameError)
        assertTrue(rig.repository.createdBankInputs.isEmpty())
    }

    @Test
    fun aNameLongerThanTheLimitIsRefused() {
        val rig = rig(BankEditorTarget.New)
        rig.open()

        rig.model.onIntent(BankEditorIntent.NameChanged("a".repeat(121)))
        rig.model.onIntent(BankEditorIntent.Save)
        settle()

        assertEquals(BankNameError.TooLong, rig.model.state.value.nameError)
        assertTrue(rig.repository.createdBankInputs.isEmpty())
    }

    @Test
    fun theNameIsSavedTrimmedAndTheEditorClosesOnlyAfterTheWrite() {
        val rig = rig(BankEditorTarget.New)
        rig.open()
        val effects = collectEffects(rig)

        rig.model.onIntent(BankEditorIntent.NameChanged("  بنك مصر  "))
        rig.model.onIntent(BankEditorIntent.Save)
        settle()

        assertEquals(listOf("بنك مصر"), rig.repository.createdBankInputs.map { it.name })
        assertTrue(rig.model.state.value.completed)
        assertEquals(listOf<BankEffect>(BankEffect.Close), effects)
    }

    @Test
    fun aSecondSubmissionWhileTheFirstRunsIsIgnored() {
        val rig = rig(BankEditorTarget.New)
        rig.open()
        val gate = CompletableDeferred<Unit>()
        rig.repository.writeGate = gate
        rig.model.onIntent(BankEditorIntent.NameChanged("CIB"))

        rig.model.onIntent(BankEditorIntent.Save)
        rig.model.onIntent(BankEditorIntent.Save)
        settle()
        assertEquals(BankBusy.Saving, rig.model.state.value.busy)
        gate.complete(Unit)
        settle()

        assertEquals(1, rig.repository.createdBankInputs.size, "one write per confirmed submission")
    }

    @Test
    fun aFailedSaveKeepsTheDraftSoRetryNeedsNoRetyping() {
        val rig = rig(BankEditorTarget.New)
        rig.open()
        rig.repository.bankWriteFailure = StorageError.Io
        rig.model.onIntent(BankEditorIntent.NameChanged("Bank Misr"))

        rig.model.onIntent(BankEditorIntent.Save)
        settle()

        val failed = rig.model.state.value
        assertEquals(BankFailure(BankAction.Save, StorageError.Io), failed.failure)
        assertEquals("Bank Misr", failed.draft.nameText)
        assertFalse(failed.completed)

        rig.repository.bankWriteFailure = null
        rig.model.onIntent(BankEditorIntent.DismissFailure)
        rig.model.onIntent(BankEditorIntent.Save)
        settle()
        assertTrue(rig.model.state.value.completed)
    }

    @Test
    fun aDiskFullFailureIsAFailureWithItsDraftKept() {
        val rig = rig(BankEditorTarget.New)
        rig.open()
        rig.repository.bankWriteFailure = StorageError.DiskFull
        rig.model.onIntent(BankEditorIntent.NameChanged("CIB"))

        rig.model.onIntent(BankEditorIntent.Save)
        settle()

        val failure = requireNotNull(rig.model.state.value.failure)
        assertEquals(StorageError.DiskFull, failure.error)
        assertFalse(failure.uncertain)
    }

    @Test
    fun aSessionEndingDuringSaveIsUncertainAndOffersNoRetry() {
        val rig = rig(BankEditorTarget.New)
        rig.open()
        rig.repository.bankWriteFailure = StorageError.SessionEnded
        rig.model.onIntent(BankEditorIntent.NameChanged("CIB"))

        rig.model.onIntent(BankEditorIntent.Save)
        settle()

        assertTrue(requireNotNull(rig.model.state.value.failure).uncertain)
    }

    @Test
    fun renamingAStoredBankSendsItsLoadedRevision() {
        val rig = rig(BankEditorTarget.Existing("cib"))
        rig.repository.seed(bank("cib", "CIB"), revision = 3)
        rig.open()
        val effects = collectEffects(rig)

        rig.model.onIntent(BankEditorIntent.NameChanged("CIB Egypt"))
        rig.model.onIntent(BankEditorIntent.Save)
        settle()

        assertEquals(listOf(3L), rig.repository.updatedBankRevisions)
        assertEquals(listOf<BankEffect>(BankEffect.Close), effects)
    }

    @Test
    fun aChangeUnderUnsavedEditsIsAConflictThatKeepsTheDraftUntilADiscard() {
        val rig = rig(BankEditorTarget.Existing("cib"))
        rig.repository.seed(bank("cib", "CIB"))
        rig.open()
        rig.model.onIntent(BankEditorIntent.NameChanged("My local edit"))
        settle()

        rig.repository.externalRename("cib", "CIB renamed elsewhere")
        settle()
        assertTrue(rig.model.state.value.conflict)
        assertEquals("My local edit", rig.model.state.value.draft.nameText)

        rig.model.onIntent(BankEditorIntent.Save)
        settle()
        assertTrue(rig.repository.updatedBankRevisions.isEmpty(), "a conflicted editor never writes")

        rig.model.onIntent(BankEditorIntent.RequestReload)
        assertEquals(BankConfirmation.Reload, rig.model.state.value.confirmation)
        rig.model.onIntent(BankEditorIntent.ConfirmReload)
        settle()
        val reloaded = rig.model.state.value
        assertFalse(reloaded.conflict)
        assertEquals("CIB renamed elsewhere", reloaded.draft.nameText)
    }

    @Test
    fun aDeletionTheRepositoryRefusesBecauseAccountsExistShowsTheBlockedDialog() {
        val rig = rig(BankEditorTarget.Existing("bank-misr"))
        seedApprovedFixture(rig.repository)
        rig.open()

        rig.model.onIntent(BankEditorIntent.RequestDelete)
        rig.model.onIntent(BankEditorIntent.ConfirmDelete)
        settle()

        val state = rig.model.state.value
        assertTrue(state.blocked)
        assertEquals("Bank Misr", state.storedName)
        assertEquals(listOf("3,000.00", "18,000.00"), state.dependents.map { it.balanceText })
        assertFalse(state.completed)
    }

    @Test
    fun reviewingTheAccountsOfABlockedBankReturnsToItsDetail() {
        val rig = rig(BankEditorTarget.Existing("bank-misr"))
        seedApprovedFixture(rig.repository)
        rig.open()
        val effects = collectEffects(rig)
        rig.model.onIntent(BankEditorIntent.RequestDelete)
        rig.model.onIntent(BankEditorIntent.ConfirmDelete)
        settle()

        rig.model.onIntent(BankEditorIntent.ReviewAccounts)
        settle()

        assertEquals(listOf<BankEffect>(BankEffect.CloseToBank("bank-misr")), effects)
    }

    @Test
    fun deletingAnEmptyBankClearsItsScreensFromTheStack() {
        val rig = rig(BankEditorTarget.Existing("cib"))
        rig.repository.seed(bank("cib", "CIB"))
        rig.open()
        val effects = collectEffects(rig)

        rig.model.onIntent(BankEditorIntent.RequestDelete)
        rig.model.onIntent(BankEditorIntent.ConfirmDelete)
        settle()

        assertEquals(listOf<BankEffect>(BankEffect.CloseToList), effects)
        assertTrue(rig.repository.bankNames().isEmpty())
    }

    @Test
    fun aDeletionNeedsItsConfirmationFirst() {
        val rig = rig(BankEditorTarget.Existing("cib"))
        rig.repository.seed(bank("cib", "CIB"))
        rig.open()

        rig.model.onIntent(BankEditorIntent.ConfirmDelete)
        settle()

        assertTrue(rig.repository.deletedBankRevisions.isEmpty())
    }

    @Test
    fun leavingWithUnsavedEditsAsksFirstAndDiscardingProceeds() {
        val rig = rig(BankEditorTarget.Existing("cib"))
        rig.repository.seed(bank("cib", "CIB"))
        rig.open()
        rig.model.onIntent(BankEditorIntent.NameChanged("Changed"))
        settle()
        var decided: Boolean? = null

        rig.model.onLeaveRequested { decided = it }
        assertNull(decided, "the leave waits for the user")
        assertEquals(BankConfirmation.Discard, rig.model.state.value.confirmation)

        rig.model.onIntent(BankEditorIntent.ConfirmDiscard)
        settle()
        assertEquals(true, decided)
        assertEquals("CIB", rig.model.state.value.draft.nameText)
    }

    @Test
    fun aCleanEditorLeavesAtOnce() {
        val rig = rig(BankEditorTarget.Existing("cib"))
        rig.repository.seed(bank("cib", "CIB"))
        rig.open()
        var decided: Boolean? = null

        rig.model.onLeaveRequested { decided = it }

        assertEquals(false, decided)
    }

    @Test
    fun aLockDuringASaveDropsTheDraftAndTheLateWriteReportsNothing() {
        val rig = rig(BankEditorTarget.New)
        rig.open()
        val effects = collectEffects(rig)
        val gate = CompletableDeferred<Unit>()
        rig.repository.writeGate = gate
        rig.model.onIntent(BankEditorIntent.NameChanged("CIB"))
        rig.model.onIntent(BankEditorIntent.Save)
        settle()

        rig.session.set(VaultSessionState.Locked)
        rig.repository.setLocked(true)
        settle()
        gate.complete(Unit)
        settle()

        assertEquals(BankPhase.Locked, rig.model.state.value.phase)
        assertEquals("", rig.model.state.value.draft.nameText)
        assertTrue(effects.isEmpty(), "a write that outlived the lock never closes an editor")
    }

    @Test
    fun aBankDeletedElsewhereIsAMissingRecord() {
        val rig = rig(BankEditorTarget.Existing("cib"))
        rig.repository.seed(bank("cib", "CIB"))
        rig.open()

        rig.repository.externalDeleteBank("cib")
        settle()

        assertEquals(BankPhase.Missing, rig.model.state.value.phase)
    }

    @Test
    fun togglingMaskingReachesTheEditorWithoutCompletingAnything() {
        val rig = rig(BankEditorTarget.New)
        rig.open()

        rig.visibility.toggle()
        settle()

        assertTrue(rig.model.state.value.hidden)
        assertFalse(rig.model.state.value.completed)
    }
}
