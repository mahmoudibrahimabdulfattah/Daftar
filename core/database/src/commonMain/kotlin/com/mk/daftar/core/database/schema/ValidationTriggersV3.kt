package com.mk.daftar.core.database.schema

import com.mk.daftar.core.model.FxBasis
import com.mk.daftar.core.model.InstitutionId

/**
 * Validation triggers added by schema version 3, beside the earlier sets and never instead of them. They cover the bank
 * identity columns and the FX basis of remote quotes. The ID rule comes from the model's [InstitutionId], so the SQL
 * cannot drift from the Kotlin type.
 */
internal object ValidationTriggersV3 {
    const val VERSION: Int = 3
    private const val PREFIX = "daftar_v3_"

    private class Check(val failure: String, val message: String)

    /** Every statement, in creation order. */
    val statements: List<String> by lazy { buildStatements() }

    /** The trigger names this version creates, used to verify an opened database. */
    val triggerNames: List<String> by lazy {
        statements.map { it.substringAfter("CREATE TRIGGER IF NOT EXISTS ").substringBefore(' ') }
    }

    private fun buildStatements(): List<String> = buildList {
        add(bankTrigger(insert = true))
        add(bankTrigger(insert = false))
        add(remoteBasisTrigger(insert = true))
        add(remoteBasisTrigger(insert = false))
    }

    private fun bankTrigger(insert: Boolean): String {
        val checks = mutableListOf(
            Check(
                "NEW.institution_id IS NOT NULL AND (length(NEW.institution_id) < 1 OR " +
                    "length(NEW.institution_id) > ${InstitutionId.MAX_LENGTH} OR " +
                    "NEW.institution_id GLOB '*[^a-z0-9.-]*')",
                "institution id must be 1 to ${InstitutionId.MAX_LENGTH} lowercase ASCII letters, digits, dots, hyphens",
            ),
            Check("NEW.identity_reviewed NOT IN (0, 1)", "identity reviewed must be 0 or 1"),
        )
        if (!insert) checks += Check("NEW.id <> OLD.id", "bank identity checks cannot change the record key")
        return trigger("banks", insert, checks, suffix = "identity")
    }

    private fun remoteBasisTrigger(insert: Boolean): String {
        val allowed = FxBasis.entries.map { "'${it.name}'" }.joinToString(", ")
        val checks = mutableListOf(
            Check(
                "(NEW.quote_key = 'USD_EGP') <> (NEW.basis IS NOT NULL)",
                "only USD to EGP quotes carry a basis",
            ),
            Check("NEW.basis IS NOT NULL AND NEW.basis NOT IN ($allowed)", "basis is not a known value"),
            Check(
                "NEW.basis IS NOT NULL AND NOT (" +
                    "(NEW.provider = 'BANQUE_MISR' AND NEW.basis = 'BANK_TRANSFER_BUY') OR " +
                    "(NEW.provider IN ('EXCHANGE_RATE_API', 'FAWAZ_CURRENCY_API') AND " +
                    "NEW.basis IN ('MID_MARKET_ESTIMATE', 'LEGACY_UNKNOWN')))",
                "basis does not match the quote provider",
            ),
        )
        if (!insert) checks += Check("NEW.basis IS NOT OLD.basis", "the basis of a remote quote is immutable")
        return trigger("remote_quotes", insert, checks, suffix = "basis")
    }

    private fun trigger(table: String, insert: Boolean, checks: List<Check>, suffix: String): String {
        val event = if (insert) "INSERT" else "UPDATE"
        val name = "$PREFIX${table}_${suffix}_${event.lowercase()}"
        val body = checks.joinToString("\n") { "  SELECT RAISE(ABORT, '${it.message}') WHERE ${it.failure};" }
        return "CREATE TRIGGER IF NOT EXISTS $name BEFORE $event ON $table\nBEGIN\n$body\nEND"
    }
}
