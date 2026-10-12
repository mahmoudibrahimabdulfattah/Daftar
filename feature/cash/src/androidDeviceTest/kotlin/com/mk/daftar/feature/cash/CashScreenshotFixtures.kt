package com.mk.daftar.feature.cash

import androidx.compose.runtime.Composable
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.valuation.QuoteWarning
import com.mk.daftar.core.model.CashBalanceId
import com.mk.daftar.core.model.CashKind
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.feature.cash.ui.CashEditorContent
import com.mk.daftar.feature.cash.ui.CashListContent

/** Every frame the screenshot test captures. Each name becomes a file, so it stays ASCII and stable. */
internal val cashFrameNames: List<String> = listOf(
    "list-populated",
    "list-masked",
    "list-usd-without-rate",
    "list-empty",
    "list-loading",
    "add-cash",
    "add-wallet",
    "add-invalid",
    "add-save-failed",
    "edit-wallet",
    "edit-cash-currency-change",
    "edit-conflict",
    "delete-confirmation",
    "discard-confirmation",
    "editor-masked",
)

private fun decimal(text: String): Decimal = Decimal.parseCanonical(text)

private fun row(
    id: String,
    name: String,
    kind: CashKind,
    currency: Currency,
    amount: String,
    egp: String?,
    warnings: List<QuoteWarning> = emptyList(),
): CashRow = CashRow(
    id = CashBalanceId(id),
    name = name,
    kind = kind,
    currency = currency,
    amount = decimal(amount),
    note = null,
    egp = egp?.let(::decimal),
    warnings = warnings,
)

private val homeRow = row("home", "Cash at home", CashKind.CASH, Currency.EGP, "2400", "2400")
private val thundrRow = row("thundr", "Thundr wallet", CashKind.WALLET, Currency.EGP, "7500", "7500")
private val usdRow = row("usd", "USD savings", CashKind.CASH, Currency.USD, "3000", null)

private fun populated(hidden: Boolean = false) = CashListUiState(
    phase = CashPhase.Ready,
    rows = listOf(homeRow, thundrRow),
    totalEgp = decimal("9900"),
    availableEgp = decimal("9900"),
    hidden = hidden,
)

private val walletDraft = CashDraft(name = "Thundr wallet", kind = CashKind.WALLET, balanceText = "7500")
private val walletValues =
    CashValues(name = "Thundr wallet", kind = CashKind.WALLET, currency = Currency.EGP, balance = "7500", note = null)

private fun editor(state: CashEditorUiState) = state

/** Stateless frame content for one named state. Each state is fixed; no repository or session is involved. */
@Composable
internal fun CashFrameContent(name: String) {
    val noop: (CashListIntent) -> Unit = {}
    val noopEditor: (CashEditorIntent) -> Unit = {}
    when (name) {
        "list-populated" -> CashListContent(populated(), noop, onBack = {})

        "list-masked" -> CashListContent(populated(hidden = true), noop, onBack = {})

        "list-usd-without-rate" -> CashListContent(
            CashListUiState(
                phase = CashPhase.Ready,
                rows = listOf(homeRow, usdRow),
                totalEgp = null,
                availableEgp = decimal("2400"),
            ),
            noop,
            onBack = {},
        )

        "list-empty" -> CashListContent(CashListUiState(phase = CashPhase.Empty), noop, onBack = {})

        "list-loading" -> CashListContent(CashListUiState(phase = CashPhase.Loading), noop, onBack = {})

        "add-cash" -> CashEditorContent(
            editor(CashEditorUiState(phase = CashPhase.Ready, isNew = true, baseline = CashDraft().values())),
            noopEditor,
            onBack = {},
        )

        "add-wallet" -> CashEditorContent(
            editor(
                CashEditorUiState(
                    phase = CashPhase.Ready,
                    isNew = true,
                    draft = walletDraft,
                    baseline = CashDraft().values(),
                ),
            ),
            noopEditor,
            onBack = {},
        )

        "add-invalid" -> CashEditorContent(
            editor(
                CashEditorUiState(
                    phase = CashPhase.Ready,
                    isNew = true,
                    draft = CashDraft(name = "", balanceText = "-5"),
                    baseline = CashDraft().values(),
                    attempted = true,
                ),
            ),
            noopEditor,
            onBack = {},
        )

        "add-save-failed" -> CashEditorContent(
            editor(
                CashEditorUiState(
                    phase = CashPhase.Ready,
                    isNew = true,
                    draft = walletDraft,
                    baseline = CashDraft().values(),
                    failure = CashFailure(CashAction.Save, StorageError.Io),
                ),
            ),
            noopEditor,
            onBack = {},
        )

        "edit-wallet" -> CashEditorContent(
            editor(
                CashEditorUiState(
                    phase = CashPhase.Ready,
                    isNew = false,
                    draft = walletDraft,
                    baseline = walletValues,
                ),
            ),
            noopEditor,
            onBack = {},
        )

        "edit-cash-currency-change" -> CashEditorContent(
            editor(
                CashEditorUiState(
                    phase = CashPhase.Ready,
                    isNew = false,
                    draft = CashDraft(
                        name = "Cash at home",
                        kind = CashKind.CASH,
                        currency = Currency.USD,
                        balanceText = "2400",
                    ),
                    baseline = CashValues("Cash at home", CashKind.CASH, Currency.EGP, "2400", null),
                    attempted = true,
                ),
            ),
            noopEditor,
            onBack = {},
        )

        "edit-conflict" -> CashEditorContent(
            editor(
                CashEditorUiState(
                    phase = CashPhase.Ready,
                    isNew = false,
                    draft = CashDraft(name = "Thundr wallet", kind = CashKind.WALLET, balanceText = "8000"),
                    baseline = walletValues,
                    conflict = true,
                ),
            ),
            noopEditor,
            onBack = {},
        )

        "delete-confirmation" -> CashEditorContent(
            editor(
                CashEditorUiState(
                    phase = CashPhase.Ready,
                    isNew = false,
                    draft = walletDraft,
                    baseline = walletValues,
                    confirmation = CashConfirmation.Delete,
                ),
            ),
            noopEditor,
            onBack = {},
        )

        "discard-confirmation" -> CashEditorContent(
            editor(
                CashEditorUiState(
                    phase = CashPhase.Ready,
                    isNew = false,
                    draft = walletDraft.copy(balanceText = "9999"),
                    baseline = walletValues,
                    confirmation = CashConfirmation.Discard,
                ),
            ),
            noopEditor,
            onBack = {},
        )

        "editor-masked" -> CashEditorContent(
            editor(
                CashEditorUiState(
                    phase = CashPhase.Ready,
                    isNew = false,
                    draft = walletDraft,
                    baseline = walletValues,
                    concealed = true,
                ),
            ),
            noopEditor,
            onBack = {},
        )

        else -> error("unknown cash frame $name")
    }
}
