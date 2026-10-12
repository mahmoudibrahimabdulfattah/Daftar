package com.mk.daftar.android

import com.mk.daftar.core.security.AndroidSecurityConfiguration

/**
 * Release builds always require hardware-backed keys. No preference, environment variable, remote flag or UI override
 * can change this, and no debug banner is shown.
 */
internal object BuildVariant {
    val securityConfiguration: AndroidSecurityConfiguration = AndroidSecurityConfiguration.RequireHardware

    val showsDebugBanner: Boolean = false
}
