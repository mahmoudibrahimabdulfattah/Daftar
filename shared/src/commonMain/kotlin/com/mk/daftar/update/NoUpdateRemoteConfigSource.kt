package com.mk.daftar.update

import com.mk.daftar.core.domain.update.RemoteConfigSource
import com.mk.daftar.core.domain.update.UpdatePolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** The update source on platforms without an in-app update prompt. It never offers an update. */
internal object NoUpdateRemoteConfigSource : RemoteConfigSource {
    override suspend fun cached(): UpdatePolicy? = null

    override suspend fun fetch(): UpdatePolicy? = null

    override fun updates(): Flow<Unit> = emptyFlow()
}
