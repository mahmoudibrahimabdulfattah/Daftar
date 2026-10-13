package com.mk.daftar.feature.banks

import androidx.compose.ui.graphics.Color
import com.mk.daftar.core.designsystem.component.DaftarBankMark
import com.mk.daftar.core.model.bank.BankArtworkMode
import com.mk.daftar.core.model.bank.BankDirectoryData
import com.mk.daftar.feature.banks.ui.bankMarkFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BankIdentityTest {

    @Test
    fun theMonogramBuildShowsTheBrandMonogramOnTheBrandColour() {
        val mark = bankMarkFor("eg.cib", BankArtworkMode.MONOGRAM)
        assertIs<DaftarBankMark.Monogram>(mark)
        assertEquals("CIB", mark.initials)
        assertEquals(Color(0xFF003B71), mark.background)
    }

    @Test
    fun everyCatalogInstitutionHasAMonogramWithItsOwnColourInBothBuilds() {
        BankDirectoryData.institutions.forEach { institution ->
            val monogram = assertIs<DaftarBankMark.Monogram>(
                bankMarkFor(institution.id.value, BankArtworkMode.MONOGRAM),
                institution.id.value,
            )
            assertEquals(institution.monogram, monogram.initials)
            val official = assertIs<DaftarBankMark.Official>(
                bankMarkFor(institution.id.value, BankArtworkMode.OFFICIAL),
                institution.id.value,
            )
            assertEquals(monogram, official.fallback)
        }
    }

    @Test
    fun aWhiteOfficialMarkSitsOnItsBrandColourAndNeverOnWhite() {
        listOf("eg.cib", "eg.alexbank", "eg.banque-du-caire", "eg.egebank").forEach { id ->
            val mark = assertIs<DaftarBankMark.Official>(bankMarkFor(id, BankArtworkMode.OFFICIAL), id)
            assertNotNull(mark.brandBacking, id)
            assertEquals(mark.fallback.background, mark.brandBacking)
        }
        val nbe = assertIs<DaftarBankMark.Official>(bankMarkFor("eg.nbe", BankArtworkMode.OFFICIAL))
        assertNull(nbe.brandBacking)
    }

    @Test
    fun aCustomOrUnknownBankShowsTheNeutralOutline() {
        BankArtworkMode.entries.forEach { mode ->
            assertEquals(DaftarBankMark.Neutral, bankMarkFor(null, mode))
            assertEquals(DaftarBankMark.Neutral, bankMarkFor("sa.future-bank", mode))
            assertEquals(DaftarBankMark.Neutral, bankMarkFor("", mode))
        }
    }
}
