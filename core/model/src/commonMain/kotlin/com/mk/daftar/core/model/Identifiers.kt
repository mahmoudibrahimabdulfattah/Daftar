package com.mk.daftar.core.model

/** Typed, nonblank identifiers. Generation belongs to the data layer, not to this module. */
data class GoldHoldingId(val value: String) {
    init {
        require(value.isNotBlank()) { "id must not be blank" }
    }
}

data class BankId(val value: String) {
    init {
        require(value.isNotBlank()) { "id must not be blank" }
    }
}

data class BankAccountId(val value: String) {
    init {
        require(value.isNotBlank()) { "id must not be blank" }
    }
}

data class CashBalanceId(val value: String) {
    init {
        require(value.isNotBlank()) { "id must not be blank" }
    }
}

data class ZakatPaymentId(val value: String) {
    init {
        require(value.isNotBlank()) { "id must not be blank" }
    }
}

data class NoteId(val value: String) {
    init {
        require(value.isNotBlank()) { "id must not be blank" }
    }
}
