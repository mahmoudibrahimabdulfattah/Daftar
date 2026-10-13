package com.mk.daftar.core.database.schema

import com.mk.daftar.core.model.HIJRI_MAX_OVERRIDE_DAYS
import com.mk.daftar.core.model.HIJRI_MAX_YEAR
import com.mk.daftar.core.model.HIJRI_MIN_YEAR

/**
 * Validation triggers added by schema version 2, beside the version 1 set and never instead of it. They cover the
 * annual declarations, the tracking row, and the year link on zakat payments. The Hijri bounds and override range come
 * from the model constants, so the SQL cannot drift from the Kotlin types.
 */
internal object ValidationTriggersV2 {
    const val VERSION: Int = 2
    private const val PREFIX = "daftar_v2_"
    private const val NANOS_MAX = 999_999_999

    /** Every statement, in creation order. */
    val statements: List<String> by lazy { buildStatements() }

    /** The trigger names this version creates, used to verify an opened database. */
    val triggerNames: List<String> by lazy {
        statements.map { it.substringAfter("CREATE TRIGGER IF NOT EXISTS ").substringBefore(' ') }
    }

    private class Check(val failure: String, val message: String)

    private fun buildStatements(): List<String> = buildList {
        add(yearTrigger(insert = true))
        add(yearTrigger(insert = false))
        add(trackingTrigger(insert = true))
        add(trackingTrigger(insert = false))
        add(paymentYearTrigger(table = "zakat_payments", insert = true))
        add(paymentYearTrigger(table = "zakat_payments", insert = false))
    }

    private fun yearTrigger(insert: Boolean): String {
        val checks = mutableListOf(
            Check(
                "NEW.hijri_year < $HIJRI_MIN_YEAR OR NEW.hijri_year > $HIJRI_MAX_YEAR",
                "Hijri year must be within $HIJRI_MIN_YEAR..$HIJRI_MAX_YEAR",
            ),
            Check("NEW.paid NOT IN (0, 1)", "paid must be 0 or 1"),
            Check("NEW.user_adjusted NOT IN (0, 1)", "user adjusted must be 0 or 1"),
            Check(
                "NEW.override_days < -$HIJRI_MAX_OVERRIDE_DAYS OR NEW.override_days > $HIJRI_MAX_OVERRIDE_DAYS",
                "date adjustment must be -1, 0 or +1 day",
            ),
            Check(
                "NEW.user_adjusted <> (NEW.override_days <> 0)",
                "user adjusted must be set exactly when the date is adjusted",
            ),
            Check(
                "(NEW.paid = 1) <> (NEW.marked_seconds IS NOT NULL)",
                "a paid year has a mark time, and only a paid year has one",
            ),
            Check(
                "(NEW.marked_seconds IS NULL) <> (NEW.marked_nanos IS NULL)",
                "a mark time needs both seconds and nanos, or neither",
            ),
            Check(
                "NEW.marked_nanos < 0 OR NEW.marked_nanos > $NANOS_MAX",
                "nanoseconds must be within 0..999999999",
            ),
        )
        if (!insert) checks += Check("NEW.hijri_year <> OLD.hijri_year", "the Hijri year is immutable")
        return trigger("zakat_years", insert, checks)
    }

    private fun trackingTrigger(insert: Boolean): String {
        val checks = mutableListOf(
            Check("NEW.id <> 1", "the tracking row is always row 1"),
            Check(
                "NEW.tracking_start_year < $HIJRI_MIN_YEAR OR NEW.tracking_start_year > $HIJRI_MAX_YEAR",
                "tracking start must be within $HIJRI_MIN_YEAR..$HIJRI_MAX_YEAR",
            ),
        )
        if (!insert) checks += Check("NEW.id <> OLD.id", "the tracking row key is immutable")
        return trigger("zakat_tracking", insert, checks)
    }

    private fun paymentYearTrigger(table: String, insert: Boolean): String = trigger(
        table,
        insert,
        listOf(
            Check(
                "NEW.for_hijri_year IS NOT NULL AND " +
                    "(NEW.for_hijri_year < $HIJRI_MIN_YEAR OR NEW.for_hijri_year > $HIJRI_MAX_YEAR)",
                "linked Hijri year must be within $HIJRI_MIN_YEAR..$HIJRI_MAX_YEAR",
            ),
        ),
        suffix = "year",
    )

    private fun trigger(table: String, insert: Boolean, checks: List<Check>, suffix: String? = null): String {
        val event = if (insert) "INSERT" else "UPDATE"
        val name = "$PREFIX$table${suffix?.let { "_$it" } ?: ""}_${event.lowercase()}"
        val body = checks.joinToString("\n") { "  SELECT RAISE(ABORT, '${it.message}') WHERE ${it.failure};" }
        return "CREATE TRIGGER IF NOT EXISTS $name BEFORE $event ON $table\nBEGIN\n$body\nEND"
    }
}
