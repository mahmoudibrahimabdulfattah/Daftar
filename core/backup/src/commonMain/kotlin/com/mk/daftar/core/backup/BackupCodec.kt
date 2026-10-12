package com.mk.daftar.core.backup

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.security.PasswordCrypto
import com.mk.daftar.core.security.SecretBytes
import kotlin.time.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** A decrypted and validated backup. Its header-level facts are returned with the snapshot. */
class DecodedBackup(val snapshot: BackupSnapshot, val exportedAt: Instant, val appVersion: String)

/** Authenticates and decodes a complete file. [BackupCodec] is the production implementation. */
fun interface BackupDecryptor {
    /** Returns the decoded backup, or the reason it was refused. [password] is wiped before this returns. */
    suspend fun decrypt(file: ByteArray, password: ByteArray): Outcome<DecodedBackup, BackupError>
}

/** Encrypts one snapshot into the complete file. [BackupCodec] is the production implementation. */
interface BackupEncryptor {
    /**
     * Returns the complete file. [password] is the UTF-8 bytes of an accepted password, and the implementation wipes it
     * before returning.
     */
    suspend fun encode(
        snapshot: BackupSnapshot,
        password: ByteArray,
        exportedAt: Instant,
        appVersion: String,
    ): Outcome<ByteArray, BackupError>
}

/**
 * Encrypts a snapshot into the version-one `.dftar` bytes and back. A fresh salt and nonce are drawn for every
 * export, and the Argon2id key is derived from the password for that salt only. The codec never writes a file and
 * never keeps the password, the key or the plaintext after it returns.
 */
