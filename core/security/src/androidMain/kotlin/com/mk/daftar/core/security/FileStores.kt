package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A single file that is replaced atomically: write a temporary sibling, sync it, then rename over the target. */
internal class DurableFile(private val file: File) {
    suspend fun read(): Outcome<ByteArray?, SecurityError> = withContext(Dispatchers.IO) {
        try {
            when {
                !file.exists() -> Outcome.Success(null)
                file.length() > MAX_FILE_BYTES -> Outcome.Failure(SecurityError.CorruptState)
                else -> Outcome.Success(file.readBytes())
            }
        } catch (failure: IOException) {
            Outcome.Failure(SecurityError.PersistenceFailure)
        }
    }

    fun exists(): Boolean = file.exists()

    /** Removes the file if present. Idempotent. */
    suspend fun delete(): Outcome<Unit, SecurityError> = withContext(Dispatchers.IO) {
        try {
            Files.deleteIfExists(file.toPath())
            Outcome.Success(Unit)
        } catch (failure: IOException) {
            Outcome.Failure(SecurityError.PersistenceFailure)
        }
    }

    suspend fun writeAtomic(bytes: ByteArray): Outcome<Unit, SecurityError> = withContext(Dispatchers.IO) {
        try {
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, "${file.name}.tmp")
            FileOutputStream(temporary).use { out ->
                out.write(bytes)
                out.flush()
                out.fd.sync()
            }
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
            Outcome.Success(Unit)
        } catch (failure: IOException) {
            Outcome.Failure(SecurityError.PersistenceFailure)
        }
    }

    private companion object {
        const val MAX_FILE_BYTES = 64L * 1024
    }
}

/** The sealed PIN envelope, stored as-is. The platform protector already authenticates it. */
internal class FileRecordStore(file: File) : SecurityRecordStore {
    private val durable = DurableFile(file)

    override suspend fun read(): Outcome<ByteArray?, SecurityError> = durable.read()

    override suspend fun createIfAbsent(record: ByteArray): Outcome<Boolean, SecurityError> {
        if (durable.exists()) return Outcome.Success(false)
        return when (val written = durable.writeAtomic(record)) {
            is Outcome.Failure -> Outcome.Failure(written.error)
            is Outcome.Success -> Outcome.Success(true)
        }
    }

    override suspend fun replace(record: ByteArray): Outcome<Unit, SecurityError> = durable.writeAtomic(record)
}

/** The backoff record, sealed under its own Keystore alias so an edit on disk is detected. */
internal class FileBackoffStore(file: File, private val protector: PlatformEnvelopeProtector) : BackoffStore {
    private val durable = DurableFile(file)

    override suspend fun prepare(): Outcome<Unit, SecurityError> = protector.prepare()

    override suspend fun read(): Outcome<ByteArray?, SecurityError> {
        val sealed = when (val read = durable.read()) {
            is Outcome.Failure -> return Outcome.Failure(read.error)
            is Outcome.Success -> read.data ?: return Outcome.Success(null)
        }
        return when (val plain = protector.unseal(sealed)) {
            is Outcome.Failure -> Outcome.Failure(plain.error)
            is Outcome.Success -> Outcome.Success(plain.data)
        }
    }

    override suspend fun write(record: ByteArray): Outcome<Unit, SecurityError> {
        val sealed = when (val result = protector.seal(record)) {
            is Outcome.Failure -> return Outcome.Failure(result.error)
            is Outcome.Success -> result.data
        }
        return durable.writeAtomic(sealed)
    }
}

/** The biometric slot, stored as-is. The platform cipher authenticates it and its header, so an edit is detected. */
internal class FileBiometricSlotStore(file: File) : BiometricSlotStore {
    private val durable = DurableFile(file)

    override suspend fun read(): Outcome<ByteArray?, BiometricError> = when (val read = durable.read()) {
        is Outcome.Failure -> Outcome.Failure(read.error.toBiometricError())
        is Outcome.Success -> Outcome.Success(read.data)
    }

    override suspend fun write(slot: ByteArray): Outcome<Unit, BiometricError> =
        when (val written = durable.writeAtomic(slot)) {
            is Outcome.Failure -> Outcome.Failure(written.error.toBiometricError())
            is Outcome.Success -> Outcome.Success(Unit)
        }

    override suspend fun delete(): Outcome<Unit, BiometricError> = when (val deleted = durable.delete()) {
        is Outcome.Failure -> Outcome.Failure(deleted.error.toBiometricError())
        is Outcome.Success -> Outcome.Success(Unit)
    }
}
