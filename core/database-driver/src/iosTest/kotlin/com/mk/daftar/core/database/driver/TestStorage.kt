@file:OptIn(ExperimentalForeignApi::class)

package com.mk.daftar.core.database.driver

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite

/** File access for the shared database tests, on the iOS simulator. */
internal object TestStorage {
    /** Each test database lives in its own directory, so its companion files are removed with it. */
    fun newDatabasePath(): String {
        val directory = NSTemporaryDirectory() + "sqlcipher-test-" + NSUUID().UUIDString()
        NSFileManager.defaultManager.createDirectoryAtPath(
            directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        return "$directory/notes.db"
    }

    /** The database file and its WAL/SHM/journal companions, byte for byte, in name order. */
    fun snapshot(databasePath: String): List<Pair<String, ByteArray>> {
        val directory = databasePath.substringBeforeLast('/')
        val base = databasePath.substringAfterLast('/')
        val names = NSFileManager.defaultManager.contentsOfDirectoryAtPath(directory, error = null)
            .orEmpty()
            .map { it as String }
            .filter { it.startsWith(base) }
            .sorted()
        return names.map { it to readBytes("$directory/$it") }
    }

    fun flipByte(path: String, offset: Int) {
        val bytes = readBytes(path)
        require(offset < bytes.size) { "offset $offset beyond ${bytes.size} bytes" }
        bytes[offset] = (bytes[offset].toInt() xor 0x01).toByte()
        writeBytes(path, bytes)
    }

    fun deleteDatabase(path: String) {
        NSFileManager.defaultManager.removeItemAtPath(path.substringBeforeLast('/'), error = null)
    }

    private fun readBytes(path: String): ByteArray {
        val file = fopen(path, "rb") ?: return ByteArray(0)
        try {
            fseek(file, 0, SEEK_END)
            val size = ftell(file).toInt()
            fseek(file, 0, SEEK_SET)
            val result = ByteArray(size)
            if (size > 0) {
                result.usePinned { pinned -> fread(pinned.addressOf(0), 1.convert(), size.convert(), file) }
            }
            return result
        } finally {
            fclose(file)
        }
    }

    private fun writeBytes(path: String, bytes: ByteArray) {
        val file = checkNotNull(fopen(path, "wb")) { "could not open $path for writing" }
        try {
            if (bytes.isNotEmpty()) {
                bytes.usePinned { pinned -> fwrite(pinned.addressOf(0), 1.convert(), bytes.size.convert(), file) }
            }
        } finally {
            fclose(file)
        }
    }
}
