package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.common.map
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single-flight PIN attempts with a persistent backoff.
 *
 * Each attempt loads the record, resolves any interrupted attempt as a counted failure, gates on the penalty,
 * durably reserves the attempt, and only then runs the KDF. A correct PIN resets the record before the secret is
 * released. A wrong PIN activates its penalty. Infrastructure failures roll the reservation back. A cancelled or
 * crashed attempt stays pending, so it counts on the next load.
 *
 * Penalties run on the monotonic clock. A relaunch restarts the persisted penalty at its full length, so wall-clock
 * changes never shorten a penalty.
 */
internal class BackoffUnlockCoordinator(private val store: BackoffStore, private val clock: MonotonicClock) :
    UnlockCoordinator {
    private val mutex = Mutex()

    /** Monotonic deadline of the open penalty, armed on the first gate after a relaunch. Guarded by [mutex]. */
    private var armedUntil: Duration? = null

    override suspend fun <T> exclusive(block: suspend () -> T): T = mutex.withLock { block() }

    override suspend fun initialize(vaultId: ByteArray): Outcome<Unit, SecurityError> {
        armedUntil = null
        return when (val prepared = store.prepare()) {
            is Outcome.Failure -> prepared
            is Outcome.Success -> persist(BackoffRecord.fresh(vaultId))
        }
    }

    override suspend fun throttle(): Outcome<Duration?, SecurityError> = mutex.withLock {
        val record = when (val loaded = loadResolved()) {
            is Outcome.Failure -> return@withLock Outcome.Failure(loaded.error)
            is Outcome.Success -> loaded.data
        }
        when (val gated = gate(record)) {
            is Outcome.Success -> Outcome.Success(null)

            is Outcome.Failure -> when (val error = gated.error) {
                is SecurityError.Throttled -> Outcome.Success(error.retryAfter)
                else -> Outcome.Failure(error)
            }
        }
    }

    override suspend fun <T> attempt(
        verify: suspend (BackoffRecord) -> AttemptResult,
        finish: suspend (SecretBytes) -> Outcome<T, SecurityError>,
    ): Outcome<T, SecurityError> = mutex.withLock {
        val loaded = when (val result = loadResolved()) {
            is Outcome.Failure -> return@withLock Outcome.Failure(result.error)
            is Outcome.Success -> result.data
        }
        val ready = when (val gated = gate(loaded)) {
            is Outcome.Failure -> return@withLock Outcome.Failure(gated.error)
            is Outcome.Success -> gated.data
        }
        val failures = minOf(ready.failures + 1, BackoffRecord.MAX_FAILURES)
        val reserved = ready.copy(
            failures = failures,
            penaltyMillis = BackoffRecord.penaltyMillisFor(failures),
            penaltyOpen = false,
            pending = true,
        )
        when (val reservation = persist(reserved)) {
            is Outcome.Failure -> return@withLock Outcome.Failure(reservation.error)
            is Outcome.Success -> Unit
        }
        when (val result = verify(reserved)) {
            is AttemptResult.Verified -> settleSuccess(reserved, result.secret, finish)
            AttemptResult.WrongPin -> settleWrong(reserved)
            is AttemptResult.Failed -> settleFailure(ready, result.error)
        }
    }

    private suspend fun <T> settleSuccess(
        reserved: BackoffRecord,
        secret: SecretBytes,
        finish: suspend (SecretBytes) -> Outcome<T, SecurityError>,
    ): Outcome<T, SecurityError> {
        if (persist(reserved.reset()) is Outcome.Failure) {
            secret.wipe()
            return Outcome.Failure(SecurityError.PersistenceFailure)
        }
        armedUntil = null
        return try {
            finish(secret)
        } catch (failure: Throwable) {
            secret.wipe()
            throw failure
        }
    }

    private suspend fun <T> settleWrong(reserved: BackoffRecord): Outcome<T, SecurityError> {
        val penalty = reserved.penaltyMillis
        // A failed write leaves the attempt pending, so the failure is still counted on the next load.
        persist(reserved.copy(pending = false, penaltyOpen = penalty > 0))
        armedUntil = if (penalty > 0) clock.now() + penalty.milliseconds else null
        return Outcome.Failure(SecurityError.AuthenticationFailed)
    }

    private suspend fun <T> settleFailure(ready: BackoffRecord, error: SecurityError): Outcome<T, SecurityError> {
        if (error in ROLLBACK_ERRORS) {
            // Positively identified infrastructure failure: the reservation is removed atomically.
            persist(ready.copy(pending = false))
        }
        return Outcome.Failure(error)
    }

    /** Reads the record and settles an interrupted attempt as a counted failure before anything else runs. */
    private suspend fun loadResolved(): Outcome<BackoffRecord, SecurityError> {
        val record = when (val read = readRecord()) {
            is Outcome.Failure -> return Outcome.Failure(read.error)
            is Outcome.Success -> read.data
        }
        if (!record.pending) return Outcome.Success(record)
        val penalty = BackoffRecord.penaltyMillisFor(record.failures)
        val resolved = record.copy(pending = false, penaltyMillis = penalty, penaltyOpen = penalty > 0)
        armedUntil = null
        return persist(resolved).map { resolved }
    }

    /**
     * Returns the record unchanged when attempts are open. While a penalty is open it returns [SecurityError.Throttled]
     * with the remaining time. An expired penalty is persisted as complete, and the failure count is kept.
     */
    private suspend fun gate(record: BackoffRecord): Outcome<BackoffRecord, SecurityError> {
        if (!record.penaltyOpen) return Outcome.Success(record)
        val now = clock.now()
        val deadline = armedUntil ?: (now + record.penaltyMillis.milliseconds).also { armedUntil = it }
        val remaining = deadline - now
        if (remaining > Duration.ZERO) return Outcome.Failure(SecurityError.Throttled(remaining))
        val completed = record.copy(penaltyOpen = false)
        return when (val written = persist(completed)) {
            is Outcome.Failure -> Outcome.Failure(written.error)

            is Outcome.Success -> {
                armedUntil = null
                Outcome.Success(completed)
            }
        }
    }

    private suspend fun readRecord(): Outcome<BackoffRecord, SecurityError> {
        val bytes = when (val read = store.read()) {
            is Outcome.Failure -> return Outcome.Failure(read.error)
            is Outcome.Success -> read.data ?: return Outcome.Failure(SecurityError.CorruptState)
        }
        return BackoffCodec.decode(bytes)
    }

    private suspend fun persist(record: BackoffRecord): Outcome<Unit, SecurityError> =
        when (store.write(BackoffCodec.encode(record))) {
            is Outcome.Failure -> Outcome.Failure(SecurityError.PersistenceFailure)
            is Outcome.Success -> Outcome.Success(Unit)
        }

    private companion object {
        val ROLLBACK_ERRORS: Set<SecurityError> = setOf(
            SecurityError.ResourceUnavailable,
            SecurityError.InsufficientMemory,
            SecurityError.DeviceKeyUnavailable,
            SecurityError.UnsupportedHardware,
            SecurityError.CorruptState,
            SecurityError.UnsupportedFormat,
            SecurityError.NotInitialized,
        )
    }
}
