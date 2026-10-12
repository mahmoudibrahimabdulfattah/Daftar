package com.mk.daftar.core.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.mk.daftar.core.database.driver.DatabaseKeyAccess
import com.mk.daftar.core.database.driver.createSqlCipherDriver
import com.mk.daftar.core.database.mapper.toBankDetail
import com.mk.daftar.core.database.mapper.toEntity
import com.mk.daftar.core.database.mapper.toRemoteEntity
import com.mk.daftar.core.database.mapper.toStored
import com.mk.daftar.core.database.schema.ValidationTriggersV1
import com.mk.daftar.core.domain.repository.ManualQuoteInput
import com.mk.daftar.core.model.Bank
import com.mk.daftar.core.model.BankAccount
import com.mk.daftar.core.model.BankAccountId
import com.mk.daftar.core.model.BankId
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.FxQuote
import com.mk.daftar.core.model.FxRate
import com.mk.daftar.core.model.GoldForm
import com.mk.daftar.core.model.GoldHolding
import com.mk.daftar.core.model.GoldHoldingId
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.GoldPrice
import com.mk.daftar.core.model.GoldQuote
import com.mk.daftar.core.model.GoldWeight
import com.mk.daftar.core.model.MerchantSide
import com.mk.daftar.core.model.Money
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.QuoteProvenance
import com.mk.daftar.core.model.QuoteProvider
import com.mk.daftar.core.model.SourceTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * Room over the SQLCipher driver, on a real engine: keys, constraints, triggers, indexes, revision checks, flows,
 * reopen persistence and quote transactions. Every test owns its own database file and removes it afterwards.
 */
class DaftarDatabaseRoomTest {

    private val correctKey = ByteArray(32) { (0x40 + it).toByte() }
    private val wrongKey = ByteArray(32) { (0x90 + it).toByte() }
    private val created = Instant.fromEpochSeconds(1_700_000_000, 0)
    private val later = Instant.fromEpochSeconds(1_700_000_100, 0)
    private val fetched = Instant.fromEpochSeconds(1_750_000_000, 0)

    private fun keyOf(bytes: ByteArray) = object : DatabaseKeyAccess {
        override fun <R> useKey(block: (ByteArray) -> R): R = block(bytes.copyOf())
    }

    private fun open(path: String, key: ByteArray = correctKey): DaftarDatabase = buildDaftarDatabase(
        testDatabaseBuilder(path),
        createSqlCipherDriver(keyOf(key)),
    )

    private fun inDatabase(block: suspend (path: String) -> Unit) = runTest {
        val path = TestStorage.newDatabasePath()
        try {
            block(path)
        } finally {
            TestStorage.deleteDatabase(path)
        }
    }

    /** Room opens lazily, so creating the file takes a first read before the database is closed. */
    private suspend fun createDatabase(path: String) {
        val db = open(path)
        db.goldHoldingDao().observeAll().first()
        db.close()
    }

    private fun <R> withRawConnection(path: String, block: (SQLiteConnection) -> R): R {
        val connection = createSqlCipherDriver(keyOf(correctKey)).open(path)
        try {
            return block(connection)
        } finally {
            connection.close()
        }
    }

    private fun gold(id: String, at: Instant = created, weight: String = "2.5") = GoldHolding(
        id = GoldHoldingId(id),
        karat = GoldKarat.K24,
        form = GoldForm.BAR,
        weight = GoldWeight(Decimal.parseCanonical(weight)),
        note = null,
        createdAt = at,
        updatedAt = at,
    )

    private fun bank(id: String) = Bank(BankId(id), "Bank $id", created, created)

    private fun account(id: String, bankId: String, at: Instant = created) = BankAccount(
        id = BankAccountId(id),
        bankId = BankId(bankId),
        name = "Account $id",
        balance = Money(Decimal.parseCanonical("100"), Currency.EGP),
        note = null,
        createdAt = at,
        updatedAt = at,
    )

