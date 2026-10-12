package com.mk.daftar.core.data.storage

import androidx.test.platform.app.InstrumentationRegistry
import com.mk.daftar.core.data.session.DaftarDatabaseBuilderProvider
import com.mk.daftar.core.database.DAFTAR_DATABASE_FILE_NAME
import com.mk.daftar.core.database.daftarDatabaseBuilder
import java.io.File
import java.util.UUID

/** A fresh, private database location for one test on the emulator or a device. */
internal fun newTestLocation(): TestLocation {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val directory = File(context.cacheDir, "daftar-data-test-${UUID.randomUUID()}").apply { mkdirs() }
    val file = File(directory, DAFTAR_DATABASE_FILE_NAME)
    val provider = DaftarDatabaseBuilderProvider { daftarDatabaseBuilder(context, file) }
    return TestLocation(provider) { directory.deleteRecursively() }
}
