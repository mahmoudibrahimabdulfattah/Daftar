package com.mk.daftar.core.database.migration

/**
 * The alias table used only by the version 2 to 3 migration. It is a frozen copy: later directory updates never change
 * what an upgrade matched, and the migration never runs again. Matching is exact after normalization and must be
 * unique. Substrings, fuzzy matches and ambiguous names stay unmatched.
 */
internal object FrozenBankIdentities {

    private class Entry(val institutionId: String, val names: List<String>)

    private val frozenEntries: List<Entry> = listOf(
        Entry("eg.nbe", listOf("البنك الأهلي المصري", "الأهلي", "National Bank of Egypt", "NBE", "AlAhli")),
        Entry("eg.banque-misr", listOf("بنك مصر", "Banque Misr", "Bank Misr", "BM")),
        Entry("eg.cib", listOf("البنك التجاري الدولي", "Commercial International Bank", "CIB")),
        Entry("eg.qnb", listOf("كيو إن بي مصر", "الأهلي", "QNB Egypt", "QNB Alahli")),
        Entry("eg.banque-du-caire", listOf("بنك القاهرة", "Banque du Caire", "BDC")),
        Entry("eg.aaib", listOf("البنك العربي الأفريقي الدولي", "Arab African International Bank", "AAIB")),
        Entry("eg.hsbc", listOf("إتش إس بي سي مصر", "HSBC Egypt")),
        Entry("eg.alexbank", listOf("بنك الإسكندرية", "ALEXBANK", "Alex Bank")),
        Entry("eg.faisal", listOf("بنك فيصل الإسلامي المصري", "Faisal Islamic Bank of Egypt", "Faisal")),
        Entry("eg.adib", listOf("مصرف أبوظبي الإسلامي مصر", "Abu Dhabi Islamic Bank Egypt", "ADIB")),
        Entry("eg.emirates-nbd", listOf("بنك الإمارات دبي الوطني مصر", "Emirates NBD Egypt", "ENBD")),
        Entry("eg.credit-agricole", listOf("كريدي أجريكول مصر", "Crédit Agricole Egypt", "Credit Agricole")),
        Entry("eg.bank-abc", listOf("بنك ABC مصر", "Bank ABC Egypt", "Arab Banking Corporation")),
        Entry("eg.egebank", listOf("إي جي بنك", "EGBANK", "Egyptian Gulf Bank")),
    )

    /** Normalized name to the institutions that claim it. A name claimed by two institutions is ambiguous. */
    // Lazy, so the fold table below is initialized before the first normalization reads it.
    private val index: Map<String, Set<String>> by lazy {
        buildMap<String, MutableSet<String>> {
            frozenEntries.forEach { entry ->
                entry.names.forEach { name ->
                    getOrPut(normalizeFrozen(name)) { mutableSetOf() } += entry.institutionId
                }
            }
        }
    }

    /** The one institution whose frozen name equals [bankName] after normalization, or null. */
    fun uniqueMatch(bankName: String): String? = index[normalizeFrozen(bankName)]?.singleOrNull()

    /** A frozen copy of the directory normalization: case, Arabic diacritics, tatweel, Latin accents and whitespace. */
    internal fun normalizeFrozen(text: String): String {
        val folded = StringBuilder(text.length)
        text.lowercase().forEach { ch ->
            when {
                ch in '\u064B'..'\u065F' || ch == '\u0670' || ch == '\u0640' -> Unit

                ch in '\u200B'..'\u200F' || ch in '\u202A'..'\u202E' || ch in '\u2066'..'\u2069' ||
                    ch == '\uFEFF' -> Unit

                else -> folded.append(LATIN_FOLDS[ch] ?: ch)
            }
        }
        return folded.toString().split(' ', '\t', '\n', '\r').filter { it.isNotEmpty() }.joinToString(" ")
    }

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
