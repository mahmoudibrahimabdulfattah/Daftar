package com.mk.daftar.lifecycle

import com.mk.daftar.core.common.MonotonicTime
import com.mk.daftar.core.domain.repository.PreferencesRead
import com.mk.daftar.core.domain.repository.UserPreferences
import com.mk.daftar.core.domain.repository.UserPreferencesRepository
import com.mk.daftar.core.domain.session.VaultSession
import com.mk.daftar.core.domain.session.VaultSessionState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Locks an open vault after the chosen period without deliberate input. Only touches, key presses and other deliberate
 * actions count, which the app reports through [recordInteraction]. The deadline is measured on the monotonic clock from
 * the last interaction. Changing the timeout re-evaluates the deadline against that same interaction, so a shorter
 * timeout locks at once when it has already passed.
 */
class InactivityMonitor(
    private val session: VaultSession,
    private val preferences: UserPreferencesRepository,
    private val coordinator: VaultLifecycleCoordinator,
    private val clock: MonotonicTime,
    private val scope: CoroutineScope,
) {
    private val lastInteraction = MutableStateFlow(clock.now())
    private var job: Job? = null

    /** Starts observing. Idempotent, so composition may ask again after a recreation. */
    fun start(): Job = job ?: scope.launch {
        val timeouts = preferences.observe()
            .map { read ->
                val prefs = (read as? PreferencesRead.Ready)?.preferences ?: UserPreferences()
                prefs.inactivityTimeout.seconds.seconds
            }
            .distinctUntilChanged()
        // Opening a session is itself activity: the deadline restarts from the moment it opens.
        var openedGeneration: Long? = null
        val states = session.state.onEach { state ->
            if (state is VaultSessionState.Open && state.generation != openedGeneration) {
                openedGeneration = state.generation
                lastInteraction.value = clock.now()
            }
        }
        combine(lastInteraction, timeouts, states) { _, timeout, state -> timeout to state }
            .collectLatest { (timeout, state) ->
                if (state !is VaultSessionState.Open) return@collectLatest
                // Read the latest interaction here, so a touch that arrived while waiting moves the deadline.
                val remaining: Duration = lastInteraction.value + timeout - clock.now()
                if (remaining > Duration.ZERO) delay(remaining)
                coordinator.lockForInactivity()
            }
    }.also { job = it }

    /** A deliberate touch, key press or accessibility action. Nothing else calls this. */
    fun recordInteraction() {
        lastInteraction.value = clock.now()
    }
}
