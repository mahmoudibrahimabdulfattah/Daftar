package com.mk.daftar.feature.banks

import androidx.compose.runtime.Composable

@Composable
actual fun PickerBackGuard(enabled: Boolean, onBack: () -> Unit) {
    // iOS has no system back gesture for this screen; the directory's top bar back control closes it.
}
