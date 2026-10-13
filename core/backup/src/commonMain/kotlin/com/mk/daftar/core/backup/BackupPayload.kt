package com.mk.daftar.core.backup

import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.domain.repository.InactivityTimeout
import com.mk.daftar.core.domain.repository.RemoteQuoteRecord
import com.mk.daftar.core.domain.repository.ThemeMode
import com.mk.daftar.core.domain.repository.isManuallyEntered
import com.mk.daftar.core.model.Bank
import com.mk.daftar.core.model.BankAccount
import com.mk.daftar.core.model.BankAccountId
import com.mk.daftar.core.model.BankId
import com.mk.daftar.core.model.CashBalance
import com.mk.daftar.core.model.CashBalanceId
import com.mk.daftar.core.model.CashKind
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.FxBasis
import com.mk.daftar.core.model.FxQuote
import com.mk.daftar.core.model.FxRate
import com.mk.daftar.core.model.GoldForm
import com.mk.daftar.core.model.GoldHolding
import com.mk.daftar.core.model.GoldHoldingId
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.GoldPrice
import com.mk.daftar.core.model.GoldQuote
import com.mk.daftar.core.model.GoldWeight
import com.mk.daftar.core.model.InstitutionId
import com.mk.daftar.core.model.MerchantSide
import com.mk.daftar.core.model.Money
import com.mk.daftar.core.model.NoteId
import com.mk.daftar.core.model.PersonalNote
import com.mk.daftar.core.model.PriceQuote
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.QuoteKind
import com.mk.daftar.core.model.QuoteProvenance
import com.mk.daftar.core.model.QuoteProvider
import com.mk.daftar.core.model.SourceTime
import com.mk.daftar.core.model.ZakatPayment
import com.mk.daftar.core.model.ZakatPaymentId
import com.mk.daftar.core.model.ZakatYear
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/*
 * The portable payload, schema version 3. It is plain UTF-8 JSON with the property order below, so one snapshot always
 * serializes to the same bytes. Decimals are canonical plain strings, timestamps are Instant.toString() text, dates are
 * ISO text, and enums are their constant names. Nothing here is a database column or a device key.
 *
 * Version 3 adds the bank identity and the FX basis of remote quotes. Versions 1 and 2 are kept as their own frozen
 * documents, so old backups still decode and re-encode to their own bytes. A version 1 payload has no declarations and
 * no tracking start, and its payments have no year. A version 2 payload has no identity and no FX basis.
 */

@Serializable
internal class PayloadDocument(
    val schemaVersion: Int,
    val exportedAt: String,
    val appVersion: String,
    val preferences: PreferencesPayload,
    val goldHoldings: List<GoldPayload>,
    val banks: List<BankPayload>,
    val bankAccounts: List<BankAccountPayload>,
    val cashBalances: List<CashPayload>,
    val zakatPayments: List<ZakatPayload>,
    val personalNotes: List<NotePayload>,
    val remoteQuotes: List<RemoteQuotePayload>,
    val manualQuotes: List<ManualQuotePayload>,
    val zakatTrackingStartYear: Int?,
    val zakatYears: List<ZakatYearPayload>,
)

/** The frozen version-two document. Banks carry no identity and remote quotes carry no FX basis. */
@Serializable
internal class PayloadDocumentV2(
    val schemaVersion: Int,
    val exportedAt: String,
    val appVersion: String,
    val preferences: PreferencesPayload,
    val goldHoldings: List<GoldPayload>,
    val banks: List<BankPayloadV2>,
    val bankAccounts: List<BankAccountPayload>,
    val cashBalances: List<CashPayload>,
    val zakatPayments: List<ZakatPayload>,
    val personalNotes: List<NotePayload>,
    val remoteQuotes: List<RemoteQuotePayloadV2>,
    val manualQuotes: List<ManualQuotePayload>,
    val zakatTrackingStartYear: Int?,
    val zakatYears: List<ZakatYearPayload>,
)