class BackupCodec(private val crypto: PasswordCrypto) :
    BackupEncryptor,
    BackupDecryptor {

    override suspend fun decrypt(file: ByteArray, password: ByteArray): Outcome<DecodedBackup, BackupError> =
        decode(file, password)

    override suspend fun encode(
        snapshot: BackupSnapshot,
        password: ByteArray,
        exportedAt: Instant,
        appVersion: String,
    ): Outcome<ByteArray, BackupError> {
        val salt = random(BackupFormat.SALT_BYTES) ?: return fail(password, BackupError.ResourceUnavailable)
        val nonce = random(BackupFormat.NONCE_BYTES) ?: return fail(password, BackupError.ResourceUnavailable)
        return encodeWith(snapshot, password, exportedAt, appVersion, salt, nonce)
    }

    /**
     * Encodes with the given salt and nonce. Production always draws them fresh through [encode]. This entry point
     * exists so the format tests can pin a known-answer vector, and it rejects any salt or nonce of the wrong length.
     */
    internal suspend fun encodeWith(
        snapshot: BackupSnapshot,
        password: ByteArray,
        exportedAt: Instant,
        appVersion: String,
        salt: ByteArray,
        nonce: ByteArray,
    ): Outcome<ByteArray, BackupError> {
        try {
            require(salt.size == BackupFormat.SALT_BYTES && nonce.size == BackupFormat.NONCE_BYTES)
            if (snapshot.recordCount > BackupFormat.MAX_RECORDS) return Outcome.Failure(BackupError.TooManyRecords)
            val json = snapshot.toDocument(exportedAt, appVersion).let { document ->
                BackupJson.encodeToString(PayloadDocument.serializer(), document)
            }
            val plaintext = json.encodeToByteArray()
            if (plaintext.size > BackupFormat.MAX_PLAINTEXT_BYTES) return Outcome.Failure(BackupError.TooLarge)
            try {
                val key = deriveKey(password, salt) ?: return Outcome.Failure(BackupError.ResourceUnavailable)
                try {
                    val header = BackupHeader(salt, nonce, plaintext.size + BackupFormat.TAG_BYTES).encode()
                    val ciphertext = crypto.seal(key, nonce, header, plaintext)
                    return Outcome.Success(header + ciphertext)
                } finally {
                    key.wipe()
                }
            } finally {
                plaintext.fill(0)
            }
        } finally {
            password.fill(0)
        }
    }

    /**
     * Authenticates and decodes a whole file. The header is validated before the KDF runs. A wrong password and a
     * modified header or ciphertext both return [BackupError.AuthenticationFailed].
     */
    suspend fun decode(file: ByteArray, password: ByteArray): Outcome<DecodedBackup, BackupError> {
        try {
            val header = when (val parsed = BackupHeader.decode(file)) {
                is Outcome.Failure -> return Outcome.Failure(parsed.error)
                is Outcome.Success -> parsed.data
            }
            val key = deriveKey(password, header.salt) ?: return Outcome.Failure(BackupError.ResourceUnavailable)
            try {
                val associatedData = file.copyOfRange(0, BackupFormat.HEADER_BYTES)
                val ciphertext = file.copyOfRange(BackupFormat.HEADER_BYTES, file.size)
                val plaintext = crypto.open(key, header.nonce, associatedData, ciphertext)
                    ?: return Outcome.Failure(BackupError.AuthenticationFailed)
                try {
                    return parse(plaintext)
                } finally {
                    plaintext.fill(0)
                }
            } finally {
                key.wipe()
            }
        } finally {
            password.fill(0)
        }
    }

    private fun parse(plaintext: ByteArray): Outcome<DecodedBackup, BackupError> {
        val text = plaintext.decodeToString()
        val document = try {
            BackupJson.decodeFromString(PayloadDocument.serializer(), text)
        } catch (failure: SerializationException) {
            return Outcome.Failure(BackupError.CorruptPayload)
        } catch (failure: IllegalArgumentException) {
            return Outcome.Failure(BackupError.CorruptPayload)
        }
        if (document.schemaVersion >
            BackupFormat.PAYLOAD_SCHEMA_VERSION
        ) {
            return Outcome.Failure(BackupError.UnsupportedFormat)
        }
        // Only the one canonical encoding is accepted: re-encoding must reproduce the authenticated bytes exactly.
        if (BackupJson.encodeToString(PayloadDocument.serializer(), document) != text) {
            return Outcome.Failure(BackupError.CorruptPayload)
        }
        val exportedAt = try {
            Instant.parse(document.exportedAt)
        } catch (failure: IllegalArgumentException) {
            return Outcome.Failure(BackupError.CorruptPayload)
        }
        val snapshot = try {
            document.toSnapshot()
        } catch (failure: CorruptPayloadException) {
            return Outcome.Failure(BackupError.CorruptPayload)
        }
        if (snapshot.recordCount > BackupFormat.MAX_RECORDS) return Outcome.Failure(BackupError.TooManyRecords)
        if (!snapshot.isConsistent()) return Outcome.Failure(BackupError.CorruptPayload)
        return Outcome.Success(DecodedBackup(snapshot, exportedAt, document.appVersion))
    }

    private fun fail(password: ByteArray, error: BackupError): Outcome<ByteArray, BackupError> {
        password.fill(0)
        return Outcome.Failure(error)
    }

    private suspend fun random(size: Int): ByteArray? = when (val result = crypto.randomBytes(size)) {
        is Outcome.Failure -> null
        is Outcome.Success -> result.data
    }

    /** The fixed Argon2id parameters, validated by the security layer before any allocation. */
    private suspend fun deriveKey(password: ByteArray, salt: ByteArray): SecretBytes? =
        when (val derived = crypto.deriveKey(password, salt, BackupFormat.MEMORY_KIB, BackupFormat.PASSES)) {
            is Outcome.Failure -> null
            is Outcome.Success -> derived.data
        }
}

/** The one JSON configuration of the format: no pretty printing, no unknown keys, and explicit nulls. */
internal val BackupJson: Json = Json {
    encodeDefaults = true
    explicitNulls = true
    ignoreUnknownKeys = false
    prettyPrint = false
}
