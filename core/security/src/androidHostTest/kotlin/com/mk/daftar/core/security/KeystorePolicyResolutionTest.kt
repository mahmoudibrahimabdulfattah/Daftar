package com.mk.daftar.core.security

import kotlin.test.Test
import kotlin.test.assertEquals

/** The policy decision alone, without a device: only a debuggable application may receive software keys. */
class KeystorePolicyResolutionTest {

    @Test
    fun theDefaultConfigurationRequiresHardwareInEveryBuild() {
        assertEquals(
            KeystorePolicy.RequireHardware,
            resolveKeystorePolicy(AndroidSecurityConfiguration.RequireHardware, debuggable = true),
        )
        assertEquals(
            KeystorePolicy.RequireHardware,
            resolveKeystorePolicy(AndroidSecurityConfiguration.RequireHardware, debuggable = false),
        )
    }

    @Test
    fun aPermissiveConfigurationIsHonoredOnlyInDebuggableApplications() {
        assertEquals(
            KeystorePolicy.AllowSoftwareForDebug,
            resolveKeystorePolicy(AndroidSecurityConfiguration.AllowSoftwareForDebug, debuggable = true),
        )
    }

    @Test
    fun aPermissiveConfigurationFallsBackToHardwareInAReleaseApplication() {
        assertEquals(
            KeystorePolicy.RequireHardware,
            resolveKeystorePolicy(AndroidSecurityConfiguration.AllowSoftwareForDebug, debuggable = false),
        )
    }
}
