package com.mk.daftar.core.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class VaultNavigationTest {

    @Test
    fun aGenerationKeepsOneNavigatorAcrossRequests() {
        val vault = VaultNavigation()
        val first = vault.navigatorFor(1)
        first.select(TopLevelDestination.ASSETS)

        assertSame(first, vault.navigatorFor(1), "configuration changes ask for the same generation")
        assertEquals(TopLevelDestination.ASSETS, vault.navigatorFor(1).topLevel)
        assertEquals(1L, vault.generation)
    }

    @Test
    fun releasingDisposesTheNavigatorSoTheNextGenerationStartsAtHome() {
        val vault = VaultNavigation()
        val locked = vault.navigatorFor(1)
        locked.select(TopLevelDestination.JOURNAL)
        locked.goTo(JournalRoute)

        vault.release()

        assertTrue(locked.isDisposed)
        assertNull(vault.generation)
        val next = vault.navigatorFor(2)
        assertEquals(TopLevelDestination.HOME, next.topLevel)
        assertEquals(listOf(HomeRoute), next.backStack)
    }

    @Test
    fun aNewGenerationDisposesAnUnreleasedPredecessor() {
        val vault = VaultNavigation()
        val old = vault.navigatorFor(1)

        vault.navigatorFor(2)

        assertTrue(old.isDisposed)
    }
}
