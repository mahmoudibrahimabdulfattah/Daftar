package com.mk.daftar.feature.settings

import com.mk.daftar.core.security.BiometricCapability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BiometricReconciliationTest {

    private fun settings(preference: Boolean, slot: BiometricSlotFact, capability: BiometricCapability) =
        biometricSettingsFor(preference, BiometricFacts(slot, capability))

    @Test
    fun aValidSlotWithAPreferenceIsOnAndReady() {
        val result = settings(true, BiometricSlotFact.Valid, BiometricCapability.Available)
        assertEquals(BiometricSettings(enabled = true, availability = BiometricAvailability.Ready), result)
    }

    @Test
    fun thePreferenceAloneNeverClaimsBiometricsWork() {
        val absent = settings(true, BiometricSlotFact.Absent, BiometricCapability.Available)
        assertFalse(absent.enabled)
        assertEquals(BiometricAvailability.Ready, absent.availability)
        assertTrue(absent.canEnable)
        assertFalse(settings(false, BiometricSlotFact.Valid, BiometricCapability.Available).enabled)
    }

    @Test
    fun unenrolledDevicesAreNotMistakenForInvalidatedKeys() {
        val result = settings(true, BiometricSlotFact.Invalid, BiometricCapability.NotEnrolled)
        assertEquals(BiometricAvailability.NotEnrolled, result.availability)
        assertFalse(result.canEnable)
        assertFalse(result.enabled)
    }

    @Test
    fun aChangedEnrollmentIsInvalidatedAndCanBeEnabledAgain() {
        val result = settings(true, BiometricSlotFact.Invalid, BiometricCapability.Available)
        assertEquals(BiometricAvailability.Invalidated, result.availability)
        assertFalse(result.enabled)
        assertTrue(result.canEnable)
    }

    @Test
    fun aTemporarilyUnavailablePlatformIsNotCalledUnenrolled() {
        val platform = settings(true, BiometricSlotFact.Valid, BiometricCapability.Unavailable)
        assertEquals(BiometricAvailability.Unavailable, platform.availability)
        assertFalse(platform.canEnable)

        val unreadable = settings(true, BiometricSlotFact.Unreadable, BiometricCapability.Available)
        assertEquals(BiometricAvailability.Unavailable, unreadable.availability)
        assertFalse(unreadable.enabled)
    }
}
