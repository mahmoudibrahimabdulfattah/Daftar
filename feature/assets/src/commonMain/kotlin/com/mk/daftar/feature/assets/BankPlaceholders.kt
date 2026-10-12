package com.mk.daftar.feature.assets

import com.mk.daftar.feature.assets.resources.Res as AssetsRes
import com.mk.daftar.feature.assets.resources.bank_sample_a
import com.mk.daftar.feature.assets.resources.bank_sample_b
import com.mk.daftar.feature.assets.resources.bank_sample_c
import com.mk.daftar.feature.assets.resources.bank_sample_unknown
import org.jetbrains.compose.resources.StringResource

/** A placeholder bank: an identifier for navigation and a neutral name. It carries no balance. */
internal data class PlaceholderBank(val id: String, val name: StringResource)

internal val PlaceholderBanks: List<PlaceholderBank> = listOf(
    PlaceholderBank("sample-a", AssetsRes.string.bank_sample_a),
    PlaceholderBank("sample-b", AssetsRes.string.bank_sample_b),
    PlaceholderBank("sample-c", AssetsRes.string.bank_sample_c),
)

/** The name for [bankId], or a generic one when the identifier is not a placeholder. */
internal fun placeholderBankName(bankId: String): StringResource =
    PlaceholderBanks.firstOrNull { it.id == bankId }?.name ?: AssetsRes.string.bank_sample_unknown
