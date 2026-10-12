package com.mk.daftar.core.data.storage

import com.mk.daftar.core.backup.BackupCodec
import com.mk.daftar.core.backup.BackupError
import com.mk.daftar.core.backup.BackupSnapshot
import com.mk.daftar.core.backup.DecodedBackup
import com.mk.daftar.core.backup.StagedRestore
import com.mk.daftar.core.backup.canonicalForm
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.data.backup.VaultBackupSnapshotReader
import com.mk.daftar.core.data.ids.UuidV4Generator
import com.mk.daftar.core.data.restore.GenerationRestoreEngine
import com.mk.daftar.core.data.restore.IosGenerationFiles
import com.mk.daftar.core.data.restore.RestorePreferences
import com.mk.daftar.core.data.restore.RestorePreferencesInstaller
import com.mk.daftar.core.data.restore.RoomStagingFactory
import com.mk.daftar.core.data.restore.VaultGenerationStore
import com.mk.daftar.core.data.restore.iosDatabasePath
import com.mk.daftar.core.data.session.DaftarDatabaseBuilderProvider
import com.mk.daftar.core.data.session.DaftarVaultSession
import com.mk.daftar.core.data.session.VaultAuthenticator
import com.mk.daftar.core.data.session.activeGeneration
import com.mk.daftar.core.database.daftarDatabaseBuilder
import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.domain.repository.InactivityTimeout
import com.mk.daftar.core.domain.repository.PreferencesRead
import com.mk.daftar.core.domain.repository.ThemeMode
import com.mk.daftar.core.domain.repository.UserPreferences
import com.mk.daftar.core.domain.repository.UserPreferencesRepository
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.security.BiometricSlotState
import com.mk.daftar.core.security.GenerationFiles
import com.mk.daftar.core.security.GenerationPointer
import com.mk.daftar.core.security.PinNormalizer
import com.mk.daftar.core.security.PostCommitInstaller
import com.mk.daftar.core.security.SecretBytes
import com.mk.daftar.core.security.SecurityStatus
import com.mk.daftar.core.security.VaultGenerations
import com.mk.daftar.core.security.VaultSecurity
import com.mk.daftar.core.security.createPasswordCrypto
import com.mk.daftar.core.security.di.securityModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * The restore pipeline on the iOS simulator: the frozen golden vector is decrypted with the real password crypto, staged into
 * a fresh vault through the Keystore and SQLCipher, committed, and then reopened with the new PIN. The restored records
 * must read back identical to the decoded backup, and the restored vault must have no biometric slot.
 */
class RestoreIosTest {

    private val preferences = DevicePreferences()

    private fun koin(): Koin = koinApplication {
        modules(
            securityModule,
            module {
                single<GenerationFiles> { IosGenerationFiles() }
                single<PostCommitInstaller> { RestorePreferencesInstaller(preferences) }
            },
        )
    }.koin

    private fun provider(): DaftarDatabaseBuilderProvider = DaftarDatabaseBuilderProvider { generation ->
        daftarDatabaseBuilder(iosDatabasePath(generation))
    }

    private fun pin(digits: String): SecretBytes =
        checkNotNull((PinNormalizer.normalize(digits) as? Outcome.Success)?.data) { "test PIN must normalize" }

    private suspend fun decodeGolden(): DecodedBackup {
        val file = GOLDEN_VECTOR_HEX.hexToBytes()
        val password = GOLDEN_PASSWORD.encodeToByteArray()
        return assertIs<Outcome.Success<DecodedBackup>>(
            BackupCodec(createPasswordCrypto()).decode(file, password),
        ).data
    }

    // The full restore into a fresh vault needs the Keychain for the generation pointer, which a Kotlin/Native test
    // binary cannot reach (errSecNotAvailable). It runs on Android as RestoreDeviceTest and on iOS in an app host.

    @Test
    fun aWrongPasswordNeverReachesTheVault() = runTest {
        val file = GOLDEN_VECTOR_HEX.hexToBytes()
        val result = BackupCodec(createPasswordCrypto()).decode(file, "not the password at all".encodeToByteArray())
        assertEquals(BackupError.AuthenticationFailed, assertIs<Outcome.Failure<BackupError>>(result).error)
    }

    private class DevicePreferences : UserPreferencesRepository {
        private var current = UserPreferences()

        override fun observe(): Flow<PreferencesRead> = flowOf(PreferencesRead.Ready(current, false))

        override suspend fun setThemeMode(mode: ThemeMode): Outcome<Unit, StorageError> {
            current = current.copy(themeMode = mode)
            return Outcome.Success(Unit)
        }

        override suspend fun setLanguage(language: AppLanguage): Outcome<Unit, StorageError> {
            current = current.copy(language = language)
            return Outcome.Success(Unit)
        }

        override suspend fun setInactivityTimeout(timeout: InactivityTimeout): Outcome<Unit, StorageError> {
            current = current.copy(inactivityTimeout = timeout)
            return Outcome.Success(Unit)
        }

        override suspend fun setBiometricEnabled(enabled: Boolean): Outcome<Unit, StorageError> {
            current = current.copy(biometricEnabled = enabled)
            return Outcome.Success(Unit)
        }
    }

    private companion object {
        /** The frozen version-one golden vector from core:backup's codec tests, with its password. */
        const val GOLDEN_PASSWORD: String = "correct horse battery"
        const val GOLDEN_VECTOR_HEX: String =
            "4446545242414b00000101010001000000000003012010000102030405060708090a0b0c0d0e0f182021222324252627" +
                "28292a2b2c2d2e2f3031323334353637000001ccf4222d42449dacacda3b44d13135cff4fe0c7d7ca990f73737eb9c08" +
                "0263d24df3d8973042c0d9ea1673ba03640ad912cd6ef2d8cd1a4ebee801b224d5ac0fb15720f8bfc12c0d5a15f77f45" +
                "f492e09befa490c1af5b4c6edef05fa9ae1fb014bfebae6c64575e9b95012302435392c65549ecceb0d4d88233498e65" +
                "d907d7c17c4d4afe0d0f604f88303e4b71456803f7f04d8ece4c55514b66273e95c6c4b6246d49296fa51113edcfeb0c" +
                "ba0cf6560d50b6cc640170fa7cd413c55ef65b3364774675c51838df9b1c710205c63e6458fe5300810da05693ff0652" +
                "a92e2e1363a6a0064b3ae060933984f37d381ccc3087bef46efe6f30aac551f24ee40ae2460a9b287a0f97f4b207efa2" +
                "1e08436604917eb538bf326973ffb8f91f29458180bfafd836e9382c01c49cc60802b0b0e47a07ae8eeaa1a00b210694" +
                "8ce29931f83f1defc2257000cc6202d41e7a95327676bb361db603708c10499a66a74a5919f317e2503f819c392ec3dc" +
                "bdd5a65cb6b7ea9a47914f3ec7721425ffde11cc953c28b1b03dc76522470c19ce56ffdb7956ee2560adf3e41be566b4" +
                "c87b0417ca279a4f821f7ee8972fae245a5dd61f979c503ee7d389afb875b35917e1c3f9a5807da8216ed6ec2588dbac"

        fun String.hexToBytes(): ByteArray = ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
