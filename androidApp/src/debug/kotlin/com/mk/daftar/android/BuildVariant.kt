package com.mk.daftar.android

import com.mk.daftar.core.security.AndroidSecurityConfiguration

/**
 * Debug builds (`com.mk.daftar.debug`) may use a software-backed Keystore, which emulators report. The permission is
 * honored only because this application is debuggable. Demo data only; the debug shell shows a persistent banner.
 */
internal object BuildVariant {
    val securityConfiguration: AndroidSecurityConfiguration = AndroidSecurityConfiguration.AllowSoftwareForDebug

    /** The debug shell renders "Debug build — software Keystore permitted. Demo data only." when this is true. */
    val showsDebugBanner: Boolean = true

    /** Debug builds offer the developer entries on the locked surface: the design catalog and the shell preview. */
    val developerTools: Boolean = true
}
