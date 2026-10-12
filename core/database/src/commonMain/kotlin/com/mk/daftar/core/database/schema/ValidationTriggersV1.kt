package com.mk.daftar.core.database.schema

import com.mk.daftar.core.database.mapper.QuoteKeys
import com.mk.daftar.core.domain.repository.isManuallyEntered
import com.mk.daftar.core.model.CashKind
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.GoldForm
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.QuoteProvider
import com.mk.daftar.core.model.USER_FRACTION_DIGITS

/**
 * Validation triggers for schema version 1. Room annotations cannot express enum membership, timestamp ordering,
 * revision rules or numeric text, so these triggers reject an invalid write before it reaches a table. Exact decimal
 * canonicality and Unicode text rules are also enforced by the mappers on every read and write; the triggers are the
 * storage-level backstop.
 *
 * The SQL is versioned. A later schema adds a new object beside this one and never edits it. The enum lists come from
 * the model, so the SQL cannot drift from the Kotlin types.
 */
internal object ValidationTriggersV1 {
    const val VERSION: Int = 1
    private const val PREFIX = "daftar_v1_"
    private const val NOTE_MAX_CODE_POINTS = 4_000
    private const val NAME_MAX_CODE_POINTS = 120
    private const val NOTE_BODY_MAX_CODE_POINTS = 10_000
    private const val NANOS_MAX = 999_999_999

    private class Check(val failure: String, val message: String)

    private val karats = GoldKarat.entries.map { it.name }
    private val forms = GoldForm.entries.map { it.name }
    private val currencies = Currency.entries.map { it.name }
    private val cashKinds = CashKind.entries.map { it.name }
    private val remoteProviders = QuoteProvider.entries.filter { it != QuoteProvider.USER }.map { it.name }

    /** Every statement, in creation order. */
    val statements: List<String> by lazy { buildStatements() }

    /** The trigger names this version creates, used to verify an opened database. */
    val triggerNames: List<String> by lazy {
        statements.map { it.substringAfter("CREATE TRIGGER IF NOT EXISTS ").substringBefore(' ') }
    }

    private fun buildStatements(): List<String> = buildList {
        addRecord("gold_holdings", identity() + timestamps() + goldChecks())
        addRecord("banks", identity() + timestamps() + bankChecks())
        addRecord("bank_accounts", identity() + timestamps() + accountChecks())
        addRecord("cash_balances", identity() + timestamps() + cashChecks())
        addRecord("zakat_payments", identity() + timestamps() + zakatChecks())
        addRecord("personal_notes", identity() + timestamps() + noteChecks())
        add(remoteQuoteTrigger(insert = true))
        add(remoteQuoteTrigger(insert = false))
        add(manualQuoteTrigger(insert = true))
        add(manualQuoteTrigger(insert = false))
    }

    /** Inserts validate the row; updates also keep the identity, creation time and revision step. */
    private fun MutableList<String>.addRecord(table: String, checks: List<Check>) {
        add(trigger(table, insert = true, checks = checks))
        add(trigger(table, insert = false, checks = checks + immutableChecks()))
    }

    private fun trigger(table: String, insert: Boolean, checks: List<Check>): String {
        val event = if (insert) "INSERT" else "UPDATE"
        return triggerSql("$PREFIX${table}_${event.lowercase()}", event, table, checks)
    }

    private fun triggerSql(name: String, event: String, table: String, checks: List<Check>): String {
        val body = checks.joinToString("\n") { "  SELECT RAISE(ABORT, '${it.message}') WHERE ${it.failure};" }
        return "CREATE TRIGGER IF NOT EXISTS $name BEFORE $event ON $table\nBEGIN\n$body\nEND"
    }

    private fun identity(): List<Check> = listOf(
        Check("NEW.id = '' OR NEW.id <> trim(NEW.id)", "id must be nonblank and trimmed"),
    )

    private fun timestamps(): List<Check> = listOf(
        Check(
            "NEW.created_nanos < 0 OR NEW.created_nanos > $NANOS_MAX OR " +
                "NEW.updated_nanos < 0 OR NEW.updated_nanos > $NANOS_MAX",
            "nanoseconds must be within 0..999999999",
        ),
        Check(
            "NEW.updated_seconds < NEW.created_seconds OR " +
                "(NEW.updated_seconds = NEW.created_seconds AND NEW.updated_nanos < NEW.created_nanos)",
            "updatedAt must not be before createdAt",
        ),
        Check("NEW.revision < 1", "revision must be at least 1"),
    )

