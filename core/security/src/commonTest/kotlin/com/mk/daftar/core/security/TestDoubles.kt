package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome
import kotlin.test.assertIs
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

/** A clock that moves only when a test advances it. */
internal class FakeClock : MonotonicClock {
    private var current = Duration.ZERO

    override fun now(): Duration = current

    fun advance(by: Duration) {
        current += by
    }
}

/**
 * A deterministic stand-in for Argon2id. It is not a real KDF, but it keeps the PIN and salt in the output, so the
 * wrong PIN and a changed salt both fail the AEAD. It records each derivation and can fail chosen calls or suspend.
 */
internal class FakeKdf(private val clock: FakeClock? = null, private val costPerPass: Duration = Duration.ZERO) :
    PinKdf {
    var derivations = 0
    val passesSeen = mutableListOf<Int>()
    val failingCalls = mutableMapOf<Int, SecurityError>()
    var gate: CompletableDeferred<Unit>? = null
    var entered: CompletableDeferred<Unit>? = null

    /** Per-call holds: the call with this index signals [arrivals] and waits for its [holds] gate. */
    val holds = mutableMapOf<Int, CompletableDeferred<Unit>>()
    val arrivals = mutableMapOf<Int, CompletableDeferred<Unit>>()

    override suspend fun derive(
        pin: ByteArray,
        salt: ByteArray,
        params: KdfParams,
    ): Outcome<SecretBytes, SecurityError> {
        derivations++
        passesSeen += params.passes
        entered?.complete(Unit)
        arrivals[derivations]?.complete(Unit)
        failingCalls.remove(derivations)?.let { return Outcome.Failure(it) }
        gate?.await()
        holds[derivations]?.await()
        clock?.advance(costPerPass * params.passes)
        val key = ByteArray(KEY_BYTES) { index ->
            (pin[index % pin.size].toInt() xor salt[index % salt.size].toInt() xor (index * 7 + params.passes)).toByte()
        }
        return Outcome.Success(SecretBytes.owned(key))
    }

    private companion object {
        const val KEY_BYTES = 32
    }
}

/** Seals with real XChaCha20-Poly1305 under a fixed fake device key. [tamper] changes the plain bytes on unseal. */
internal class FakeProtector : PlatformEnvelopeProtector {
    private val aead = LibsodiumAead()
    private val random = LibsodiumSecureRandom()
    private val deviceKey = ByteArray(32) { (it * 13 + 5).toByte() }

    var keyPresent = true
    var prepareCalls = 0
    var tamper: ((ByteArray) -> ByteArray)? = null

    override suspend fun prepare(): Outcome<Unit, SecurityError> {
        prepareCalls++
        keyPresent = true
        return Outcome.Success(Unit)
    }

    override suspend fun seal(plaintext: ByteArray): Outcome<ByteArray, SecurityError> {
        if (!keyPresent) return Outcome.Failure(SecurityError.DeviceKeyUnavailable)
        val nonce = random.bytes(NONCE_BYTES)
        return Outcome.Success(nonce + aead.seal(deviceKey, nonce, AD, plaintext))
    }

    override suspend fun unseal(sealed: ByteArray): Outcome<ByteArray, SecurityError> {
        if (!keyPresent) return Outcome.Failure(SecurityError.DeviceKeyUnavailable)
        val opened = aead.open(
            deviceKey,
            sealed.copyOfRange(0, NONCE_BYTES),
            AD,
            sealed.copyOfRange(NONCE_BYTES, sealed.size),
        ) ?: return Outcome.Failure(SecurityError.CorruptState)
        val changed = tamper?.invoke(opened) ?: opened
        return Outcome.Success(changed)
    }

    /** Seals bytes exactly as stored, bypassing [tamper]. Used to craft records an attacker could write. */
    suspend fun sealRaw(plaintext: ByteArray): ByteArray = (seal(plaintext) as Outcome.Success).data

    private companion object {
        const val NONCE_BYTES = 24
        val AD = "fake-device".encodeToByteArray()
    }
}

