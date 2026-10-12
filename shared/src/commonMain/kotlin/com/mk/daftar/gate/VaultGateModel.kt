package com.mk.daftar.gate

import com.mk.daftar.core.domain.session.VaultSessionState

/**
 * What the gate shows for a session state. Only [Shell] reaches private content, and it carries the generation that
 * owns the navigation. Every other surface is neutral and carries no figures.
 */
internal sealed interface VaultSurface {
    /** The temporary locked surface. Phase 9 replaces it with setup and unlock without moving the gate. */
    data object Locked : VaultSurface

    /** Opening or closing. [closing] only changes the wording. */
    data class Progress(val closing: Boolean) : VaultSurface

    data object Unavailable : VaultSurface

    /** The private shell of one open generation. */
    data class Shell(val generation: Long) : VaultSurface
}

internal fun vaultSurfaceFor(state: VaultSessionState): VaultSurface = when (state) {
    VaultSessionState.Locked -> VaultSurface.Locked
    VaultSessionState.Opening -> VaultSurface.Progress(closing = false)
    VaultSessionState.Closing -> VaultSurface.Progress(closing = true)
    VaultSessionState.Unavailable -> VaultSurface.Unavailable
    is VaultSessionState.Open -> VaultSurface.Shell(state.generation)
}

/** The developer tools that the locked surface can open. They exist only in debug builds. */
internal enum class DeveloperDestination { DESIGN_CATALOG, SHELL_PREVIEW }

/**
 * Whether the locked surface offers developer entries. A release build never does, and an open or transitioning
 * session never does either, so no developer route is reachable from private content.
 */
internal fun developerEntriesVisible(state: VaultSessionState, developerTools: Boolean): Boolean =
    developerTools && state == VaultSessionState.Locked
