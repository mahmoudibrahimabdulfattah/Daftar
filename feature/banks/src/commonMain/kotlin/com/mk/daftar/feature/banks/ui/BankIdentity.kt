package com.mk.daftar.feature.banks.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.mk.daftar.core.designsystem.component.DaftarBankMark
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.bank.BankArtworkConfig
import com.mk.daftar.core.model.bank.BankArtworkMode
import com.mk.daftar.core.model.bank.BankDirectoryData
import com.mk.daftar.core.model.bank.BankInstitution

/**
 * The tile for a bank. In the official build a catalog institution with bundled artwork shows its official mark;
 * otherwise, and whenever the artwork is missing, it shows its brand monogram. A custom bank, an unknown institution ID
 * or a missing record shows the neutral bank outline. Artwork is never fetched, so every build works offline.
 */
internal fun bankMarkFor(institutionId: String?, mode: BankArtworkMode = BankArtworkConfig.mode): DaftarBankMark {
    val institution = institutionId?.let { id ->
        BankDirectoryData.institutions.firstOrNull { it.id.value == id }
    } ?: return DaftarBankMark.Neutral
    val brand = Color(institution.brandBackground.removePrefix("#").toLong(HEX_RADIX) or OPAQUE_ALPHA)
    val monogram = DaftarBankMark.Monogram(initials = institution.monogram, background = brand)
    val asset = institution.officialAsset
    if (mode != BankArtworkMode.OFFICIAL || asset == null) return monogram
    return DaftarBankMark.Official(
        assetKey = asset,
        brandBacking = brand.takeIf { institution.officialBacking == BankInstitution.BACKING_BRAND },
        fallback = monogram,
    )
}

/** The typographic glyph of a currency: `$`, `ج.م` in Arabic, and `E£` in English. Never a flag. */
internal data class CurrencyGlyph(val text: String, val arabic: Boolean)

@Composable
internal fun currencyGlyphOf(currency: Currency): CurrencyGlyph {
    val arabic = LocalLayoutDirection.current == LayoutDirection.Rtl
    return when (currency) {
        Currency.USD -> CurrencyGlyph("$", arabic = false)
        Currency.EGP -> if (arabic) CurrencyGlyph("ج.م", arabic = true) else CurrencyGlyph("E£", arabic = false)
    }
}

private const val HEX_RADIX = 16
private const val OPAQUE_ALPHA = 0xFF000000L
