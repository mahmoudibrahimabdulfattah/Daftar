package com.mk.daftar.android

import android.content.res.Configuration
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.data.session.VaultOpenFailure
import com.mk.daftar.core.domain.repository.GoldHoldingInput
import com.mk.daftar.core.domain.repository.GoldRepository
import com.mk.daftar.core.domain.session.VaultSessionState
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.GoldForm
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.GoldWeight
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.GlobalContext

/** The production graph on the debug emulator: the Keystore policy, Room over SQLCipher, and the activity's locking. */
class VaultLifecycleDeviceTest {

    @Before
    fun startFromAnEmptyVault() = runBlocking<Unit> { DeviceVault.reset() }

    @After
    fun leaveNoVaultBehind() = runBlocking<Unit> { DeviceVault.reset() }

    // Every test opens the vault while the launcher is resumed, as the real flow does. A background stop that
    // precedes an unlock with no visible screen keeps the vault locked by design.

    @Test
    fun aRecordSurvivesLockAndReopenThroughTheProductionGraph() = runBlocking<Unit> {
        ActivityScenario.launch(MainActivity::class.java).use {
            val session = DeviceVault.session
            val gold = GlobalContext.get().get<GoldRepository>()
            val opened = DeviceVault.authenticator.create(DeviceVault.pin("246810"))
            check(opened is Outcome.Success) { "create must succeed, got ${(opened as Outcome.Failure).error}" }

            val created = gold.create(
                GoldHoldingInput(GoldKarat.K24, GoldForm.BAR, GoldWeight(Decimal.parseCanonical("2.5")), "demo"),
            )
            check(created is Outcome.Success) {
                "the record must be written, got ${(created as Outcome.Failure).error}"
            }
            val id = created.data.value.id

            session.lock()
            assertEquals(VaultSessionState.Locked, session.state.value)
            assertEquals(VaultRead.Locked, gold.observe(id).first())

            assertEquals(
                VaultOpenFailure.Security(com.mk.daftar.core.security.SecurityError.AuthenticationFailed),
                (DeviceVault.authenticator.unlock(DeviceVault.pin("000001")) as Outcome.Failure).error,
            )
            assertIs<Outcome.Success<Unit>>(DeviceVault.authenticator.unlock(DeviceVault.pin("246810")))
            assertIs<VaultRead.Ready<*>>(gold.observe(id).first())
        }
    }

    @Test
    fun theLauncherWindowIsSecureForItsWholeLifetime() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            }
        }
    }

    @Test
    fun backgroundingTheActivityClosesTheSession() = runBlocking<Unit> {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertIs<Outcome.Success<Unit>>(DeviceVault.authenticator.create(DeviceVault.pin("246810")))
            assertTrue(DeviceVault.session.state.value is VaultSessionState.Open)

            scenario.moveToState(Lifecycle.State.CREATED)
            DeviceVault.awaitSession { it == VaultSessionState.Locked }
        }
    }

    @Test
    fun aRotationKeepsTheSessionOpen() = runBlocking<Unit> {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                assertIs<Outcome.Success<Unit>>(DeviceVault.authenticator.create(DeviceVault.pin("246810")))
                // A real configuration change: the system sets isChangingConfigurations, so nothing locks.
                device.setOrientationLeft()
                awaitLandscape(scenario)
                assertTrue(DeviceVault.session.state.value is VaultSessionState.Open)
            } finally {
                device.unfreezeRotation()
            }
        }
    }

    private suspend fun awaitLandscape(scenario: ActivityScenario<MainActivity>) {
        withTimeout(5_000L) {
            while (true) {
                var landscape = false
                scenario.onActivity {
                    landscape =
                        it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                }
                if (landscape) return@withTimeout
                delay(25L)
            }
        }
    }
}
