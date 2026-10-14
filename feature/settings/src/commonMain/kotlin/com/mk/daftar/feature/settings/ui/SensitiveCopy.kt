package com.mk.daftar.feature.settings.ui

import androidx.compose.runtime.Composable

/**
 * Copies a secret the owner asked to copy (the recovery key) so it can be written down while screenshots are blocked.
 * The platform marks it sensitive, keeps it on this device, and clears it after [SENSITIVE_CLIP_SECONDS] seconds.
 */
@Composable
internal expect fun rememberSensitiveCopy(): (String) -> Unit

internal const val SENSITIVE_CLIP_SECONDS: Long = 60
