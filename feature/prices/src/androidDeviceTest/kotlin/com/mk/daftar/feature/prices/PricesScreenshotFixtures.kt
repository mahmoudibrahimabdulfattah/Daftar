package com.mk.daftar.feature.prices

import com.mk.daftar.core.domain.refresh.PriceRefreshStatus
import com.mk.daftar.core.domain.repository.QuoteSnapshot
import com.mk.daftar.core.domain.repository.RemoteQuoteRecord
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.valuation.PortfolioInput
import com.mk.daftar.core.domain.valuation.SelectedQuote
import com.mk.daftar.core.domain.valuation.selectQuotes
import com.mk.daftar.core.domain.valuation.selectRemoteQuotes
import com.mk.daftar.core.domain.valuation.valuePortfolio
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
import com.mk.daftar.core.model.PriceQuote
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.QuoteKind
import com.mk.daftar.core.model.QuoteProvenance
import com.mk.daftar.core.model.QuoteProvider
import com.mk.daftar.core.model.SourceTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** Fake data only. The screenshot host never sees a vault, a repository or a real balance. */
internal val SHOT_NOW: Instant = Instant.parse("2026-10-12T12:00:00Z")

private fun dec(text: String): Decimal = Decimal.parseCanonical(text)

private fun sell(karat: GoldKarat): QuoteKey = QuoteKey.Gold(karat, MerchantSide.SELLS_TO_CUSTOMER)

private fun sellKeyOfQuote(quote: PriceQuote): QuoteKey = when (quote) {
    is GoldQuote -> quote.key
    is FxQuote -> quote.key
}

private const val ISAGHA_URL = "https://market.isagha.com/prices"
private const val EXCHANGE_URL = "https://open.er-api.com/v6/latest/USD"

private fun remoteGold(
    karat: GoldKarat,
    price: String,
    side: MerchantSide = MerchantSide.SELLS_TO_CUSTOMER,
    fetchedAt: Instant = SHOT_NOW,
    cached: Boolean = false,
): RemoteQuoteRecord {
    val live = QuoteProvenance.live(QuoteProvider.ISAGHA, ISAGHA_URL, SourceTime.Timestamp(fetchedAt), fetchedAt)
    val provenance = if (cached) live.restoredFromStorage() else live
    return RemoteQuoteRecord(GoldQuote(QuoteKey.Gold(karat, side), GoldPrice(dec(price)), provenance), null, null)
}

private fun remoteUsd(rate: String, fetchedAt: Instant = SHOT_NOW, cached: Boolean = false): RemoteQuoteRecord {
    val live = QuoteProvenance.live(
        QuoteProvider.EXCHANGE_RATE_API,
        EXCHANGE_URL,
        SourceTime.DateOnly(LocalDate(2026, 10, 12)),
        fetchedAt,
    )
    val provenance = if (cached) live.restoredFromStorage() else live
    return RemoteQuoteRecord(FxQuote(FxRate(dec(rate)), provenance), null, "https://www.exchangerate-api.com")
}

private fun manualGold(karat: GoldKarat, price: String, revision: Long, pinned: Boolean): Stored<PriceQuote> = Stored(
    GoldQuote(QuoteKey.Gold(karat, MerchantSide.SELLS_TO_CUSTOMER), GoldPrice(dec(price)), manualProvenance(pinned)),
    revision,
)

private fun manualProvenance(pinned: Boolean): QuoteProvenance = QuoteProvenance(
    provider = QuoteProvider.USER,
    sourceUrl = null,
    sourceTime = SourceTime.Unknown,
    fetchedAt = null,
    recordedAt = SHOT_NOW - 1.days,
    kind = QuoteKind.MANUAL,
    manualPinned = pinned,
    note = "Local goldsmith quote",
)

private fun goldHolding(id: String, karat: GoldKarat, form: GoldForm, grams: String): GoldHolding = GoldHolding.create(
    id = GoldHoldingId(id),
    karat = karat,
    form = form,
    weight = GoldWeight(dec(grams)),
    note = null,
    createdAt = SHOT_NOW,
    updatedAt = SHOT_NOW,
)

/** Every sell and buy price, and the USD rate, all live: the complete table. */
private fun liveRemote(cached: Boolean = false): List<RemoteQuoteRecord> = listOf(
    remoteGold(GoldKarat.K24, "7069", cached = cached),
    remoteGold(GoldKarat.K21, "6185", cached = cached),
    remoteGold(GoldKarat.K18, "5301", cached = cached),
    remoteGold(GoldKarat.K24, "7012", MerchantSide.BUYS_FROM_CUSTOMER, cached = cached),
    remoteGold(GoldKarat.K21, "6135", MerchantSide.BUYS_FROM_CUSTOMER, cached = cached),
    remoteGold(GoldKarat.K18, "5259", MerchantSide.BUYS_FROM_CUSTOMER, cached = cached),
    remoteUsd("48.50", cached = cached),
)

