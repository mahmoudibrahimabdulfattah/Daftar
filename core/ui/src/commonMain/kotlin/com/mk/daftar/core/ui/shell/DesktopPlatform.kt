package com.mk.daftar.core.ui.shell

/**
 * True only on desktop, fixed at build time. For choices made outside composition, such as navigation metadata, where
 * [LocalDesktopLayout] cannot be read. Phones and tablets never see a desktop-only arrangement.
 */
expect val isDesktopPlatform: Boolean
