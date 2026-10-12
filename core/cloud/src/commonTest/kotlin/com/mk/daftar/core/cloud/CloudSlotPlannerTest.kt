package com.mk.daftar.core.cloud

import kotlin.test.Test
import kotlin.test.assertEquals

class CloudSlotPlannerTest {
    private fun slot(createdAt: Long?) = SlotRecord("id", 1, 84, "0".repeat(64), createdAt, null)

    @Test
    fun anEmptySlotIsUsedFirst() {
        assertEquals(1, CloudSlotPlanner.chooseSlot(listOf(slot(5), null, slot(1))))
        assertEquals(0, CloudSlotPlanner.chooseSlot(listOf(null, slot(9), slot(1))))
    }

    @Test
    fun theOldestVersionIsReplacedWhenAllSlotsAreUsed() {
        assertEquals(2, CloudSlotPlanner.chooseSlot(listOf(slot(30), slot(20), slot(10))))
        assertEquals(1, CloudSlotPlanner.chooseSlot(listOf(slot(30), slot(10), slot(20))))
    }

    @Test
    fun equalAgesAreBrokenBySlotNumber() {
        assertEquals(0, CloudSlotPlanner.chooseSlot(listOf(slot(7), slot(7), slot(7))))
        assertEquals(1, CloudSlotPlanner.chooseSlot(listOf(slot(9), slot(7), slot(7))))
    }
}
