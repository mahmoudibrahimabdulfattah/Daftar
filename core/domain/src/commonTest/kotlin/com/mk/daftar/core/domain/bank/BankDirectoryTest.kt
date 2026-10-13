package com.mk.daftar.core.domain.bank

import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.model.InstitutionId
import com.mk.daftar.core.model.bank.BankCountry
import com.mk.daftar.core.model.bank.BankDirectoryData
import com.mk.daftar.core.model.bank.BankInstitution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BankDirectoryTest {

    private val institutions = BankDirectoryData.institutions
    private val countries = BankDirectoryData.countries

    private fun ids(query: String, language: AppLanguage = AppLanguage.EN): List<String> =
        BankDirectory.search(query, institutions, countries, language).map { it.id.value }

    @Test
    fun theDirectoryHoldsExactlyTheFourteenEgyptianIdentities() {
        val egypt = institutions.map { it.id.value }.filter { it.startsWith("eg.") }.toSet()
        assertEquals(14, egypt.size)
        assertEquals(institutions.size, institutions.map { it.id }.toSet().size)
        assertTrue(institutions.all { it.countryCode == "EG" })
    }

    @Test
    fun everyInstitutionIsFoundByItsArabicName() {
        institutions.forEach { institution ->
            assertTrue(institution.id.value in ids(institution.nameAr, AppLanguage.AR), institution.id.value)
        }
    }

    @Test
    fun everyInstitutionIsFoundByItsEnglishName() {
        institutions.forEach { institution ->
            assertTrue(institution.id.value in ids(institution.nameEn), institution.id.value)
        }
    }

    @Test
    fun everyAliasFindsItsInstitution() {
        institutions.forEach { institution ->
            (institution.aliasesEn + institution.aliasesAr).forEach { alias ->
                assertTrue(institution.id.value in ids(alias), "${institution.id.value} via $alias")
            }
        }
    }

    @Test
    fun caseDiacriticsTatweelAndHarmlessWhitespaceAreIgnored() {
        assertEquals(listOf("eg.nbe"), ids("  national   BANK of egypt  "))
        assertEquals(listOf("eg.nbe"), ids("NATIONAL bank of EGYPT".lowercase()))
        assertEquals(listOf("eg.credit-agricole"), ids("Crédit Agricole"))
        // Tatweel and harakat do not change the Arabic name.
        assertEquals(listOf("eg.nbe"), ids("الْبَنْكُ الأَهْلِيُّ الـمِصْرِيُّ", AppLanguage.AR))
        assertEquals(listOf("eg.banque-misr"), ids("بـنـك مصر", AppLanguage.AR))
    }

    @Test
    fun alefYehAndTaMarbutaFormsAreNotFolded() {
        // The search matches what was typed: a different alef is a different letter.
        assertEquals(emptyList(), ids("إتش إس بي سي", AppLanguage.AR).filter { it != "eg.hsbc" })
        assertEquals(listOf("eg.hsbc"), ids("إتش إس بي سي", AppLanguage.AR))
        assertEquals(emptyList(), ids("أتش إس بي سي", AppLanguage.AR))
    }

    @Test
    fun theAmbiguousArabicAliasMatchesEveryClaimantInSearch() {
        assertEquals(listOf("eg.nbe", "eg.qnb"), ids("الأهلي", AppLanguage.AR))
    }

    @Test
    fun aSubstringSearchFindsNamesAndCountryNames() {
        assertEquals(institutions.size, ids("مصر", AppLanguage.AR).size)
        assertEquals(institutions.size, ids("Egypt").size)
        assertEquals(listOf("eg.banque-misr"), ids("misr"))
    }

    @Test
    fun aBlankQueryReturnsEveryInstitutionSortedByTheLocalizedName() {
        val english = BankDirectory.search("  ", institutions, countries, AppLanguage.EN)
        assertEquals(institutions.size, english.size)
        val names = english.map { BankDirectory.normalize(it.nameEn) }
        assertEquals(names.sorted(), names)

        val arabic = BankDirectory.search("", institutions, countries, AppLanguage.AR)
        val arabicNames = arabic.map { BankDirectory.normalize(it.nameAr) }
        assertEquals(arabicNames.sorted(), arabicNames)
    }

    @Test
    fun aNonMatchingQueryReturnsNothing() {
        assertEquals(emptyList(), ids("zzz-no-such-bank"))
    }

    @Test
    fun aDataOnlyCountryExtensionSearchesWithoutAnySchemaChange() {
        val extension = listOf(
            BankInstitution(
                id = InstitutionId("sa.alrajhi"),
                countryCode = "SA",
                nameAr = "مصرف الراجحي",
                nameEn = "Al Rajhi Bank",
                aliasesAr = listOf("الراجحي"),
                aliasesEn = listOf("Rajhi"),
                monogram = "RJ",
                brandBackground = "#00594C",
                monogramForeground = "#FFFFFF",
                officialAsset = null,
                manifestRef = null,
            ),
            BankInstitution(
                id = InstitutionId("ae.adcb"),
                countryCode = "AE",
                nameAr = "بنك أبوظبي التجاري",
                nameEn = "Abu Dhabi Commercial Bank",
                aliasesAr = emptyList(),
                aliasesEn = listOf("ADCB"),
                monogram = "AD",
                brandBackground = "#003B71",
                monogramForeground = "#FFFFFF",
                officialAsset = null,
                manifestRef = null,
            ),
        )
        val countriesWithExtensions = countries + listOf(
            BankCountry("SA", "السعودية", "Saudi Arabia"),
            BankCountry("AE", "الإمارات", "United Arab Emirates"),
        )
        val all = institutions + extension

        assertEquals(
            listOf("sa.alrajhi"),
            BankDirectory.search("Saudi", all, countriesWithExtensions, AppLanguage.EN).map { it.id.value },
        )
        assertEquals(
            listOf("ae.adcb"),
            BankDirectory.search("ADCB", all, countriesWithExtensions, AppLanguage.EN).map { it.id.value },
        )
        // An Egyptian search still returns only Egyptian institutions and does not conflate by initials.
        assertTrue(
            BankDirectory.search("Egypt", all, countriesWithExtensions, AppLanguage.EN).none {
                it.countryCode !=
                    "EG"
            },
        )
    }

    @Test
    fun anUnknownButValidInstitutionIdIsStillAValidIdentity() {
        assertEquals("sa.future-bank-7", InstitutionId("sa.future-bank-7").value)
    }
}
