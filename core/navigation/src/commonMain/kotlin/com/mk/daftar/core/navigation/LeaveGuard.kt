package com.mk.daftar.core.navigation

/**
 * What the shell is about to do with the screen on top: a back action, or a reselect of the shown tab that pops its
 * pushed screens. Switching to another tab is not a request: it keeps every stack and never asks.
 */
sealed interface LeaveRequest {
    data object Back : LeaveRequest

    /** Reselecting the shown tab while it has pushed screens: the tab returns to its root. */
    data class PopToRoot(val destination: TopLevelDestination) : LeaveRequest
}

/**
 * Screens that hold unsaved edits register here, so the shell asks before it abandons them. The most recent
 * registration answers first. Without a registration the request proceeds at once.
 */
class LeaveGuard {
    /** Decides a leave request. [proceed] performs the request once the screen allows it. */
    fun interface Handler {
        fun onLeaveRequested(request: LeaveRequest, proceed: () -> Unit)
    }

    private val handlers = LinkedHashMap<Any, Handler>()

    /** Registers [handler] for [owner]. The returned function removes this registration only. */
    fun register(owner: Any, handler: Handler): () -> Unit {
        handlers.remove(owner)
        handlers[owner] = handler
        return {
            if (handlers[owner] === handler) handlers.remove(owner)
        }
    }

    /** Runs [proceed] now, or hands it to the innermost registered handler. */
    internal fun ask(request: LeaveRequest, proceed: () -> Unit) {
        val handler = handlers.values.lastOrNull()
        if (handler == null) proceed() else handler.onLeaveRequested(request, proceed)
    }

    /** Drops every registration. Used when the navigator that owned them is disposed. */
    internal fun clear() {
        handlers.clear()
    }
}
