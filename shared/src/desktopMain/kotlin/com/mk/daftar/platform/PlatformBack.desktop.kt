package com.mk.daftar.platform

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // The desktop window has no system back; Escape and the top bar's close control are the way out.
}
