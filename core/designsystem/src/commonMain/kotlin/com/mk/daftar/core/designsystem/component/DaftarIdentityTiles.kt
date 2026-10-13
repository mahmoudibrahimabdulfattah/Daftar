package com.mk.daftar.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.resources.Res
import com.mk.daftar.core.designsystem.resources.allDrawableResources
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarStyles
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import org.jetbrains.compose.resources.painterResource

/** The clear space inside a bank tile around its mark or monogram. */
private val BankTileClearance = 4.dp

/** Bank tiles use an 11dp radius; the rest of the app uses the tile shape. */
private val BankTileShape = RoundedCornerShape(11.dp)

/** Initials are always white on the brand colour, in both themes. */
private val MonogramForeground = Color(0xFFFFFFFF)

/**
 * What a bank tile shows. The design system never resolves institutions; the caller chooses the mark.
 *  - [Neutral]: the outline bank icon for an other or unknown bank.
 *  - [Monogram]: initials on the institution's brand colour, the offline fallback for every bank.
 *  - [Official]: the bundled, unmodified official mark named [assetKey]. [brandBacking] is null for a mark drawn on a
 *    white tile (with a line border, in both themes) and the brand colour for a light mark that would vanish on white.
 *    A build without that asset (the monogram build) shows [fallback] instead.
 */
sealed interface DaftarBankMark {
    data object Neutral : DaftarBankMark

    data class Monogram(val initials: String, val background: Color) : DaftarBankMark

    data class Official(val assetKey: String, val brandBacking: Color?, val fallback: Monogram) : DaftarBankMark
}

/**
 * The 38dp identity tile of a bank. Decorative: the row beside it announces the bank name once, so the tile carries no
 * semantics of its own.
 */
@Composable
fun DaftarBankTile(mark: DaftarBankMark, modifier: Modifier = Modifier) {
    val resolved = when (mark) {
        is DaftarBankMark.Official -> Res.allDrawableResources[mark.assetKey]?.let { mark to it }
            ?: (mark.fallback to null)

        else -> mark to null
    }
    when (val shown = resolved.first) {
        DaftarBankMark.Neutral -> NeutralTile(modifier)

        is DaftarBankMark.Monogram -> MonogramTile(shown, modifier)

        is DaftarBankMark.Official -> {
            val drawable = checkNotNull(resolved.second)
            val colors = DaftarTheme.colors
            val backing = shown.brandBacking
            Box(
                modifier = modifier
                    .size(DaftarSpacing.tileSize)
                    .clearAndSetSemantics {}
                    .then(
                        if (backing != null) {
                            Modifier.background(backing, BankTileShape)
                        } else {
                            Modifier.background(OfficialWhite, BankTileShape).border(1.dp, colors.line, BankTileShape)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // The bundled bitmap already holds the 4dp clearance around a uniformly scaled mark.
                Image(
                    painter = painterResource(drawable),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun NeutralTile(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(DaftarSpacing.tileSize)
            .clearAndSetSemantics {}
            .styleable(rememberUpdatedStyleState(null), DaftarStyles.tileIcon),
        contentAlignment = Alignment.Center,
    ) {
        DaftarIcon(
            icon = DaftarIcons.Bank,
            contentDescription = null,
            tint = DaftarTheme.colors.primary,
            size = DaftarSpacing.tileIconSize,
        )
    }
}

@Composable
private fun MonogramTile(mark: DaftarBankMark.Monogram, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(DaftarSpacing.tileSize)
            .clearAndSetSemantics {}
            .background(mark.background, BankTileShape)
            .padding(BankTileClearance),
        contentAlignment = Alignment.Center,
    ) {
        // Latin initials never mirror, even inside an Arabic layout.
        Text(
            text = "⁦${mark.initials}⁩",
            style = DaftarTheme.typography.ledgerValue.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            color = MonogramForeground,
            maxLines = 1,
        )
    }
}

/** Official marks sit on true white in both themes, so their own colours stay as the bank publishes them. */
private val OfficialWhite = Color(0xFFFFFFFF)

/**
 * The typographic currency badge: `$`, `E£` or `ج.م` on the soft tile. Never a flag, never gold. Decorative, because
 * the row names the currency in words (US dollars, Egyptian pounds) and no amount is announced here.
 */
@Composable
fun DaftarCurrencyBadge(glyph: String, modifier: Modifier = Modifier, arabicGlyph: Boolean = false) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    val style = if (arabicGlyph) {
        typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium)
    } else {
        typography.ledgerValue.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
    Box(
        modifier = modifier
            .size(DaftarSpacing.tileSize)
            .clearAndSetSemantics {}
            .styleable(rememberUpdatedStyleState(null), DaftarStyles.tileIcon),
        contentAlignment = Alignment.Center,
    ) {
        // Glyph runs keep their own direction: an LTR isolate stops `$` and `E£` from mirroring in RTL.
        Text(text = "⁦$glyph⁩", style = style, color = colors.primary, maxLines = 1)
    }
}
