package com.mk.daftar.core.account.di

import com.mk.daftar.core.domain.cloud.CloudAccount
import com.mk.daftar.core.domain.cloud.CloudAccountGateway
import com.mk.daftar.core.domain.cloud.CloudAuthOutcome
import com.mk.daftar.core.domain.cloud.CloudAvailability
import com.mk.daftar.core.domain.cloud.CloudProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.module.Module
import org.koin.dsl.module

/** Cloud backups are Android-only in this release. On iOS the gateway reports unavailable and never signs in. */
actual val accountModule: Module = module {
    single<CloudAccountGateway> { UnavailableCloudAccountGateway }
}

private object UnavailableCloudAccountGateway : CloudAccountGateway {
    override val availability: CloudAvailability = CloudAvailability.UNAVAILABLE
    override val provider: CloudProvider = CloudProvider.GOOGLE
    override val account: Flow<CloudAccount?> = flowOf(null)
    override fun currentAccount(): CloudAccount? = null
    override suspend fun signIn(): CloudAuthOutcome = CloudAuthOutcome.Unavailable
    override suspend fun reauthenticate(): CloudAuthOutcome = CloudAuthOutcome.Unavailable
    override suspend fun revokeProviderAuthorization(): Boolean = true
    override suspend fun deleteAuthAccount(): CloudAuthOutcome = CloudAuthOutcome.Unavailable
    override suspend fun signOut() = Unit
}