    private fun goldQuote(side: MerchantSide, price: String) = GoldQuote(
        key = QuoteKey.Gold(GoldKarat.K24, side),
        price = GoldPrice(Decimal.parseCanonical(price)),
        provenance = QuoteProvenance.live(
            QuoteProvider.ISAGHA,
            "https://example.com/gold",
            SourceTime.Timestamp(fetched),
            fetched,
        ),
    )

    private fun fxQuote(provider: QuoteProvider, rate: String) = FxQuote(
        rate = FxRate(Decimal.parseCanonical(rate)),
        provenance = QuoteProvenance.live(provider, "https://example.com/fx", SourceTime.Unknown, fetched),
    )

    @Test
    fun revisionCheckedUpdateAndDeleteReportRowCounts() = inDatabase { path ->
        val db = open(path)
        val dao = db.goldHoldingDao()
        dao.insert(gold("g1").toEntity(revision = 1))

        val edited = gold("g1", at = created).copy(
            updatedAt = later,
            weight = GoldWeight(Decimal.parseCanonical("3")),
        )
        assertEquals(0, dao.updateIfRevision(edited.toEntity(revision = 2), expectedRevision = 5))
        assertEquals(1, dao.updateIfRevision(edited.toEntity(revision = 2), expectedRevision = 1))
        assertEquals(0, dao.updateIfRevision(edited.toEntity(revision = 3), expectedRevision = 1))

        val stored = assertNotNull(dao.get("g1")).toStored()
        assertEquals(2L, stored.revision)
        assertEquals(edited, stored.value)

        assertEquals(0, dao.deleteIfRevision("g1", expectedRevision = 1))
        assertEquals(1, dao.deleteIfRevision("g1", expectedRevision = 2))
        assertNull(dao.get("g1"))
        db.close()
    }

    @Test
    fun observedListsAreOrderedAndEmitAfterWrites() = inDatabase { path ->
        val db = open(path)
        val dao = db.goldHoldingDao()
        assertTrue(dao.observeAll().first().isEmpty())
        dao.insert(gold("b", at = later).toEntity(revision = 1))
        dao.insert(gold("a", at = created).toEntity(revision = 1))
        dao.insert(gold("c", at = later).toEntity(revision = 1))
        assertEquals(listOf("a", "b", "c"), dao.observeAll().first().map { it.id })
        db.close()
    }

    @Test
    fun aWaitingCollectorSeesALaterWrite() = inDatabase { path ->
        val db = open(path)
        val dao = db.goldHoldingDao()
        coroutineScope {
            val seen = async(Dispatchers.Default) { dao.observeAll().first { it.size == 2 } }
            dao.insert(gold("x").toEntity(revision = 1))
            dao.insert(gold("y").toEntity(revision = 1))
            assertEquals(2, seen.await().size)
        }
        db.close()
    }

    @Test
    fun aBankWithAccountsCannotBeDeletedAndAnAccountNeedsAnExistingBank() = inDatabase { path ->
        val db = open(path)
        val banks = db.bankDao()
        val accounts = db.bankAccountDao()
        banks.insert(bank("b1").toEntity(revision = 1))

        assertFails { accounts.insert(account("orphan", bankId = "missing").toEntity(revision = 1)) }
        accounts.insert(account("a1", bankId = "b1").toEntity(revision = 1))

        assertFails { banks.deleteIfRevision("b1", expectedRevision = 1) }
        assertNotNull(banks.get("b1"))

        assertEquals(1, accounts.deleteIfRevision("a1", expectedRevision = 1))
        assertEquals(1, banks.deleteIfRevision("b1", expectedRevision = 1))
        db.close()
    }

