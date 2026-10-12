@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mk.daftar.feature.settings

import com.mk.daftar.core.common.MonotonicTime
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.domain.repository.InactivityTimeout
import com.mk.daftar.core.domain.repository.PreferencesRead
import com.mk.daftar.core.domain.repository.ThemeMode
import com.mk.daftar.core.domain.repository.UserPreferences
import com.mk.daftar.core.domain.repository.UserPreferencesRepository
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.security.BiometricCapability
import com.mk.daftar.core.security.SecretBytes
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope

/** Preferences held in memory. A write can be made to fail once, or held until the test releases it. */
class FakePreferencesRepository(initial: UserPreferences = UserPreferences()) : UserPreferencesRepository {
    private val state =
        MutableStateFlow<PreferencesRead>(PreferencesRead.Ready(initial, recoveredFromCorruption = false))

    /** Every write in the order it started. */
    val calls = mutableListOf<String>()

    /** Fails the next write with this error and leaves the stored value unchanged. */
    var failNextWith: StorageError? = null

    /** Holds the next write until it is completed. */
    var hold: CompletableDeferred<Unit>? = null

    val current: UserPreferences
        get() = (state.value as PreferencesRead.Ready).preferences

    fun publishRead(read: PreferencesRead) {
        state.value = read
    }

    override fun observe(): Flow<PreferencesRead> = state

    override suspend fun setThemeMode(mode: ThemeMode): Outcome<Unit, StorageError> =
        write("theme:$mode") { copy(themeMode = mode) }

    override suspend fun setLanguage(language: AppLanguage): Outcome<Unit, StorageError> =
        write("language:$language") { copy(language = language) }

    override suspend fun setInactivityTimeout(timeout: InactivityTimeout): Outcome<Unit, StorageError> =
        write("timeout:$timeout") { copy(inactivityTimeout = timeout) }

    override suspend fun setBiometricEnabled(enabled: Boolean): Outcome<Unit, StorageError> =
        write("biometric:$enabled") { copy(biometricEnabled = enabled) }

    private suspend fun write(
        label: String,
        change: UserPreferences.() -> UserPreferences,
    ): Outcome<Unit, StorageError> {
        calls += label
        hold?.let {
            hold = null
            it.await()
        }
        failNextWith?.let {
            failNextWith = null
            return Outcome.Failure(it)
        }
        state.value = PreferencesRead.Ready(current.change(), recoveredFromCorruption = false)
        return Outcome.Success(Unit)
    }
}

/** A gateway whose answers each test scripts. It records the PINs it receives, which tests may then assert on. */
class FakeSettingsGateway : SettingsSecurityGateway {
    var facts: BiometricFacts = BiometricFacts(BiometricSlotFact.Absent, BiometricCapability.Available)

    var onVerify: suspend (String) -> Outcome<Unit, SecurityActionFailure> = { Outcome.Success(Unit) }
    var onChange: suspend (String, String) -> Outcome<Unit, SecurityActionFailure> = { _, _ -> Outcome.Success(Unit) }
    var onReenroll: suspend (String) -> Outcome<Unit, SecurityActionFailure> = { Outcome.Success(Unit) }
    var onDisable: suspend () -> Outcome<Unit, SecurityActionFailure> = { Outcome.Success(Unit) }

    val verified = mutableListOf<String>()
    val changed = mutableListOf<Pair<String, String>>()
    val reenrolled = mutableListOf<String>()
    var disables = 0

    override suspend fun biometricFacts(): BiometricFacts = facts

    override suspend fun verifyCurrentPin(pin: SecretBytes): Outcome<Unit, SecurityActionFailure> {
        val digits = pin.use { it.decodeToString() }
        verified += digits
        return onVerify(digits)
    }

    override suspend fun changePin(currentPin: SecretBytes, newPin: SecretBytes): Outcome<Unit, SecurityActionFailure> {
        val pair = currentPin.use { it.decodeToString() } to newPin.use { it.decodeToString() }
        changed += pair
        return onChange(pair.first, pair.second)
    }

    override suspend fun reenrollBiometrics(pin: SecretBytes): Outcome<Unit, SecurityActionFailure> {
        val digits = pin.use { it.decodeToString() }
        reenrolled += digits
        return onReenroll(digits)
    }

    override suspend fun disableBiometrics(): Outcome<Unit, SecurityActionFailure> {
        disables++
        return onDisable()
    }
}

/** A clock that follows the test scheduler, so countdowns advance with virtual time. */
fun TestScope.testClock(): MonotonicTime = MonotonicTime { testScheduler.currentTime.milliseconds }
