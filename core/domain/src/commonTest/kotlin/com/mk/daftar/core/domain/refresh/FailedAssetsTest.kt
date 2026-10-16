package com.mk.daftar.core.domain.refresh

import com.mk.daftar.core.domain.repository.RefreshRejection
import com.mk.daftar.core.domain.repository.RefreshReport
import com.mk.daftar.core.domain.repository.RefreshStepResult
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.QuoteProvider
import kotlin.test.Test
import kotlin.test.assertEquals

/** An asset failed only when nothing for it refreshed; a fallback or a confirmed-current value counts as refreshed. */
class FailedAssetsTest {
    private val accepted = RefreshStepResult.Accepted(emptyList())
    private val unreachable = RefreshStepResult.Kept(RefreshRejection.Unreachable)
    private val current = RefreshStepResult.Kept(RefreshRejection.OlderThanStored)

    @Test
    fun aBankFailureWithAWorkingFallbackIsNotADollarFailure() {
        val report = RefreshReport(
            gold = mapOf(GoldKarat.K24 to accepted, GoldKarat.K21 to RefreshStepResult.Kept(RefreshRejection.Missing)),
            fx = mapOf(QuoteProvider.BANQUE_MISR to unreachable, QuoteProvider.EXCHANGE_RATE_API to accepted),
        )
        assertEquals(emptySet(), report.failedAssets())
    }

    @Test
    fun anUnchangedPublicationIsNotAFailure() {
        val report =
            RefreshReport(gold = mapOf(GoldKarat.K24 to current), fx = mapOf(QuoteProvider.BANQUE_MISR to current))
        assertEquals(emptySet(), report.failedAssets())
    }

    @Test
    fun eachAssetFailsOnItsOwn() {
        val report = RefreshReport(
            gold = mapOf(GoldKarat.K24 to accepted),
            fx = mapOf(QuoteProvider.BANQUE_MISR to unreachable, QuoteProvider.EXCHANGE_RATE_API to unreachable),
        )
        assertEquals(setOf(PriceAsset.USD), report.failedAssets())
        assertEquals(
            PriceAsset.entries.toSet(),
            PriceRefreshStatus.Finished(1, RefreshOutcome.Failed).failedAssets,
            "a refresh that failed as a whole failed every asset",
        )
    }
}
