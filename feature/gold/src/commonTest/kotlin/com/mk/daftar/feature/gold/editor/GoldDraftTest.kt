package com.mk.daftar.feature.gold.editor

import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.GoldForm
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.MerchantSide
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.feature.gold.testing.dec
import com.mk.daftar.feature.gold.testing.holding
import com.mk.daftar.feature.gold.testing.readyQuotes
import com.mk.daftar.feature.gold.testing.remoteGold
import com.mk.daftar.feature.gold.testing.selectedOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoldDraftTest {

    private fun weight(text: String) = GoldDraft(weightText = text).weightOrNull()?.grams?.toPlainString()

    @Test
    fun positiveWeightsWithUpToTwoDecimalsAreAccepted() {
        assertEquals("26.77", weight("26.77"))
        assertEquals("0.01", weight("0.01"))
        assertEquals("80", weight("80"))
        assertEquals("1000", weight("1,000"))
    }

    @Test
    fun arabicIndicDigitsAndSeparatorsAreNormalizedToTheSameWeight() {
        assertEquals("26.77", weight("٢٦٫٧٧"))
        assertEquals("1000", weight("١٬٠٠٠"))
        assertEquals("26.77", weight("۲۶٫۷۷"))
    }

    @Test
    fun zeroNegativeSignedExponentAndOverprecisionAreRejected() {
        val rejected =
            listOf("", "   ", "0", "0.00", "-1", "+1", "1e2", "1E2", "26.777", "1.", ".", "abc", "1,00", "1.5.5")
        for (text in rejected) {
            assertNull(weight(text), "$text is rejected")
            assertTrue(GoldDraft(weightText = text).errors().weight != null, "$text carries the weight error")
        }
    }

    @Test
    fun intermediateTypingIsRejectedUntilSubmissionWithoutRounding() {
        assertNull(weight("26."))
        assertNull(weight("0.001"))
        assertEquals("26.7", weight("26.70"))
    }

    @Test
    fun noteLimitIsCountedAfterTrimmingAndBlankMeansNoNote() {
        val fourThousand = "ا".repeat(4_000)
        assertNull(GoldDraft(noteText = fourThousand).errors().note)
        assertTrue(GoldDraft(noteText = "$fourThousand!").errors().note != null)
        assertTrue(GoldDraft(noteText = "   $fourThousand   ").errors().note == null)
        assertNull(GoldDraft(noteText = "   ").values().note)
        assertEquals("للاحتفاظ", GoldDraft(noteText = "  للاحتفاظ  ").values().note)
    }

    @Test
    fun karatChangesNeverModifyTheWeight() {
        val base = GoldDraft(weightText = "٢٦٫٧٧")
        val changed = base.copy(karat = GoldKarat.K18)
        assertEquals(base.weightOrNull(), changed.weightOrNull())
        assertEquals(base.values().weight, changed.values().weight)
    }

    @Test
    fun valuesCompareByMeaningNotByTyping() {
        val arabic = GoldDraft(weightText = "٢٦٫٧٧", noteText = "  ")
        val latin = GoldDraft(weightText = "26.77", noteText = "")
        assertEquals(arabic.values(), latin.values())
    }

    @Test
    fun aHoldingsValuesMatchItsDraftAndRoundTrip() {
        val stored = holding("g1", GoldKarat.K21, "26.77", form = GoldForm.JEWELRY, note = "زفاف")
        val values = stored.toValues()
        assertEquals(GoldValues(GoldKarat.K21, GoldForm.JEWELRY, "26.77", "زفاف"), values)
        assertEquals(values, values.toDraft().values())
    }

    @Test
    fun previewUsesTheSelectedKaratsMerchantSellPrice() {
        val preview = previewOf(GoldDraft(GoldKarat.K21, weightText = "٢٦٫٧٧"), readyQuotes())
        assertEquals(dec("6185"), preview.quote?.value)
        assertEquals("165572.45", preview.egp?.toPlainString())
    }

    @Test
    fun aMissingSellQuoteMakesThePreviewUnavailableButNeverBlocksTheDraft() {
        val preview = previewOf(GoldDraft(GoldKarat.K18, weightText = "3.29"), selectedOf(emptyList()))
        assertNull(preview.quote)
        assertNull(preview.egp)
        assertFalse(GoldDraft(GoldKarat.K18, weightText = "3.29").errors().any)
    }

    @Test
    fun aBuyPriceNeverValuesAHolding() {
        val buyOnly = selectedOf(
            listOf(
                remoteGold(GoldKarat.K21, "6185"),
            ),
        )
        assertNotNull(buyOnly[QuoteKey.Gold(GoldKarat.K21, MerchantSide.SELLS_TO_CUSTOMER)])
        assertNull(buyOnly[QuoteKey.Gold(GoldKarat.K21, MerchantSide.BUYS_FROM_CUSTOMER)])
    }

    @Test
    fun decimalsKeepTheirExactValueThroughTheSelectedWeight() {
        val preview = previewOf(GoldDraft(GoldKarat.K21, weightText = "0.01"), readyQuotes())
        assertEquals(dec("61.85").toPlainString(), preview.egp?.toPlainString())
        assertEquals(Decimal.parseCanonical("0.01").toPlainString(), "0.01")
    }
}
