package com.mk.daftar.feature.auth

import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.security.SecretBytes
import com.mk.daftar.core.security.SecurityError
import com.mk.daftar.core.security.SecurityStatus

/**
 * The only way authentication reaches the vault. `shared` binds it to the security layer and the authenticator, so this
 * feature never sees a key lease or the data layer. The caller owns the [SecretBytes] it passes and wipes them.
 */
interface AuthGateway {
    /** A snapshot of the vault's state. It is not a stream: callers ask again when they need an answer. */
    suspend fun status(): SecurityStatus

    /** Creates the vault and opens it. Never retried blindly: after a failure the caller rechecks [status]. */
    suspend fun create(pin: SecretBytes): AuthResult

    suspend fun unlock(pin: SecretBytes): AuthResult
}

/** The outcome of one create or unlock, in terms the feature owns. */
sealed interface AuthResult {
    /** The vault is open. The gate shows the private shell for the new generation. */
    data object Opened : AuthResult

    /** The security layer refused or failed. Carries no PIN or key material. */
    data class Security(val error: SecurityError) : AuthResult

    /** Opening the database failed after the security layer succeeded. */
    data class Storage(val error: StorageError) : AuthResult

    /** A session is already open, so this attempt was not made. */
    data object AlreadyOpen : AuthResult

    /** A lock arrived while the attempt was in flight. The late result was discarded silently. */
    data object Superseded : AuthResult
}
