package com.mk.daftar.feature.prices

import androidx.compose.runtime.Composable

@Composable
actual fun DraftBackGuard(enabled: Boolean, onBack: () -> Unit) {
    // The desktop window has no system back; Escape and the top bar's back control go through the draft guard.
}
