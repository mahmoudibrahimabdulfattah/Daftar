package com.mk.daftar.core.backup

import kotlin.time.TimeSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How long a system picker operation may wait for its answer: five minutes of monotonic time. */
const val PICKER_OPERATION_LIFETIME_MILLIS: Long = 5L * 60L * 1000L

/** Monotonic milliseconds. It never moves backwards, so a changed wall clock cannot extend or shorten an operation. */
fun interface MonotonicClock {
    fun millis(): Long

    companion object {
        private val origin = TimeSource.Monotonic.markNow()

        val Default: MonotonicClock = MonotonicClock { origin.elapsedNow().inWholeMilliseconds }
    }
}

/**
 * The single-use identity of one system picker operation. Its first answer consumes it; after that, after expiry and
 * after abandonment, the coordinator accepts nothing for it. Tokens are compared by identity.
 */
class PickerOperationToken<R : Any> internal constructor() {
    internal val answer = CompletableDeferred<R?>()
}

/**
 * Owns the one system picker operation a platform has open at a time. Restore and export open a system picker that
 * runs outside the app, so the app is paused, covered and may be stopped while it is on screen. The operation therefore
 * depends on nothing that a lock or a privacy event clears:
 *
 * - [begin] issues a fresh token. Its operation expires [lifetimeMillis] after it was issued, on the monotonic clock.
 * - [complete] and [completePending] consume the token once. A late, duplicate or expired answer is refused; the caller
 *   then removes whatever that answer created.
 * - [awaitResult] suspends until the answer, expiry or abandonment. Cancelling the awaiting coroutine abandons the
 *   operation. Nothing else cancels it, so a privacy cover never does.
 * - Staging handed to [begin] is discarded when the operation expires, is cancelled or is abandoned. A consumed operation
 *   leaves its staging with the caller, which already owns it.
 *
 * The coordinator keeps only tokens, staging and one timer. It holds no Activity, screen or view model, so it survives
 * activity recreation. State is confined to the main thread, where platform pickers answer.
 */
class PickerOperationCoordinator<R : Any>(
    private val scope: CoroutineScope,
    private val clock: MonotonicClock = MonotonicClock.Default,
    private val lifetimeMillis: Long = PICKER_OPERATION_LIFETIME_MILLIS,
) {
    private class Entry<R : Any>(val token: PickerOperationToken<R>, val issuedAt: Long, val staging: StagedBackup?) {
        var timer: Job? = null
    }

    private var current: Entry<R>? = null

    /** The token of the operation that is waiting for an answer, if there is one. */
    val pendingToken: PickerOperationToken<R>? get() = current?.token

    /**
     * Opens a new operation and returns its token. A previous operation that is still waiting is abandoned first, and its
     * staging is discarded.
     */
    fun begin(staging: StagedBackup? = null): PickerOperationToken<R> {
        current?.let { finish(it, answer = null, discardStaging = true) }
        val token = PickerOperationToken<R>()
        val entry = Entry(token, clock.millis(), staging)
        current = entry
        entry.timer = scope.launch {
            delay(lifetimeMillis)
            expire(token)
        }
        return token
    }

    /**
     * Records [result] for [token] and consumes it. Returns false, and changes nothing, when the token is no longer the
     * live operation (already answered, abandoned or expired) or when the lifetime has passed. A false return means the
     * caller must discard whatever the result refers to.
     */
    fun complete(token: PickerOperationToken<R>, result: R?): Boolean {
        val entry = current?.takeIf { it.token === token } ?: return false
        if (clock.millis() - entry.issuedAt >= lifetimeMillis) {
            finish(entry, answer = null, discardStaging = true)
            return false
        }
        finish(entry, answer = result, discardStaging = false)
        return true
    }

    /** Answers the operation that is waiting, if any. Returns false when nothing is waiting. */
    fun completePending(result: R?): Boolean {
        val token = pendingToken ?: return false
        return complete(token, result)
    }

    /**
     * Suspends until [token] is answered. Returns null when the operation was cancelled, expired or abandoned, and when
     * the user cancelled the picker. Cancelling the awaiting coroutine abandons the operation.
     */
    suspend fun awaitResult(token: PickerOperationToken<R>): R? {
        try {
            return token.answer.await()
        } finally {
            abandon(token)
        }
    }

    /** Ends [token] without an answer, for example when its owner is cleared. Its staging is discarded. */
    fun abandon(token: PickerOperationToken<R>) {
        val entry = current?.takeIf { it.token === token } ?: return
        finish(entry, answer = null, discardStaging = true)
    }

    private fun expire(token: PickerOperationToken<R>) {
        val entry = current?.takeIf { it.token === token } ?: return
        finish(entry, answer = null, discardStaging = true)
    }

    private fun finish(entry: Entry<R>, answer: R?, discardStaging: Boolean) {
        if (current === entry) current = null
        entry.timer?.cancel()
        if (discardStaging) {
            entry.staging?.let { staging -> scope.launch { withContext(NonCancellable) { staging.discard() } } }
        }
        entry.token.answer.complete(answer)
    }
}
