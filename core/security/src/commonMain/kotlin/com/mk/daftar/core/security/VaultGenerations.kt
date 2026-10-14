package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Erases the database files of one generation. Implemented by the data layer, which owns the database location. */
interface GenerationFiles {
    /** Removes the files of [generation]. Idempotent: a missing file is not an error. */
    suspend fun erase(generation: VaultGeneration): Outcome<Unit, SecurityError>
}

/**
 * Installs the settings a committed restore carries. It must be idempotent, and it switches biometrics off before it
 * writes anything else, so no interruption can leave the new vault with a biometric preference that has no slot.
 */
interface PostCommitInstaller {
    suspend fun install(payload: String): Outcome<Unit, SecurityError>
}

/** The platform state of one generation: its key stores, durable records and erasure of its keys and files. */
internal class PlatformVaultStorage(
    val protector: PlatformEnvelopeProtector,
    val records: SecurityRecordStore,
    val backoff: BackoffStore,
    val biometricKeys: BiometricKeyStore,
    val biometricSlots: BiometricSlotStore,
    /** The recovery slot, sealed by [protector] like the PIN envelope. */
    val recoverySlot: SecurityRecordStore,
    /** Removes every platform key and every security file of this generation. Idempotent. */
    val eraseSecurity: suspend () -> Outcome<Unit, SecurityError>,
)

/** Builds the platform state of a generation. Creating it never touches the keystore. */
internal interface PlatformVaultStorageFactory {
    fun forGeneration(generation: VaultGeneration): PlatformVaultStorage

    val pointers: GenerationPointerStore
}

/**
 * The generation registry and the one vault security that follows the active generation.
 *
 * The pointer is read once, and the first successful read runs recovery: a committed pointer's pending settings are
 * installed, a retired generation is erased and an abandoned staging generation is erased. Each recovery step only
 * writes the pointer again after it succeeds, so an interrupted recovery is simply run again on the next start.
 *
 * Every pointer change goes through [update], which writes the new value durably before it becomes visible here.
 */
