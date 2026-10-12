package com.mk.daftar.core.data.ids

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class UuidV4GeneratorTest {

    @Test
    fun allOnesBecomeTheVersionFourAndVariantLayout() {
        val id = UuidV4Generator.formatUuidV4(ByteArray(16) { 0xFF.toByte() })
        assertEquals("ffffffff-ffff-4fff-bfff-ffffffffffff", id)
    }

    @Test
    fun allZerosBecomeTheVersionFourAndVariantLayout() {
        val id = UuidV4Generator.formatUuidV4(ByteArray(16))
        assertEquals("00000000-0000-4000-8000-000000000000", id)
    }

    @Test
    fun generatedIdsAreCanonicalAndVersionFour() {
        val generator = UuidV4Generator()
        val shape = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
        repeat(200) {
            val id = generator.nextId()
            assertTrue(shape.matches(id), id)
        }
    }

    @Test
    fun consecutiveIdsDiffer() {
        val generator = UuidV4Generator()
        assertNotEquals(generator.nextId(), generator.nextId())
    }

    @Test
    fun theInjectedRandomSourceIsUsedForEveryId() {
        var calls = 0
        val generator = UuidV4Generator { size ->
            calls++
            ByteArray(size) { calls.toByte() }
        }
        generator.nextId()
        generator.nextId()
        assertEquals(2, calls)
    }

    @Test
    fun aShortRandomSourceIsRejected() {
        assertFailsWith<IllegalArgumentException> { UuidV4Generator.formatUuidV4(ByteArray(15)) }
    }
}
