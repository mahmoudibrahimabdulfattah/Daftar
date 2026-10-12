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

    override suspend fun derive(
        pin: ByteArray,
        salt: ByteArray,
        params: KdfParams,
    ): Outcome<SecretBytes, SecurityError> {
        derivations++
        passesSeen += params.passes
        entered?.complete(Unit)
        failingCalls.remove(derivations)?.let { return Outcome.Failure(it) }
        gate?.await()
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

/** A vault wired to fakes. A "relaunch" is a new [vault] over the same stores and clock. */
internal class VaultHarness(
    val clock: FakeClock = FakeClock(),
    val kdf: FakeKdf = FakeKdf(clock),
    val protector: FakeProtector = FakeProtector(),
    val records: FakeRecordStore = FakeRecordStore(),
    val backoff: FakeBackoffStore = FakeBackoffStore(),
) {
    fun vault(): DefaultVaultSecurity = DefaultVaultSecurity(
        kdf = kdf,
        aead = LibsodiumAead(),
        random = LibsodiumSecureRandom(),
        protector = protector,
        records = records,
        coordinator = BackoffUnlockCoordinator(backoff, clock),
        clock = clock,
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
