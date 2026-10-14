package com.mk.daftar.core.domain.broker

import com.mk.daftar.core.domain.bank.BankDirectory
import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.model.bank.BankCountry
import com.mk.daftar.core.model.bank.BankInstitution
import com.mk.daftar.core.model.broker.BrokerDirectoryData

/**
 * The offline broker directory. It shares the bank directory's matching rules and only supplies its own data, so the
 * two catalogs stay separate: brokers are never searched through the bank list, and banks never through this one.
 */
object BrokerDirectory {
    /** Brokers whose names or aliases match [query], sorted like the bank list. A blank query returns every broker. */
    fun search(
        query: String,
        language: AppLanguage,
        institutions: List<BankInstitution> = BrokerDirectoryData.institutions,
        countries: List<BankCountry> = BrokerDirectoryData.countries,
    ): List<BankInstitution> = BankDirectory.search(query, institutions, countries, language)
}
