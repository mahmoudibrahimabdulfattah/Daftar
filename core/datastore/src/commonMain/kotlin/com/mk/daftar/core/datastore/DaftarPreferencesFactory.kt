package com.mk.daftar.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import okio.Path

/** The preferences file name inside the device-only preferences directory. */
const val DAFTAR_PREFERENCES_FILE_NAME: String = "daftar.preferences_pb"

/**
 * Builds the preferences source over the file [produceFile] returns. A corrupt file is replaced by empty preferences,
 * and that recovery is reported through the source's observation state.
 */
fun createDaftarPreferencesDataSource(
    produceFile: () -> Path,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
): DaftarPreferencesDataSource {
    val recovered = MutableStateFlow(false)
    val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
        corruptionHandler = ReplaceFileCorruptionHandler { _ ->
            recovered.value = true
            emptyPreferences()
        },
        scope = scope,
        produceFile = produceFile,
    )
    return DaftarPreferencesDataSource(dataStore, recovered)
}
