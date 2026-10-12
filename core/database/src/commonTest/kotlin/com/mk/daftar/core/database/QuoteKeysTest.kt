package com.mk.daftar.core.database

import com.mk.daftar.core.database.mapper.QuoteKeys
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.MerchantSide
import com.mk.daftar.core.model.QuoteKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuoteKeysTest {

    @Test
    fun theSevenStoredCodesAreExactlyTheKnownKeys() {
        assertEquals(
            setOf(
                "GOLD_24_SELL",
                "GOLD_24_BUY",
                "GOLD_21_SELL",
                "GOLD_21_BUY",
                "GOLD_18_SELL",
                "GOLD_18_BUY",
                "USD_EGP",
            ),
            QuoteKeys.all.map { QuoteKeys.encode(it) }.toSet(),
        )
        assertEquals(7, QuoteKeys.all.size)
    }

    @Test
    fun everyKeyDecodesBackToItself() {
        QuoteKeys.all.forEach { key -> assertEquals(key, QuoteKeys.decode(QuoteKeys.encode(key))) }
    }

    @Test
    fun unknownCodesDoNotDecode() {
        listOf("GOLD_24", "GOLD_22_SELL", "USD", "gold_24_sell", "", "GOLD_24_SELL ").forEach { code ->
            assertNull(QuoteKeys.decode(code), code)
        }
    }

    @Test
    fun directionComesFromTheKeyNotTheMagnitude() {
        val sell = QuoteKey.Gold(GoldKarat.K21, MerchantSide.SELLS_TO_CUSTOMER)
        val buy = QuoteKey.Gold(GoldKarat.K21, MerchantSide.BUYS_FROM_CUSTOMER)
        assertEquals("GOLD_21_SELL", QuoteKeys.encode(sell))
        assertEquals("GOLD_21_BUY", QuoteKeys.encode(buy))
    }

    @Test
    fun onlySellPricesAndUsdAreManuallyEntered() {
        assertEquals(
            setOf("GOLD_24_SELL", "GOLD_21_SELL", "GOLD_18_SELL", "USD_EGP"),
            QuoteKeys.manualKeys.map { QuoteKeys.encode(it) }.toSet(),
        )
    }
}
