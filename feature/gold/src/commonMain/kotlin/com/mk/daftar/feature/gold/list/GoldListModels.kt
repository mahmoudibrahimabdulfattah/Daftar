package com.mk.daftar.feature.gold.list

import androidx.navigation3.runtime.NavKey
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.valuation.AssetRef
import com.mk.daftar.core.domain.valuation.AssetValuation
import com.mk.daftar.core.domain.valuation.PortfolioValuation
import com.mk.daftar.core.domain.valuation.SelectedQuote
import com.mk.daftar.core.domain.valuation.SelectedQuotes
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.GoldForm
import com.mk.daftar.core.model.GoldHolding
import com.mk.daftar.core.model.GoldHoldingId
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.MerchantSide
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.ui.mvi.UiIntent
import com.mk.daftar.core.ui.mvi.UiState
import com.mk.daftar.feature.gold.GoldPhase
import com.mk.daftar.feature.gold.nonReadyPhase
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** One holding as the list shows it. [exactEgp] is null while the holding's karat has no merchant sell price. */
data class GoldHoldingRow(
    val id: GoldHoldingId,
    val karat: GoldKarat,
    val form: GoldForm,
    val weight: Decimal,
    val note: String?,
    val exactEgp: Decimal?,
)

/** The merchant sell quote a karat is valued at, with the time it was recorded in the device zone. */
data class GoldQuoteLine(val karat: GoldKarat, val quote: SelectedQuote?, val recordedAt: LocalDateTime?)

/**
 * The category figures. [totalEgp] is present only when every holding is valued. [subtotalEgp] sums the valued holdings
 * and is shown as an available subtotal while any holding waits for its price.
 */
data class GoldSummary(
    val totalWeight: Decimal,
    val totalEgp: Decimal?,
    val subtotalEgp: Decimal,
    val unvaluedCount: Int,
)

data class GoldListUiState(
    val phase: GoldPhase = GoldPhase.Loading,
    val rows: List<GoldHoldingRow> = emptyList(),
    val summary: GoldSummary? = null,
    val quotes: List<GoldQuoteLine> = emptyList(),
    val hidden: Boolean = false,
    /** The screen the user asked for while values are hidden. It opens only after an explicit reveal. */
    val pendingRoute: NavKey? = null,
) : UiState

sealed interface GoldListIntent : UiIntent {
    data object OpenAdd : GoldListIntent

    data class OpenHolding(val holdingId: GoldHoldingId) : GoldListIntent

    /** Shows the values, then opens the screen the user asked for while they were hidden. */
    data object RevealAndOpen : GoldListIntent

    data object CancelReveal : GoldListIntent

    data object ToggleHidden : GoldListIntent
}

/**
 * Builds the list from one generation's holdings, valuation and quotes. Holdings keep the repository order. A valuation
 * joins to a holding by its typed ID, never by position. A locked, failed or loading holdings or valuation read decides
 * the phase; the quotes only add provenance and never block the list.
 */
internal fun goldListStateOf(
    holdings: VaultRead<List<Stored<GoldHolding>>>,
    portfolio: VaultRead<PortfolioValuation>,
    quotes: VaultRead<SelectedQuotes>,
    hidden: Boolean,
    timeZone: TimeZone,
): GoldListUiState {
    val blocking = listOfNotNull(holdings.nonReadyPhase(), portfolio.nonReadyPhase())
    val phase = blocking.firstOrNull { it == GoldPhase.Locked }
        ?: blocking.firstOrNull { it is GoldPhase.Failed }
        ?: blocking.firstOrNull()
    if (phase != null) return GoldListUiState(phase = phase, hidden = hidden)
    val records = (holdings as? VaultRead.Ready)?.value?.map { it.value }
        ?: return GoldListUiState(phase = GoldPhase.Loading, hidden = hidden)
    val valuation = (portfolio as? VaultRead.Ready)?.value
        ?: return GoldListUiState(phase = GoldPhase.Loading, hidden = hidden)
    if (records.isEmpty()) return GoldListUiState(phase = GoldPhase.Empty, hidden = hidden)

    val valueById: Map<GoldHoldingId, Decimal> = valuation.gold.assets.mapNotNull { valued ->
        val ref = valued.asset as? AssetRef.Gold ?: return@mapNotNull null
        (valued as? AssetValuation.Valued)?.let { ref.id to it.exactEgp }
    }.toMap()
    val selected = (quotes as? VaultRead.Ready)?.value ?: SelectedQuotes.EMPTY
    val karats = GoldKarat.entries.filter { karat -> records.any { it.karat == karat } }
    val quoteLines = karats.map { karat ->
        val quote = selected[QuoteKey.Gold(karat, MerchantSide.SELLS_TO_CUSTOMER)]
        GoldQuoteLine(karat, quote, quote?.referenceAt?.toLocalDateTime(timeZone))
    }
    return GoldListUiState(
        phase = GoldPhase.Ready,
        rows = records.map { holding ->
            GoldHoldingRow(
                id = holding.id,
                karat = holding.karat,
                form = holding.form,
                weight = holding.weight.grams,
                note = holding.note,
                exactEgp = valueById[holding.id],
            )
        },
        summary = GoldSummary(
            totalWeight = records.fold(Decimal.ZERO) { sum, holding -> sum + holding.weight.grams },
            totalEgp = valuation.gold.totalEgp,
            subtotalEgp = valuation.gold.subtotalEgp,
            unvaluedCount = valuation.gold.unvaluedAssets.size,
        ),
        quotes = quoteLines,
        hidden = hidden,
    )
}
