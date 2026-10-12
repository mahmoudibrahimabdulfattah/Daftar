package com.mk.daftar.feature.prices

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun DraftBackGuard(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
