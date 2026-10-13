package com.mk.daftar.feature.banks

import androidx.navigation3.runtime.NavKey
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.valuation.SelectedQuoteWarning
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.ui.mvi.UiEffect
import com.mk.daftar.core.ui.mvi.UiIntent
import com.mk.daftar.core.ui.mvi.UiState

/**
 * Where a bank screen stands. Lists use [Empty] when nothing is recorded yet; detail and editor screens use [Missing]
 * when their record no longer exists. [Locked] means no session is open, so nothing is shown and every draft is gone.
 */
sealed interface BankPhase {
    data object Loading : BankPhase

    data object Ready : BankPhase

    data object Empty : BankPhase

    data object Missing : BankPhase

    data class Failed(val error: StorageError) : BankPhase

    data object Locked : BankPhase
}

/** The write an editor is running. While one runs, every other action on that editor is ignored. */
enum class BankBusy { Saving, Deleting }

/** Which write failed. Its draft is kept, so the user can retry or go back to editing. */
enum class BankAction { Save, Delete }

/** A failed write. [StorageError.SessionEnded] is an uncertain outcome: it offers no retry. */
data class BankFailure(val action: BankAction, val error: StorageError) {
    val uncertain: Boolean get() = error == StorageError.SessionEnded
}

/** The confirmations an editor can raise. Their copy is shared; reload follows a conflict. */
enum class BankConfirmation { Delete, Discard, Reload }

/**
 * One-shot effects of the bank ViewModels. [Navigate] opens a destination the shell owns. The close effects say how the
 * editor leaves the stack: a saved bank returns to its detail, a deleted bank clears every bank screen beneath it, and a
 * saved or moved account returns to the bank it now belongs to.
 */
sealed interface BankEffect : UiEffect {
    data class Navigate(val route: NavKey) : BankEffect

    /** A bank name was saved: the editor closes back to where it was opened. */
    data object Close : BankEffect

    /** The bank was deleted: no screen of that bank may remain on the stack. */
    data object CloseToList : BankEffect

    /** An account was saved, moved or deleted: the editor closes and the bank that now holds it is shown. */
    data class CloseToBank(val bankId: String) : BankEffect
}

/** A bank's total. [amount] is the full EGP value when [complete]; otherwise it is the labeled available subtotal. */
data class BankTotal(val amount: Decimal, val complete: Boolean, val warnings: List<SelectedQuoteWarning>)

/** One bank row of the list. [valueEgp] is null while any of the bank's accounts cannot be valued. */
data class BankRow(
    val id: String,
    val name: String,
    val accountCount: Int,
    val currencies: List<Currency>,
    val valueEgp: Decimal?,
    /** The catalog identity, or null for a custom bank. It only chooses the tile. */
    val institutionId: String? = null,
)

data class BankListUiState(
    val phase: BankPhase = BankPhase.Loading,
    val rows: List<BankRow> = emptyList(),
    val total: BankTotal? = null,
    val hidden: Boolean = false,
) : UiState

sealed interface BankListIntent : UiIntent {
    data class OpenBank(val bankId: String) : BankListIntent

    data object AddBank : BankListIntent

    data object ToggleValues : BankListIntent
}

/**
 * One account inside a bank, as the detail shows it. [balanceText] is the balance in its own currency. [egpText] is the
 * display conversion of a USD balance; it is null for EGP accounts and when a USD quote is missing ([valued] false).
 */
data class AccountRow(
    val id: String,
    val name: String,
    val currency: Currency,
    val balanceText: String,
    val valued: Boolean,
    val egpText: String?,
    val note: String?,
)

data class BankDetailUiState(
    val phase: BankPhase = BankPhase.Loading,
    val bankId: String = "",
    val name: String = "",
    /** The catalog identity of the bank, or null for a custom bank. It only chooses the tile. */
    val institutionId: String? = null,
    val total: BankTotal? = null,
    val accounts: List<AccountRow> = emptyList(),
    val hidden: Boolean = false,
    /** The editor the user asked for while values are hidden. It opens only after an explicit reveal. */
    val pendingRoute: NavKey? = null,
) : UiState

