package com.mk.daftar.core.ui.shell

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShellLayoutTest {

    @Test
    fun bottomBarBelowSixHundredDpAndRailFromThere() {
        assertEquals(ShellNavigation.BOTTOM, shellNavigationFor(599.dp))
        assertEquals(ShellNavigation.RAIL, shellNavigationFor(600.dp))
        assertEquals(ShellNavigation.RAIL, shellNavigationFor(839.dp))
        assertEquals(ShellNavigation.RAIL, shellNavigationFor(840.dp))
    }

    @Test
    fun simultaneousPanesStartAtEightHundredFortyDp() {
        assertFalse(simultaneousPanesFor(599.dp))
        assertFalse(simultaneousPanesFor(600.dp))
        assertFalse(simultaneousPanesFor(839.dp))
        assertTrue(simultaneousPanesFor(840.dp))
    }
}
