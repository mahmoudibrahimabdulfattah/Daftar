package com.mk.daftar.feature.banks

import com.mk.daftar.core.domain.session.VaultSessionState
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.usecase.CreateBankAccountUseCase
import com.mk.daftar.core.domain.usecase.DeleteBankAccountUseCase
import com.mk.daftar.core.domain.usecase.ObserveBankAccountUseCase
import com.mk.daftar.core.domain.usecase.ObserveBanksUseCase
import com.mk.daftar.core.domain.usecase.UpdateBankAccountUseCase
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
class AccountEditorViewModelTest {

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
        val model: AccountEditorViewModel,
    )

    private fun rig(target: AccountEditorTarget): Rig {
        val repository = FakeBankRepository()
        val session = FakeSession()
        val visibility = ValuesVisibility()
        val model = AccountEditorViewModel(
            target = target,
            observeAccount = ObserveBankAccountUseCase(repository),
            observeBanks = ObserveBanksUseCase(repository),
            createAccount = CreateBankAccountUseCase(repository),
            updateAccount = UpdateBankAccountUseCase(repository),
            deleteAccount = DeleteBankAccountUseCase(repository),
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

    private fun Rig.fillNew(name: String = "Savings", balance: String, currency: Currency = Currency.EGP) {
        model.onIntent(AccountEditorIntent.NameChanged(name))
        model.onIntent(AccountEditorIntent.CurrencyChanged(currency))
        model.onIntent(AccountEditorIntent.BalanceChanged(balance))
        settle()
    }

    @Test
    fun aNewAccountPreselectsItsBankAndSavesArabicIndicBalancesExactly() {
        val rig = rig(AccountEditorTarget.New("bank-misr"))
        seedApprovedFixture(rig.repository)
        rig.open()
        val effects = collectEffects(rig)
        assertEquals("bank-misr", rig.model.state.value.draft.bankId)

        rig.fillNew(balance = "٣٬٠٠٠٫٥٠")
        rig.model.onIntent(AccountEditorIntent.Save)
        settle()

        val input = rig.repository.createdAccountInputs.single()
        assertEquals("bank-misr", input.bankId.value)
        assertEquals("3000.5", input.balance.amount.toPlainString())
        assertEquals(Currency.EGP, input.balance.currency)
        assertEquals(listOf<BankEffect>(BankEffect.CloseToBank("bank-misr")), effects)
    }

    @Test
    fun zeroIsAValidBalanceAndEmptyIsRequired() {
        val rig = rig(AccountEditorTarget.New("bank-misr"))
        seedApprovedFixture(rig.repository)
        rig.open()

        rig.fillNew(balance = "")
        rig.model.onIntent(AccountEditorIntent.Save)
        settle()
        assertEquals(AccountBalanceError.Required, rig.model.state.value.errors.balance)
        assertTrue(rig.repository.createdAccountInputs.isEmpty())

        rig.model.onIntent(AccountEditorIntent.BalanceChanged("0"))
        rig.model.onIntent(AccountEditorIntent.Save)
        settle()
        assertEquals(Decimal.ZERO, rig.repository.createdAccountInputs.single().balance.amount)
    }

    @Test
    fun signsExponentsExtraDecimalsAndWordsAreRejectedNotRounded() {
        val rig = rig(AccountEditorTarget.New("bank-misr"))
        seedApprovedFixture(rig.repository)
        rig.open()

        listOf("-5", "1.234", "1e3", "abc", "1.").forEach { text ->
            rig.model.onIntent(AccountEditorIntent.BalanceChanged(text))
            rig.model.onIntent(AccountEditorIntent.Save)
            settle()
            assertEquals(AccountBalanceError.Invalid, rig.model.state.value.errors.balance, "for $text")
        }
        assertTrue(rig.repository.createdAccountInputs.isEmpty(), "invalid balances never reach the repository")
    }

    @Test
    fun aBankThatNoLongerExistsCannotReceiveAnAccount() {
        val rig = rig(AccountEditorTarget.New("ghost"))
        seedApprovedFixture(rig.repository)
        rig.open()

        rig.fillNew(balance = "10")
        rig.model.onIntent(AccountEditorIntent.Save)
        settle()

        assertEquals(AccountBankError.Required, rig.model.state.value.errors.bank)
        assertTrue(rig.repository.createdAccountInputs.isEmpty())
    }

    @Test
    fun movingAnAccountSavesItToTheDestinationBankAndReturnsThere() {
        val rig = rig(AccountEditorTarget.Existing("a-alahli"))
        seedApprovedFixture(rig.repository)
        rig.open()
        val effects = collectEffects(rig)

        rig.model.onIntent(AccountEditorIntent.BankChanged("cib"))
        rig.model.onIntent(AccountEditorIntent.Save)
        settle()

        assertEquals(listOf(1L), rig.repository.updatedAccountRevisions)
        assertEquals("cib", rig.repository.accountList().single { it.id.value == "a-alahli" }.bankId.value)
        assertEquals(listOf<BankEffect>(BankEffect.CloseToBank("cib")), effects)
    }

    @Test
    fun changingCurrencyRelabelsTheBalanceAndNeedsAnAcknowledgmentBeforeSaving() {
        val rig = rig(AccountEditorTarget.Existing("a-egp"))
        seedApprovedFixture(rig.repository)
        rig.open()

        rig.model.onIntent(AccountEditorIntent.CurrencyChanged(Currency.USD))
        rig.model.onIntent(AccountEditorIntent.Save)
        settle()
        assertTrue(rig.model.state.value.currencyChanged)
        assertTrue(rig.repository.updatedAccountRevisions.isEmpty(), "no save before the acknowledgment")

        rig.model.onIntent(AccountEditorIntent.AcknowledgeCurrency(true))
        rig.model.onIntent(AccountEditorIntent.Save)
        settle()

        val saved = rig.repository.accountList().single { it.id.value == "a-egp" }
        assertEquals(Currency.USD, saved.balance.currency)
        assertEquals("18000", saved.balance.amount.toPlainString(), "the number is kept; only its currency changes")
    }

    @Test
    fun changingCurrencyAgainAfterAnAcknowledgmentNeedsANewOne() {
        val rig = rig(AccountEditorTarget.Existing("a-egp"))
        seedApprovedFixture(rig.repository)
        rig.open()

        rig.model.onIntent(AccountEditorIntent.CurrencyChanged(Currency.USD))
        rig.model.onIntent(AccountEditorIntent.AcknowledgeCurrency(true))
        rig.model.onIntent(AccountEditorIntent.CurrencyChanged(Currency.EGP))
        rig.model.onIntent(AccountEditorIntent.CurrencyChanged(Currency.USD))
        settle()

        assertFalse(rig.model.state.value.acknowledged)
    }

    @Test
    fun aConflictKeepsTheDraftAndBlocksTheSave() {
        val rig = rig(AccountEditorTarget.Existing("a-cib"))
        seedApprovedFixture(rig.repository)
        rig.open()
        rig.model.onIntent(AccountEditorIntent.NameChanged("My edit"))
        settle()

        rig.repository.accountWriteFailure = StorageError.Conflict
        rig.model.onIntent(AccountEditorIntent.Save)
        settle()

        assertTrue(rig.model.state.value.conflict)
        assertEquals("My edit", rig.model.state.value.draft.name)
    }

    @Test
    fun aDeletedAccountReturnsToItsBank() {
        val rig = rig(AccountEditorTarget.Existing("a-cib"))
        seedApprovedFixture(rig.repository)
        rig.open()
        val effects = collectEffects(rig)

        rig.model.onIntent(AccountEditorIntent.RequestDelete)
        rig.model.onIntent(AccountEditorIntent.ConfirmDelete)
        settle()

        assertEquals(listOf(1L), rig.repository.deletedAccountRevisions)
        assertEquals(listOf<BankEffect>(BankEffect.CloseToBank("cib")), effects)
    }

    @Test
    fun aFailedSaveKeepsTheDraft() {
        val rig = rig(AccountEditorTarget.New("bank-misr"))
        seedApprovedFixture(rig.repository)
        rig.open()
        rig.repository.accountWriteFailure = StorageError.Io
        rig.fillNew(name = "Current", balance = "250.25")

        rig.model.onIntent(AccountEditorIntent.Save)
        settle()

        val state = rig.model.state.value
        assertEquals(BankFailure(BankAction.Save, StorageError.Io), state.failure)
        assertEquals("250.25", state.draft.balanceText)
        assertEquals("Current", state.draft.name)
    }

    @Test
    fun maskingConcealsTheFormSoNothingIsSavedUntilAnExplicitReveal() {
        val rig = rig(AccountEditorTarget.New("bank-misr"))
        seedApprovedFixture(rig.repository)
        rig.open()
        rig.fillNew(balance = "10")

        rig.visibility.toggle()
        settle()
        assertTrue(rig.model.state.value.concealed)
        rig.model.onIntent(AccountEditorIntent.Save)
        settle()
        assertTrue(rig.repository.createdAccountInputs.isEmpty())

        rig.model.onIntent(AccountEditorIntent.RevealForm)
        settle()
        assertFalse(rig.model.state.value.concealed)
        assertFalse(rig.visibility.isHidden())
    }

    @Test
    fun aLockDropsTheDraftWithoutAsking() {
        val rig = rig(AccountEditorTarget.New("bank-misr"))
        seedApprovedFixture(rig.repository)
        rig.open()
        rig.fillNew(balance = "10")

        rig.session.set(VaultSessionState.Locked)
        rig.repository.setLocked(true)
        settle()

        assertEquals(BankPhase.Locked, rig.model.state.value.phase)
        assertEquals(AccountDraft(), rig.model.state.value.draft)
    }

    @Test
    fun anAccountDeletedElsewhereIsAMissingRecord() {
        val rig = rig(AccountEditorTarget.Existing("a-cib"))
        seedApprovedFixture(rig.repository)
        rig.open()

        rig.repository.externalDeleteAccount("a-cib")
        settle()

        assertEquals(BankPhase.Missing, rig.model.state.value.phase)
    }

    @Test
    fun leavingWithUnsavedEditsAsksFirst() {
        val rig = rig(AccountEditorTarget.Existing("a-cib"))
        seedApprovedFixture(rig.repository)
        rig.open()
        rig.model.onIntent(AccountEditorIntent.BalanceChanged("1"))
        settle()
        var decided: Boolean? = null

        rig.model.onLeaveRequested { decided = it }

        assertNull(decided)
        assertEquals(BankConfirmation.Discard, rig.model.state.value.confirmation)
    }
}
