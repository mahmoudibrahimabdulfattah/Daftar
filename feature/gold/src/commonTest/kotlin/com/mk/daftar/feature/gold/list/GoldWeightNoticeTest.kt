package com.mk.daftar.feature.gold.list

import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.feature.gold.testing.dec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class GoldWeightNoticeTest {

    @Test
    fun aSingleKaratStatesItsKaratWithoutAPureGoldWarning() {
        val notice =
            goldWeightNoticeOf(hidden = false, karats = listOf(GoldKarat.K21, GoldKarat.K21), totalWeight = dec("80"))

        assertEquals(GoldWeightNotice.Single(dec("80"), GoldKarat.K21), notice)
    }

    @Test
    fun mixedKaratsSayTheTotalIsNotPureGold() {
        val notice = goldWeightNoticeOf(
            hidden = false,
            karats = listOf(GoldKarat.K24, GoldKarat.K21, GoldKarat.K21),
            totalWeight = dec("110.06"),
        )

        assertEquals(GoldWeightNotice.Mixed(dec("110.06")), notice)
    }

    @Test
    fun hiddenValuesReadNoWeightWhateverTheKarats() {
        assertEquals(
            GoldWeightNotice.Hidden,
            goldWeightNoticeOf(hidden = true, karats = listOf(GoldKarat.K18), totalWeight = dec("5")),
        )
        assertEquals(
            GoldWeightNotice.Hidden,
            goldWeightNoticeOf(hidden = true, karats = listOf(GoldKarat.K18, GoldKarat.K24), totalWeight = dec("5")),
        )
    }

    @Test
    fun noHoldingsHasNoNotice() {
        assertNull(goldWeightNoticeOf(hidden = false, karats = emptyList(), totalWeight = dec("0")))
    }

    @Test
    fun aSingleKaratIsNotMistakenForMixedWhenTheOrderVaries() {
        val notice = goldWeightNoticeOf(
            hidden = false,
            karats = listOf(GoldKarat.K18, GoldKarat.K18),
            totalWeight = dec("2"),
        )

        assertIs<GoldWeightNotice.Single>(notice)
        assertEquals(GoldKarat.K18, notice.karat)
    }
}