/** The frozen version-one document. Its payments carry no year, and it has no declarations. */
@Serializable
internal class PayloadDocumentV1(
    val schemaVersion: Int,
    val exportedAt: String,
    val appVersion: String,
    val preferences: PreferencesPayload,
    val goldHoldings: List<GoldPayload>,
    val banks: List<BankPayloadV2>,
    val bankAccounts: List<BankAccountPayload>,
    val cashBalances: List<CashPayload>,
    val zakatPayments: List<ZakatPayloadV1>,
    val personalNotes: List<NotePayload>,
    val remoteQuotes: List<RemoteQuotePayloadV2>,
    val manualQuotes: List<ManualQuotePayload>,
)

@Serializable
internal class PreferencesPayload(val themeMode: String, val language: String, val inactivityTimeoutSeconds: Int)

@Serializable
internal class GoldPayload(
    val id: String,
    val karat: String,
    val form: String,
    val weightGrams: String,
    val note: String?,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
internal class BankPayload(
    val id: String,
    val name: String,
    val createdAt: String,
    val updatedAt: String,
    /** The catalog institution ID, or null for a custom or unidentified bank. */
    val institutionId: String?,
    /** Whether the user made the identity choice. */
    val identityReviewed: Boolean,
)

/** The version-two bank shape, which has no identity. */
@Serializable
internal class BankPayloadV2(val id: String, val name: String, val createdAt: String, val updatedAt: String)

@Serializable
internal class BankAccountPayload(
    val id: String,
    val bankId: String,
    val name: String,
    val balance: String,
    val currency: String,
    val note: String?,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
internal class CashPayload(
    val id: String,
    val name: String,
    val kind: String,
    val balance: String,
    val currency: String,
    val note: String?,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
internal class ZakatPayloadV1(
    val id: String,
    val paidOn: String,
    val amount: String,
    val currency: String,
    val note: String?,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
internal class ZakatPayload(
    val id: String,
    val paidOn: String,
    val amount: String,
    val currency: String,
    val note: String?,
    val createdAt: String,
    val updatedAt: String,
    /** The linked Hijri year, or null for an unassigned payment. */
    val forHijriYear: Int?,
)

@Serializable
internal class ZakatYearPayload(
    val hijriYear: Int,
    val paid: Boolean,
    val markedAt: String?,
    val overrideDays: Int,
    val userAdjusted: Boolean,
)

@Serializable
internal class NotePayload(
    val id: String,
    val title: String,
    val body: String,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
internal class RemoteQuotePayload(
    val quoteKey: String,
    val provider: String,
    val value: String,
    val sourceUrl: String,
    /** TIMESTAMP, DATE_ONLY or UNKNOWN. [sourceTime] is present exactly for the first two. */
    val sourceTimeKind: String,
    val sourceTime: String?,
    val fetchedAt: String,
    val recordedAt: String,
    val note: String?,
    val nextUpdateAt: String?,
    val attributionUrl: String?,
    /** The FX basis for USD to EGP, and null for gold. */
    val basis: String?,
)

/** The version-two remote quote shape, which has no FX basis. */
@Serializable
internal class RemoteQuotePayloadV2(
    val quoteKey: String,
    val provider: String,
    val value: String,
    val sourceUrl: String,
    val sourceTimeKind: String,
    val sourceTime: String?,
    val fetchedAt: String,
    val recordedAt: String,
    val note: String?,
    val nextUpdateAt: String?,
    val attributionUrl: String?,
)

@Serializable
internal class ManualQuotePayload(
    val quoteKey: String,
    val value: String,
    val enteredAt: String,
    val pinned: Boolean,
    val note: String?,
)

/** A payload that does not describe valid records. Carries no value, only the reason category. */
internal class CorruptPayloadException : RuntimeException("corrupt backup payload")

internal fun BackupSnapshot.toDocument(exportedAt: Instant, appVersion: String): PayloadDocument = PayloadDocument(
    schemaVersion = BackupFormat.PAYLOAD_SCHEMA_VERSION,
    exportedAt = exportedAt.toString(),
    appVersion = appVersion,
    preferences = PreferencesPayload(
        themeMode = preferences.themeMode.name,
        language = preferences.language.name,
        inactivityTimeoutSeconds = preferences.inactivityTimeout.seconds,
    ),
    goldHoldings = goldHoldings.sortedBy { it.id.value }.map { it.toPayload() },
    banks = banks.sortedBy {
        it.id.value
    }.map { it.toPayload() },
    bankAccounts = bankAccounts.sortedBy { it.id.value }.map { it.toPayload() },
    cashBalances = cashBalances.sortedBy { it.id.value }.map { it.toPayload() },
    zakatPayments = zakatPayments.sortedBy { it.id.value }.map { it.toPayload() },
    personalNotes = personalNotes.sortedBy { it.id.value }.map {
        NotePayload(it.id.value, it.title, it.body, it.createdAt.text(), it.updatedAt.text())
    },
    remoteQuotes = remoteQuotes.map { it.toPayload() }.sortedWith(
        compareBy<RemoteQuotePayload> { it.quoteKey }.thenBy { it.provider },
    ),
    manualQuotes = manualQuotes.map { it.toManualPayload() }.sortedBy { it.quoteKey },
    zakatTrackingStartYear = zakatTrackingStartYear,
    zakatYears = zakatYears.sortedBy { it.hijriYear }.map { it.toPayload() },
)

/** A version-one document as version two: no declarations, no tracking start, and unassigned payments. */
internal fun PayloadDocumentV1.toV2(): PayloadDocumentV2 = PayloadDocumentV2(
    schemaVersion = 2,
    exportedAt = exportedAt,
    appVersion = appVersion,
    preferences = preferences,
    goldHoldings = goldHoldings,
    banks = banks,
    bankAccounts = bankAccounts,
    cashBalances = cashBalances,
    zakatPayments = zakatPayments.map {
        ZakatPayload(
            id = it.id,
            paidOn = it.paidOn,
            amount = it.amount,
            currency = it.currency,
            note = it.note,
            createdAt = it.createdAt,
            updatedAt = it.updatedAt,
            forHijriYear = null,
        )
    },
    personalNotes = personalNotes,
    remoteQuotes = remoteQuotes,
    manualQuotes = manualQuotes,
    zakatTrackingStartYear = null,
    zakatYears = emptyList(),
)

/**
 * A version-two document as the current shape. A bank has no identity, so it is unreviewed. A stored FX rate gets the
 * basis its provider identity proves; a rate whose identity does not prove one is legacy unknown.
 */
internal fun PayloadDocumentV2.toCurrent(): PayloadDocument = PayloadDocument(
    schemaVersion = BackupFormat.PAYLOAD_SCHEMA_VERSION,
    exportedAt = exportedAt,
    appVersion = appVersion,
    preferences = preferences,
    goldHoldings = goldHoldings,
    banks = banks.map {
        BankPayload(it.id, it.name, it.createdAt, it.updatedAt, institutionId = null, identityReviewed = false)
    },
    bankAccounts = bankAccounts,
    cashBalances = cashBalances,
    zakatPayments = zakatPayments,
    personalNotes = personalNotes,
    remoteQuotes = remoteQuotes.map { it.toCurrent() },
    manualQuotes = manualQuotes,
    zakatTrackingStartYear = zakatTrackingStartYear,
    zakatYears = zakatYears,
)

private fun RemoteQuotePayloadV2.toCurrent(): RemoteQuotePayload {
    val basis = if (quoteKey != "USD_EGP") {
        null
    } else if (provider == "EXCHANGE_RATE_API" || provider == "FAWAZ_CURRENCY_API") {
        "MID_MARKET_ESTIMATE"
    } else {
        "LEGACY_UNKNOWN"
    }
    return RemoteQuotePayload(
        quoteKey = quoteKey,
        provider = provider,
        value = value,
        sourceUrl = sourceUrl,
        sourceTimeKind = sourceTimeKind,
        sourceTime = sourceTime,
        fetchedAt = fetchedAt,
        recordedAt = recordedAt,
        note = note,
        nextUpdateAt = nextUpdateAt,
        attributionUrl = attributionUrl,
        basis = basis,
    )
}

/** Rebuilds the model from a decoded payload. Every value goes through its model constructor. */
internal fun PayloadDocument.toSnapshot(): BackupSnapshot {
    if (schemaVersion != BackupFormat.PAYLOAD_SCHEMA_VERSION) throw CorruptPayloadException()
    return try {
        BackupSnapshot(
            preferences = BackupPreferences(
                themeMode = enumByName<ThemeMode>(preferences.themeMode),
                language = enumByName<AppLanguage>(preferences.language),
                inactivityTimeout = InactivityTimeout.entries.firstOrNull {
                    it.seconds == preferences.inactivityTimeoutSeconds
                } ?: throw CorruptPayloadException(),
            ),
            goldHoldings = goldHoldings.map { it.toModel() },
            banks = banks.map { it.toModel() },
            bankAccounts = bankAccounts.map { it.toModel() },
            cashBalances = cashBalances.map { it.toModel() },
            zakatPayments = zakatPayments.map { it.toModel() },
            personalNotes = personalNotes.map {
                PersonalNote(NoteId(it.id), it.title, it.body, it.createdAt.instant(), it.updatedAt.instant())
            },
            remoteQuotes = remoteQuotes.map { it.toModel() },
            manualQuotes = manualQuotes.map { it.toModel() },
            zakatYears = zakatYears.map { it.toModel() },
            zakatTrackingStartYear = zakatTrackingStartYear,
        )
    } catch (failure: IllegalArgumentException) {
        // A model constructor refused the value. The message could name a record, so it is dropped here.
        throw CorruptPayloadException()
    }
}

private fun GoldHolding.toPayload(): GoldPayload = GoldPayload(
    id = id.value,
    karat = karat.name,
    form = form.name,
    weightGrams = weight.grams.toPlainString(),
    note = note,
    createdAt = createdAt.text(),
    updatedAt = updatedAt.text(),
)

private fun GoldPayload.toModel(): GoldHolding = GoldHolding(
    id = GoldHoldingId(id),
    karat = enumByName<GoldKarat>(karat),
    form = enumByName<GoldForm>(form),
    weight = GoldWeight(decimal(weightGrams)),
    note = note,
    createdAt = createdAt.instant(),
    updatedAt = updatedAt.instant(),
)

private fun Bank.toPayload(): BankPayload = BankPayload(
    id = id.value,
    name = name,
    createdAt = createdAt.text(),
    updatedAt = updatedAt.text(),
    institutionId = institutionId?.value,
    identityReviewed = identityReviewed,
)

private fun BankPayload.toModel(): Bank = Bank(
    id = BankId(id),
    name = name,
    createdAt = createdAt.instant(),
    updatedAt = updatedAt.instant(),
    institutionId = institutionId?.let { InstitutionId.parseOrNull(it) ?: throw CorruptPayloadException() },
    identityReviewed = identityReviewed,
)

private fun BankAccount.toPayload(): BankAccountPayload = BankAccountPayload(
    id = id.value,
    bankId = bankId.value,
    name = name,
    balance = balance.amount.toPlainString(),
    currency = balance.currency.name,
    note = note,
    createdAt = createdAt.text(),
    updatedAt = updatedAt.text(),
)

private fun BankAccountPayload.toModel(): BankAccount = BankAccount(
    id = BankAccountId(id),
    bankId = BankId(bankId),
    name = name,
    balance = Money(decimal(balance), enumByName<Currency>(currency)),
    note = note,
    createdAt = createdAt.instant(),
    updatedAt = updatedAt.instant(),
)

private fun CashBalance.toPayload(): CashPayload = CashPayload(
    id = id.value,
    name = name,
    kind = kind.name,
    balance = balance.amount.toPlainString(),
    currency = balance.currency.name,
    note = note,
    createdAt = createdAt.text(),
    updatedAt = updatedAt.text(),
)

private fun CashPayload.toModel(): CashBalance = CashBalance(
    id = CashBalanceId(id),
    name = name,
    kind = enumByName<CashKind>(kind),
    balance = Money(decimal(balance), enumByName<Currency>(currency)),
    note = note,
    createdAt = createdAt.instant(),
    updatedAt = updatedAt.instant(),
)

private fun ZakatPayment.toPayload(): ZakatPayload = ZakatPayload(
    id = id.value,
    paidOn = paidOn.toString(),
    amount = amount.amount.toPlainString(),
    currency = amount.currency.name,
    note = note,
    createdAt = createdAt.text(),
    updatedAt = updatedAt.text(),
    forHijriYear = forHijriYear,
)

private fun ZakatPayload.toModel(): ZakatPayment = ZakatPayment(
    id = ZakatPaymentId(id),
    paidOn = canonicalDate(paidOn),
    amount = Money(decimal(amount), enumByName<Currency>(currency)),
    note = note,
    createdAt = createdAt.instant(),
    updatedAt = updatedAt.instant(),
    forHijriYear = forHijriYear,
)

private fun ZakatYear.toPayload(): ZakatYearPayload = ZakatYearPayload(
    hijriYear = hijriYear,
    paid = paid,
    markedAt = markedAt?.text(),
    overrideDays = overrideDays,
    userAdjusted = userAdjusted,
)

private fun ZakatYearPayload.toModel(): ZakatYear = ZakatYear(
    hijriYear = hijriYear,
    paid = paid,
    markedAt = markedAt?.instant(),
    overrideDays = overrideDays,
    userAdjusted = userAdjusted,
)

private fun RemoteQuoteRecord.toPayload(): RemoteQuotePayload {
    val quote = quote
    val provenance = quote.provenance
    val basis = (quote as? FxQuote)?.basis?.name
    val time = provenance.sourceTime
    val (kind, text) = when (time) {
        is SourceTime.Timestamp -> "TIMESTAMP" to time.value.text()
        is SourceTime.DateOnly -> "DATE_ONLY" to time.value.toString()
        SourceTime.Unknown -> "UNKNOWN" to null
    }
    return RemoteQuotePayload(
        quoteKey = PortableQuoteKey.encode(quote.quoteKey()),
        provider = provenance.provider.name,
        value = quote.value().toPlainString(),
        sourceUrl = requireNotNull(provenance.sourceUrl) { "remote quotes have a source" },
        sourceTimeKind = kind,
        sourceTime = text,
        fetchedAt = requireNotNull(provenance.fetchedAt) { "remote quotes have a fetch time" }.text(),
        recordedAt = provenance.recordedAt.text(),
        note = provenance.note,
        nextUpdateAt = nextUpdateAt?.text(),
        attributionUrl = attributionUrl,
        basis = basis,
    )
}

private fun RemoteQuotePayload.toModel(): RemoteQuoteRecord {
    val key = PortableQuoteKey.decode(quoteKey) ?: throw CorruptPayloadException()
    val providerKind = enumByName<QuoteProvider>(provider)
    if (!providerMatches(key, providerKind)) throw CorruptPayloadException()
    val time = when (sourceTimeKind) {
        "TIMESTAMP" -> SourceTime.Timestamp(instantOfPayload(sourceTime ?: throw CorruptPayloadException()))
        "DATE_ONLY" -> SourceTime.DateOnly(canonicalDate(sourceTime ?: throw CorruptPayloadException()))
        "UNKNOWN" -> if (sourceTime == null) SourceTime.Unknown else throw CorruptPayloadException()
        else -> throw CorruptPayloadException()
    }
    // Restored remote quotes are always cached, never live and never pinned.
    val provenance = QuoteProvenance(
        provider = providerKind,
        sourceUrl = sourceUrl,
        sourceTime = time,
        fetchedAt = fetchedAt.instant(),
        recordedAt = recordedAt.instant(),
        kind = QuoteKind.CACHED,
        manualPinned = false,
        note = note,
    )
    val quote: PriceQuote = when (key) {
        is QuoteKey.Gold -> {
            if (basis != null) throw CorruptPayloadException()
            GoldQuote(key, GoldPrice(decimal(value)), provenance)
        }

        QuoteKey.UsdEgp -> {
            val fxBasis = enumByName<FxBasis>(basis ?: throw CorruptPayloadException())
            FxQuote(FxRate(decimal(value)), provenance, fxBasis)
        }
    }
    return RemoteQuoteRecord(
        quote = quote,
        nextUpdateAt = nextUpdateAt?.instant(),
        attributionUrl = attributionUrl,
    )
}

private fun PriceQuote.toManualPayload(): ManualQuotePayload {
    val provenance = this.provenance
    return ManualQuotePayload(
        quoteKey = PortableQuoteKey.encode(quoteKey()),
        value = value().toPlainString(),
        enteredAt = provenance.recordedAt.text(),
        pinned = provenance.manualPinned,
        note = provenance.note,
    )
}

private fun ManualQuotePayload.toModel(): PriceQuote {
    val key = PortableQuoteKey.decode(quoteKey) ?: throw CorruptPayloadException()
    if (!key.isManuallyEntered()) throw CorruptPayloadException()
    val entered = enteredAt.instant()
    val provenance = QuoteProvenance(
        provider = QuoteProvider.USER,
        sourceUrl = null,
        sourceTime = SourceTime.Timestamp(entered),
        fetchedAt = null,
        recordedAt = entered,
        kind = QuoteKind.MANUAL,
        manualPinned = pinned,
        note = note,
    )
    return when (key) {
        is QuoteKey.Gold -> GoldQuote(key, GoldPrice(decimal(value)), provenance)
        QuoteKey.UsdEgp -> FxQuote(FxRate(decimal(value)), provenance)
    }
}

/** The provider must match the key: ISAGHA for gold, one of the two FX providers for USD to EGP. */
private fun providerMatches(key: QuoteKey, provider: QuoteProvider): Boolean = when (key) {
    is QuoteKey.Gold -> provider == QuoteProvider.ISAGHA

    QuoteKey.UsdEgp ->
        provider == QuoteProvider.BANQUE_MISR ||
            provider == QuoteProvider.EXCHANGE_RATE_API ||
            provider == QuoteProvider.FAWAZ_CURRENCY_API
}

private fun PriceQuote.quoteKey(): QuoteKey = when (this) {
    is GoldQuote -> key
    is FxQuote -> key
}

private fun PriceQuote.value(): Decimal = when (this) {
    is GoldQuote -> price.egpPerGram
    is FxQuote -> rate.egpPerUsd
}

/** Stable, explicit tokens for the seven quote keys. They are the format's own, not the database's. */
internal object PortableQuoteKey {
    private val all: List<QuoteKey> = buildList {
        GoldKarat.entries.forEach { karat -> MerchantSide.entries.forEach { side -> add(QuoteKey.Gold(karat, side)) } }
        add(QuoteKey.UsdEgp)
    }

    fun encode(key: QuoteKey): String = when (key) {
        is QuoteKey.Gold -> "GOLD_${karatToken(key.karat)}_${sideToken(key.side)}"
        QuoteKey.UsdEgp -> "USD_EGP"
    }

    fun decode(token: String): QuoteKey? = all.firstOrNull { encode(it) == token }

    private fun karatToken(karat: GoldKarat): String = karat.carats.toString()

    private fun sideToken(side: MerchantSide): String = when (side) {
        MerchantSide.SELLS_TO_CUSTOMER -> "SELL"
        MerchantSide.BUYS_FROM_CUSTOMER -> "BUY"
    }
}

private inline fun <reified E : Enum<E>> enumByName(name: String): E =
    enumValues<E>().firstOrNull { it.name == name } ?: throw CorruptPayloadException()

/** Canonical decimal text only: the same characters that [Decimal.toPlainString] writes. */
private fun decimal(text: String): Decimal {
    val parsed = Decimal.parseCanonicalOrNull(text) ?: throw CorruptPayloadException()
    if (parsed.toPlainString() != text) throw CorruptPayloadException()
    return parsed
}

/** ISO `YYYY-MM-DD`, required to round-trip to the same text. */
private fun canonicalDate(text: String): LocalDate {
    val date = try {
        LocalDate.parse(text)
    } catch (failure: IllegalArgumentException) {
        throw CorruptPayloadException()
    }
    if (date.toString() != text) throw CorruptPayloadException()
    return date
}

/** Instant text is accepted only in the one canonical form that [Instant.toString] writes. */
private fun instantOfPayload(text: String): Instant {
    val parsed = try {
        Instant.parse(text)
    } catch (failure: IllegalArgumentException) {
        throw CorruptPayloadException()
    }
    if (parsed.toString() != text) throw CorruptPayloadException()
    return parsed
}

private fun String.instant(): Instant = instantOfPayload(this)

private fun Instant.text(): String = toString()
