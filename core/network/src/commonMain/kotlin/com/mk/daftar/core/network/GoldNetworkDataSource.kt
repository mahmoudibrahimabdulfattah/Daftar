package com.mk.daftar.core.network

import com.mk.daftar.core.common.DataError
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.GoldQuote
import com.mk.daftar.core.model.MerchantSide

/** Remote merchant gold prices. Implementations never throw for expected failures. */
interface GoldNetworkDataSource {
    suspend fun fetch(): Outcome<GoldFetch, DataError>
}

/** The valid karats of one fetch, plus the reason each rejected or missing karat was dropped. */
data class GoldFetch(val quotes: Map<GoldKarat, MerchantGoldQuotes>, val rejected: Map<GoldKarat, DataError>)

/** One karat's two merchant prices, each with its explicit direction. */
data class MerchantGoldQuotes(val sell: GoldQuote, val buy: GoldQuote) {
    init {
        require(sell.key.side == MerchantSide.SELLS_TO_CUSTOMER) { "sell quote must be the merchant sell side" }
        require(buy.key.side == MerchantSide.BUYS_FROM_CUSTOMER) { "buy quote must be the merchant buy side" }
        require(sell.key.karat == buy.key.karat) { "sell and buy quotes must share a karat" }
    }
}
