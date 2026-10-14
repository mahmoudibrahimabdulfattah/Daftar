package com.mk.daftar.core.security

import android.content.Context
import com.mk.daftar.core.common.Outcome
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The keystore alias root. Every platform key of this module starts with it. */
internal const val ANDROID_KEY_ROOT: String = "com.mk.daftar.security."

/** The keystore alias for [purpose] in [generation]. The legacy alias is exactly the one the first release wrote. */
internal fun keystoreAlias(generation: VaultGeneration, purpose: String): String =
    ANDROID_KEY_ROOT + generation.name(purpose)

/**
 * The state of one generation on Android. Files and keys sit beside each other under the generation's name, so
 * erasing a generation removes exactly its own keys and records. The legacy generation keeps the first release's names.
 */
internal class AndroidVaultStorageFactory(private val context: Context, private val policy: KeystorePolicy) :
    PlatformVaultStorageFactory {
    override val pointers: GenerationPointerStore = FileGenerationPointerStore(
        File(context.noBackupFilesDir, POINTER_FILE),
    )

    override fun forGeneration(generation: VaultGeneration): PlatformVaultStorage {
        val directory = securityDirectory(context, generation)
        return PlatformVaultStorage(
            protector = AndroidKeystoreProtector(keystoreAlias(generation, PIN_ENVELOPE_PURPOSE), policy),
            records = FileRecordStore(directory.resolve(PIN_ENVELOPE_FILE)),
            backoff = FileBackoffStore(
                file = directory.resolve(BACKOFF_FILE),
                protector = AndroidKeystoreProtector(keystoreAlias(generation, BACKOFF_PURPOSE), policy),
            ),
            biometricKeys = AndroidBiometricKeyStore(context, policy, generation),
            biometricSlots = FileBiometricSlotStore(directory.resolve(BIOMETRIC_SLOT_FILE)),
            recoverySlot = FileRecordStore(directory.resolve(RECOVERY_SLOT_FILE)),
            eraseSecurity = { eraseAndroidGeneration(generation, directory) },
        )
    }

    private companion object {
        const val POINTER_FILE = "daftar-generation.v1.txt"
        const val PIN_ENVELOPE_PURPOSE = "pin-envelope.v1"
        const val BACKOFF_PURPOSE = "backoff.v1"
        const val RECOVERY_SLOT_FILE = "recovery-slot.v1.bin"
    }
}

/** The durable pointer file. [DurableFile] replaces it atomically. */
internal class FileGenerationPointerStore(file: File) : GenerationPointerStore {
    private val durable = DurableFile(file)

    override suspend fun read(): Outcome<ByteArray?, SecurityError> = durable.read()

    override suspend fun write(bytes: ByteArray): Outcome<Unit, SecurityError> = durable.writeAtomic(bytes)
}

/**
 * Removes the keys of [generation] from the Android Keystore, then its security directory. A key belongs to a
 * generation when its alias carries the generation's name; the legacy aliases are the ones that carry no generation
 * name. Idempotent.
 */
internal suspend fun eraseAndroidGeneration(
    generation: VaultGeneration,
    directory: File,
): Outcome<Unit, SecurityError> = withContext(Dispatchers.IO) {
    try {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        Collections.list(keyStore.aliases()).filter { alias -> ownsAlias(generation, alias) }.forEach { alias ->
            keyStore.deleteEntry(alias)
        }
        if (directory.exists() && !directory.deleteRecursively()) {
            return@withContext Outcome.Failure(SecurityError.PersistenceFailure)
        }
        Outcome.Success(Unit)
    } catch (failure: IOException) {
        Outcome.Failure(SecurityError.PersistenceFailure)
    } catch (failure: java.security.GeneralSecurityException) {
        Outcome.Failure(SecurityError.PersistenceFailure)
    }
}

/** Whether [alias] is a key of [generation]. Legacy keys are the aliases whose name carries no generation prefix. */
internal fun ownsAlias(generation: VaultGeneration, alias: String): Boolean {
    if (!alias.startsWith(ANDROID_KEY_ROOT)) return false
    val rest = alias.removePrefix(ANDROID_KEY_ROOT)
    val prefixed = VaultGeneration.parse(rest.substringBefore('.'))?.takeIf { !it.isLegacy }
    return if (generation.isLegacy) {
        prefixed == null
    } else {
        prefixed == generation && rest.startsWith(generation.id + ".")
    }
}

/** The directory of [generation]'s security files. Legacy keeps the first release's directory. */
internal fun securityDirectory(context: Context, generation: VaultGeneration): File = if (generation.isLegacy) {
    File(context.noBackupFilesDir, "daftar-security")
} else {
    File(context.noBackupFilesDir, "daftar-generation/${generation.id}/security")
}

private const val ANDROID_KEYSTORE: String = "AndroidKeyStore"

/** The security directory of the legacy generation. */
internal fun securityDirectory(context: Context): File = securityDirectory(context, VaultGeneration.LEGACY)