    @Test
    fun aBankIsReadTogetherWithItsAccountsInCreationOrder() = inDatabase { path ->
        val db = open(path)
        db.bankDao().insert(bank("b1").toEntity(revision = 1))
        db.bankDao().insert(bank("b2").toEntity(revision = 1))
        db.bankAccountDao().insert(account("a2", "b1", at = later).toEntity(revision = 1))
        db.bankAccountDao().insert(account("a1", "b1", at = created).toEntity(revision = 1))

        val detail = assertNotNull(db.bankDao().observeDetail("b1").first().toBankDetail())
        assertEquals(listOf("a1", "a2"), detail.accounts.map { it.value.id.value })
        assertEquals("b1", detail.bank.value.id.value)

        assertTrue(assertNotNull(db.bankDao().observeDetail("b2").first().toBankDetail()).accounts.isEmpty())
        assertNull(db.bankDao().observeDetail("missing").first().toBankDetail())
        db.close()
    }

    @Test
    fun validationTriggersRejectInvalidEnumsTimestampsRevisionsAndDecimals() = inDatabase { path ->
        createDatabase(path)
        withRawConnection(path) { connection ->
            assertRejected(connection, goldSql(karat = "K22"), "karat")
            assertRejected(connection, goldSql(form = "COIN"), "form")
            listOf("1.234", "01.5", "1.50", "0", "-1", "", "1,000", "2.5.1").forEach { weight ->
                assertRejected(connection, goldSql(weight = weight), "weight")
            }
            assertRejected(connection, goldSql(updatedSeconds = 1_699_999_999), "updatedAt")
            assertRejected(connection, goldSql(updatedNanos = 1_000_000_000), "nanoseconds")
            assertRejected(connection, goldSql(revision = 0), "revision")
            assertRejected(connection, goldSql(id = " "), "id")
            connection.execute(goldSql(id = "valid"))
        }
    }

    @Test
    fun updatesKeepIdentityAndAdvanceTheRevisionByOne() = inDatabase { path ->
        createDatabase(path)
        withRawConnection(path) { connection ->
            connection.execute(goldSql(id = "u1"))
            assertRejected(
                connection,
                "UPDATE gold_holdings SET revision = 3 WHERE id = 'u1'",
                "revision must increase",
            )
            assertRejected(
                connection,
                "UPDATE gold_holdings SET created_seconds = 1 WHERE id = 'u1'",
                "creation time is immutable",
            )
            assertRejected(connection, "UPDATE gold_holdings SET id = 'u2' WHERE id = 'u1'", "id is immutable")
            connection.execute("UPDATE gold_holdings SET revision = 2 WHERE id = 'u1'")
        }
    }

    @Test
    fun paymentNoteAndQuoteRowsAreValidatedByTheirTriggers() = inDatabase { path ->
        createDatabase(path)
        withRawConnection(path) { connection ->
            assertRejected(connection, zakatSql(currency = "USD"), "recorded in EGP")
            assertRejected(connection, zakatSql(paidOn = "2026-3-1"), "paid_on")
            assertRejected(connection, zakatSql(amount = "0"), "amount")
            assertRejected(connection, noteSql(title = " padded"), "title")
            assertRejected(connection, noteSql(body = "   "), "body")

            assertRejected(connection, remoteSql(provider = "EXCHANGE_RATE_API"), "gold quotes come from")
            assertRejected(connection, remoteSql(url = "http://example.com"), "HTTPS")
            assertRejected(connection, remoteSql(date = "'2026-06-01'"), "timestamp source time")
            assertRejected(connection, remoteSql(kind = "DATE_ONLY", date = "NULL"), "date-only")
            assertRejected(connection, remoteSql(value = "4100.50"), "value")

            assertRejected(connection, manualSql(key = "GOLD_21_BUY"), "only gold sell prices")
            assertRejected(connection, manualSql(pinned = 2), "pinned")

            connection.execute(zakatSql())
            connection.execute(noteSql())
            connection.execute(remoteSql())
            connection.execute(manualSql())
        }
    }

