package com.mk.daftar.feature.banks

import com.mk.daftar.core.domain.repository.BankDetail
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.valuation.AssetRef
import com.mk.daftar.core.domain.valuation.AssetValuation
import com.mk.daftar.core.domain.valuation.CategoryValuation
import com.mk.daftar.core.domain.valuation.SelectedQuotes
import com.mk.daftar.core.domain.valuation.valueBankAccounts
import com.mk.daftar.core.model.Bank
import com.mk.daftar.core.model.BankAccount
import com.mk.daftar.core.model.BankAccountId
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.ui.presentation.moneyText

/**
 * Several observations that must describe one session generation. A lock, a failure, a loading read or a mismatch of
 * generations decides the joined state, so no screen mixes two sessions.
 */
internal sealed interface Joined {
    data object Locked : Joined

    data class Failed(val error: StorageError) : Joined

    data object Loading : Joined

    data class Ready(val generation: Long) : Joined
}

internal fun joinReads(vararg reads: VaultRead<*>): Joined {
    reads.filterIsInstance<VaultRead.Failed>().firstOrNull()?.let { return Joined.Failed(it.error) }
    if (reads.any { it == VaultRead.Locked }) return Joined.Locked
    val generations = reads.map { read ->
        (read as? VaultRead.Ready<*>)?.generation ?: return Joined.Loading
    }.distinct()
    return generations.singleOrNull()?.let { Joined.Ready(it) } ?: Joined.Loading
}

/** The total of a valuation: the full value when every account is valued, otherwise the labeled available subtotal. */
internal fun CategoryValuation.toTotal(): BankTotal = BankTotal(
    amount = totalEgp ?: subtotalEgp,
    complete = totalEgp != null,
    warnings = warnings,
)

/** Each account's exact EGP value, or null when it is not valued. Joined by typed ID, never by position. */
internal fun CategoryValuation.egpByAccount(): Map<BankAccountId, Decimal?> = assets.mapNotNull { valuation ->
    val id = (valuation.asset as? AssetRef.Account)?.id ?: return@mapNotNull null
    id to (valuation as? AssetValuation.Valued)?.exactEgp
}.toMap()

/** Bank names and accounts the list shows, with each bank's own value from the same valuation as the total. */
internal fun bankListStateOf(
    banks: VaultRead<List<Stored<Bank>>>,
    accounts: VaultRead<List<Stored<BankAccount>>>,
    quotes: VaultRead<SelectedQuotes>,
    hidden: Boolean,
): BankListUiState = when (val joined = joinReads(banks, accounts, quotes)) {
    Joined.Locked -> BankListUiState(phase = BankPhase.Locked, hidden = hidden)

    Joined.Loading -> BankListUiState(phase = BankPhase.Loading, hidden = hidden)

    is Joined.Failed -> BankListUiState(phase = BankPhase.Failed(joined.error), hidden = hidden)

    is Joined.Ready -> {
        val bankRecords = (banks as VaultRead.Ready).value.map { it.value }
        val accountRecords = (accounts as VaultRead.Ready).value.map { it.value }
        val selected = (quotes as VaultRead.Ready).value
        val rows = bankRecords.map { bank ->
            val mine = accountRecords.filter { it.bankId == bank.id }
            BankRow(
                id = bank.id.value,
                name = bank.name,
                accountCount = mine.size,
                currencies = mine.map { it.balance.currency }.distinct().sortedBy { it != Currency.USD },
                valueEgp = valueBankAccounts(mine, selected).totalEgp,
            )
        }
        BankListUiState(
            phase = if (bankRecords.isEmpty()) BankPhase.Empty else BankPhase.Ready,
            rows = rows,
            total = valueBankAccounts(accountRecords, selected).toTotal(),
            hidden = hidden,
        )
    }
}

/**
 * One bank with its accounts, from one coherent detail observation and the quotes of the same generation. A missing
 * bank is a missing record, never an empty bank.
 */
internal fun bankDetailStateOf(
    bankId: String,
    detail: VaultRead<BankDetail?>,
    quotes: VaultRead<SelectedQuotes>,
    hidden: Boolean,
): BankDetailUiState = when (val joined = joinReads(detail, quotes)) {
    Joined.Locked -> BankDetailUiState(bankId = bankId, phase = BankPhase.Locked, hidden = hidden)

    Joined.Loading -> BankDetailUiState(bankId = bankId, phase = BankPhase.Loading, hidden = hidden)

    is Joined.Failed -> BankDetailUiState(bankId = bankId, phase = BankPhase.Failed(joined.error), hidden = hidden)

    is Joined.Ready -> {
        val record = (detail as VaultRead.Ready).value
        if (record == null) {
            BankDetailUiState(bankId = bankId, phase = BankPhase.Missing, hidden = hidden)
        } else {
            val selected = (quotes as VaultRead.Ready).value
            val accounts = record.accounts.map { it.value }
            val valuation = valueBankAccounts(accounts, selected)
            val egp = valuation.egpByAccount()
            val rows = record.accounts.map { stored ->
                val account = stored.value
                val value = egp[account.id]
                val isUsd = account.balance.currency == Currency.USD
                AccountRow(
                    id = account.id.value,
                    name = account.name,
                    currency = account.balance.currency,
                    balanceText = account.balance.amount.moneyText(),
                    valued = value != null,
                    egpText = if (isUsd) value?.moneyText() else null,
                    note = account.note,
                )
            }
            BankDetailUiState(
                phase = if (rows.isEmpty()) BankPhase.Empty else BankPhase.Ready,
                bankId = bankId,
                name = record.bank.value.name,
                total = valuation.toTotal(),
                accounts = rows,
                hidden = hidden,
            )
        }
    }
}
