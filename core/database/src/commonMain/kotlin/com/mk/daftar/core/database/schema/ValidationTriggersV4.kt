package com.mk.daftar.core.database.schema

import com.mk.daftar.core.model.InstitutionId
import com.mk.daftar.core.model.InvestmentAccount

/**
 * Validation triggers added by schema version 4, beside the earlier sets and never instead of them. They cover the
 * investment account table: identity, provenance, timestamps, the revision step, and the exact decimal bound of one
 * account. The bound is checked as canonical text, never through REAL arithmetic, and the decimal rules are the same ones
 * version 1 uses for balances. The ID rule comes from the model's [InstitutionId] and the value bound from
 * [InvestmentAccount.MAX_AMOUNT], so the SQL cannot drift from the Kotlin types.
 */
internal object ValidationTriggersV4 {
    const val VERSION: Int = 4
    private const val PREFIX = "daftar_v4_"
    private const val NAME_MAX_CODE_POINTS = 120
    private const val NOTE_MAX_CODE_POINTS = 4_000
    private const val NANOS_MAX = 999_999_999
    private const val MAX_INTEGER_DIGITS_OF_AMOUNT = 12

    private class Check(val failure: String, val message: String)

    /** Every statement, in creation order. */
    val statements: List<String> by lazy { buildStatements() }

    /** The trigger names this version creates, used to verify an opened database. */
    val triggerNames: List<String> by lazy {
        statements.map { it.substringAfter("CREATE TRIGGER IF NOT EXISTS ").substringBefore(' ') }
    }

    private fun buildStatements(): List<String> = buildList {
        add(investmentTrigger(insert = true))
        add(investmentTrigger(insert = false))
    }

    private fun investmentTrigger(insert: Boolean): String {
        val checks = mutableListOf(
            Check("NEW.id = '' OR NEW.id <> trim(NEW.id)", "id must be nonblank and trimmed"),
            Check(
                "NEW.created_nanos < 0 OR NEW.created_nanos > $NANOS_MAX OR " +
                    "NEW.updated_nanos < 0 OR NEW.updated_nanos > $NANOS_MAX OR " +
                    "NEW.value_as_of_nanos < 0 OR NEW.value_as_of_nanos > $NANOS_MAX",
                "nanoseconds must be within 0..999999999",
            ),
            Check(
                "NEW.updated_seconds < NEW.created_seconds OR " +
                    "(NEW.updated_seconds = NEW.created_seconds AND NEW.updated_nanos < NEW.created_nanos)",
                "updatedAt must not be before createdAt",
            ),
            Check("NEW.revision < 1", "revision must be at least 1"),
            Check(
                "NEW.provider_id IS NOT NULL AND (length(NEW.provider_id) < 1 OR " +
                    "length(NEW.provider_id) > ${InstitutionId.MAX_LENGTH} OR " +
                    "NEW.provider_id GLOB '*[^a-z0-9.-]*')",
                "provider id must be 1 to ${InstitutionId.MAX_LENGTH} lowercase ASCII letters, digits, dots, hyphens",
            ),
            Check(
                "length(NEW.name) = 0 OR length(NEW.name) > $NAME_MAX_CODE_POINTS OR NEW.name <> trim(NEW.name)",
                "name must be trimmed, nonblank and at most $NAME_MAX_CODE_POINTS characters",
            ),
            Check(
                ValidationTriggersV1.decimalInvalid("amount", maxFraction = 2, positive = false),
                "amount must be a canonical decimal with at most two decimals",
            ),
            Check(amountBound(), "amount must not exceed ${InvestmentAccount.MAX_AMOUNT.toPlainString()}"),
            Check(
                "NEW.note IS NOT NULL AND (length(NEW.note) = 0 OR NEW.note <> trim(NEW.note) OR " +
                    "length(NEW.note) > $NOTE_MAX_CODE_POINTS)",
                "note must be trimmed, nonblank and at most $NOTE_MAX_CODE_POINTS characters",
            ),
            Check(
                "NEW.converted_from_cash_id IS NOT NULL AND (length(NEW.converted_from_cash_id) = 0 OR " +
                    "NEW.converted_from_cash_id <> trim(NEW.converted_from_cash_id))",
                "converted source id must be nonblank and trimmed",
            ),
        )
        if (!insert) {
            checks += Check("NEW.id <> OLD.id", "id is immutable")
            checks += Check(
                "NEW.created_seconds <> OLD.created_seconds OR NEW.created_nanos <> OLD.created_nanos",
                "creation time is immutable",
            )
            checks += Check("NEW.revision <> OLD.revision + 1", "revision must increase by one on each edit")
            checks += Check(
                "NEW.converted_from_cash_id IS NOT OLD.converted_from_cash_id",
                "the conversion source of an investment is immutable",
            )
        }
        val event = if (insert) "INSERT" else "UPDATE"
        val name = "${PREFIX}investment_accounts_${event.lowercase()}"
        val body = checks.joinToString("\n") { "  SELECT RAISE(ABORT, '${it.message}') WHERE ${it.failure};" }
        return "CREATE TRIGGER IF NOT EXISTS $name BEFORE $event ON investment_accounts\nBEGIN\n$body\nEND"
    }

    /**
     * The bound as canonical text: at most twelve integer digits, and no larger than the bound's text when it has
     * exactly twelve. Canonical text has no sign, grouping or leading zeros, so text order equals numeric order here.
     */
    private fun amountBound(): String {
        val c = "NEW.amount"
        val integerDigits = "(CASE WHEN instr($c, '.') > 0 THEN instr($c, '.') - 1 ELSE length($c) END)"
        val max = InvestmentAccount.MAX_AMOUNT.toPlainString()
        return "$integerDigits > $MAX_INTEGER_DIGITS_OF_AMOUNT OR " +
            "($integerDigits = $MAX_INTEGER_DIGITS_OF_AMOUNT AND $c > '$max')"
    }
}
