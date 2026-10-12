package com.mk.daftar.shell

import com.mk.daftar.core.navigation.HomeRoute
import com.mk.daftar.core.navigation.JournalRoute
import com.mk.daftar.core.navigation.TopLevelDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ShellStateOwnerTest {

    @Test
    fun aGenerationKeepsItsNavigatorAcrossConfigurationChanges() {
        val owner = ShellStateOwner()
        val navigator = owner.navigatorFor(1)
        navigator.select(TopLevelDestination.JOURNAL)

        assertSame(navigator, owner.navigatorFor(1))
        assertEquals(TopLevelDestination.JOURNAL, owner.navigatorFor(1).topLevel)
    }

    @Test
    fun releaseDisposesStacksAndTheNextGenerationStartsAtHome() {
        val owner = ShellStateOwner()
        val locked = owner.navigatorFor(1)
        locked.goTo(JournalRoute)
        owner.retention.savedStates = mapOf("entry" to listOf("scroll"))

        owner.release()

        assertTrue(locked.isDisposed)
        assertNull(owner.retention.savedStates, "entry state is dropped with the generation")
        val next = owner.navigatorFor(2)
        assertEquals(listOf(HomeRoute), next.backStack)
    }

    @Test
    fun aNewGenerationDropsTheRetentionOfTheOneBeforeIt() {
        val owner = ShellStateOwner()
        owner.navigatorFor(1)
        owner.retention.savedStates = mapOf("entry" to listOf("scroll"))

        owner.navigatorFor(2)

        assertNull(owner.retention.savedStates)
        assertNotNull(owner.retention.newRegistry())
    }
}
