package com.mk.daftar.core.model

/** Currencies the app stores. Egyptian pounds are the valuation base; US dollars keep their original account currency. */
enum class Currency { EGP, USD }

/** An exact amount in one currency. Computed amounts may carry more than two decimals; entry validation does not. */
data class Money(val amount: Decimal, val currency: Currency)
