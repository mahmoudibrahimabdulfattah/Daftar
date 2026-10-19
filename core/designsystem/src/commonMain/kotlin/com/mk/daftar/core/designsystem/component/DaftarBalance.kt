package com.mk.daftar.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme

/** One slice of a [DaftarAllocationBar]. [weight] comes from the domain, never from demo percentages. */
data class DaftarAllocationSegment(val weight: Float, val color: Color)

/**
 * A formatted amount with its unit, e.g. "677,812.74 EGP". [amount] is null when the value cannot be valued
 * yet; it then shows an em dash so an incomplete total is never read as zero. [masked] replaces the digits with
 * dots, and both masked and unavailable states replace the spoken text with [maskedDescription] or
 * [unavailableDescription], so the hidden figure is not announced.
 *
 * The number and its unit sit in one LTR isolate, with the number in Rubik in either language.
 */
@Composable
fun DaftarAmount(
    amount: String?,
    unit: String,
    modifier: Modifier = Modifier,
    masked: Boolean = false,
    maskedDescription: String = "",
    unavailableDescription: String = "",
    style: TextStyle = DaftarTheme.typography.heroAmount,
    color: Color = DaftarTheme.colors.ink,
) {
    val numeric = DaftarTheme.typography.quoteNumber.fontFamily
    val shown = when {
        masked -> MASKED
        amount == null -> UNAVAILABLE
        else -> amount
    }
    val text = buildAnnotatedString {
        append(LTR_ISOLATE)
        withStyle(SpanStyle(fontFamily = numeric)) { append(shown) }
        // A no-break space keeps the unit beside the figure (or its mask) instead of wrapping onto its own line.
        append("\u00A0")
        append(unit)
        append(POP_ISOLATE)
    }
    val hiddenDescription = when {
        masked -> maskedDescription
        amount == null -> unavailableDescription
        else -> null
    }
    val amountModifier = if (hiddenDescription != null) {
        modifier.clearAndSetSemantics { contentDescription = hiddenDescription }
    } else {
        modifier
    }
    // One line that shrinks to fit the width: the figure and its unit never wrap apart, whatever the screen.
    val maxSize = if (style.fontSize.isSpecified) style.fontSize else 32.sp
    val layoutDirection = LocalLayoutDirection.current
    // Arabic text keeps zero tracking. Negative tracking on the figure lets the wrapped width come out narrower than
    // the drawn text, which drops the unit's last glyph in Arabic. English keeps the approved tracking.
    val shaped = if (layoutDirection == LayoutDirection.Rtl) style.copy(letterSpacing = 0.sp) else style
    BasicText(
        text = text,
        style = shaped.copy(color = color).withAmountDirection(layoutDirection),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = maxSize),
        modifier = amountModifier,
    )
}

/**
 * The value of a group of assets: a muted label, the total, and optional slots for provenance, an allocation
 * strip and a footer such as an available subtotal. Masking hides the amount and the allocation strip; figures
 * placed in [footer] are the caller's to mask.
 */
@Composable
fun DaftarBalanceSummary(
    label: String,
    amount: String?,
    unit: String,
    modifier: Modifier = Modifier,
    masked: Boolean = false,
    maskedDescription: String = "",
    unavailableDescription: String = "",
    provenance: (@Composable () -> Unit)? = null,
    allocation: (@Composable () -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = DaftarTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 22.dp)) {
            Text(label, style = DaftarTheme.typography.fineText, color = colors.muted)
            Spacer(Modifier.height(12.dp))
            DaftarAmount(
                amount = amount,
                unit = unit,
                masked = masked,
                maskedDescription = maskedDescription,
                unavailableDescription = unavailableDescription,
            )
            if (provenance != null) {
                Spacer(Modifier.height(4.dp))
                provenance()
            }
            if (allocation != null && !masked) {
                Spacer(Modifier.height(20.dp))
                allocation()
            }
            if (footer != null) {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.xs), content = footer)
            }
        }
        HorizontalDivider(color = colors.line, thickness = 1.dp)
    }
}

/**
 * A thin strip showing how a total splits between categories. It is laid out left-to-right in both languages,
 * draws nothing while [masked], and is read aloud as [summary] instead of as segments.
 */
@Composable
fun DaftarAllocationBar(
    segments: List<DaftarAllocationSegment>,
    summary: String,
    modifier: Modifier = Modifier,
    masked: Boolean = false,
) {
    if (masked) return
    val visible = segments.filter { it.weight > 0f }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(5.dp)
                .semantics { contentDescription = summary },
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            visible.forEach { segment ->
                Spacer(
                    Modifier
                        .weight(segment.weight)
                        .fillMaxHeight()
                        .background(segment.color, RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

/**
 * Applies [amountTextDirection]. A forced LTR paragraph keeps the alignment the layout direction gave it, so a figure
 * wider than its text still sits at the start in Arabic.
 */
private fun TextStyle.withAmountDirection(layoutDirection: LayoutDirection): TextStyle {
    if (amountTextDirection == TextDirection.Unspecified) return this
    val rtl = layoutDirection == LayoutDirection.Rtl
    val align = when (textAlign) {
        TextAlign.End -> if (rtl) TextAlign.Left else TextAlign.Right
        TextAlign.Unspecified, TextAlign.Start -> if (rtl) TextAlign.Right else TextAlign.Left
        else -> textAlign
    }
    return copy(textDirection = amountTextDirection, textAlign = align)
}

private const val LTR_ISOLATE = "⁦"
private const val POP_ISOLATE = "⁩"
private const val MASKED = "••••••"
private const val UNAVAILABLE = "—"