sealed interface BankDetailIntent : UiIntent {
    data class OpenAccount(val accountId: String) : BankDetailIntent

    data object AddAccount : BankDetailIntent

    /** Shows the values, then opens the editor the user asked for while they were hidden. */
    data object RevealAndOpen : BankDetailIntent

    data object CancelReveal : BankDetailIntent

    data object ToggleValues : BankDetailIntent

    data object EditBank : BankDetailIntent
}

/** What a bank editor opens for: a new bank, or one bank by its ID. */
sealed interface BankEditorTarget {
    data object New : BankEditorTarget

    data class Existing(val bankId: String) : BankEditorTarget
}

/** What an account editor opens for: a new account in a bank, or one account by its ID. */
sealed interface AccountEditorTarget {
    data class New(val bankId: String) : AccountEditorTarget

    data class Existing(val accountId: String) : AccountEditorTarget
}

/** The text the bank name field holds. Nothing is trimmed until it is saved or compared. */
data class BankDraft(val nameText: String = "")

/** An account the bank still holds, as the blocked-deletion dialog lists it. */
data class DependentAccount(val name: String, val currency: Currency, val balanceText: String)

sealed interface BankEditorIntent : UiIntent {
    data class NameChanged(val text: String) : BankEditorIntent

    data object Save : BankEditorIntent

    data object DismissFailure : BankEditorIntent

    data object RequestDelete : BankEditorIntent

    data object ConfirmDelete : BankEditorIntent

    data object RequestReload : BankEditorIntent

    data object ConfirmReload : BankEditorIntent

    data object ConfirmDiscard : BankEditorIntent

    data object DismissConfirmation : BankEditorIntent

    /** The blocked-deletion dialog's way back to the bank's detail, where its accounts are listed. */
    data object ReviewAccounts : BankEditorIntent

    data object DismissBlocked : BankEditorIntent

    /** Opens the bank directory, to choose a bank or to change the identity of this one. */
    data object OpenPicker : BankEditorIntent

    data class PickerQueryChanged(val text: String) : BankEditorIntent

    /** A catalog bank. A new bank takes its localized name as an editable draft; an existing one keeps its name. */
    data class PickInstitution(val institutionId: String, val localizedName: String) : BankEditorIntent

    /** A bank outside the directory: a custom identity with a free name. */
    data object PickOther : BankEditorIntent

    /** Back from the directory. A new bank that has not chosen yet leaves the editor instead. */
    data object ClosePicker : BankEditorIntent
}

/**
 * The bank editor's state. [draft] is kept as typed; [baselineName] is the normalized name the record had when it was
 * last loaded, so [dirty] is a value comparison. [blocked] shows the deletion refusal and the accounts that still hold
 * the bank.
 */
data class BankEditorUiState(
    val phase: BankPhase = BankPhase.Loading,
    val isNew: Boolean = true,
    val draft: BankDraft = BankDraft(),
    val baselineName: String? = null,
    val storedName: String = "",
    val attempted: Boolean = false,
    val conflict: Boolean = false,
    val busy: BankBusy? = null,
    val failure: BankFailure? = null,
    val confirmation: BankConfirmation? = null,
    val blocked: Boolean = false,
    val dependents: List<DependentAccount> = emptyList(),
    val hidden: Boolean = false,
    val completed: Boolean = false,
    /** The identity the record holds: a catalog institution ID, or null for a custom or not yet identified bank. */
    val storedInstitutionId: String? = null,
    /** Whether the user confirmed the stored identity. A migration match is not confirmed until chosen again. */
    val storedIdentityReviewed: Boolean = false,
    /** The identity chosen in this edit, or null when it is unchanged. */
    val identityChoice: BankIdentityDraft? = null,
    /** The open directory and its query, or null while the form shows. */
    val picker: BankPickerState? = null,
) : UiState {
    val nameError: BankNameError?
        get() = bankNameError(draft.nameText)

    /** The identity the tile and the save use: the new choice, else the stored one. */
    val institutionId: String?
        get() = if (identityChoice != null) identityChoice.institutionId else storedInstitutionId

    val dirty: Boolean
        get() = identityChoice != null ||
            (baselineName?.let { draft.nameText.trim() != it } ?: draft.nameText.isNotBlank())
}

