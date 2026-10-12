package com.mk.daftar.feature.gold

import androidx.compose.runtime.Composable
import com.mk.daftar.core.domain.repository.QuoteSnapshot
import com.mk.daftar.core.domain.repository.RemoteQuoteRecord
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.valuation.SelectedQuotes
import com.mk.daftar.core.domain.valuation.selectQuotes
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.GoldForm
import com.mk.daftar.core.model.GoldHolding
import com.mk.daftar.core.model.GoldHoldingId
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.GoldPrice
import com.mk.daftar.core.model.GoldQuote
import com.mk.daftar.core.model.GoldWeight
import com.mk.daftar.core.model.MerchantSide
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.QuoteProvenance
import com.mk.daftar.core.model.QuoteProvider
import com.mk.daftar.core.model.SourceTime
import com.mk.daftar.feature.gold.editor.GoldConfirmation
import com.mk.daftar.feature.gold.editor.GoldDraft
import com.mk.daftar.feature.gold.editor.GoldEditorUiState
import com.mk.daftar.feature.gold.editor.GoldValues
import com.mk.daftar.feature.gold.list.GoldHoldingRow
import com.mk.daftar.feature.gold.list.GoldListUiState
import com.mk.daftar.feature.gold.list.GoldQuoteLine
import com.mk.daftar.feature.gold.list.GoldSummary
import com.mk.daftar.feature.gold.ui.GoldEditorContent
import com.mk.daftar.feature.gold.ui.GoldListContent
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime

/** Every frame the screenshot test captures, in both languages and themes. */
internal val goldFrameNames: List<String> = listOf(
    "list-ready",
    "list-empty",
    "list-masked",
    "list-incomplete",
    "add",
    "validation",
    "edit",
    "edit-masked",
    "edit-no-quote",
    "delete-confirm",
    "discard",
    "save-error",
    "conflict",
    "missing",
)

private val SHOT_NOW: Instant = Instant.parse("2026-10-12T09:40:00Z")
private val SHOT_RECORDED: LocalDateTime = LocalDateTime(2026, 10, 12, 9, 40)

private val MERCHANT_PRICES: Map<GoldKarat, String> = mapOf(
    GoldKarat.K24 to "7069",
    GoldKarat.K21 to "6185",
    GoldKarat.K18 to "5301",
)

private fun sell(karat: GoldKarat): QuoteKey.Gold = QuoteKey.Gold(karat, MerchantSide.SELLS_TO_CUSTOMER)

private fun dec(text: String): Decimal = Decimal.parseCanonical(text)

private fun remote(karat: GoldKarat): RemoteQuoteRecord {
    val provenance = QuoteProvenance.live(
        provider = QuoteProvider.ISAGHA,
        sourceUrl = "https://market.isagha.com/prices",
        sourceTime = SourceTime.Unknown,
        fetchedAt = SHOT_NOW,
    )
    val price = GoldPrice(dec(checkNotNull(MERCHANT_PRICES[karat])))
    return RemoteQuoteRecord(GoldQuote(sell(karat), price, provenance), nextUpdateAt = null, attributionUrl = null)
}

/** Selected quotes for the karats given, fetched fresh at the fixture instant. */
private fun quotesFor(vararg karats: GoldKarat): SelectedQuotes =
    selectQuotes(QuoteSnapshot(remote = karats.map { remote(it) }, manual = emptyList()), SHOT_NOW)

private fun holding(id: String, karat: GoldKarat, grams: String, form: GoldForm, note: String?): GoldHolding =
    GoldHolding.create(
        id = GoldHoldingId(id),
        karat = karat,
        form = form,
        weight = GoldWeight(dec(grams)),
        note = note,
        createdAt = SHOT_NOW,
        updatedAt = SHOT_NOW,
    )

private val holdings: List<GoldHolding> = listOf(
    holding("g1", GoldKarat.K21, "80", GoldForm.BAR, "للاحتفاظ"),
    holding("g2", GoldKarat.K21, "26.77", GoldForm.JEWELRY, null),
    holding("g3", GoldKarat.K18, "3.29", GoldForm.JEWELRY, null),
)

private fun rowsFor(quotes: SelectedQuotes): List<GoldHoldingRow> = holdings.map { stored ->
    val price = quotes[sell(stored.karat)]?.value
    GoldHoldingRow(
        id = stored.id,
        karat = stored.karat,
        form = stored.form,
        weight = stored.weight.grams,
        note = stored.note,
        exactEgp = price?.let { stored.weight.grams * it },
    )
}

private fun linesFor(quotes: SelectedQuotes): List<GoldQuoteLine> = listOf(GoldKarat.K21, GoldKarat.K18).map { karat ->
    GoldQuoteLine(karat, quotes[sell(karat)], SHOT_RECORDED.takeIf { quotes[sell(karat)] != null })
}

