package com.mk.daftar.core.security

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/**
 * The release configuration on the device under test. The emulator reports software-backed Keystore keys, so the
 * release policy must refuse them and leave no key behind. On hardware the same policy must accept the key.
 */
class ReleasePolicyDeviceTest {

    private fun alias(name: String) = "com.mk.daftar.security.releasetest.$name.${UUID.randomUUID()}"

    @Test
    fun releaseConfigurationRejectsSoftwareBackedKeys() = runTest {
        val policy = resolveKeystorePolicy(AndroidSecurityConfiguration.RequireHardware, debuggable = true)
        assertEquals(KeystorePolicy.RequireHardware, policy)

        val probe = AndroidKeystoreProtector(alias("probe"), KeystorePolicy.AllowSoftwareForDebug)
        val level = try {
            probe.prepare().requireSuccess()
            probe.securityLevel().requireSuccess()
        } finally {
            probe.deleteKeyForTests()
        }

        val release = AndroidKeystoreProtector(alias("release"), policy)
        try {
            val outcome = release.prepare()
            if (level == KeystoreLevel.Software) {
                assertEquals(SecurityError.UnsupportedHardware, outcome.requireFailure())
                assertEquals(SecurityError.DeviceKeyUnavailable, release.securityLevel().requireFailure())
                assertEquals(SecurityError.DeviceKeyUnavailable, release.seal(ByteArray(8)).requireFailure())
            } else {
                outcome.requireSuccess()
            }
        } finally {
            release.deleteKeyForTests()
        }
    }
}
