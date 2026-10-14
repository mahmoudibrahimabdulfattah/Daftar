package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome

/** A pointer store with scripted faults. A failed write can still land, which models an uncertain outcome. */
internal class FakePointerStore : GenerationPointerStore {
    var bytes: ByteArray? = null
    var failRead: SecurityError? = null
    var failNextWrite: SecurityError? = null

    /** When true, a failed write still stores its bytes, as a crash after the rename would. */
    var landOnFailure: Boolean = false
    var writes = 0

    override suspend fun read(): Outcome<ByteArray?, SecurityError> {
        failRead?.let { return Outcome.Failure(it) }
        return Outcome.Success(bytes?.copyOf())
    }

    override suspend fun write(bytes: ByteArray): Outcome<Unit, SecurityError> {
        writes++
        val fault = failNextWrite
        failNextWrite = null
        if (fault != null) {
            if (landOnFailure) this.bytes = bytes.copyOf()
            return Outcome.Failure(fault)
        }
        this.bytes = bytes.copyOf()
        return Outcome.Success(Unit)
    }
}

/** The fakes behind one generation's platform state. Erasure clears them, as the platform would remove the items. */
internal class FakeGenerationStorage {
    val protector = FakeProtector()
    val records = FakeRecordStore()
    val backoff = FakeBackoffStore()
    val keys = FakeBiometricKeyStore()
    val slots = FakeBiometricSlotStore()
    val recovery = FakeRecordStore()
    var erasures = 0

    val storage: PlatformVaultStorage = PlatformVaultStorage(
        protector = protector,
        records = records,
        backoff = backoff,
        biometricKeys = keys,
        biometricSlots = slots,
        recoverySlot = recovery,
        eraseSecurity = {
            erasures++
            records.record = null
            backoff.record = null
            slots.slot = null
            recovery.record = null
            protector.keyPresent = false
            Outcome.Success(Unit)
        },
    )

    /** Whether this generation holds an envelope, that is, whether a PIN vault exists here. */
    val hasVault: Boolean
        get() = records.record != null
}

/** One [FakeGenerationStorage] per generation name, created on first use. */
internal class FakeStorageFactory(private val pointerStore: FakePointerStore) : PlatformVaultStorageFactory {
    val generations = mutableMapOf<String, FakeGenerationStorage>()

    override val pointers: GenerationPointerStore
        get() = pointerStore

    override fun forGeneration(generation: VaultGeneration): PlatformVaultStorage =
        generations.getOrPut(generation.id) { FakeGenerationStorage() }.storage

    fun of(generation: VaultGeneration): FakeGenerationStorage = generations.getOrPut(generation.id) {
        FakeGenerationStorage()
    }
}

internal class FakeGenerationFiles : GenerationFiles {
    val erased = mutableListOf<String>()
    var failNext: SecurityError? = null

    override suspend fun erase(generation: VaultGeneration): Outcome<Unit, SecurityError> {
        failNext?.let {
            failNext = null
            return Outcome.Failure(it)
        }
        erased += generation.id
        return Outcome.Success(Unit)
    }
}

internal class FakePostCommitInstaller : PostCommitInstaller {
    val installed = mutableListOf<String>()
    var failNext: Boolean = false

    override suspend fun install(payload: String): Outcome<Unit, SecurityError> {
        if (failNext) {
            failNext = false
            return Outcome.Failure(SecurityError.PersistenceFailure)
        }
        installed += payload
        return Outcome.Success(Unit)
    }
}

/** The registry under test, wired to fakes. A relaunch is a new instance over the same pointer and storage. */
internal class GenerationHarness(
    val clock: FakeClock = FakeClock(),
    val pointers: FakePointerStore = FakePointerStore(),
    val files: FakeGenerationFiles = FakeGenerationFiles(),
    val installer: FakePostCommitInstaller = FakePostCommitInstaller(),
) {
    val storage = FakeStorageFactory(pointers)

    fun generations(): VaultGenerations = VaultGenerations(
        storage = storage,
        files = files,
        installer = installer,
        kdf = FakeKdf(clock),
        aead = LibsodiumAead(),
        random = LibsodiumSecureRandom(),
        clock = clock,
    )
}
