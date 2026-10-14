package com.mk.daftar.feature.investments

import androidx.compose.ui.graphics.Color
import com.mk.daftar.core.designsystem.component.DaftarBankMark
import com.mk.daftar.core.model.bank.BankArtworkMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDateTime

class InvestmentIdentityTest {

    @Test
    fun thndrOfficialMarkSitsOnItsNearBlackTileInTheOfficialBuild() {
        val mark = brokerMarkFor("eg.thndr", BankArtworkMode.OFFICIAL)
        val official = assertIs<DaftarBankMark.Official>(mark)
        assertEquals("broker_eg_thndr", official.assetKey)
        assertEquals(Color(0xFF141414), official.brandBacking)
        assertEquals(Color(0xFFFFE147), official.fallback.background)
        assertEquals(Color(0xFF262D24), official.fallback.foreground)
        assertEquals("TH", official.fallback.initials)
    }

    @Test
    fun theMonogramBuildUsesTheBrandMonogramOnTheSameColours() {
        val mark = brokerMarkFor("eg.thndr", BankArtworkMode.MONOGRAM)
        val monogram = assertIs<DaftarBankMark.Monogram>(mark)
        assertEquals("TH", monogram.initials)
        assertEquals(Color(0xFFFFE147), monogram.background)
        assertEquals(Color(0xFF262D24), monogram.foreground)
    }

    @Test
    fun otherAndUnknownBrokersHaveNoBrandMarkAndNeverYellow() {
        assertNull(brokerMarkFor(null, BankArtworkMode.OFFICIAL))
        assertNull(brokerMarkFor("eg.unknown-broker", BankArtworkMode.OFFICIAL))
        assertNull(brokerMarkFor("eg.unknown-broker", BankArtworkMode.MONOGRAM))
    }

    @Test
    fun aBrokerWithoutItsOwnAssetFallsBackToTheMonogramEvenInTheOfficialBuild() {
        // The directory is the single source for identities: every catalog broker resolves to a mark.
        assertNotNull(brokerMarkFor("eg.thndr", BankArtworkMode.OFFICIAL))
        assertTrue(brokerMarkFor("eg.thndr", BankArtworkMode.MONOGRAM) is DaftarBankMark.Monogram)
    }
}

class CairoTimeTest {

    @Test
    fun aNormalCairoTimeMapsToItsInstant() {
        val instant = cairoInstantOf(LocalDateTime(2026, 10, 14, 12, 0))
        assertNotNull(instant)
        assertEquals(cairoLocalOf(instant), LocalDateTime(2026, 10, 14, 12, 0))
    }

    @Test
    fun aSpringForwardGapTimeDoesNotExistAndIsRejected() {
        // Egypt moves its clocks from 00:00 to 01:00 on the last Friday of April.
        assertNull(cairoInstantOf(LocalDateTime(2026, 4, 24, 0, 30)))
    }

    @Test
    fun anAmbiguousFallBackTimeIsRejectedUntilAnOffsetIsChosen() {
        // The last Thursday of October repeats the hour from 23:00 to 24:00.
        assertNull(cairoInstantOf(LocalDateTime(2026, 10, 29, 23, 30)))
    }
}