private val holdings: List<GoldHolding> = listOf(
    goldHolding("bar-24", GoldKarat.K24, GoldForm.BAR, "1.50"),
    goldHolding("jew-21", GoldKarat.K21, GoldForm.JEWELRY, "4.00"),
    goldHolding("jew-18", GoldKarat.K18, GoldForm.JEWELRY, "3.29"),
)

/** The Prices state for [snapshot], computed by the same mapping the app uses. */
internal fun pricesStateFor(
    snapshot: QuoteSnapshot,
    refresh: PriceRefreshStatus = PriceRefreshStatus.Idle,
    heldGold: List<GoldHolding> = holdings,
    hidden: Boolean = false,
): PricesUiState {
    val selected = selectQuotes(snapshot, SHOT_NOW)
    val valuation = valuePortfolio(PortfolioInput(heldGold, emptyList(), emptyList()), selected)
    return pricesStateOf(
        snapshot = VaultRead.Ready(1, snapshot),
        selected = VaultRead.Ready(1, selected),
        valuation = VaultRead.Ready(1, valuation),
        holdings = VaultRead.Ready(1, heldGold.map { Stored(it, 1) }),
        now = SHOT_NOW,
        valuesHidden = hidden,
        refresh = refresh,
    )
}

internal fun liveSnapshot(): QuoteSnapshot = QuoteSnapshot(remote = liveRemote(), manual = emptyList())

internal fun cachedSnapshot(): QuoteSnapshot = QuoteSnapshot(remote = liveRemote(cached = true), manual = emptyList())

/** 21K pinned to a manual value; the remote 21K stays beneath it, and USD is live. */
internal fun pinnedSnapshot(): QuoteSnapshot = QuoteSnapshot(
    remote = liveRemote(cached = true),
    manual = listOf(manualGold(GoldKarat.K21, "6100", revision = 3, pinned = true)),
)

/** A cached 21K and USD from two days ago: past the stale window, so the stale state applies. */
internal fun staleSnapshot(): QuoteSnapshot {
    val old = SHOT_NOW - 2.days
    return QuoteSnapshot(
        remote = listOf(
            remoteGold(GoldKarat.K24, "7069", fetchedAt = old, cached = true),
            remoteGold(GoldKarat.K21, "6185", fetchedAt = old, cached = true),
            remoteGold(GoldKarat.K18, "5301", fetchedAt = old, cached = true),
            remoteUsd("48.50", fetchedAt = old, cached = true),
        ),
        manual = emptyList(),
    )
}

/** No 18K quote at all: the 18K holding cannot be valued, and the missing state says what is still here. */
internal fun missingSnapshot(): QuoteSnapshot = QuoteSnapshot(
    remote = liveRemote().filterNot { sellKeyOfQuote(it.quote) == sell(GoldKarat.K18) },
    manual = emptyList(),
)

/** The remote 18K sell is absent, and a 21K sell is pinned: the editor has both a source and an override to show. */
internal fun manualSnapshot(revision: Long = 3L, value: String = "6100"): QuoteSnapshot = QuoteSnapshot(
    remote = liveRemote(),
    manual = listOf(manualGold(GoldKarat.K21, value, revision, pinned = true)),
)

/** The remote 18K is absent and the manual 18K is only a last resort. */
internal fun lastResortSnapshot(): QuoteSnapshot = QuoteSnapshot(
    remote = liveRemote().filterNot { sellKeyOfQuote(it.quote) == sell(GoldKarat.K18) },
    manual = listOf(manualGold(GoldKarat.K18, "5200", revision = 2, pinned = false)),
)

/** The remote source beneath a key, as the editor shows it for comparison. */
internal fun sourceOf(snapshot: QuoteSnapshot, key: QuoteKey): SelectedQuote? =
    selectRemoteQuotes(snapshot, SHOT_NOW)[key]

/** The editor for [key] with [draft] typed, over [snapshot]. */
internal fun editorFor(
    snapshot: QuoteSnapshot,
    key: QuoteKey?,
    draft: String,
    note: String = "",
    validation: ManualValueProblem? = null,
    dirty: Boolean = true,
    conflict: Boolean = false,
    busy: Boolean = false,
    choosing: Boolean = false,
): ManualQuoteUiState {
    val slot = key?.let { manualSlotOf(snapshot, it) }
    val slotState = slot?.let {
        ManualSlotState(
            value = priceQuoteValue(it.value),
            revision = it.revision,
            pinned = it.value.provenance.manualPinned,
            enteredAt = it.value.provenance.recordedAt,
            note = it.value.provenance.note,
        )
    }
    return ManualQuoteUiState(
        key = key,
        choosing = choosing,
        loaded = true,
        draftValue = draft,
        draftNote = note,
        validation = validation,
        dirty = dirty,
        busy = busy,
        conflict = conflict,
        slot = slotState,
        source = key?.let { sourceOf(snapshot, it) },
    )
}
