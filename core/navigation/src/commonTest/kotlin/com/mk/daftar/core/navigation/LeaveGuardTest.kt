package com.mk.daftar.core.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LeaveGuardTest {

    private class Owner

    @Test
    fun withoutAGuardBackProceedsAtOnce() {
        val navigator = Navigator()
        navigator.goTo(ZakatRoute)

        navigator.requestLeave(LeaveRequest.Back)
        assertEquals(listOf(HomeRoute), navigator.backStack)
    }

    @Test
    fun aGuardHoldsTheRequestUntilItProceeds() {
        val navigator = Navigator()
        navigator.select(TopLevelDestination.JOURNAL)
        navigator.goTo(NoteAddRoute)
        val asked = mutableListOf<LeaveRequest>()
        var pending: (() -> Unit)? = null
        navigator.leaveGuard.register(Owner()) { request, proceed ->
            asked += request
            pending = proceed
        }

        navigator.requestLeave(LeaveRequest.Back)

        assertEquals(listOf<LeaveRequest>(LeaveRequest.Back), asked)
        assertEquals(listOf(HomeRoute, JournalRoute, NoteAddRoute), navigator.backStack)

        pending?.invoke()
        assertEquals(listOf(HomeRoute, JournalRoute), navigator.backStack)
    }

    @Test
    fun switchingTabsNeverAsksAndKeepsTheDirtyStack() {
        val navigator = Navigator()
        navigator.select(TopLevelDestination.JOURNAL)
        navigator.goTo(NoteAddRoute)
        var asked = 0
        navigator.leaveGuard.register(Owner()) { _, _ -> asked++ }

        navigator.reselect(TopLevelDestination.ASSETS)
        navigator.reselect(TopLevelDestination.JOURNAL)

        assertEquals(0, asked)
        assertEquals(listOf(JournalRoute, NoteAddRoute), navigator.stackOf(TopLevelDestination.JOURNAL))
    }

    @Test
    fun theMostRecentGuardAnswersAndUnregisteringRestoresTheOuterOne() {
        val navigator = Navigator()
        val outer = Owner()
        val inner = Owner()
        val answers = mutableListOf<String>()
        navigator.leaveGuard.register(outer) { _, proceed ->
            answers += "outer"
            proceed()
        }
        val removeInner = navigator.leaveGuard.register(inner) { _, _ -> answers += "inner" }

        navigator.requestLeave(LeaveRequest.Back)
        assertEquals(listOf("inner"), answers)

        removeInner()
        navigator.requestLeave(LeaveRequest.Back)
        assertEquals(listOf("inner", "outer"), answers)
    }

    @Test
    fun disposingTheNavigatorDropsEveryGuardAndIgnoresRequests() {
        val navigator = Navigator()
        navigator.leaveGuard.register(Owner()) { _, _ -> error("a disposed navigator never asks") }

        navigator.dispose()
        navigator.requestLeave(LeaveRequest.Back)
        navigator.reselect(TopLevelDestination.JOURNAL)

        assertTrue(navigator.isDisposed)
        assertEquals(TopLevelDestination.HOME, navigator.topLevel)
    }

    @Test
    fun reselectingAPushedTabPopsItToItsRootAfterTheGuardProceeds() {
        val navigator = Navigator()
        navigator.select(TopLevelDestination.JOURNAL)
        navigator.goTo(NoteAddRoute)
        var pending: (() -> Unit)? = null
        navigator.leaveGuard.register(Owner()) { request, proceed ->
            assertEquals(LeaveRequest.PopToRoot(TopLevelDestination.JOURNAL), request)
            pending = proceed
        }

        navigator.reselect(TopLevelDestination.JOURNAL)
        assertEquals(listOf(JournalRoute, NoteAddRoute), navigator.stackOf(TopLevelDestination.JOURNAL))

        pending?.invoke()
        assertEquals(listOf(JournalRoute), navigator.stackOf(TopLevelDestination.JOURNAL))
    }

    @Test
    fun cancelledDiscardKeepsTheDirtyFormAndTheTab() {
        val navigator = Navigator()
        navigator.select(TopLevelDestination.JOURNAL)
        navigator.goTo(NoteAddRoute)
        navigator.leaveGuard.register(Owner()) { _, _ -> }

        navigator.reselect(TopLevelDestination.JOURNAL)

        assertEquals(listOf(JournalRoute, NoteAddRoute), navigator.stackOf(TopLevelDestination.JOURNAL))
        assertEquals(TopLevelDestination.JOURNAL, navigator.topLevel)
    }

    @Test
    fun rootReselectScrollsWithoutPoppingAndRepeatedTapsCountUp() {
        val navigator = Navigator()
        navigator.select(TopLevelDestination.ASSETS)
        assertEquals(0, navigator.rootReselectTick(TopLevelDestination.ASSETS))

        navigator.reselect(TopLevelDestination.ASSETS)
        navigator.reselect(TopLevelDestination.ASSETS)

        assertEquals(2, navigator.rootReselectTick(TopLevelDestination.ASSETS))
        assertEquals(listOf(AssetsRoute), navigator.stackOf(TopLevelDestination.ASSETS))
    }

    @Test
    fun switchingBackRestoresThePushedStackOfTheOtherTab() {
        val navigator = Navigator()
        navigator.select(TopLevelDestination.JOURNAL)
        navigator.goTo(NoteAddRoute)

        navigator.reselect(TopLevelDestination.HOME)
        navigator.reselect(TopLevelDestination.JOURNAL)

        assertEquals(listOf(JournalRoute, NoteAddRoute), navigator.stackOf(TopLevelDestination.JOURNAL))
        assertEquals(TopLevelDestination.JOURNAL, navigator.topLevel)
    }
}
