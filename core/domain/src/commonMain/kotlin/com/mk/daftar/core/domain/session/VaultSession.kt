package com.mk.daftar.core.domain.session

import kotlinx.coroutines.flow.StateFlow

/** Where the vault session is. Only [Open] exposes data, and it carries the generation the data belongs to. */
sealed interface VaultSessionState {
    data object Locked : VaultSessionState

    data object Opening : VaultSessionState

    data class Open(val generation: Long) : VaultSessionState

    data object Closing : VaultSessionState

    /** The session cannot open, for example after an unrecoverable storage or schema failure. */
    data object Unavailable : VaultSessionState
}

/**
 * The session lifetime boundary. Unlocking belongs to the composition-facing authentication adapter, which keeps PIN
 * buffers and security types out of the domain. Locking revokes access before it finishes cleaning up.
 */
interface VaultSession {
    val state: StateFlow<VaultSessionState>

    /**
     * Revokes access immediately, cancels session work, closes the database and releases the key. Idempotent. Cleanup
     * completes even when the caller is cancelled.
     */
    suspend fun lock()
}
