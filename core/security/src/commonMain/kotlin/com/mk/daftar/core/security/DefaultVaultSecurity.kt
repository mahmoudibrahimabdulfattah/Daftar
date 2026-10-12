package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The PIN-unlocked vault. The DEK is random and is wrapped by a PIN-derived key. The wrapped envelope is sealed by
 * the platform protector, so the PIN verifier never exists on disk in clear form.
 *
 * A session generation token rejects an unlock that completes after [lock], and a PIN change never touches the
 * DEK or the database bytes.
 */
internal class DefaultVaultSecurity(
    private val kdf: PinKdf,
    private val aead: Aead,
    private val random: SecureRandom,
    private val protector: PlatformEnvelopeProtector,
    private val records: SecurityRecordStore,
    private val coordinator: UnlockCoordinator,
    private val clock: MonotonicClock,
    private val biometrics: BiometricSlots,
) : VaultSecurity {
    private val sessionMutex = Mutex()

    /** Biometric prompts run one at a time; a second request waits for the first to settle. */
    private val biometricMutex = Mutex()

    /** Guarded by [sessionMutex]. Each publish and each lock bumps the generation. */
    private var sessionGeneration = 0L
    private var session: KeyLease? = null

    override suspend fun status(): SecurityStatus {
        val envelope = when (val read = records.read()) {
            is Outcome.Failure -> return SecurityStatus.Unavailable(read.error)
            is Outcome.Success -> read.data
        } ?: return SecurityStatus.NotInitialized
        if (envelope.size > EnvelopeCodec.MAX_SEALED_BYTES) {
            return SecurityStatus.Unavailable(SecurityError.CorruptState)
        }
        if (sessionMutex.withLock { session != null }) return SecurityStatus.Unlocked
        return when (val throttle = coordinator.throttle()) {
            is Outcome.Failure -> SecurityStatus.Unavailable(throttle.error)
            is Outcome.Success -> SecurityStatus.Locked(throttle.data)
        }
    }

    override suspend fun create(pin: SecretBytes): Outcome<KeyLease, SecurityError> {
        if (!pin.use { PinNormalizer.isValidDigits(it) }) return Outcome.Failure(SecurityError.InvalidPinFormat)
        when (val ready = Libsodium.ensureReady()) {
            is Outcome.Failure -> return Outcome.Failure(ready.error)
            is Outcome.Success -> Unit
        }
        return coordinator.exclusive { createLocked(pin) }
    }

    override suspend fun unlock(pin: SecretBytes): Outcome<KeyLease, SecurityError> {
        if (!pin.use { PinNormalizer.isValidDigits(it) }) return Outcome.Failure(SecurityError.InvalidPinFormat)
        when (val ready = Libsodium.ensureReady()) {
            is Outcome.Failure -> return Outcome.Failure(ready.error)
            is Outcome.Success -> Unit
        }
        when (val envelope = records.read()) {
            is Outcome.Failure -> return Outcome.Failure(envelope.error)
            is Outcome.Success -> if (envelope.data == null) return Outcome.Failure(SecurityError.NotInitialized)
        }
        val startGeneration = sessionMutex.withLock { sessionGeneration }
        return coordinator.attempt(
            verify = { record -> verifyPin(pin, record) },
            finish = { dek -> publish(dek, startGeneration, UnlockProof.Pin) },
        )
    }

    override suspend fun changePin(currentPin: SecretBytes, newPin: SecretBytes): Outcome<Unit, SecurityError> {
        if (!currentPin.use { PinNormalizer.isValidDigits(it) } || !newPin.use { PinNormalizer.isValidDigits(it) }) {
            return Outcome.Failure(SecurityError.InvalidPinFormat)
        }
        when (val ready = Libsodium.ensureReady()) {
            is Outcome.Failure -> return Outcome.Failure(ready.error)
            is Outcome.Success -> Unit
        }
        // Any lock or unlock before the envelope commit supersedes this change, so the commit checks this token again.
        val generation = sessionMutex.withLock { sessionGeneration }
        return coordinator.attempt(
            verify = { record -> verifyPin(currentPin, record) },
            finish = { dek ->
                try {
                    rewrap(dek, newPin, generation)
                } finally {
                    dek.wipe()
                }
            },
        )
    }

    override suspend fun verifyCurrentPin(pin: SecretBytes): Outcome<Unit, SecurityError> {
        if (!pin.use { PinNormalizer.isValidDigits(it) }) return Outcome.Failure(SecurityError.InvalidPinFormat)
        when (val ready = Libsodium.ensureReady()) {
            is Outcome.Failure -> return Outcome.Failure(ready.error)
            is Outcome.Success -> Unit
        }
        return coordinator.attempt(
            verify = { record -> verifyPin(pin, record) },
            // The verified key is discarded at once: verification never publishes a lease.
            finish = { dek ->
                dek.wipe()
                Outcome.Success(Unit)
            },
        )
    }

    override suspend fun lock() {
        sessionMutex.withLock {
            session?.close()
            session = null
            sessionGeneration++
        }
    }

    override suspend fun biometricCapability(): BiometricCapability = biometrics.capability()

    override suspend fun biometricSlotState(): Outcome<BiometricSlotState, BiometricError> = biometrics.state()

    override suspend fun enrollBiometrics(lease: KeyLease, prompter: BiometricPrompter): Outcome<Unit, BiometricError> {
        if (lease.proof != UnlockProof.Pin) return Outcome.Failure(BiometricError.PinRequired)
        if (!isCurrent(lease)) return Outcome.Failure(BiometricError.Superseded)
        val ids = when (val envelope = envelopeIdentifiers()) {
            is Outcome.Failure -> return Outcome.Failure(envelope.error)
            is Outcome.Success -> envelope.data
        }
        val key = lease.copyKeyForEnrollment()
        val committed = try {
            biometricMutex.withLock { biometrics.enroll(ids.vaultId, ids.dekId, key, prompter) }
        } finally {
            key.fill(0)
        }
        if (!isCurrent(lease)) {
            // A lock arrived during the prompt. The slot must not outlive the session that created it.
            if (committed is Outcome.Success) biometrics.remove()
            return Outcome.Failure(BiometricError.Superseded)
        }
        return committed
    }

    override suspend fun reenrollBiometrics(
        pin: SecretBytes,
        prompter: BiometricPrompter,
    ): Outcome<Unit, BiometricError> {
        if (!pin.use { PinNormalizer.isValidDigits(it) }) {
            return Outcome.Failure(BiometricError.Security(SecurityError.InvalidPinFormat))
        }
        when (val ready = Libsodium.ensureReady()) {
            is Outcome.Failure -> return Outcome.Failure(BiometricError.Security(ready.error))
            is Outcome.Success -> Unit
        }
        var enrolled: Outcome<Unit, BiometricError> = Outcome.Failure(BiometricError.Superseded)
        val verified = coordinator.attempt(
            verify = { record -> verifyPin(pin, record) },
            finish = { dek ->
                enrolled = try {
                    val ids = envelopeIdentifiers()
                    when (ids) {
                        is Outcome.Failure -> Outcome.Failure(ids.error)

                        is Outcome.Success -> dek.use { key ->
                            biometricMutex.withLock {
                                biometrics.enroll(ids.data.vaultId, ids.data.dekId, key, prompter)
                            }
                        }
                    }
                } finally {
                    dek.wipe()
                }
                Outcome.Success(Unit)
            },
        )
        return when (verified) {
            is Outcome.Failure -> Outcome.Failure(BiometricError.Security(verified.error))
            is Outcome.Success -> enrolled
        }
    }

    override suspend fun unlockWithBiometrics(prompter: BiometricPrompter): Outcome<KeyLease, BiometricError> {
        val startGeneration = sessionMutex.withLock { sessionGeneration }
        val ids = when (val envelope = envelopeIdentifiers()) {
            is Outcome.Failure -> return Outcome.Failure(envelope.error)
            is Outcome.Success -> envelope.data
        }
        // Biometric failures never touch the PIN backoff record: the OS throttles its own attempts.
        val key = when (
            val unwrapped = biometricMutex.withLock {
                biometrics.unwrap(ids.vaultId, ids.dekId, prompter)
            }
        ) {
            is Outcome.Failure -> return Outcome.Failure(unwrapped.error)
            is Outcome.Success -> unwrapped.data
        }
        return when (val published = publish(SecretBytes.owned(key), startGeneration, UnlockProof.Biometric)) {
            is Outcome.Success -> Outcome.Success(published.data)

            is Outcome.Failure -> Outcome.Failure(
                if (published.error == SecurityError.SessionSuperseded) {
                    BiometricError.Superseded
                } else {
                    BiometricError.Security(published.error)
                },
            )
        }
    }

    override suspend fun disableBiometrics(): Outcome<Unit, BiometricError> = biometricMutex.withLock {
        biometrics.remove()
    }

    /** The vault and DEK identifiers from the stored envelope. The envelope is unsealed without the PIN. */
    private suspend fun envelopeIdentifiers(): Outcome<EnvelopeIdentifiers, BiometricError> =
        when (val opened = openEnvelope()) {
            is Outcome.Failure -> Outcome.Failure(BiometricError.Security(opened.error))

            is Outcome.Success -> Outcome.Success(
                EnvelopeIdentifiers(vaultId = opened.data.header.vaultId, dekId = opened.data.header.dekId),
            )
        }

    private suspend fun isCurrent(lease: KeyLease): Boolean = sessionMutex.withLock {
        session === lease && sessionGeneration == lease.generation
    }

    private suspend fun createLocked(pin: SecretBytes): Outcome<KeyLease, SecurityError> {
        when (val existing = records.read()) {
            is Outcome.Failure -> return Outcome.Failure(existing.error)
            is Outcome.Success -> if (existing.data != null) return Outcome.Failure(SecurityError.AlreadyInitialized)
        }
        when (val prepared = protector.prepare()) {
            is Outcome.Failure -> return Outcome.Failure(prepared.error)
            is Outcome.Success -> Unit
        }
        val passes = when (val selected = KdfCalibrator(kdf, clock, random).selectPasses()) {
            is Outcome.Failure -> return Outcome.Failure(selected.error)
            is Outcome.Success -> selected.data
        }
        val vaultId = random.bytes(EnvelopeCodec.ID_BYTES)
        val header = EnvelopeHeader(
            generation = 1L,
            vaultId = vaultId,
            dekId = random.bytes(EnvelopeCodec.ID_BYTES),
            kdf = KdfParams(KdfPolicy.MEMORY_KIB, passes, KdfPolicy.LANES),
            salt = random.bytes(KdfPolicy.SALT_BYTES),
        )
        val nonce = random.bytes(EnvelopeCodec.NONCE_BYTES)
        val dek = SecretBytes.owned(random.bytes(KEY_BYTES))
        val sealed = pin.use { pinBytes -> dek.use { dekBytes -> sealEnvelope(pinBytes, header, nonce, dekBytes) } }
        val envelope = when (sealed) {
            is Outcome.Failure -> {
                dek.wipe()
                return Outcome.Failure(sealed.error)
            }

            is Outcome.Success -> sealed.data
        }
        // The backoff record is written first, so an envelope on disk always has its record beside it.
        when (val initialized = coordinator.initialize(vaultId)) {
            is Outcome.Failure -> {
                dek.wipe()
                return Outcome.Failure(initialized.error)
            }

            is Outcome.Success -> Unit
        }
        when (val created = records.createIfAbsent(envelope)) {
            is Outcome.Failure -> {
                dek.wipe()
                return Outcome.Failure(created.error)
            }

            is Outcome.Success -> if (!created.data) {
                dek.wipe()
                return Outcome.Failure(SecurityError.AlreadyInitialized)
            }
        }
        return publish(dek, sessionMutex.withLock { sessionGeneration }, UnlockProof.Pin)
    }

    /**
     * Opens the stored envelope with [pin]. The vault binding and the stored record are checked before any KDF.
     * A failed AEAD on a well-formed envelope means the PIN was wrong.
     */
    private suspend fun verifyPin(pin: SecretBytes, record: BackoffRecord): AttemptResult {
        val parsed = when (val opened = openEnvelope()) {
            is Outcome.Failure -> return AttemptResult.Failed(opened.error)
            is Outcome.Success -> opened.data
        }
        try {
            if (!parsed.header.vaultId.contentEquals(record.vaultId)) {
                return AttemptResult.Failed(SecurityError.CorruptState)
            }
            val kek = when (val derived = pin.use { kdf.derive(it, parsed.header.salt, parsed.header.kdf) }) {
                is Outcome.Failure -> return AttemptResult.Failed(derived.error)
                is Outcome.Success -> derived.data
            }
            val dek = kek.use { key ->
                aead.open(key, parsed.nonce, parsed.headerBytes, parsed.inner)
            }
            kek.wipe()
            if (dek == null) return AttemptResult.WrongPin
            if (dek.size != KEY_BYTES) {
                dek.fill(0)
                return AttemptResult.Failed(SecurityError.CorruptState)
            }
            return AttemptResult.Verified(SecretBytes.owned(dek))
        } finally {
            parsed.inner.fill(0)
        }
    }

    /** Reads, unseals and decodes the stored envelope. */
    private suspend fun openEnvelope(): Outcome<ParsedEnvelope, SecurityError> {
        val sealed = when (val read = records.read()) {
            is Outcome.Failure -> return Outcome.Failure(read.error)
            is Outcome.Success -> read.data ?: return Outcome.Failure(SecurityError.NotInitialized)
        }
        if (sealed.size > EnvelopeCodec.MAX_SEALED_BYTES) return Outcome.Failure(SecurityError.CorruptState)
        val plain = when (val unsealed = protector.unseal(sealed)) {
            is Outcome.Failure -> return Outcome.Failure(unsealed.error)
            is Outcome.Success -> unsealed.data
        }
        val parsed = EnvelopeCodec.decode(plain)
        plain.fill(0)
        return parsed
    }

    /**
     * Builds the envelope for [header], then proves it before it can be committed: the sealed bytes are unsealed,
     * decoded and opened with the same KEK, and the DEK must come back identical.
     */
    private suspend fun sealEnvelope(
        pin: ByteArray,
        header: EnvelopeHeader,
        nonce: ByteArray,
        dek: ByteArray,
    ): Outcome<ByteArray, SecurityError> {
        if (dek.size != KEY_BYTES) return Outcome.Failure(SecurityError.CorruptState)
        val headerBytes = EnvelopeCodec.encodeHeader(header)
        val kek = when (val derived = kdf.derive(pin, header.salt, header.kdf)) {
            is Outcome.Failure -> return Outcome.Failure(derived.error)
            is Outcome.Success -> derived.data
        }
        val inner = kek.use { key -> aead.seal(key, nonce, headerBytes, dek) }
        val plain = EnvelopeCodec.encode(headerBytes, nonce, inner)
        val sealed = when (val result = protector.seal(plain)) {
            is Outcome.Failure -> {
                kek.wipe()
                plain.fill(0)
                return Outcome.Failure(result.error)
            }

            is Outcome.Success -> result.data
        }
        val proof = verifyRecord(sealed, plain, kek, dek)
        kek.wipe()
        plain.fill(0)
        return when (proof) {
            is Outcome.Failure -> Outcome.Failure(proof.error)
            is Outcome.Success -> Outcome.Success(sealed)
        }
    }

    private suspend fun verifyRecord(
        sealed: ByteArray,
        expectedPlain: ByteArray,
        kek: SecretBytes,
        dek: ByteArray,
    ): Outcome<Unit, SecurityError> {
        val unsealed = when (val result = protector.unseal(sealed)) {
            is Outcome.Failure -> return Outcome.Failure(result.error)
            is Outcome.Success -> result.data
        }
        val roundTrip = unsealed.contentEquals(expectedPlain)
        unsealed.fill(0)
        if (!roundTrip) return Outcome.Failure(SecurityError.CorruptState)
        val parsed = when (val decoded = EnvelopeCodec.decode(expectedPlain)) {
            is Outcome.Failure -> return Outcome.Failure(decoded.error)
            is Outcome.Success -> decoded.data
        }
        val opened = kek.use { key -> aead.open(key, parsed.nonce, parsed.headerBytes, parsed.inner) }
        val matches = opened != null && opened.contentEquals(dek)
        opened?.fill(0)
        return if (matches) Outcome.Success(Unit) else Outcome.Failure(SecurityError.CorruptState)
    }

    /** Commits a new envelope around the existing DEK. The old envelope stays if anything before the replace fails. */
    private suspend fun rewrap(dek: SecretBytes, newPin: SecretBytes, generation: Long): Outcome<Unit, SecurityError> {
        val current = when (val opened = openEnvelope()) {
            is Outcome.Failure -> return Outcome.Failure(opened.error)
            is Outcome.Success -> opened.data
        }
        if (current.header.generation >= EnvelopeCodec.GENERATION_MAX) {
            return Outcome.Failure(SecurityError.UnsupportedFormat)
        }
        val header = EnvelopeHeader(
            generation = current.header.generation + 1,
            vaultId = current.header.vaultId,
            dekId = current.header.dekId,
            kdf = current.header.kdf,
            salt = random.bytes(KdfPolicy.SALT_BYTES),
        )
        val nonce = random.bytes(EnvelopeCodec.NONCE_BYTES)
        val sealed = when (
            val result = newPin.use { pinBytes ->
                dek.use { dekBytes -> sealEnvelope(pinBytes, header, nonce, dekBytes) }
            }
        ) {
            is Outcome.Failure -> return Outcome.Failure(result.error)
            is Outcome.Success -> result.data
        }
        // The session is checked and the envelope replaced under one lock, so a lock cannot slip in between them. A
        // session that was locked during the KDF leaves the previous envelope in place.
        return sessionMutex.withLock {
            if (sessionGeneration != generation) {
                return@withLock Outcome.Failure(SecurityError.SessionSuperseded)
            }
            when (val replaced = records.replace(sealed)) {
                is Outcome.Failure -> replaced
                is Outcome.Success -> Outcome.Success(Unit)
            }
        }
    }

    /** Publishes [dek] as the unlocked session. A lock that happened since [startGeneration] discards it. */
    private suspend fun publish(
        dek: SecretBytes,
        startGeneration: Long,
        proof: UnlockProof,
    ): Outcome<KeyLease, SecurityError> = sessionMutex.withLock {
        if (sessionGeneration != startGeneration) {
            dek.wipe()
            return@withLock Outcome.Failure(SecurityError.SessionSuperseded)
        }
        session?.close()
        sessionGeneration++
        val lease = KeyLease(dek, sessionGeneration, proof)
        session = lease
        Outcome.Success(lease)
    }

    private class EnvelopeIdentifiers(val vaultId: ByteArray, val dekId: ByteArray)

    private companion object {
        const val KEY_BYTES = 32
    }
}
