package com.mk.daftar.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mk.daftar.core.designsystem.resources.Res
import com.mk.daftar.core.designsystem.resources.alexandria_variable
import com.mk.daftar.core.designsystem.resources.rubik_variable
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.FontResource

/**
 * Text roles from the approved design, named by role rather than size. Numeric roles use Rubik
 * in either language, with tabular figures.
 */
@Immutable
data class DaftarTypography(
    /** Screen heading, 27sp / 500. */
    val screenHeading: TextStyle,
    /** App-bar title, 18sp / 500. */
    val appBarTitle: TextStyle,
    /** Section heading, 19sp / 500. */
    val sectionHeading: TextStyle,
    /** Small heading, 14sp / 700. */
    val smallHeading: TextStyle,
    /** Notebook card title, 15sp / 700. */
    val notebookTitle: TextStyle,
    /** Body, 13sp / 400. */
    val body: TextStyle,
    /** Supporting copy in panels and notebook cards, 12sp / 400. */
    val supportingBody: TextStyle,
    /** Note panel copy, 12sp / 400 with a taller line. */
    val noteBody: TextStyle,
    /** Ledger row title, 13sp / 500. */
    val ledgerTitle: TextStyle,
    /** Ledger value and input text, 14sp / 400. */
    val ledgerValue: TextStyle,
    /** Button label, 12sp / 500. */
    val buttonLabel: TextStyle,
    /** Setting title, 12sp / 500. */
    val settingTitle: TextStyle,
    /** Field label, 11sp / 500. */
    val fieldLabel: TextStyle,
    /** Fine print, 11sp / 400. */
    val fineText: TextStyle,
    /** Ledger and setting row subtitle, 11sp / 400. */
    val rowSubtitle: TextStyle,
    /** Helper, tag, timestamp and setting caption, 10sp / 400. */
    val caption: TextStyle,
    /** Segment label, 12sp / 400; selected segments use weight 600. */
    val segmentLabel: TextStyle,
    /** Navigation label, 10sp / 400; the selected one uses weight 600. */
    val navLabel: TextStyle,
    /** Quote number, 13sp / 500, Rubik. */
    val quoteNumber: TextStyle,
    /** Hero amount, 38sp / 500, Rubik, tracking -0.035em. */
    val heroAmount: TextStyle,
    /** PIN and keypad numerals, 24sp / 400, Rubik. */
    val pinDigit: TextStyle,
)

internal fun isArabicLocale(): Boolean = Locale.current.language == "ar"

/** Alexandria for Arabic and Rubik for Latin text; Rubik for numbers in both languages. */
@Composable
internal fun daftarFontFamilies(arabic: Boolean): Pair<FontFamily, FontFamily> {
    val text = if (arabic) Res.font.alexandria_variable else Res.font.rubik_variable
    return variableFamily(text) to variableFamily(Res.font.rubik_variable)
}

@Composable
private fun variableFamily(font: FontResource): FontFamily = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(font, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    },
)

/**
 * Arabic text keeps zero tracking and gets lines at least 1.7em tall, with the extra space split evenly.
 * Dots and marks reach beyond the font's default line box, so trimmed lines would clip them. Numeric
 * styles keep their own Rubik metrics in both languages.
 */
internal fun daftarTypography(text: FontFamily, numeric: FontFamily, arabic: Boolean): DaftarTypography {
    fun style(
        size: Int,
        lineHeight: Double,
        weight: FontWeight = FontWeight.Normal,
        tracking: Double = 0.0,
        keepTracking: Boolean = true,
    ): TextStyle = TextStyle(
        fontFamily = text,
        fontSize = size.sp,
        fontWeight = weight,
        lineHeight = (if (arabic) maxOf(lineHeight, ARABIC_MIN_LINE_HEIGHT) else lineHeight).em,
        lineHeightStyle = if (arabic) ArabicLineHeightStyle else null,
        letterSpacing = if (arabic && keepTracking) 0.em else tracking.em,
    )
    fun numericStyle(
        size: Int,
        lineHeight: Double,
        weight: FontWeight = FontWeight.Normal,
        tracking: Double = 0.0,
    ): TextStyle = TextStyle(
        fontFamily = numeric,
        fontSize = size.sp,
        fontWeight = weight,
        lineHeight = lineHeight.em,
        letterSpacing = tracking.em,
        fontFeatureSettings = "tnum",
    )
    return DaftarTypography(
        screenHeading = style(27, 1.55, FontWeight.Medium, tracking = -0.02, keepTracking = false),
        appBarTitle = style(18, 1.6, FontWeight.Medium),
        sectionHeading = style(19, 1.6, FontWeight.Medium),
        smallHeading = style(14, 1.8, FontWeight.Bold),
        notebookTitle = style(15, 1.8, FontWeight.Bold),
        body = style(13, 1.8),
        supportingBody = style(12, 1.8),
        noteBody = style(12, 1.9),
        ledgerTitle = style(13, 1.8, FontWeight.Medium),
        ledgerValue = style(14, 1.8),
        buttonLabel = style(12, 1.8, FontWeight.Medium),
        settingTitle = style(12, 1.8, FontWeight.Medium),
        fieldLabel = style(11, 1.8, FontWeight.Medium),
        fineText = style(11, 1.8),
        rowSubtitle = style(11, 1.8),
        caption = style(10, 1.8),
        segmentLabel = style(12, 1.8),
        navLabel = style(10, 1.8),
        quoteNumber = numericStyle(13, 1.8, FontWeight.Medium),
        heroAmount = numericStyle(38, 1.3, FontWeight.Medium, tracking = -0.035),
        pinDigit = numericStyle(24, 1.2),
    )
}

/** Tall enough for Arabic dots above and below to stay inside the line. */
private const val ARABIC_MIN_LINE_HEIGHT = 1.7

private val ArabicLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)