internal class FakeRecordStore : SecurityRecordStore {
    var record: ByteArray? = null
    var failRead: SecurityError? = null
    var failReplace: SecurityError? = null
    var replaceCalls = 0

    override suspend fun read(): Outcome<ByteArray?, SecurityError> {
        failRead?.let { return Outcome.Failure(it) }
        return Outcome.Success(record?.copyOf())
    }

    override suspend fun createIfAbsent(record: ByteArray): Outcome<Boolean, SecurityError> {
        if (this.record != null) return Outcome.Success(false)
        this.record = record.copyOf()
        return Outcome.Success(true)
    }

    override suspend fun replace(record: ByteArray): Outcome<Unit, SecurityError> {
        replaceCalls++
        failReplace?.let { return Outcome.Failure(it) }
        this.record = record.copyOf()
        return Outcome.Success(Unit)
    }
}

internal class FakeBackoffStore : BackoffStore {
    var record: ByteArray? = null
    var writes = 0

    /** One-based write numbers that fail with a persistence error. */
    val failingWrites = mutableSetOf<Int>()

    override suspend fun prepare(): Outcome<Unit, SecurityError> = Outcome.Success(Unit)

    override suspend fun read(): Outcome<ByteArray?, SecurityError> = Outcome.Success(record?.copyOf())

    override suspend fun write(record: ByteArray): Outcome<Unit, SecurityError> {
        writes++
        if (writes in failingWrites) return Outcome.Failure(SecurityError.PersistenceFailure)
        this.record = record.copyOf()
        return Outcome.Success(Unit)
    }
}

/** The crypto handle the fake key store hands to the prompter. */
internal class FakeBiometricCrypto : BiometricCrypto

/**
 * Stands in for the platform biometric key. Keys are random 32-byte secrets per key identifier. Wrapping and
 * unwrapping run the prompter first, exactly as the platform does, and [invalidated] simulates an enrollment change.
 */
internal class FakeBiometricKeyStore : BiometricKeyStore {
    private val aead = LibsodiumAead()
    private val random = LibsodiumSecureRandom()
    val keys = mutableMapOf<String, ByteArray>()
    var capabilityValue: BiometricCapability = BiometricCapability.Available
    var invalidated = false
    var createCalls = 0
    var deleteCalls = 0
    var unwrapCalls = 0

    override suspend fun capability(): BiometricCapability = capabilityValue

    override suspend fun createKey(keyId: ByteArray): Outcome<Unit, BiometricError> {
        createCalls++
        if (keys.containsKey(hex(keyId))) return Outcome.Failure(BiometricError.Unavailable)
        keys[hex(keyId)] = random.bytes(KEY_BYTES)
        return Outcome.Success(Unit)
    }

    override suspend fun deleteKey(keyId: ByteArray): Outcome<Unit, BiometricError> {
        deleteCalls++
        keys.remove(hex(keyId))
        return Outcome.Success(Unit)
    }

    override suspend fun wrap(
        keyId: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
        prompter: BiometricPrompter,
    ): Outcome<ByteArray, BiometricError> {
        val key = keys[hex(keyId)] ?: return Outcome.Failure(BiometricError.EnrollmentChanged)
        if (invalidated) return Outcome.Failure(BiometricError.EnrollmentChanged)
        val crypto = FakeBiometricCrypto()
        prompter.authorize(crypto).failureOrNull()?.let { return Outcome.Failure(it) }
        val nonce = random.bytes(NONCE_BYTES)
        return Outcome.Success(nonce + aead.seal(key, nonce, aad, plaintext))
    }

