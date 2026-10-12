package com.mk.daftar.feature.prices

import androidx.compose.runtime.Composable

/**
 * Handles the platform's back gesture while [enabled]. A dirty manual draft asks before the editor closes; the screen's
 * own back control goes through the same check. iOS has no system back gesture for this screen.
 */
@Composable
expect fun DraftBackGuard(enabled: Boolean, onBack: () -> Unit)
