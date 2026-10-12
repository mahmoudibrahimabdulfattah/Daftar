package com.mk.daftar.platform

import androidx.compose.runtime.Composable

/**
 * Handles the platform's back gesture while [enabled]: Android's system back, which closes a developer screen before it
 * would leave the app. iOS has no system back, so its screens carry their own close control.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
