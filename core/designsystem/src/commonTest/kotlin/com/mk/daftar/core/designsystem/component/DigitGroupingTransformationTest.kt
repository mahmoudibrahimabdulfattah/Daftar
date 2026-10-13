package com.mk.daftar.core.designsystem.component

import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DigitGroupingTransformationTest {

    @Test
    fun wholeNumbersAreGroupedInThousands() {
        assertEquals("30,000", DigitGroupingTransformation.group("30000"))
        assertEquals("2,000,000", DigitGroupingTransformation.group("2000000"))
        assertEquals("1,000", DigitGroupingTransformation.group("1000"))
        assertNull(DigitGroupingTransformation.group("999"))
    }

    @Test
    fun onlyTheWholePartIsGrouped() {
        assertEquals("12,360.50", DigitGroupingTransformation.group("12360.50"))
        assertEquals("12,360.", DigitGroupingTransformation.group("12360."))
    }

    @Test
    fun arabicIndicDigitsUseTheArabicSeparator() {
        assertEquals("٣٠٬٠٠٠", DigitGroupingTransformation.group("٣٠٠٠٠"))
    }

    @Test
    fun anythingThatIsNotAPlainNumberIsShownAsTyped() {
        assertNull(DigitGroupingTransformation.group("30,000"))
        assertNull(DigitGroupingTransformation.group("-3000"))
        assertNull(DigitGroupingTransformation.group("30a00"))
        assertNull(DigitGroupingTransformation.group(""))
    }

    @Test
    fun theCursorMapsAroundTheInsertedSeparators() {
        val transformed = DigitGroupingTransformation.filter(AnnotatedString("2000000"))
        assertEquals("2,000,000", transformed.text.text)
        val mapping = transformed.offsetMapping
        assertEquals(0, mapping.originalToTransformed(0))
        assertEquals(2, mapping.originalToTransformed(1))
        assertEquals(9, mapping.originalToTransformed(7))
        assertEquals(1, mapping.transformedToOriginal(2))
        assertEquals(7, mapping.transformedToOriginal(9))
        (0..7).forEach { assertEquals(it, mapping.transformedToOriginal(mapping.originalToTransformed(it))) }
    }
}