    override suspend fun unwrap(
        keyId: ByteArray,
        aad: ByteArray,
        sealed: ByteArray,
        prompter: BiometricPrompter,
    ): Outcome<ByteArray, BiometricError> {
        unwrapCalls++
        val key = keys[hex(keyId)] ?: return Outcome.Failure(BiometricError.EnrollmentChanged)
        if (invalidated) return Outcome.Failure(BiometricError.EnrollmentChanged)
        val crypto = FakeBiometricCrypto()
        prompter.authorize(crypto).failureOrNull()?.let { return Outcome.Failure(it) }
        val opened = aead.open(
            key,
            sealed.copyOfRange(0, NONCE_BYTES),
            aad,
            sealed.copyOfRange(NONCE_BYTES, sealed.size),
        ) ?: return Outcome.Failure(BiometricError.CorruptSlot)
        return Outcome.Success(opened)
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") {
        (it.toInt() and 0xFF).toString(16).padStart(2, '0')
    }

    private companion object {
        const val KEY_BYTES = 32
        const val NONCE_BYTES = 24
    }
}

internal class FakeBiometricSlotStore : BiometricSlotStore {
    var slot: ByteArray? = null
    var writes = 0
    var failingWrites = mutableSetOf<Int>()

    override suspend fun read(): Outcome<ByteArray?, BiometricError> = Outcome.Success(slot?.copyOf())

    override suspend fun write(slot: ByteArray): Outcome<Unit, BiometricError> {
        writes++
        if (writes in failingWrites) return Outcome.Failure(BiometricError.StorageFailure)
        this.slot = slot.copyOf()
        return Outcome.Success(Unit)
    }

    override suspend fun delete(): Outcome<Unit, BiometricError> {
        slot = null
        return Outcome.Success(Unit)
    }
}

/**
 * A prompter that answers with [results] in order, then authenticates. [gate] holds the answer until completed, so a
 * test can change the session while a prompt is on screen.
 */
internal class ScriptedPrompter(vararg results: BiometricAuthResult) : BiometricPrompter {
    private val queue = ArrayDeque(results.toList())
    var calls = 0
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun authorize(crypto: BiometricCrypto): BiometricAuthResult {
        calls++
        gate?.await()
        val next = queue.removeFirstOrNull() ?: return BiometricAuthResult.Authenticated(crypto)
        return if (next is BiometricAuthResult.Authenticated) BiometricAuthResult.Authenticated(crypto) else next
    }
}

/** A vault wired to fakes. A "relaunch" is a new [vault] over the same stores and clock. */
internal class VaultHarness(
    val clock: FakeClock = FakeClock(),
    val kdf: FakeKdf = FakeKdf(clock),
    val protector: FakeProtector = FakeProtector(),
    val records: FakeRecordStore = FakeRecordStore(),
    val backoff: FakeBackoffStore = FakeBackoffStore(),
    val biometricKeys: FakeBiometricKeyStore = FakeBiometricKeyStore(),
    val biometricSlots: FakeBiometricSlotStore = FakeBiometricSlotStore(),
    val recoverySlot: FakeRecordStore = FakeRecordStore(),
) {
    fun vault(): DefaultVaultSecurity = DefaultVaultSecurity(
        kdf = kdf,
        aead = LibsodiumAead(),
        random = LibsodiumSecureRandom(),
        protector = protector,
        records = records,
        coordinator = BackoffUnlockCoordinator(backoff, clock),
        clock = clock,
        biometrics = BiometricSlots(biometricKeys, biometricSlots, LibsodiumSecureRandom()),
        recovery = RecoverySlots(protector, recoverySlot),
    )

    fun storedRecord(): BackoffRecord = (BackoffCodec.decode(checkNotNull(backoff.record)) as Outcome.Success).data
}

internal fun pin(text: String): SecretBytes = (PinNormalizer.normalize(text) as Outcome.Success).data

internal fun <D, E> Outcome<D, E>.requireSuccess(): D {
    assertIs<Outcome.Success<D>>(this)
    return data
}

internal fun <D, E> Outcome<D, E>.requireFailure(): E {
    assertIs<Outcome.Failure<E>>(this)
    return error
}

internal fun CoroutineScope.startUnlock(vault: DefaultVaultSecurity, text: String) = async { vault.unlock(pin(text)) }