private fun readyList(hidden: Boolean = false): GoldListUiState {
    val quotes = quotesFor(GoldKarat.K24, GoldKarat.K21, GoldKarat.K18)
    return GoldListUiState(
        phase = com.mk.daftar.feature.gold.GoldPhase.Ready,
        rows = rowsFor(quotes),
        summary = GoldSummary(
            totalWeight = dec("110.06"),
            totalEgp = dec("677812.74"),
            subtotalEgp = dec("677812.74"),
            unvaluedCount = 0,
        ),
        quotes = linesFor(quotes),
        hidden = hidden,
    )
}

private fun incompleteList(): GoldListUiState {
    val quotes = quotesFor(GoldKarat.K21)
    return GoldListUiState(
        phase = com.mk.daftar.feature.gold.GoldPhase.Ready,
        rows = rowsFor(quotes),
        summary = GoldSummary(
            totalWeight = dec("110.06"),
            totalEgp = null,
            subtotalEgp = dec("660372.45"),
            unvaluedCount = 1,
        ),
        quotes = linesFor(quotes),
    )
}

private fun edit(
    draft: GoldDraft,
    isNew: Boolean = false,
    attempted: Boolean = false,
    quotes: SelectedQuotes = quotesFor(GoldKarat.K24, GoldKarat.K21, GoldKarat.K18),
): GoldEditorUiState {
    val baseline = if (isNew) {
        GoldValues(GoldKarat.K21, GoldForm.BAR, "", null)
    } else {
        GoldValues(draft.karat, draft.form, draft.weightText.trim(), draft.noteText.trim().ifEmpty { null })
    }
    return GoldEditorUiState(
        phase = com.mk.daftar.feature.gold.GoldPhase.Ready,
        isNew = isNew,
        draft = draft,
        baseline = baseline,
        attempted = attempted,
        quotes = quotes,
    )
}

private val SAVED_VALUES = GoldValues(GoldKarat.K21, GoldForm.BAR, "80", "للاحتفاظ")

private val savedDraft = GoldDraft(
    karat = GoldKarat.K21,
    form = GoldForm.BAR,
    weightText = "80",
    noteText = "للاحتفاظ",
)

/** The screen for one frame name. Every state is fixed, so each capture is repeatable. */
@Composable
internal fun GoldFrameContent(name: String) {
    when (name) {
        "list-ready" -> GoldListContent(state = readyList(), onIntent = {}, onBack = {})

        "list-empty" -> GoldListContent(
            state = GoldListUiState(phase = com.mk.daftar.feature.gold.GoldPhase.Empty),
            onIntent = {},
            onBack = {},
        )

        "list-masked" -> GoldListContent(state = readyList(hidden = true), onIntent = {}, onBack = {})

        "list-incomplete" -> GoldListContent(state = incompleteList(), onIntent = {}, onBack = {})

        "add" -> GoldEditorContent(state = edit(GoldDraft(), isNew = true), onIntent = {}, onBack = {})

        "validation" -> GoldEditorContent(
            state = edit(GoldDraft(weightText = "0", noteText = ""), isNew = true, attempted = true),
            onIntent = {},
            onBack = {},
        )

        "edit" -> GoldEditorContent(state = edit(savedDraft), onIntent = {}, onBack = {})

        "edit-masked" -> GoldEditorContent(
            state = edit(savedDraft).copy(concealed = true),
            onIntent = {},
            onBack = {},
        )

        "edit-no-quote" -> GoldEditorContent(
            state = edit(
                GoldDraft(karat = GoldKarat.K18, form = GoldForm.JEWELRY, weightText = "3.29"),
                quotes = quotesFor(GoldKarat.K21),
            ),
            onIntent = {},
            onBack = {},
        )

        "delete-confirm" -> GoldEditorContent(
            state = edit(savedDraft).copy(confirmation = GoldConfirmation.Delete),
            onIntent = {},
            onBack = {},
        )

        "discard" -> GoldEditorContent(
            state = edit(savedDraft.copy(weightText = "90")).copy(
                baseline = SAVED_VALUES,
                confirmation = GoldConfirmation.Discard,
            ),
            onIntent = {},
            onBack = {},
        )

        "save-error" -> GoldEditorContent(
            state = edit(savedDraft).copy(
                failure = com.mk.daftar.feature.gold.GoldFailure(
                    com.mk.daftar.feature.gold.GoldAction.Save,
                    StorageError.DiskFull,
                ),
            ),
            onIntent = {},
            onBack = {},
        )

        "conflict" -> GoldEditorContent(
            state = edit(savedDraft.copy(weightText = "90")).copy(
                baseline = SAVED_VALUES,
                conflict = true,
            ),
            onIntent = {},
            onBack = {},
        )

        "missing" -> GoldEditorContent(
            state = GoldEditorUiState(phase = com.mk.daftar.feature.gold.GoldPhase.Missing, isNew = false),
            onIntent = {},
            onBack = {},
        )

        else -> error("unknown gold frame $name")
    }
}
