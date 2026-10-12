package com.mk.daftar.core.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LeaveGuardTest {

    private class Owner

    @Test
    fun withoutAGuardBackAndSwitchProceedAtOnce() {
        val navigator = Navigator()
        navigator.goTo(ZakatRoute)

        navigator.requestLeave(LeaveRequest.Back)
        assertEquals(listOf(HomeRoute), navigator.backStack)

        navigator.requestLeave(LeaveRequest.Switch(TopLevelDestination.JOURNAL))
        assertEquals(TopLevelDestination.JOURNAL, navigator.topLevel)
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
    fun switchingToTheShownTabNeverAsks() {
        val navigator = Navigator()
        var asked = 0
        navigator.leaveGuard.register(Owner()) { _, _ -> asked++ }

        navigator.requestLeave(LeaveRequest.Switch(TopLevelDestination.HOME))

        assertEquals(0, asked)
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

        navigator.requestLeave(LeaveRequest.Switch(TopLevelDestination.ASSETS))
        assertEquals(listOf("inner"), answers)

        removeInner()
        navigator.requestLeave(LeaveRequest.Switch(TopLevelDestination.SETTINGS))
        assertEquals(listOf("inner", "outer"), answers)
        assertEquals(TopLevelDestination.SETTINGS, navigator.topLevel)
    }

    @Test
    fun disposingTheNavigatorDropsEveryGuardAndIgnoresRequests() {
        val navigator = Navigator()
        navigator.leaveGuard.register(Owner()) { _, _ -> error("a disposed navigator never asks") }

        navigator.dispose()
        navigator.requestLeave(LeaveRequest.Switch(TopLevelDestination.JOURNAL))

        assertTrue(navigator.isDisposed)
        assertEquals(TopLevelDestination.HOME, navigator.topLevel)
    }
}
