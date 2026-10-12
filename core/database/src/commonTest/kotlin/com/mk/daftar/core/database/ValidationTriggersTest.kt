package com.mk.daftar.core.database

import com.mk.daftar.core.database.schema.ValidationTriggersV1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The trigger SQL is generated text, so these checks pin its shape without a database engine. */
class ValidationTriggersTest {

    private val tables = listOf(
        "gold_holdings",
        "banks",
        "bank_accounts",
        "cash_balances",
        "zakat_payments",
        "personal_notes",
        "remote_quotes",
        "manual_quotes",
    )

    @Test
    fun everyWritableTableHasAnInsertAndAnUpdateTrigger() {
        tables.forEach { table ->
            assertEquals(
                2,
                ValidationTriggersV1.statements.count { it.contains(" ON $table\n") },
                "triggers on $table",
            )
        }
        assertEquals(tables.size * 2, ValidationTriggersV1.statements.size)
    }

    @Test
    fun triggerNamesAreUniqueAndVersioned() {
        assertEquals(ValidationTriggersV1.triggerNames.toSet().size, ValidationTriggersV1.triggerNames.size)
        assertTrue(ValidationTriggersV1.triggerNames.all { it.startsWith("daftar_v1_") })
    }

    @Test
    fun enumListsAreDerivedFromTheModel() {
        val gold = ValidationTriggersV1.statements.first { it.contains("ON gold_holdings\n") && it.contains("INSERT") }
        assertTrue(gold.contains("'K24', 'K21', 'K18'"), gold)
        assertTrue(gold.contains("'BAR', 'JEWELRY'"), gold)
    }

    @Test
    fun quoteKeyListsMatchTheSevenPermittedCodes() {
        val remote = ValidationTriggersV1.statements.first {
            it.contains("ON remote_quotes\n") && it.contains("INSERT")
        }
        assertTrue(remote.contains("'GOLD_24_SELL', 'GOLD_24_BUY'"), remote)
        val manual = ValidationTriggersV1.statements.first {
            it.contains("ON manual_quotes\n") && it.contains("INSERT")
        }
        assertTrue(manual.contains("'GOLD_24_SELL', 'GOLD_21_SELL', 'GOLD_18_SELL', 'USD_EGP'"), manual)
    }
}
