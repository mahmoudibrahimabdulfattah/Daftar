package com.mk.daftar.feature.banks

import androidx.compose.runtime.Composable

/**
 * Handles the platform's back gesture while the bank directory covers the form, so it closes the directory instead of
 * leaving the editor. iOS has no system back gesture here; the top bar's back control does the same.
 */
@Composable
expect fun PickerBackGuard(enabled: Boolean, onBack: () -> Unit)
