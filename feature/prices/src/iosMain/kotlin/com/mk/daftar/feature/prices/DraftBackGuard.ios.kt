package com.mk.daftar.feature.prices

import androidx.compose.runtime.Composable

@Composable
actual fun DraftBackGuard(enabled: Boolean, onBack: () -> Unit) {
    // iOS has no system back gesture for this screen; its top bar's back control is the way out and asks the same way.
}
