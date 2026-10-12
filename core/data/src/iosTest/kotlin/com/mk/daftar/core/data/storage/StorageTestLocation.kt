@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.mk.daftar.core.data.storage

import com.mk.daftar.core.data.session.DaftarDatabaseBuilderProvider
import com.mk.daftar.core.database.DAFTAR_DATABASE_FILE_NAME
import com.mk.daftar.core.database.daftarDatabaseBuilder
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/** A fresh, private database location for one test on the iOS simulator. */
internal fun newTestLocation(): TestLocation {
    val directory = NSTemporaryDirectory() + "daftar-data-test-" + NSUUID().UUIDString()
    NSFileManager.defaultManager.createDirectoryAtPath(
        directory,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    val path = "$directory/$DAFTAR_DATABASE_FILE_NAME"
    val provider = DaftarDatabaseBuilderProvider { _ -> daftarDatabaseBuilder(path) }
    return TestLocation(provider) { NSFileManager.defaultManager.removeItemAtPath(directory, error = null) }
}
