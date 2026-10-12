package com.mk.daftar.android

import androidx.test.platform.app.InstrumentationRegistry
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.data.session.VaultAuthenticator
import com.mk.daftar.core.database.daftarDatabaseFile
import com.mk.daftar.core.domain.session.VaultSession
import com.mk.daftar.core.domain.session.VaultSessionState
import com.mk.daftar.core.security.PinNormalizer
import com.mk.daftar.core.security.SecretBytes
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.koin.core.context.GlobalContext

/** Helpers for the emulator tests: they use the application's own graph and start every test from an empty vault. */
internal object DeviceVault {
    val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    val session: VaultSession get() = GlobalContext.get().get()

    val authenticator: VaultAuthenticator get() = GlobalContext.get().get()

    /**
     * Locks the session, lets the process-level stop fallback of an earlier activity finish, then removes the vault and
     * its database. Debug-build demo data only.
     */
    suspend fun reset() {
        session.lock()
        delay(PROCESS_FALLBACK_SETTLE_MS)
        File(context.noBackupFilesDir, "daftar-security").deleteRecursively()
        checkNotNull(daftarDatabaseFile(context).parentFile).deleteRecursively()
    }

    fun pin(digits: String): SecretBytes =
        checkNotNull((PinNormalizer.normalize(digits) as? Outcome.Success)?.data) { "test PIN must normalize" }

    /** Polls until the session state satisfies [predicate], failing after a bounded wait. */
    suspend fun awaitSession(predicate: (VaultSessionState) -> Boolean) {
        withTimeout(TIMEOUT_MS) {
            while (!predicate(session.state.value)) delay(POLL_MS)
        }
    }

    private const val TIMEOUT_MS = 5_000L
    private const val PROCESS_FALLBACK_SETTLE_MS = 1_500L
    private const val POLL_MS = 25L
}