class VaultGenerations internal constructor(
    private val storage: PlatformVaultStorageFactory,
    private val files: GenerationFiles,
    private val installer: PostCommitInstaller,
    private val kdf: PinKdf,
    private val aead: Aead,
    private val random: SecureRandom,
    private val clock: MonotonicClock,
) {
    private val mutex = Mutex()

    /** Guarded by [mutex]. The last pointer that was durably written or read. */
    private var pointer: GenerationPointer? = null

    /** Guarded by [mutex]. Set once recovery has completed for this process. */
    private var recovered = false

    /** Guarded by [mutex]. The security built for the active generation, reused while the pointer does not move. */
    private var current: Pair<VaultGeneration, VaultSecurity>? = null

    /** The vault security for the active generation. It is the only security the rest of the app uses. */
    val security: VaultSecurity = GenerationVaultSecurity()

    /** The current pointer. Recovers pending work the first time it is read. */
    suspend fun pointer(): Outcome<GenerationPointer, SecurityError> = mutex.withLock { load() }

    /** Replaces the pointer with [transform] applied to the current one. Durable before it returns. */
    suspend fun update(transform: (GenerationPointer) -> GenerationPointer): Outcome<GenerationPointer, SecurityError> =
        mutex.withLock {
            val base = when (val loaded = load()) {
                is Outcome.Failure -> return@withLock loaded
                is Outcome.Success -> loaded.data
            }
            val next = transform(base)
            when (val written = write(next)) {
                is Outcome.Failure -> written
                is Outcome.Success -> Outcome.Success(next)
            }
        }

    /**
     * Completes pending post-commit work now, with the same steps the first read runs at startup. It is idempotent, and
     * a failure leaves the pointer naming the pending work, so the next start finishes it.
     */
    suspend fun finishPendingWork(): Outcome<Unit, SecurityError> = mutex.withLock {
        val state = when (val loaded = load()) {
            is Outcome.Failure -> return@withLock Outcome.Failure(loaded.error)
            is Outcome.Success -> loaded.data
        }
        recover(state)
    }

    /** A new, unused generation. Its name is random and no platform item carries it yet. */
    fun newGeneration(): VaultGeneration = VaultGeneration.fresh(random.bytes(VaultGeneration.RANDOM_BYTES))

    /** A new security object bound to [generation]. It holds no session; the caller owns its lifetime. */
    fun securityFor(generation: VaultGeneration): VaultSecurity = buildSecurity(generation)

    /** Erases every key, security file and database file of [generation]. Idempotent. */
    suspend fun erase(generation: VaultGeneration): Outcome<Unit, SecurityError> {
        when (val security = storage.forGeneration(generation).eraseSecurity()) {
            is Outcome.Failure -> return security
            is Outcome.Success -> Unit
        }
        return files.erase(generation)
    }

    /** Revokes the session of the generation that was active. Never throws. */
    suspend fun lockCurrent() {
        val session = mutex.withLock { current?.second }
        session?.lock()
    }

    private suspend fun activeSecurity(): Outcome<VaultSecurity, SecurityError> = mutex.withLock {
        val active = when (val loaded = load()) {
            is Outcome.Failure -> return@withLock Outcome.Failure(loaded.error)
            is Outcome.Success -> loaded.data.active
        }
        val cached = current
        if (cached != null && cached.first == active) return@withLock Outcome.Success(cached.second)
        val built = buildSecurity(active)
        current = active to built
        Outcome.Success(built)
    }

    /**
     * Guarded by [mutex]. Reads the pointer, and recovers pending work before the first answer is given. The cache is
     * only trusted while it is known to match the store: a failed write or a failed recovery step clears it, so the next
     * read starts again from what the store actually holds.
     */
    private suspend fun load(): Outcome<GenerationPointer, SecurityError> {
        pointer?.let { return Outcome.Success(it) }
        val bytes = when (val read = storage.pointers.read()) {
            is Outcome.Failure -> return Outcome.Failure(read.error)
            is Outcome.Success -> read.data
        }
        val loaded = if (bytes == null) {
            GenerationPointer.DEFAULT
        } else {
            GenerationPointerCodec.decode(bytes) ?: return Outcome.Failure(SecurityError.CorruptState)
        }
        if (!recovered) {
            when (val done = recover(loaded)) {
                is Outcome.Failure -> {
                    pointer = null
                    return Outcome.Failure(done.error)
                }

                is Outcome.Success -> recovered = true
            }
        }
        val current = pointer ?: loaded
        pointer = current
        return Outcome.Success(current)
    }

    /**
     * Guarded by [mutex]. Each step writes the pointer only after its work succeeded, so an interrupted recovery is
     * simply run again. Preferences come first: the committed generation's settings must be installed before its
     * retired generation is erased.
     */
    private suspend fun recover(start: GenerationPointer): Outcome<Unit, SecurityError> {
        var state = start
        state.preferences?.let { payload ->
            when (val installed = installer.install(payload)) {
                is Outcome.Failure -> return installed
                is Outcome.Success -> Unit
            }
            state = state.copy(preferences = null)
            when (val written = write(state)) {
                is Outcome.Failure -> return written
                is Outcome.Success -> Unit
            }
        }
        state.previous?.let { retired ->
            when (val erased = erase(retired)) {
                is Outcome.Failure -> return erased
                is Outcome.Success -> Unit
            }
            state = state.copy(previous = null)
            when (val written = write(state)) {
                is Outcome.Failure -> return written
                is Outcome.Success -> Unit
            }
        }
        val staging = state.staging
        if (staging != null && staging != state.active) {
            when (val erased = erase(staging)) {
                is Outcome.Failure -> return erased
                is Outcome.Success -> Unit
            }
            state = state.copy(staging = null)
            when (val written = write(state)) {
                is Outcome.Failure -> return written
                is Outcome.Success -> Unit
            }
        }
        return Outcome.Success(Unit)
    }

    /** Guarded by [mutex]. Writes [next] and makes it the cached value only after the write succeeded. */
    private suspend fun write(next: GenerationPointer): Outcome<Unit, SecurityError> {
        val written = storage.pointers.write(GenerationPointerCodec.encode(next))
        // A failed write may still have landed, so the cache is dropped and the next read asks the store.
        pointer = if (written is Outcome.Success) next else null
        return written
    }

    private fun buildSecurity(generation: VaultGeneration): VaultSecurity {
        val platform = storage.forGeneration(generation)
        return DefaultVaultSecurity(
            kdf = kdf,
            aead = aead,
            random = random,
            protector = platform.protector,
            records = platform.records,
            coordinator = BackoffUnlockCoordinator(platform.backoff, clock),
            clock = clock,
            biometrics = BiometricSlots(platform.biometricKeys, platform.biometricSlots, random),
            recovery = RecoverySlots(platform.protector, platform.recoverySlot),
        )
    }

    /**
     * The security of the active generation. A pointer that cannot be read makes every operation unavailable, so no
     * key is ever used from a generation the pointer does not name.
     */
    private inner class GenerationVaultSecurity : VaultSecurity {
        private suspend fun target(): Outcome<VaultSecurity, SecurityError> = activeSecurity()

        override suspend fun status(): SecurityStatus = when (val target = target()) {
            is Outcome.Failure -> SecurityStatus.Unavailable(target.error)
            is Outcome.Success -> target.data.status()
        }

        override suspend fun create(pin: SecretBytes): Outcome<KeyLease, SecurityError> = when (val target = target()) {
            is Outcome.Failure -> target
            is Outcome.Success -> target.data.create(pin)
        }

        override suspend fun unlock(pin: SecretBytes): Outcome<KeyLease, SecurityError> = when (val target = target()) {
            is Outcome.Failure -> target
            is Outcome.Success -> target.data.unlock(pin)
        }

        override suspend fun changePin(currentPin: SecretBytes, newPin: SecretBytes): Outcome<Unit, SecurityError> =
            when (val target = target()) {
                is Outcome.Failure -> target
                is Outcome.Success -> target.data.changePin(currentPin, newPin)
            }

        override suspend fun verifyCurrentPin(pin: SecretBytes): Outcome<Unit, SecurityError> =
            when (val target = target()) {
                is Outcome.Failure -> target
                is Outcome.Success -> target.data.verifyCurrentPin(pin)
            }

        override suspend fun lock() {
            lockCurrent()
        }

        override suspend fun biometricCapability(): BiometricCapability = when (val target = target()) {
            is Outcome.Failure -> BiometricCapability.Unavailable
            is Outcome.Success -> target.data.biometricCapability()
        }

        override suspend fun biometricSlotState(): Outcome<BiometricSlotState, BiometricError> =
            when (val target = target()) {
                is Outcome.Failure -> Outcome.Failure(BiometricError.Security(target.error))
                is Outcome.Success -> target.data.biometricSlotState()
            }

        override suspend fun enrollBiometrics(
            lease: KeyLease,
            prompter: BiometricPrompter,
        ): Outcome<Unit, BiometricError> = when (val target = target()) {
            is Outcome.Failure -> Outcome.Failure(BiometricError.Security(target.error))
            is Outcome.Success -> target.data.enrollBiometrics(lease, prompter)
        }

        override suspend fun reenrollBiometrics(
            pin: SecretBytes,
            prompter: BiometricPrompter,
        ): Outcome<Unit, BiometricError> = when (val target = target()) {
            is Outcome.Failure -> Outcome.Failure(BiometricError.Security(target.error))
            is Outcome.Success -> target.data.reenrollBiometrics(pin, prompter)
        }

        override suspend fun unlockWithBiometrics(prompter: BiometricPrompter): Outcome<KeyLease, BiometricError> =
            when (val target = target()) {
                is Outcome.Failure -> Outcome.Failure(BiometricError.Security(target.error))
                is Outcome.Success -> target.data.unlockWithBiometrics(prompter)
            }

        override suspend fun disableBiometrics(): Outcome<Unit, BiometricError> = when (val target = target()) {
            is Outcome.Failure -> Outcome.Failure(BiometricError.Security(target.error))
            is Outcome.Success -> target.data.disableBiometrics()
        }

        override suspend fun recoveryState(): Outcome<RecoveryState, SecurityError> = when (val target = target()) {
            is Outcome.Failure -> target
            is Outcome.Success -> target.data.recoveryState()
        }

        override suspend fun setUpRecovery(pin: SecretBytes): Outcome<String, SecurityError> =
            when (val target = target()) {
                is Outcome.Failure -> target
                is Outcome.Success -> target.data.setUpRecovery(pin)
            }

        override suspend fun recover(recoveryKey: String, newPin: SecretBytes): Outcome<KeyLease, SecurityError> =
            when (val target = target()) {
                is Outcome.Failure -> target
                is Outcome.Success -> target.data.recover(recoveryKey, newPin)
            }

        override suspend fun recoveryPublicKey(): Outcome<ByteArray?, SecurityError> = when (val target = target()) {
            is Outcome.Failure -> target
            is Outcome.Success -> target.data.recoveryPublicKey()
        }
    }
}
