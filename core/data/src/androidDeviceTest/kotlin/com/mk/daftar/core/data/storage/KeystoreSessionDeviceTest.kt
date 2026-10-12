package com.mk.daftar.core.data.storage

import androidx.test.platform.app.InstrumentationRegistry
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.data.ids.UuidV4Generator
import com.mk.daftar.core.data.repository.GoldRecordRepository
import com.mk.daftar.core.data.session.DaftarDatabaseBuilderProvider
import com.mk.daftar.core.data.session.DaftarVaultSession
import com.mk.daftar.core.data.session.VaultAuthenticator
import com.mk.daftar.core.data.session.VaultOpenFailure
import com.mk.daftar.core.database.daftarDatabaseBuilder
import com.mk.daftar.core.database.daftarDatabaseFile
import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.domain.repository.GoldHoldingInput
import com.mk.daftar.core.domain.repository.InactivityTimeout
import com.mk.daftar.core.domain.repository.PreferencesRead
import com.mk.daftar.core.domain.repository.ThemeMode
import com.mk.daftar.core.domain.repository.UserPreferences
import com.mk.daftar.core.domain.repository.UserPreferencesRepository
import com.mk.daftar.core.domain.session.VaultSessionState
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.GoldForm
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.GoldWeight
import com.mk.daftar.core.security.AndroidSecurityConfiguration
import com.mk.daftar.core.security.PinNormalizer
import com.mk.daftar.core.security.SecretBytes
import com.mk.daftar.core.security.SecurityError
import com.mk.daftar.core.security.VaultSecurity
import com.mk.daftar.core.security.androidSecurityConfigurationModule
import com.mk.daftar.core.security.di.securityModule
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication

/**
 * The production session on the emulator: the real VaultSecurity over the Keystore (the software-backed policy is
 * permitted only for debuggable builds), real Room over SQLCipher, then create, write, lock, a refused PIN, and a
 * reopen that reads the record back. The vault and database are removed afterwards.
 */
class KeystoreSessionDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aRecordWrittenThroughTheKeystoreVaultSurvivesLockAndReopen() = runTest {
        val koin = koinApplication {
            androidContext(context)
            modules(
                securityModule,
                androidSecurityConfigurationModule(AndroidSecurityConfiguration.AllowSoftwareForDebug),
            )
        }.koin
        val security: VaultSecurity = koin.get()
        val databaseFile = daftarDatabaseFile(context)
        val provider = DaftarDatabaseBuilderProvider { daftarDatabaseBuilder(context, databaseFile) }
        val session = DaftarVaultSession(security, provider, Clock.System, UuidV4Generator())
        val authenticator = VaultAuthenticator(security, session, MemoryPreferences)
        val gold = GoldRecordRepository(session.host)
        try {
            val created = authenticator.create(pin("246810"))
            if (created is Outcome.Failure) {
                // A vault from an earlier run is still on the device. It must open with the same PIN.
                assertIs<Outcome.Success<Unit>>(authenticator.unlock(pin("246810")))
            }
            val id = gold.create(
                GoldHoldingInput(GoldKarat.K24, GoldForm.BAR, GoldWeight(Decimal.parseCanonical("2.5")), "demo"),
            ).requireSuccess().value.id

            session.lock()
            assertEquals(VaultSessionState.Locked, session.state.value)
            assertEquals(VaultRead.Locked, gold.observe(id).first())

            val refused = authenticator.unlock(pin("000001"))
            assertIs<Outcome.Failure<VaultOpenFailure>>(refused)
            assertEquals(VaultOpenFailure.Security(SecurityError.AuthenticationFailed), refused.error)

            assertIs<Outcome.Success<Unit>>(authenticator.unlock(pin("246810")))
            val reread = gold.observe(id).first()
            assertIs<VaultRead.Ready<*>>(reread)
            assertTrue(session.state.value is VaultSessionState.Open)
        } finally {
            session.lock()
            File(context.noBackupFilesDir, "daftar-security").deleteRecursively()
            checkNotNull(databaseFile.parentFile).deleteRecursively()
        }
    }

    private fun pin(digits: String): SecretBytes =
        checkNotNull((PinNormalizer.normalize(digits) as? Outcome.Success)?.data) { "test PIN must normalize" }
}

/** Holds preferences in memory. This test never reads them: it needs only the authenticator's constructor. */
private object MemoryPreferences : UserPreferencesRepository {
    override fun observe(): Flow<PreferencesRead> = flowOf(PreferencesRead.Ready(UserPreferences(), false))

    override suspend fun setThemeMode(mode: ThemeMode): Outcome<Unit, StorageError> = Outcome.Success(Unit)

    override suspend fun setLanguage(language: AppLanguage): Outcome<Unit, StorageError> = Outcome.Success(Unit)

    override suspend fun setInactivityTimeout(timeout: InactivityTimeout): Outcome<Unit, StorageError> =
        Outcome.Success(Unit)

    override suspend fun setBiometricEnabled(enabled: Boolean): Outcome<Unit, StorageError> = Outcome.Success(Unit)
}
