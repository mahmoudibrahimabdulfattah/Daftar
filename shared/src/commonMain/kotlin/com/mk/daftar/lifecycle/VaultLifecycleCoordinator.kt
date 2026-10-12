package com.mk.daftar.lifecycle

import com.mk.daftar.core.domain.session.VaultSession
import com.mk.daftar.core.domain.session.VaultSessionState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Turns platform visibility callbacks into the vault's privacy state and session lock.
 *
 * Every callback obscures content first, synchronously, and then asks the session to lock. The lock runs in
 * [scope], the application-owned scope, so an activity or scene going away never cancels it, and it is never queued
 * behind a timeout. The coordinator never calls the security layer directly: [VaultSession.lock] owns database
 * closure and key release.
 *
 * The cover lifts only when every obscured surface is back and no lock is still cleaning up. A return during cleanup
 * therefore keeps the cover up until the previous generation is gone.
 *
 * State is confined to [mainDispatcher]; platform callbacks already run on the main thread.
 */
class VaultLifecycleCoordinator(
    private val session: VaultSession,
    private val scope: CoroutineScope,
    private val mainDispatcher: CoroutineDispatcher,
    /** Runs synchronously whenever content is obscured, even when the session is already locked. Wipes auth drafts. */
    private val onPrivacyObscured: () -> Unit = {},
) {
    private val obscuredSurfaces = mutableSetOf<String>()

    /** True while an inactivity lock covers the content. It lifts when that lock finishes. */
    private var inactivityCover = false

    /** Identifies the in-flight inactivity lock, if any. */
    private var inactivityToken: Any? = null

    /** True from a background request until a return finds no cleanup pending. */
    private var inBackground = false

    /** Set when a surface returns with every surface visible. Cleanup that finishes later may then lift the cover. */
    private var revealRequested = false

    /** A configuration change has stopped the activity and its replacement has not resumed yet. */
    private var configurationChangeInFlight = false

    /** The in-flight lock, if any. Repeated background callbacks share it. */
    private var lockJob: Job? = null
    private var lockToken: Any? = null

    private val cover = MutableStateFlow(false)

    /** True while content must be hidden. Platform code may read [isCovered] synchronously. */
    val covered: StateFlow<Boolean> = cover.asStateFlow()

    val isCovered: Boolean get() = cover.value

    /**
     * Starts the guard against late unlocks: if a session opens while the app is in the background, it is locked
     * again. Call once per process, after Koin has started.
     */
    fun start(): Job = scope.launch(mainDispatcher) {
        session.state.collect { state ->
            if (state is VaultSessionState.Open && inBackground) {
                scope.launch { session.lock() }
            }
        }
    }

    /**
     * A surface stops being active (Android `onPause`, iOS `willDeactivate`). The cover goes up before this returns.
     * [surface] names the activity or scene, so several scenes can be tracked at once.
     */
    fun obscure(surface: String) {
        obscuredSurfaces += surface
        revealRequested = false
        onPrivacyObscured()
        publish()
    }

    /**
     * A surface is active again (Android `onResume`, iOS `didActivate`). Returns the cleanup the cover waits for, or
     * null when nothing is pending. The caller removes its native cover once the returned job completes, or after the
     * next frame when it is null, and only while [isCovered] is false.
     */
    fun reveal(surface: String): Job? {
        obscuredSurfaces -= surface
        configurationChangeInFlight = false
        if (obscuredSurfaces.isEmpty()) revealRequested = true
        val pending = lockJob
        if (obscuredSurfaces.isEmpty() && pending == null) inBackground = false
        publish()
        return pending
    }

    /**
     * Android `onStop`. A configuration change keeps the session and the cover, and locks nothing. Any other stop
     * requests an immediate lock.
     */
    fun stopped(changingConfigurations: Boolean): Job? {
        if (changingConfigurations) {
            configurationChangeInFlight = true
            return null
        }
        return enteredBackground()
    }

    /**
     * The process-level stop fallback (`ProcessLifecycleOwner` ON_STOP), which arrives after a delay. It never locks
     * during a configuration change: a slow recreation can outlast the delay, and the session must survive it.
     */
    fun processStopped(): Job? = if (configurationChangeInFlight) null else enteredBackground()

    /**
     * The app left the foreground: iOS `didEnterBackground`, the process-level stop fallback, or protected data
     * becoming unavailable. Covers, then locks. Repeated calls while a lock is in flight share it.
     */
    fun enteredBackground(): Job {
        inBackground = true
        revealRequested = false
        onPrivacyObscured()
        publish()
        lockJob?.let { return it }
        val token = Any()
        lockToken = token
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                session.lock()
            } finally {
                withContext(NonCancellable + mainDispatcher) { lockFinished(token) }
            }
        }
        // The lock can finish before this line runs, in which case there is nothing left to track.
        if (lockToken === token) lockJob = job
        return job
    }

    /**
     * The inactivity deadline passed. Covers at once, drops every draft, and locks the session. The cover lifts when the
     * lock finishes, unless the app has left the foreground meanwhile, which keeps its own cover. Returns null when the
     * app is already locking in the background.
     */
    fun lockForInactivity(): Job? {
        if (inBackground || inactivityToken != null) return null
        onPrivacyObscured()
        inactivityCover = true
        publish()
        val token = Any()
        inactivityToken = token
        return scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                session.lock()
            } finally {
                withContext(NonCancellable + mainDispatcher) {
                    if (inactivityToken === token) {
                        inactivityToken = null
                        inactivityCover = false
                        publish()
                    }
                }
            }
        }
    }

    private fun lockFinished(token: Any) {
        if (lockToken !== token) return
        lockToken = null
        lockJob = null
        if (revealRequested && obscuredSurfaces.isEmpty()) inBackground = false
        publish()
    }

    private fun publish() {
        cover.value = obscuredSurfaces.isNotEmpty() || inBackground || inactivityCover
    }
}
