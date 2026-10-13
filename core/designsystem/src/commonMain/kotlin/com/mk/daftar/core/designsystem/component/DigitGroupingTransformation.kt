package com.mk.daftar.core.designsystem.component

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Shows a typed amount with thousands separators, `30000` as `30,000`, while the field keeps the plain digits it was
 * given. Only the whole-number part is grouped, in Western or Arabic-Indic digits; text that is not a plain number
 * (already grouped, signed or mixed) is shown unchanged, so the transformation never hides what was typed.
 */
internal object DigitGroupingTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val grouped = group(raw) ?: return TransformedText(text, OffsetMapping.Identity)
        // shown[i] is the index in the grouped text of original character i; the end maps to the end.
        val toShown = IntArray(raw.length + 1)
        var shownIndex = 0
        var original = 0
        while (original < raw.length) {
            while (grouped[shownIndex] != raw[original]) shownIndex++
            toShown[original] = shownIndex
            shownIndex++
            original++
        }
        toShown[raw.length] = grouped.length
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = toShown[offset.coerceIn(0, raw.length)]

            override fun transformedToOriginal(offset: Int): Int {
                val target = offset.coerceIn(0, grouped.length)
                return toShown.indexOfFirst { it >= target }.takeIf { it >= 0 } ?: raw.length
            }
        }
        return TransformedText(AnnotatedString(grouped), mapping)
    }

    /** The grouped form of [raw], or null when it is not a plain unsigned number with at least four whole digits. */
    internal fun group(raw: String): String? {
        val separator = raw.indexOfFirst { it == '.' || it == ARABIC_DECIMAL }
        val whole = if (separator < 0) raw else raw.substring(0, separator)
        val rest = if (separator < 0) "" else raw.substring(separator)
        if (whole.length < GROUP + 1 || !whole.all { it.isAsciiDigit() || it in ARABIC_INDIC }) return null
        if (!rest.drop(1).all { it.isAsciiDigit() || it in ARABIC_INDIC }) return null
        val comma = if (whole.any { it in ARABIC_INDIC }) ARABIC_THOUSANDS else ','
        val builder = StringBuilder()
        whole.forEachIndexed { index, ch ->
            if (index > 0 && (whole.length - index) % GROUP == 0) builder.append(comma)
            builder.append(ch)
        }
        return builder.append(rest).toString()
    }

    private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

    private const val GROUP = 3
    private const val ARABIC_DECIMAL = '٫'
    private const val ARABIC_THOUSANDS = '٬'
    private val ARABIC_INDIC: CharRange = '٠'..'٩'
}
