package com.mk.daftar.core.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey

/**
 * Four independent back stacks, one per [TopLevelDestination], starting at Home.
 *
 * - Selecting a tab shows its stack and keeps its position; reselecting the current tab changes nothing.
 * - Back pops the current stack first, then returns a non-start tab's root to Home. Back on Home's root returns
 *   false so the platform can leave the app.
 * - [showDetail] replaces a detail on top of a stack rather than accumulating detail history.
 * - [dispose] clears every stack and makes the navigator inert. A disposed navigator belongs to a closed vault
 *   generation and is never reused.
 */
class Navigator(private val start: TopLevelDestination = TopLevelDestination.HOME) {

    private val stacks: Map<TopLevelDestination, SnapshotStateList<NavKey>> =
        TopLevelDestination.entries.associateWith { mutableStateListOf(it.route) }

    var topLevel: TopLevelDestination by mutableStateOf(start)
        private set

    var isDisposed: Boolean = false
        private set

    /**
     * A snapshot of the stack of [destination], rooted at its key. Renderers decorate each stack on its own. A copy, so
     * callers never observe a mutation that is still in progress.
     */
    fun stackOf(destination: TopLevelDestination): List<NavKey> = stacks.getValue(destination).toList()

    /** The start stack beneath whichever tab is shown, which is what the platform's back gesture walks. */
    val backStack: List<NavKey>
        get() = if (topLevel == start) {
            stackOf(start)
        } else {
            stackOf(start) + stackOf(topLevel)
        }

    fun select(destination: TopLevelDestination) {
        if (isDisposed) return
        topLevel = destination
    }

    /** Opens [route]. A top-level key selects its tab; any other key is pushed onto the current stack. */
    fun goTo(route: NavKey) {
        if (isDisposed) return
        TopLevelDestination.entries.firstOrNull { it.route == route }?.let {
            select(it)
            return
        }
        stacks.getValue(topLevel).add(route)
    }

    /**
     * Shows [route] as the detail of the current stack. A detail already on top of the stack of the same kind is
     * replaced, so choosing another bank never stacks a second detail behind the first.
     */
    fun showDetail(route: NavKey) {
        if (isDisposed) return
        val stack = stacks.getValue(topLevel)
        if (stack.size > 1 && stack.last()::class == route::class) {
            stack[stack.lastIndex] = route
        } else {
            stack.add(route)
        }
    }

    /** Returns false when nothing is left to pop, so the platform can close the app. */
    fun goBack(): Boolean {
        if (isDisposed) return false
        val current = stacks.getValue(topLevel)
        return when {
            current.size > 1 -> {
                current.removeAt(current.lastIndex)
                true
            }

            topLevel != start -> {
                topLevel = start
                true
            }

            else -> false
        }
    }

    /** Screens with unsaved edits register here, so [requestLeave] can ask before it abandons them. */
    val leaveGuard: LeaveGuard = LeaveGuard()

    /**
     * The shell's way out of the current screen: a back action or a switch to another tab. Switching to the tab that is
     * already shown is nothing. The innermost guard may ask first; without one the request is performed at once.
     */
    fun requestLeave(request: LeaveRequest) {
        if (isDisposed) return
        if (request is LeaveRequest.Switch && request.destination == topLevel) return
        leaveGuard.ask(request) {
            when (request) {
                LeaveRequest.Back -> goBack()
                is LeaveRequest.Switch -> select(request.destination)
            }
        }
    }

    /** Returns every stack to its root and the start tab to the front, without marking the navigator disposed. */
    fun reset() {
        TopLevelDestination.entries.forEach { destination ->
            val stack = stacks.getValue(destination)
            stack.clear()
            stack.add(destination.route)
        }
        topLevel = start
    }

    /** Resets, then makes the navigator inert. Used when the vault generation that owned it is closed. */
    fun dispose() {
        reset()
        leaveGuard.clear()
        isDisposed = true
    }
}
