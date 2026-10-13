package com.mk.daftar.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the first screen after launch is ready to show: the lock surface knows the vault status, so a biometric
 * prompt can start as soon as it is drawn. The Android splash stays up until then (with a cap), so the prompt follows
 * the splash at once instead of after a moment of "opening". It carries no private state and only ever turns true.
 */
object LaunchReadiness {
    private val state = MutableStateFlow(false)

    val ready: StateFlow<Boolean> = state.asStateFlow()

    fun markReady() {
        state.value = true
    }
}
