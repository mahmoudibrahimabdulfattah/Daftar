package com.mk.daftar.gate

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.data.session.VaultAuthenticator
import com.mk.daftar.core.data.session.VaultOpenFailure
import com.mk.daftar.core.security.SecretBytes
import com.mk.daftar.core.security.SecurityStatus
import com.mk.daftar.core.security.VaultSecurity
import com.mk.daftar.feature.auth.AuthGateway
import com.mk.daftar.feature.auth.AuthResult

/**
 * The composition-facing bridge from the auth feature to the vault. Status comes straight from the security layer, and
 * create and unlock go through the authenticator, which owns the key lease. Nothing here keeps a PIN or a lease.
 */
internal class VaultAuthGateway(private val security: VaultSecurity, private val authenticator: VaultAuthenticator) :
    AuthGateway {
    override suspend fun status(): SecurityStatus = security.status()

    override suspend fun create(pin: SecretBytes): AuthResult = authenticator.create(pin).toAuthResult()

    override suspend fun unlock(pin: SecretBytes): AuthResult = authenticator.unlock(pin).toAuthResult()
}

internal fun Outcome<Unit, VaultOpenFailure>.toAuthResult(): AuthResult = when (this) {
    is Outcome.Success -> AuthResult.Opened

    is Outcome.Failure -> when (val failure = error) {
        is VaultOpenFailure.Security -> AuthResult.Security(failure.error)
        is VaultOpenFailure.Storage -> AuthResult.Storage(failure.error)
        VaultOpenFailure.AlreadyOpen -> AuthResult.AlreadyOpen
        VaultOpenFailure.Superseded -> AuthResult.Superseded
    }
}