    /** Edits keep the ID and creation time, and advance the revision by exactly one. */
    private fun immutableChecks(): List<Check> = listOf(
        Check("NEW.id <> OLD.id", "id is immutable"),
        Check(
            "NEW.created_seconds <> OLD.created_seconds OR NEW.created_nanos <> OLD.created_nanos",
            "creation time is immutable",
        ),
        Check("NEW.revision <> OLD.revision + 1", "revision must increase by one on each edit"),
    )

    private fun goldChecks(): List<Check> = listOf(
        oneOf("karat", karats, "karat is not a known value"),
        oneOf("form", forms, "form is not a known value"),
        Check(
            decimalInvalid("weight_grams", USER_FRACTION_DIGITS, positive = true),
            "weight must be a positive canonical decimal with at most two decimals",
        ),
        optionalNote(),
    )

    private fun bankChecks(): List<Check> = listOf(name("name"))

    private fun accountChecks(): List<Check> = listOf(
        Check("NEW.bank_id = '' OR NEW.bank_id <> trim(NEW.bank_id)", "bank id must be nonblank and trimmed"),
        name("name"),
        balanceCheck(),
        oneOf("currency", currencies, "currency is not a known value"),
        optionalNote(),
    )

    private fun cashChecks(): List<Check> = listOf(
        name("name"),
        oneOf("kind", cashKinds, "kind is not a known value"),
        balanceCheck(),
        oneOf("currency", currencies, "currency is not a known value"),
        optionalNote(),
    )

    private fun balanceCheck(): Check = Check(
        decimalInvalid("balance", USER_FRACTION_DIGITS, positive = false),
        "balance must be a canonical decimal with at most two decimals",
    )

    private fun zakatChecks(): List<Check> = listOf(
        Check(
            "NEW.paid_on NOT GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'",
            "paid_on must be an ISO date",
        ),
        Check(
            decimalInvalid("amount", USER_FRACTION_DIGITS, positive = true),
            "amount must be a positive canonical decimal with at most two decimals",
        ),
        Check("NEW.currency <> 'EGP'", "zakat payments are recorded in EGP"),
        optionalNote(),
    )

    private fun noteChecks(): List<Check> = listOf(
        name("title"),
        Check(
            "trim(NEW.body) = '' OR length(NEW.body) > $NOTE_BODY_MAX_CODE_POINTS",
            "note body must be nonblank and at most $NOTE_BODY_MAX_CODE_POINTS characters",
        ),
    )

    private fun name(column: String): Check = Check(
        "length(NEW.$column) = 0 OR length(NEW.$column) > $NAME_MAX_CODE_POINTS OR " +
            "NEW.$column <> trim(NEW.$column)",
        "$column must be trimmed, nonblank and at most $NAME_MAX_CODE_POINTS characters",
    )

    private fun optionalNote(): Check = Check(
        "NEW.note IS NOT NULL AND (length(NEW.note) = 0 OR NEW.note <> trim(NEW.note) OR " +
            "length(NEW.note) > $NOTE_MAX_CODE_POINTS)",
        "note must be trimmed, nonblank and at most $NOTE_MAX_CODE_POINTS characters",
    )

    private fun oneOf(column: String, allowed: List<String>, message: String): Check =
        Check("NEW.$column NOT IN (${quoted(allowed)})", message)

    private fun quoted(values: List<String>): String = values.joinToString(", ") { "'$it'" }

    /**
     * True when [column] is not canonical decimal text under the model's limits. Canonical text starts with a digit,
     * has no sign, grouping or exponent, no leading zero before another digit, no trailing fraction zero or dot, and
     * fits the input and digit limits. Positive columns also refuse zero.
     */
    private fun decimalInvalid(column: String, maxFraction: Int, positive: Boolean): String {
        val c = "NEW.$column"
        val integerDigits = "(CASE WHEN instr($c, '.') > 0 THEN instr($c, '.') - 1 ELSE length($c) END)"
        val fractionDigits = "(CASE WHEN instr($c, '.') > 0 THEN length($c) - instr($c, '.') ELSE 0 END)"
        val failures = mutableListOf(
            "$c IS NULL",
            "$c NOT GLOB '[0-9]*'",
            "$c GLOB '*[^0-9.]*'",
            "length($c) - length(replace($c, '.', '')) > 1",
            "$c GLOB '*.'",
            "$c GLOB '0[0-9]*'",
            "(instr($c, '.') > 0 AND substr($c, -1, 1) = '0')",
            "length($c) > ${Decimal.MAX_INPUT_LENGTH}",
            "$integerDigits > ${Decimal.MAX_INTEGER_DIGITS}",
            "$fractionDigits > $maxFraction",
        )
        if (positive) failures += "$c = '0'"
        return failures.joinToString(" OR ")
    }

