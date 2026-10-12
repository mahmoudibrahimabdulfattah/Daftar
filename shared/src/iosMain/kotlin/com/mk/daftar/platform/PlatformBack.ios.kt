package com.mk.daftar.platform

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS has no system back gesture for these screens; the top bar's close control is the way out.
}
