package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.common.desktop.DesktopAppDirectories
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The state of one generation on desktop. Its files sit in the generation's own security directory, and its platform
 * keys carry the generation's name, so erasing a generation removes exactly its own keys and records.
 */
internal class DesktopVaultStorageFactory(private val permitsSoftwareKeys: Boolean) : PlatformVaultStorageFactory {
    override val pointers: GenerationPointerStore by lazy {
        FileGenerationPointerStore(DesktopAppDirectories.directory(SECURITY_ROOT).resolve(POINTER_FILE))
    }

    override fun forGeneration(generation: VaultGeneration): PlatformVaultStorage {
        val directory = securityDirectory(generation)
        val keys = DesktopKeys(generation, directory, permitsSoftwareKeys)
        return PlatformVaultStorage(
            protector = keys.protector(PIN_ENVELOPE_PURPOSE),
            records = FileRecordStore(directory.resolve(PIN_ENVELOPE_FILE)),
            backoff = FileBackoffStore(directory.resolve(BACKOFF_FILE), keys.protector(BACKOFF_PURPOSE)),
            biometricKeys = keys.biometricKeys(),
            biometricSlots = FileBiometricSlotStore(directory.resolve(BIOMETRIC_SLOT_FILE)),
            recoverySlot = FileRecordStore(directory.resolve(RECOVERY_SLOT_FILE)),
            eraseSecurity = { eraseDesktopGeneration(keys, directory) },
        )
    }

    private fun securityDirectory(generation: VaultGeneration): File {
        val root = DesktopAppDirectories.directory(SECURITY_ROOT)
        return if (generation.isLegacy) root.resolve(LEGACY_DIRECTORY) else root.resolve("generations/${generation.id}")
    }

    private companion object {
        const val SECURITY_ROOT = "security"
        const val LEGACY_DIRECTORY = "vault"
        const val POINTER_FILE = "daftar-generation.v1.txt"
        const val PIN_ENVELOPE_PURPOSE = "pin-envelope.v1"
        const val BACKOFF_PURPOSE = "backoff.v1"
    }
}

/** The durable pointer file. [DurableFile] replaces it atomically. */
internal class FileGenerationPointerStore(file: File) : GenerationPointerStore {
    private val durable = DurableFile(file)

    override suspend fun read(): Outcome<ByteArray?, SecurityError> = durable.read()

    override suspend fun write(bytes: ByteArray): Outcome<Unit, SecurityError> = durable.writeAtomic(bytes)
}

/** Removes the platform keys of a generation, then its security directory. Idempotent. */
private suspend fun eraseDesktopGeneration(keys: DesktopKeys, directory: File): Outcome<Unit, SecurityError> {
    when (val erased = keys.eraseAll()) {
        is Outcome.Failure -> return erased
        is Outcome.Success -> Unit
    }
    return withContext(Dispatchers.IO) {
        try {
            if (directory.exists() && !directory.deleteRecursively()) {
                Outcome.Failure(SecurityError.PersistenceFailure)
            } else {
                Outcome.Success(Unit)
            }
        } catch (failure: IOException) {
            Outcome.Failure(SecurityError.PersistenceFailure)
        }
    }
}
