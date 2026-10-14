package com.mk.daftar.feature.investments

import androidx.compose.ui.graphics.Color
import com.mk.daftar.core.designsystem.component.DaftarBankMark
import com.mk.daftar.core.model.bank.BankArtworkConfig
import com.mk.daftar.core.model.bank.BankArtworkMode
import com.mk.daftar.core.model.bank.BankInstitution
import com.mk.daftar.core.model.broker.BrokerDirectoryData

/**
 * The identity tile of a broker. A catalog broker shows its official mark in the official build, on the backing the
 * directory names: Thndr's mark sits on its own near-black tile, never on yellow. Every other case, including the monogram
 * build and a missing asset, shows the brand monogram. An Other or unknown broker has no mark and returns null, so the
 * caller draws the Investments glyph on the default tone instead.
 */
internal fun brokerMarkFor(providerId: String?, mode: BankArtworkMode = BankArtworkConfig.mode): DaftarBankMark? {
    val broker = providerId?.let { id -> BrokerDirectoryData.institutions.firstOrNull { it.id.value == id } }
        ?: return null
    val brand = Color(broker.brandBackground.removePrefix("#").toLong(HEX_RADIX) or OPAQUE_ALPHA)
    val monogram = DaftarBankMark.Monogram(
        initials = broker.monogram,
        background = brand,
        foreground = Color(broker.monogramForeground.removePrefix("#").toLong(HEX_RADIX) or OPAQUE_ALPHA),
    )
    val asset = broker.officialAsset
    if (mode != BankArtworkMode.OFFICIAL || asset == null) return monogram
    return DaftarBankMark.Official(
        assetKey = asset,
        brandBacking = broker.officialBackgroundColor(brand),
        fallback = monogram,
    )
}

/** The backing an official mark is drawn on: its brand colour, its own dark tile, or none (a white tile). */
private fun BankInstitution.officialBackgroundColor(brand: Color): Color? = when (officialBacking) {
    BankInstitution.BACKING_BRAND -> brand

    BankInstitution.BACKING_DARK -> officialBackground?.let {
        Color(it.removePrefix("#").toLong(HEX_RADIX) or OPAQUE_ALPHA)
    }

    else -> null
}

private const val HEX_RADIX = 16
private const val OPAQUE_ALPHA = 0xFF000000L
