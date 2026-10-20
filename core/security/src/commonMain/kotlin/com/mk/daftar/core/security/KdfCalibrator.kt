package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Chooses the Argon2id pass count at enrollment. Memory stays at the 64 MiB Standard baseline, and the pass count is
 * the largest value in 3..6 whose measured derivation time stays at or under [TARGET_MAX]. Passes never drop below the
 * baseline of 3, even on a slow device, so the result is never weaker than the baseline.
 *
 * An allocation failure ([SecurityError.InsufficientMemory]) on the first step, pass count 3, is returned so the caller
 * can fall back to LowMemory. A failure on a later step stops calibration and keeps the largest pass count that already
 * succeeded at 64 MiB, so a device that could run Standard once never drops to LowMemory.
 */
internal class KdfCalibrator(
    private val kdf: PinKdf,
    private val clock: MonotonicClock,
    private val random: SecureRandom,
) {
    suspend fun selectPasses(): Outcome<Int, SecurityError> {
        val salt = random.bytes(KdfPolicy.SALT_BYTES)
        val dummyPin = "000000".encodeToByteArray()
        var selected = KdfPolicy.MIN_PASSES
        for (passes in KdfPolicy.MIN_PASSES..KdfPolicy.MAX_PASSES) {
            val elapsed = when (val measured = measure(dummyPin, salt, passes)) {
                is Outcome.Failure -> {
                    if (measured.error == SecurityError.InsufficientMemory && passes > KdfPolicy.MIN_PASSES) break
                    return Outcome.Failure(measured.error)
                }

                is Outcome.Success -> measured.data
            }
            if (elapsed > TARGET_MAX) break
            selected = passes
        }
        return Outcome.Success(selected)
    }

    private suspend fun measure(pin: ByteArray, salt: ByteArray, passes: Int): Outcome<Duration, SecurityError> {
        val params = KdfParams(KdfPolicy.MEMORY_KIB, passes, KdfPolicy.LANES)
        val start = clock.now()
        return when (val derived = kdf.derive(pin, salt, params)) {
            is Outcome.Failure -> Outcome.Failure(derived.error)

            is Outcome.Success -> {
                derived.data.wipe()
                Outcome.Success(clock.now() - start)
            }
        }
    }

    companion object {
        val TARGET_MAX = 1_000.milliseconds
    }
}
