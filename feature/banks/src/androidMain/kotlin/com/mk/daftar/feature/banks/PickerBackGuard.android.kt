package com.mk.daftar.feature.banks

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun PickerBackGuard(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
