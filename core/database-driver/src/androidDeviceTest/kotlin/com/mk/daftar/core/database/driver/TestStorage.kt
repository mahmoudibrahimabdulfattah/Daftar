package com.mk.daftar.core.database.driver

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID

/** Database files for the shared tests, on the Android device, under the app's databases directory. */
internal object TestStorage {
    fun newDatabasePath(): String = InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath(
        "sqlcipher-test-${UUID.randomUUID()}.db",
    ).absolutePath

    /** The database file and its WAL/SHM/journal companions, byte for byte, in name order. */
    fun snapshot(databasePath: String): List<Pair<String, ByteArray>> = companionsOf(databasePath)
        .sortedBy { it.name }
        .map { it.name to it.readBytes() }

    fun flipByte(path: String, offset: Int) {
        val file = File(path)
        val bytes = file.readBytes()
        require(offset < bytes.size) { "offset $offset beyond ${bytes.size} bytes" }
        bytes[offset] = (bytes[offset].toInt() xor 0x01).toByte()
        file.writeBytes(bytes)
    }

    fun deleteDatabase(path: String) {
        companionsOf(path).forEach { it.delete() }
    }

    private fun companionsOf(databasePath: String): List<File> {
        val database = File(databasePath)
        return database.parentFile!!.listFiles { file -> file.name.startsWith(database.name) }.orEmpty().toList()
    }
}