    private fun remoteQuoteTrigger(insert: Boolean): String {
        val keys = QuoteKeys.all.map { QuoteKeys.encode(it) }
        val checks = mutableListOf(
            Check("NEW.quote_key NOT IN (${quoted(keys)})", "quote_key is not a known key"),
            Check("NEW.provider NOT IN (${quoted(remoteProviders)})", "provider is not a remote provider"),
            Check(
                "(NEW.quote_key <> 'USD_EGP' AND NEW.provider <> 'ISAGHA') OR " +
                    "(NEW.quote_key = 'USD_EGP' AND NEW.provider = 'ISAGHA')",
                "gold quotes come from ISAGHA and USD to EGP from the FX providers",
            ),
            Check(
                decimalInvalid("value", Decimal.MAX_FRACTION_DIGITS, positive = true),
                "value must be a positive canonical decimal",
            ),
            Check("NEW.source_url NOT GLOB 'https://*'", "source URL must be HTTPS"),
            oneOf("source_time_kind", listOf("TIMESTAMP", "DATE_ONLY", "UNKNOWN"), "source time kind is unknown"),
            Check(
                "NEW.source_time_kind = 'TIMESTAMP' AND (NEW.source_seconds IS NULL OR NEW.source_nanos IS NULL OR " +
                    "NEW.source_date IS NOT NULL OR NEW.source_nanos < 0 OR NEW.source_nanos > $NANOS_MAX)",
                "a timestamp source time needs seconds and nanos only",
            ),
            Check(
                "NEW.source_time_kind = 'DATE_ONLY' AND (NEW.source_date IS NULL OR " +
                    "NEW.source_date NOT GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' OR " +
                    "NEW.source_seconds IS NOT NULL OR NEW.source_nanos IS NOT NULL)",
                "a date-only source time needs an ISO date only",
            ),
            Check(
                "NEW.source_time_kind = 'UNKNOWN' AND (NEW.source_seconds IS NOT NULL OR " +
                    "NEW.source_nanos IS NOT NULL OR NEW.source_date IS NOT NULL)",
                "an unknown source time carries no time fields",
            ),
            Check(
                "NEW.fetched_nanos < 0 OR NEW.fetched_nanos > $NANOS_MAX OR " +
                    "NEW.recorded_nanos < 0 OR NEW.recorded_nanos > $NANOS_MAX OR " +
                    "(NEW.next_update_nanos IS NOT NULL AND " +
                    "(NEW.next_update_nanos < 0 OR NEW.next_update_nanos > $NANOS_MAX))",
                "nanoseconds must be within 0..999999999",
            ),
            Check(
                "(NEW.next_update_seconds IS NULL) <> (NEW.next_update_nanos IS NULL)",
                "next update needs both seconds and nanos, or neither",
            ),
            Check(
                "NEW.attribution_url IS NOT NULL AND NEW.attribution_url NOT GLOB 'https://*'",
                "attribution URL must be HTTPS",
            ),
        )
        if (!insert) {
            checks += Check(
                "NEW.quote_key <> OLD.quote_key OR NEW.provider <> OLD.provider",
                "the key and provider of a remote quote are immutable",
            )
        }
        return triggerSql(
            name = "${PREFIX}remote_quotes_${if (insert) "insert" else "update"}",
            event = if (insert) "INSERT" else "UPDATE",
            table = "remote_quotes",
            checks = checks,
        )
    }

    private fun manualQuoteTrigger(insert: Boolean): String {
        val keys = QuoteKeys.all.filter { it.isManuallyEntered() }.map { QuoteKeys.encode(it) }
        val checks = mutableListOf(
            Check(
                "NEW.quote_key NOT IN (${quoted(keys)})",
                "only gold sell prices and USD to EGP are entered manually",
            ),
            Check(
                decimalInvalid("value", Decimal.MAX_FRACTION_DIGITS, positive = true),
                "value must be a positive canonical decimal",
            ),
            Check(
                "NEW.entered_nanos < 0 OR NEW.entered_nanos > $NANOS_MAX",
                "nanoseconds must be within 0..999999999",
            ),
            Check("NEW.pinned NOT IN (0, 1)", "pinned must be 0 or 1"),
            optionalNote(),
            Check("NEW.revision < 1", "revision must be at least 1"),
        )
        if (!insert) {
            checks += Check("NEW.quote_key <> OLD.quote_key", "quote key is immutable")
            checks += Check("NEW.revision <> OLD.revision + 1", "revision must increase by one on each change")
        }
        return triggerSql(
            name = "${PREFIX}manual_quotes_${if (insert) "insert" else "update"}",
            event = if (insert) "INSERT" else "UPDATE",
            table = "manual_quotes",
            checks = checks,
        )
    }
}
