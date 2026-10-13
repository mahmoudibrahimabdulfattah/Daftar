package com.mk.daftar.core.domain.bank

import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.model.bank.BankCountry
import com.mk.daftar.core.model.bank.BankInstitution

/**
 * Offline search over the bundled bank directory. Pure: it reads no clock, storage or network. Matching ignores case,
 * Arabic diacritics, tatweel, common Latin accents and harmless whitespace. Alef, yeh and teh-marbuta forms are not
 * folded, so the search matches what the user typed. Results are sorted by the localized name.
 */
object BankDirectory {

    /** The canonical form used for both queries and directory text. */
    fun normalize(text: String): String {
        val folded = StringBuilder(text.length)
        text.lowercase().forEach { ch ->
            if (!isIgnorable(ch)) folded.append(LATIN_FOLDS[ch] ?: ch)
        }
        return folded.toString().split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /**
     * Institutions whose Arabic or English name, alias or country name contains [query] after normalization. A blank
     * query returns every institution. Sorted by the name in [language], then by ID.
     */
    fun search(
        query: String,
        institutions: List<BankInstitution>,
        countries: List<BankCountry>,
        language: AppLanguage,
    ): List<BankInstitution> {
        val needle = normalize(query)
        val countryNames = countries.associate { it.code to listOf(it.nameAr, it.nameEn) }
        return institutions
            .filter { institution ->
                needle.isEmpty() || haystacks(institution, countryNames).any { normalize(it).contains(needle) }
            }
            .sortedWith(compareBy<BankInstitution> { normalize(localizedName(it, language)) }.thenBy { it.id.value })
    }

    /** The name shown for [institution] in [language]. */
    fun localizedName(institution: BankInstitution, language: AppLanguage): String = when (language) {
        AppLanguage.AR -> institution.nameAr
        AppLanguage.EN -> institution.nameEn
    }

    private fun haystacks(institution: BankInstitution, countryNames: Map<String, List<String>>): List<String> =
        buildList {
            add(institution.nameAr)
            add(institution.nameEn)
            addAll(institution.aliasesAr)
            addAll(institution.aliasesEn)
            addAll(countryNames[institution.countryCode].orEmpty())
        }

    private fun isIgnorable(ch: Char): Boolean = ch in ARABIC_DIACRITICS ||
        ch == TATWEEL ||
        ch in INVISIBLE

    private val WHITESPACE = Regex("\\s+")

    private val ARABIC_DIACRITICS: CharRange = '\u064B'..'\u065F'

    private const val TATWEEL: Char = '\u0640'

    /** Format and bidi controls, plus the byte-order mark. They carry no meaning in a name. */
    private val INVISIBLE: Set<Char> = setOf(
        '\u200B', '\u200C', '\u200D', '\u200E', '\u200F', '\u202A', '\u202B', '\u202C', '\u202D', '\u202E',
        '\u2066', '\u2067', '\u2068', '\u2069', '\uFEFF', '\u0670',
    )

    private val LATIN_FOLDS: Map<Char, Char> = buildMap {
        "àáâãäå".forEach { put(it, 'a') }
        put('ç', 'c')
        put('ć', 'c')
        "èéêë".forEach { put(it, 'e') }
        "ìíîï".forEach { put(it, 'i') }
        put('ñ', 'n')
        "òóôõö".forEach { put(it, 'o') }
        "ùúûü".forEach { put(it, 'u') }
        "ýÿ".forEach { put(it, 'y') }
    }
}