    @Test
    fun declaredIndexesAndEveryTriggerExistInTheOpenedFile() = inDatabase { path ->
        createDatabase(path)
        withRawConnection(path) { connection ->
            val indexes = connection.names("SELECT name FROM sqlite_master WHERE type = 'index'")
            listOf(
                "index_gold_holdings_created_seconds_created_nanos_id",
                "index_banks_created_seconds_created_nanos_id",
                "index_bank_accounts_bank_id_created_seconds_created_nanos_id",
                "index_cash_balances_created_seconds_created_nanos_id",
                "index_zakat_payments_paid_on_id",
                "index_personal_notes_updated_seconds_updated_nanos_id",
            ).forEach { name -> assertTrue(name in indexes, "missing index $name") }

            val triggers = connection.names("SELECT name FROM sqlite_master WHERE type = 'trigger'")
            assertEquals(ValidationTriggersV1.triggerNames.toSet(), triggers.toSet())
        }
    }

    @Test
    fun recordsPersistAcrossReopenAndAnotherKeyIsRefused() = inDatabase { path ->
        val first = open(path)
        first.goldHoldingDao().insert(gold("keep").toEntity(revision = 1))
        first.close()

        val reopened = open(path)
        assertEquals(listOf("keep"), reopened.goldHoldingDao().observeAll().first().map { it.id })
        reopened.close()

        val intruder = open(path, wrongKey)
        assertFails { intruder.goldHoldingDao().observeAll().first() }
        intruder.close()
    }

    @Test
    fun aClosedDatabaseEndsAllAccess() = inDatabase { path ->
        val db = open(path)
        db.goldHoldingDao().insert(gold("x").toEntity(revision = 1))
        db.close()
        assertFails { db.goldHoldingDao().observeAll().first() }
    }

    @Test
    fun aRefreshBatchCommitsCompletelyOrNotAtAll() = inDatabase { path ->
        val db = open(path)
        val quotes = db.remoteQuoteDao()
        val sell = goldQuote(MerchantSide.SELLS_TO_CUSTOMER, "4100").toRemoteEntity(null, null)
        val buy = goldQuote(MerchantSide.BUYS_FROM_CUSTOMER, "4000").toRemoteEntity(null, null)
        val broken = buy.copy(quoteKey = "GOLD_24_SELL", provider = "EXCHANGE_RATE_API")

        assertFails { quotes.upsertAll(listOf(sell, broken)) }
        assertTrue(quotes.observeAll().first().isEmpty())

        quotes.upsertAll(listOf(sell, buy))
        assertEquals(2, quotes.observeAll().first().size)
        db.close()
    }

    @Test
    fun fxProvidersKeepTheirOwnQuotes() = inDatabase { path ->
        val db = open(path)
        val quotes = db.remoteQuoteDao()
        quotes.upsertAll(
            listOf(
                fxQuote(QuoteProvider.EXCHANGE_RATE_API, "50").toRemoteEntity(null, null),
                fxQuote(QuoteProvider.FAWAZ_CURRENCY_API, "51").toRemoteEntity(null, null),
            ),
        )
        quotes.upsertAll(listOf(fxQuote(QuoteProvider.FAWAZ_CURRENCY_API, "52").toRemoteEntity(null, null)))

        val rows = quotes.observeAll().first()
        assertEquals(listOf("EXCHANGE_RATE_API", "FAWAZ_CURRENCY_API"), rows.map { it.provider })
        assertEquals(listOf("50", "52"), rows.map { it.value })
        db.close()
    }

    @Test
    fun aManualSlotIsCreatedOnceReplacedAtItsRevisionAndUnpinnedWithoutLosingTheValue() = inDatabase { path ->
        val db = open(path)
        val manual = db.manualQuoteDao()
        val entered = Instant.fromEpochSeconds(1_700_000_000, 0)
        val input = ManualQuoteInput(QuoteKey.UsdEgp, Decimal.parseCanonical("51"), note = null)

        manual.insert(input.toEntity(entered, pinned = true, revision = 1))
        assertFails { manual.insert(input.toEntity(entered, pinned = true, revision = 1)) }

        assertEquals(0, manual.replaceIfRevision("USD_EGP", 9, "52", 1_700_000_100, 0, null))
        assertEquals(1, manual.replaceIfRevision("USD_EGP", 1, "52", 1_700_000_100, 0, null))
        assertEquals(1, manual.unpinIfRevision("USD_EGP", 2))

        val row = assertNotNull(manual.get("USD_EGP"))
        assertEquals("52", row.value)
        assertFalse(row.pinned)
        assertEquals(3L, row.revision)

        assertEquals(0, manual.deleteIfRevision("USD_EGP", 2))
        assertEquals(1, manual.deleteIfRevision("USD_EGP", 3))
        assertNull(manual.get("USD_EGP"))
        db.close()
    }