/** A chosen identity: a catalog institution, or null for a custom bank. */
data class BankIdentityDraft(val institutionId: String?)

/** The directory over the form. Nothing here is saved until the form is. */
data class BankPickerState(val query: String = "")

data class AccountDraft(
    val name: String = "",
    val bankId: String? = null,
    val currency: Currency = Currency.EGP,
    val balanceText: String = "",
    val noteText: String = "",
)

/** A draft in its normalized meaning, so dirtiness is a value comparison. */
data class AccountValues(
    val name: String,
    val bankId: String?,
    val currency: Currency,
    val balance: String,
    val note: String?,
)

/** A bank a draft can be filed under. */
data class BankOption(val id: String, val name: String, val institutionId: String? = null)

sealed interface AccountEditorIntent : UiIntent {
    data class NameChanged(val text: String) : AccountEditorIntent

    data class BankChanged(val bankId: String) : AccountEditorIntent

    data class CurrencyChanged(val currency: Currency) : AccountEditorIntent

    data class BalanceChanged(val text: String) : AccountEditorIntent

    data class NoteChanged(val text: String) : AccountEditorIntent

    /** The explicit acknowledgment that a changed currency leaves the amount as the user intends. */
    data class AcknowledgeCurrency(val checked: Boolean) : AccountEditorIntent

    data object Save : AccountEditorIntent

    data object DismissFailure : AccountEditorIntent

    data object RequestDelete : AccountEditorIntent

    data object ConfirmDelete : AccountEditorIntent

    data object RequestReload : AccountEditorIntent

    data object ConfirmReload : AccountEditorIntent

    data object ConfirmDiscard : AccountEditorIntent

    data object DismissConfirmation : AccountEditorIntent

    data object RevealForm : AccountEditorIntent
}

enum class AccountBankError { Required }

/** Field errors of an account draft. Each is null when that field is acceptable. */
data class AccountErrors(
    val name: AccountNameError?,
    val bank: AccountBankError?,
    val balance: AccountBalanceError?,
    val note: Boolean,
    val acknowledgment: Boolean,
) {
    val any: Boolean get() = name != null || bank != null || balance != null || note || acknowledgment
}

/**
 * The account editor's state. [banks] lists the real banks a draft can be filed under. The currency acknowledgment is
 * required before saving once an existing account's currency has changed, because the balance is relabeled, not converted.
 */
data class AccountEditorUiState(
    val phase: BankPhase = BankPhase.Loading,
    val isNew: Boolean = true,
    val draft: AccountDraft = AccountDraft(),
    val baseline: AccountValues? = null,
    val banks: List<BankOption> = emptyList(),
    val acknowledged: Boolean = false,
    val attempted: Boolean = false,
    val conflict: Boolean = false,
    val busy: BankBusy? = null,
    val failure: BankFailure? = null,
    val confirmation: BankConfirmation? = null,
    val concealed: Boolean = false,
    val completed: Boolean = false,
) : UiState {
    val currencyChanged: Boolean
        get() = baseline?.let { !isNew && draft.currency != it.currency } ?: false

    val errors: AccountErrors
        get() = AccountErrors(
            name = accountNameError(draft.name),
            bank = if (draft.bankId == null ||
                banks.none { it.id == draft.bankId }
            ) {
                AccountBankError.Required
            } else {
                null
            },
            balance = balanceError(draft.balanceText),
            note = noteTooLong(draft.noteText),
            acknowledgment = currencyChanged && !acknowledged,
        )

    val dirty: Boolean
        get() = values() != baseline

    fun values(): AccountValues = AccountValues(
        name = draft.name.trim(),
        bankId = draft.bankId,
        currency = draft.currency,
        balance = normalizedBalanceText(draft.balanceText),
        note = normalizedNote(draft.noteText),
    )
}
