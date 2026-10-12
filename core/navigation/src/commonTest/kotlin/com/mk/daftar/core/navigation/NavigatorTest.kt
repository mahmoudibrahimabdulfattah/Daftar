package com.mk.daftar.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigatorTest {

    private data object Detail : NavKey

    @Test
    fun startsAtHomeWithOneRootPerTab() {
        val navigator = Navigator()

        assertEquals(TopLevelDestination.HOME, navigator.topLevel)
        assertEquals(listOf(HomeRoute), navigator.backStack)
        assertEquals(listOf(AssetsRoute), navigator.stackOf(TopLevelDestination.ASSETS))
    }

    @Test
    fun tabsKeepTheirOwnStacks() {
        val navigator = Navigator()
        navigator.select(TopLevelDestination.ASSETS)
        navigator.goTo(BanksRoute)
        navigator.select(TopLevelDestination.JOURNAL)
        navigator.goTo(Detail)

        assertEquals(listOf(HomeRoute, JournalRoute, Detail), navigator.backStack)
        navigator.select(TopLevelDestination.ASSETS)
        assertEquals(listOf(HomeRoute, AssetsRoute, BanksRoute), navigator.backStack)
    }

    @Test
    fun reselectingTheCurrentTabPreservesItsPosition() {
        val navigator = Navigator()
        navigator.goTo(BanksRoute)

        navigator.select(TopLevelDestination.HOME)

        assertEquals(listOf(HomeRoute, BanksRoute), navigator.backStack)
    }

    @Test
    fun backPopsDetailsThenReturnsOtherTabsToHomeThenExits() {
        val navigator = Navigator()
        navigator.select(TopLevelDestination.ASSETS)
        navigator.goTo(BanksRoute)
        navigator.goTo(BankDetailRoute("a"))

        assertTrue(navigator.goBack())
        assertEquals(listOf(HomeRoute, AssetsRoute, BanksRoute), navigator.backStack)
        assertTrue(navigator.goBack())
        assertEquals(listOf(HomeRoute, AssetsRoute), navigator.backStack)
        assertTrue(navigator.goBack())
        assertEquals(TopLevelDestination.HOME, navigator.topLevel)
        assertFalse(navigator.goBack(), "Home's root leaves the app to the platform")
    }

    @Test
    fun showingAnotherDetailReplacesTheCurrentOne() {
        val navigator = Navigator()
        navigator.select(TopLevelDestination.ASSETS)
        navigator.goTo(BanksRoute)
        navigator.showDetail(BankDetailRoute("a"))

        navigator.showDetail(BankDetailRoute("b"))

        assertEquals(listOf(HomeRoute, AssetsRoute, BanksRoute, BankDetailRoute("b")), navigator.backStack)
        assertTrue(navigator.goBack())
        assertEquals(listOf(HomeRoute, AssetsRoute, BanksRoute), navigator.backStack)
    }

    @Test
    fun showingDetailOnARootPushesIt() {
        val navigator = Navigator()
        navigator.select(TopLevelDestination.ASSETS)

        navigator.showDetail(BankDetailRoute("a"))

        assertEquals(listOf(HomeRoute, AssetsRoute, BankDetailRoute("a")), navigator.backStack)
    }

    @Test
    fun resetReturnsEveryStackToItsRootAndHomeToFront() {
        val navigator = Navigator()
        navigator.select(TopLevelDestination.SETTINGS)
        navigator.goTo(Detail)

        navigator.reset()

        assertEquals(TopLevelDestination.HOME, navigator.topLevel)
        assertEquals(listOf(HomeRoute), navigator.backStack)
        assertEquals(listOf(SettingsRoute), navigator.stackOf(TopLevelDestination.SETTINGS))
        assertFalse(navigator.isDisposed)
    }

    @Test
    fun disposedNavigatorIgnoresEveryChange() {
        val navigator = Navigator()
        navigator.goTo(Detail)

        navigator.dispose()
        navigator.select(TopLevelDestination.ASSETS)
        navigator.goTo(BanksRoute)
        navigator.showDetail(BankDetailRoute("a"))

        assertTrue(navigator.isDisposed)
        assertEquals(TopLevelDestination.HOME, navigator.topLevel)
        assertEquals(listOf(HomeRoute), navigator.backStack)
        assertFalse(navigator.goBack())
    }
}