    private fun assertRejected(connection: SQLiteConnection, sql: String, fragment: String) {
        val error = assertFails { connection.execute(sql) }
        assertTrue(error.message.orEmpty().contains(fragment), "expected '$fragment' in '${error.message}'")
    }

    private fun SQLiteConnection.execute(sql: String) {
        prepare(sql).useStatement { statement -> statement.step() }
    }

    private fun SQLiteConnection.names(sql: String): List<String> = prepare(sql).useStatement { statement ->
        val names = mutableListOf<String>()
        while (statement.step()) names += statement.getText(0)
        names
    }

    private inline fun <T> SQLiteStatement.useStatement(block: (SQLiteStatement) -> T): T = try {
        block(this)
    } finally {
        close()
    }

    private fun goldSql(
        id: String = "g1",
        karat: String = "K24",
        form: String = "BAR",
        weight: String = "2.5",
        updatedSeconds: Long = 1_700_000_000,
        updatedNanos: Int = 0,
        revision: Long = 1,
    ) = "INSERT INTO gold_holdings (id, created_seconds, created_nanos, updated_seconds, updated_nanos, revision, " +
        "karat, form, weight_grams, note) VALUES ('$id', 1700000000, 0, $updatedSeconds, $updatedNanos, $revision, " +
        "'$karat', '$form', '$weight', NULL)"

    private fun zakatSql(paidOn: String = "2026-03-01", amount: String = "250", currency: String = "EGP") =
        "INSERT INTO zakat_payments (id, created_seconds, created_nanos, updated_seconds, updated_nanos, revision, " +
            "paid_on, amount, currency, note) VALUES ('z1', 1700000000, 0, 1700000000, 0, 1, '$paidOn', '$amount', " +
            "'$currency', NULL)"

    private fun noteSql(title: String = "Title", body: String = "Body") =
        "INSERT INTO personal_notes (id, created_seconds, created_nanos, updated_seconds, updated_nanos, revision, " +
            "title, body) VALUES ('n1', 1700000000, 0, 1700000000, 0, 1, '$title', '$body')"

    private fun remoteSql(
        key: String = "GOLD_24_SELL",
        provider: String = "ISAGHA",
        value: String = "4100",
        url: String = "https://example.com/gold",
        kind: String = "TIMESTAMP",
        date: String = "NULL",
    ): String {
        val seconds = if (kind == "TIMESTAMP") "1700000000" else "NULL"
        val nanos = if (kind == "TIMESTAMP") "0" else "NULL"
        return "INSERT INTO remote_quotes (quote_key, provider, value, source_url, source_time_kind, source_seconds, " +
            "source_nanos, source_date, fetched_seconds, fetched_nanos, recorded_seconds, recorded_nanos, note, " +
            "next_update_seconds, next_update_nanos, attribution_url) VALUES ('$key', '$provider', '$value', '$url', " +
            "'$kind', $seconds, $nanos, $date, 1700000000, 0, 1700000000, 0, NULL, NULL, NULL, NULL)"
    }

    private fun manualSql(key: String = "GOLD_24_SELL", pinned: Int = 1) =
        "INSERT INTO manual_quotes (quote_key, value, entered_seconds, entered_nanos, pinned, note, revision) " +
            "VALUES ('$key', '4100', 1700000000, 0, $pinned, NULL, 1)"
}
