package com.mk.daftar.core.domain.repository

import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.MerchantSide
import com.mk.daftar.core.model.QuoteKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManualQuoteInputTest {

    @Test
    fun onlySellPricesAndUsdAreEnteredByHand() {
        GoldKarat.entries.forEach { karat ->
            assertTrue(QuoteKey.Gold(karat, MerchantSide.SELLS_TO_CUSTOMER).isManuallyEntered())
            assertFalse(QuoteKey.Gold(karat, MerchantSide.BUYS_FROM_CUSTOMER).isManuallyEntered())
        }
        assertTrue(QuoteKey.UsdEgp.isManuallyEntered())
    }

    @Test
    fun aBuySidePriceCannotBeEnteredManually() {
        assertFailsWith<IllegalArgumentException> {
            ManualQuoteInput(
                key = QuoteKey.Gold(GoldKarat.K21, MerchantSide.BUYS_FROM_CUSTOMER),
                value = Decimal.parseCanonical("3000"),
                note = null,
            )
        }
    }

    @Test
    fun aManualValueMustBePositive() {
        assertFailsWith<IllegalArgumentException> {
            ManualQuoteInput(QuoteKey.UsdEgp, Decimal.ZERO, note = null)
        }
    }

    @Test
    fun storedRevisionStartsAtOne() {
        assertEquals(1L, Stored("value", 1L).revision)
        assertFailsWith<IllegalArgumentException> { Stored("value", 0L) }
    }
}
