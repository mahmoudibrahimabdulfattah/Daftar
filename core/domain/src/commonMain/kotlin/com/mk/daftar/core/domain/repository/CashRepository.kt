package com.mk.daftar.core.domain.repository

import com.mk.daftar.core.model.CashBalance
import com.mk.daftar.core.model.CashBalanceId
import com.mk.daftar.core.model.CashKind
import com.mk.daftar.core.model.Money

data class CashBalanceInput(val name: String, val kind: CashKind, val balance: Money, val note: String?)

interface CashRepository : RecordRepository<CashBalanceId, CashBalance, CashBalanceInput>
