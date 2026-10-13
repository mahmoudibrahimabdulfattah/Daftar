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

/**
 * The identity of a catalog institution, such as `eg.nbe`. Lowercase ASCII letters, digits, dots and hyphens, one to
 * [MAX_LENGTH] characters. Valid IDs that this build does not know are kept as they are. The ID is never a local record
 * ID and never reused.
 */
data class InstitutionId(val value: String) {
    init {
        require(isValid(value)) { "invalid institution id" }
    }

    companion object {
        const val MAX_LENGTH: Int = 64

        fun isValid(text: String): Boolean = text.length in 1..MAX_LENGTH && text.all { ch ->
            ch in 'a'..'z' || ch in '0'..'9' || ch == '.' || ch == '-'
        }

        fun parseOrNull(text: String): InstitutionId? = if (isValid(text)) InstitutionId(text) else null
    }
}
