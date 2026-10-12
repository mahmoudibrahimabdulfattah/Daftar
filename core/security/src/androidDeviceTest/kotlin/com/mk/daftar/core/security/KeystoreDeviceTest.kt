package com.mk.daftar.core.security

import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Real Android Keystore behavior on the device under test. Each test uses its own alias and removes it afterwards.
 * Software-backed keys are accepted here only because the emulator reports them. The production policy is covered
 * separately.
 */
class KeystoreDeviceTest {

    private fun alias(name: String) = "com.mk.daftar.security.devicetest.$name.${UUID.randomUUID()}"

    private val plain = ByteArray(EnvelopeCodec.PLAIN_BYTES) { (it * 31).toByte() }

    @Test
    fun wrapAndUnwrapRoundTripWithAFreshIvEveryTime() = runTest {
        val protector = AndroidKeystoreProtector(alias("roundtrip"), KeystorePolicy.AllowSoftwareForDebug)
        try {
            protector.prepare().requireSuccess()
            val first = protector.seal(plain).requireSuccess()
            val second = protector.seal(plain).requireSuccess()
            assertFalse(first.contentEquals(second), "IV must be fresh on every seal")
            assertContentEquals(plain, protector.unseal(first).requireSuccess())
            assertContentEquals(plain, protector.unseal(second).requireSuccess())
        } finally {
            protector.deleteKeyForTests()
        }
    }

    @Test
    fun aSealedRecordIsNotPlaintextAndIsCompactlyBounded() = runTest {
        val protector = AndroidKeystoreProtector(alias("layout"), KeystorePolicy.AllowSoftwareForDebug)
        try {
            protector.prepare().requireSuccess()
            val sealed = protector.seal(plain).requireSuccess()
            assertEquals(1 + 12 + plain.size + 16, sealed.size)
            assertEquals(12, sealed[0].toInt())
            assertTrue(sealed.size < EnvelopeCodec.MAX_SEALED_BYTES)
            assertFalse(sealed.copyOfRange(13, 13 + 16).contentEquals(plain.copyOf(16)))
        } finally {
            protector.deleteKeyForTests()
        }
    }

    @Test
    fun securityLevelIsReportedAndTheProductionPolicyMatchesIt() = runTest {
        val probe = AndroidKeystoreProtector(alias("level"), KeystorePolicy.AllowSoftwareForDebug)
        val level = try {
            probe.prepare().requireSuccess()
            probe.securityLevel().requireSuccess()
        } finally {
            probe.deleteKeyForTests()
        }
        println("KEYSTORE_SECURITY_LEVEL=$level")

        val production = AndroidKeystoreProtector(alias("policy"), KeystorePolicy.RequireHardware)
        val outcome = production.prepare()
        val hardwareBacked = level == KeystoreLevel.StrongBox || level == KeystoreLevel.TrustedEnvironment
        if (hardwareBacked) {
            outcome.requireSuccess()
            production.deleteKeyForTests()
        } else {
            assertEquals(SecurityError.UnsupportedHardware, outcome.requireFailure())
            assertEquals(SecurityError.DeviceKeyUnavailable, production.securityLevel().requireFailure())
        }
    }

    @Test
    fun aRecordSealedUnderOneKeyIsRejectedByAnother() = runTest {
        val sealingKey = AndroidKeystoreProtector(alias("sealer"), KeystorePolicy.AllowSoftwareForDebug)
        val otherKey = AndroidKeystoreProtector(alias("other"), KeystorePolicy.AllowSoftwareForDebug)
        try {
            sealingKey.prepare().requireSuccess()
            otherKey.prepare().requireSuccess()
            val sealed = sealingKey.seal(plain).requireSuccess()
            assertEquals(SecurityError.CorruptState, otherKey.unseal(sealed).requireFailure())
            assertContentEquals(plain, sealingKey.unseal(sealed).requireSuccess())
        } finally {
            sealingKey.deleteKeyForTests()
            otherKey.deleteKeyForTests()
        }
    }

    @Test
    fun tamperedCiphertextAndTagAreRejected() = runTest {
        val protector = AndroidKeystoreProtector(alias("tamper"), KeystorePolicy.AllowSoftwareForDebug)
        try {
            protector.prepare().requireSuccess()
            val sealed = protector.seal(plain).requireSuccess()
            val bodyFlip = sealed.copyOf().also { it[sealed.size - 1] = (it[sealed.size - 1] + 1).toByte() }
            assertEquals(SecurityError.CorruptState, protector.unseal(bodyFlip).requireFailure())
            val ivFlip = sealed.copyOf().also { it[1] = (it[1] + 1).toByte() }
            assertEquals(SecurityError.CorruptState, protector.unseal(ivFlip).requireFailure())
            val wrongIvLength = sealed.copyOf().also { it[0] = 11 }
            assertEquals(SecurityError.CorruptState, protector.unseal(wrongIvLength).requireFailure())
        } finally {
            protector.deleteKeyForTests()
        }
    }

    @Test
    fun aMissingKeyIsReportedAndNeverReplacedByUnseal() = runTest {
        val protector = AndroidKeystoreProtector(alias("lost"), KeystorePolicy.AllowSoftwareForDebug)
        protector.prepare().requireSuccess()
        val sealed = protector.seal(plain).requireSuccess()
        protector.deleteKeyForTests()
        assertEquals(SecurityError.DeviceKeyUnavailable, protector.unseal(sealed).requireFailure())
        assertEquals(SecurityError.DeviceKeyUnavailable, protector.seal(plain).requireFailure())
    }

    @Test
    fun prepareNeverReplacesAnExistingKey() = runTest {
        val protector = AndroidKeystoreProtector(alias("keep"), KeystorePolicy.AllowSoftwareForDebug)
        try {
            protector.prepare().requireSuccess()
            val sealed = protector.seal(plain).requireSuccess()
            protector.prepare().requireSuccess()
            assertContentEquals(plain, protector.unseal(sealed).requireSuccess())
        } finally {
            protector.deleteKeyForTests()
        }
    }

    @Test
    fun differentAliasesHoldDifferentKeys() = runTest {
        val a = AndroidKeystoreProtector(alias("a"), KeystorePolicy.AllowSoftwareForDebug)
        val b = AndroidKeystoreProtector(alias("b"), KeystorePolicy.AllowSoftwareForDebug)
        try {
            a.prepare().requireSuccess()
            b.prepare().requireSuccess()
            assertEquals(SecurityError.CorruptState, b.unseal(a.seal(plain).requireSuccess()).requireFailure())
        } finally {
            a.deleteKeyForTests()
            b.deleteKeyForTests()
        }
    }
}
