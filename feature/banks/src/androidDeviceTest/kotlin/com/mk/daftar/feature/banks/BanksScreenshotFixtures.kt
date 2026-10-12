package com.mk.daftar.feature.banks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.feature.banks.ui.AccountEditorContent
import com.mk.daftar.feature.banks.ui.BankDetailContent
import com.mk.daftar.feature.banks.ui.BankEditorContent
import com.mk.daftar.feature.banks.ui.BankListContent

/** Every frame the screenshot tests capture. Names are the file names, so they stay short and stable. */
internal val bankFrameNames: List<String> = listOf(
    "list-populated",
    "list-empty",
    "list-masked",
    "list-loading",
    "detail-populated",
    "detail-empty",
    "detail-masked",
    "bank-add",
    "bank-add-error",
    "bank-delete-blocked",
    "bank-delete-confirm",
    "account-add",
    "account-add-error",
    "account-edit-currency",
    "account-masked",
    "account-save-failed",
    "expanded-two-pane",
)

private val banks: List<BankOption> = listOf(
    BankOption("bank-misr", "بنك مصر"),
    BankOption("alahli", "الأهلي المصري"),
    BankOption("cib", "CIB"),
)

private val listReady: BankListUiState = BankListUiState(
    phase = BankPhase.Ready,
    rows = listOf(
        BankRow("bank-misr", "بنك مصر", 2, listOf(Currency.USD, Currency.EGP), Decimal.parseCanonical("163500")),
        BankRow("alahli", "الأهلي المصري", 1, listOf(Currency.EGP), Decimal.parseCanonical("180000")),
        BankRow("cib", "CIB", 1, listOf(Currency.EGP), Decimal.parseCanonical("45000")),
    ),
    total = BankTotal(Decimal.parseCanonical("388500"), complete = true, warnings = emptyList()),
)

private val misrDetail: BankDetailUiState = BankDetailUiState(
    phase = BankPhase.Ready,
    bankId = "bank-misr",
    name = "بنك مصر",
    total = BankTotal(Decimal.parseCanonical("163500"), complete = true, warnings = emptyList()),
    accounts = listOf(
        AccountRow(
            id = "a-usd",
            name = "توفير بالدولار",
            currency = Currency.USD,
            balanceText = "3,000.00",
            valued = true,
            egpText = "145,500.00",
            note = "للطوارئ",
        ),
        AccountRow(
            id = "a-egp",
            name = "حساب جاري",
            currency = Currency.EGP,
            balanceText = "18,000.00",
            valued = true,
            egpText = null,
            note = null,
        ),
    ),
)

private val dependents: List<DependentAccount> = listOf(
    DependentAccount("توفير بالدولار", Currency.USD, "3,000.00"),
    DependentAccount("حساب جاري", Currency.EGP, "18,000.00"),
)

private val accountNew: AccountEditorUiState = AccountEditorUiState(
    phase = BankPhase.Ready,
    isNew = true,
    draft = AccountDraft(name = "حساب جاري", bankId = "bank-misr", currency = Currency.EGP, balanceText = "18000"),
    banks = banks,
)

/** Captures the bank screens for one frame. Each frame is stateless, so every state is reproduced exactly. */
@Composable
internal fun BanksFrameContent(name: String) {
    val noop: () -> Unit = {}
    when (name) {
        "list-populated" -> BankListContent(listReady, "bank-misr", showsBack = true, onBack = noop, onIntent = {})

        "list-empty" -> BankListContent(
            BankListUiState(phase = BankPhase.Empty),
            null,
            showsBack = true,
            onBack = noop,
            onIntent = {},
        )

        "list-masked" -> BankListContent(
            listReady.copy(
                hidden = true,
            ),
            null,
            showsBack = true,
            onBack = noop,
            onIntent = {
            },
        )

        "list-loading" -> BankListContent(BankListUiState(), null, showsBack = true, onBack = noop, onIntent = {})

        "detail-populated" -> BankDetailContent(misrDetail, showsBack = true, onBack = noop, onIntent = {})

        "detail-empty" -> BankDetailContent(
            BankDetailUiState(
                phase = BankPhase.Empty,
                bankId = "cib",
                name = "CIB",
                total = BankTotal(Decimal.ZERO, true, emptyList()),
            ),
            showsBack = true,
            onBack = noop,
            onIntent = {},
        )

        "detail-masked" -> BankDetailContent(
            misrDetail.copy(
                hidden = true,
            ),
            showsBack = true,
            onBack = noop,
            onIntent = {
            },
        )

        "bank-add" -> BankEditorContent(
            BankEditorUiState(phase = BankPhase.Ready, isNew = true),
            onIntent = {},
            onBack = noop,
        )

        "bank-add-error" -> BankEditorContent(
            BankEditorUiState(phase = BankPhase.Ready, isNew = true, draft = BankDraft("  "), attempted = true),
            onIntent = {},
            onBack = noop,
        )

        "bank-delete-blocked" -> BankEditorContent(
            BankEditorUiState(
                phase = BankPhase.Ready,
                isNew = false,
                draft = BankDraft("بنك مصر"),
                baselineName = "بنك مصر",
                storedName = "بنك مصر",
                blocked = true,
                dependents = dependents,
            ),
            onIntent = {},
            onBack = noop,
        )

        "bank-delete-confirm" -> BankEditorContent(
            BankEditorUiState(
                phase = BankPhase.Ready,
                isNew = false,
                draft = BankDraft("CIB"),
                baselineName = "CIB",
                storedName = "CIB",
                confirmation = BankConfirmation.Delete,
            ),
            onIntent = {},
            onBack = noop,
        )

        "account-add" -> AccountEditorContent(accountNew, onIntent = {}, onBack = noop)

        "account-add-error" -> AccountEditorContent(
            accountNew.copy(
                draft = AccountDraft(name = "", bankId = "bank-misr", balanceText = "1.234"),
                attempted = true,
            ),
            onIntent = {},
            onBack = noop,
        )

        "account-edit-currency" -> AccountEditorContent(
            AccountEditorUiState(
                phase = BankPhase.Ready,
                isNew = false,
                draft = AccountDraft(
                    name = "حساب جاري",
                    bankId = "bank-misr",
                    currency = Currency.USD,
                    balanceText = "18000",
                ),
                baseline = AccountValues("حساب جاري", "bank-misr", Currency.EGP, "18000", null),
                banks = banks,
            ),
            onIntent = {},
            onBack = noop,
        )

        "account-masked" -> AccountEditorContent(
            accountNew.copy(concealed = true),
            onIntent = {},
            onBack = noop,
        )

        "account-save-failed" -> AccountEditorContent(
            accountNew.copy(failure = BankFailure(BankAction.Save, StorageError.DiskFull)),
            onIntent = {},
            onBack = noop,
        )

        "expanded-two-pane" -> Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(0.4f)) {
                BankListContent(listReady, "bank-misr", showsBack = false, onBack = noop, onIntent = {})
            }
            Box(modifier = Modifier.weight(0.6f)) {
                BankDetailContent(misrDetail, showsBack = false, onBack = noop, onIntent = {})
            }
        }

        else -> error("unknown frame $name")
    }
}
