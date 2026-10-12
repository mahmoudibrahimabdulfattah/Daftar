package com.mk.daftar.core.database.mapper

import com.mk.daftar.core.domain.repository.isManuallyEntered
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.MerchantSide
import com.mk.daftar.core.model.QuoteKey

/**
 * The one place where a [QuoteKey] becomes stored text, and back. Direction is always explicit in the key: a buy and a
 * sell price are never told apart by their magnitude.
 */
object QuoteKeys {
    /** The seven keys the app stores: buy and sell for each karat, and USD to EGP. */
    val all: List<QuoteKey> = buildList {
        GoldKarat.entries.forEach { karat ->
            MerchantSide.entries.forEach { side -> add(QuoteKey.Gold(karat, side)) }
        }
        add(QuoteKey.UsdEgp)
    }

    /** The keys a user may enter by hand. */
    val manualKeys: List<QuoteKey> = all.filter { it.isManuallyEntered() }

    fun encode(key: QuoteKey): String = when (key) {
        is QuoteKey.Gold -> when (key.karat) {
            GoldKarat.K24 -> when (key.side) {
                MerchantSide.SELLS_TO_CUSTOMER -> "GOLD_24_SELL"
                MerchantSide.BUYS_FROM_CUSTOMER -> "GOLD_24_BUY"
            }

            GoldKarat.K21 -> when (key.side) {
                MerchantSide.SELLS_TO_CUSTOMER -> "GOLD_21_SELL"
                MerchantSide.BUYS_FROM_CUSTOMER -> "GOLD_21_BUY"
            }

            GoldKarat.K18 -> when (key.side) {
                MerchantSide.SELLS_TO_CUSTOMER -> "GOLD_18_SELL"
                MerchantSide.BUYS_FROM_CUSTOMER -> "GOLD_18_BUY"
            }
        }

        QuoteKey.UsdEgp -> "USD_EGP"
    }

    /** The key for stored [code], or null when the code is not one of the seven. */
    fun decode(code: String): QuoteKey? = all.firstOrNull { encode(it) == code }
}
