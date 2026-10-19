package com.mk.daftar.core.data.repository

import com.mk.daftar.core.datastore.DaftarPreferencesDataSource
import com.mk.daftar.core.domain.update.SoftUpdateSnooze
import com.mk.daftar.core.domain.update.UpdateSnoozeRepository

/** The postponed soft update over the non-sensitive preferences store. Readable while the vault is locked. */
internal class UpdateSnoozeRecordRepository(private val source: DaftarPreferencesDataSource) : UpdateSnoozeRepository {
    override suspend fun softSnooze(): SoftUpdateSnooze? = source.softUpdateSnooze()

    override suspend fun snoozeSoft(snooze: SoftUpdateSnooze) {
        source.setSoftUpdateSnooze(snooze)
    }
}
