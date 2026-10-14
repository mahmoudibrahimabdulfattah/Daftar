package com.mk.daftar.core.database.driver

import java.io.File
import java.nio.file.Files

/** File access for the shared database tests, on the desktop JVM. */
internal object TestStorage {
    /** Each test database lives in its own directory, so its companion files are removed with it. */
    fun newDatabasePath(): String =
        Files.createTempDirectory("sqlcipher-test-").toFile().resolve("notes.db").absolutePath

    /** The database file and its WAL/SHM/journal companions, byte for byte, in name order. */
    fun snapshot(databasePath: String): List<Pair<String, ByteArray>> {
        val file = File(databasePath)
        return file.parentFile.listFiles().orEmpty()
            .filter { it.name.startsWith(file.name) }
            .sortedBy { it.name }
            .map { it.name to it.readBytes() }
    }

    fun flipByte(path: String, offset: Int) {
        val file = File(path)
        val bytes = file.readBytes()
        require(offset < bytes.size) { "offset $offset beyond ${bytes.size} bytes" }
        bytes[offset] = (bytes[offset].toInt() xor 0x01).toByte()
        file.writeBytes(bytes)
    }

    fun deleteDatabase(path: String) {
        File(path).parentFile.deleteRecursively()
    }
}
