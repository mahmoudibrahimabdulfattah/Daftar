package com.mk.daftar.core.backup

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.security.PasswordCrypto
import com.mk.daftar.core.security.SecretBytes
import kotlin.time.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

    /**
     * Routes by the payload's own schema version before any record is read. A newer version is refused as unsupported;
     * versions one, two and three each have their own exact shape, and only the canonical encoding of that shape is
     * accepted.
     */
    private fun parse(plaintext: ByteArray): Outcome<DecodedBackup, BackupError> {
        val text = plaintext.decodeToString()
        val version = schemaVersionOf(text) ?: return Outcome.Failure(BackupError.CorruptPayload)
        return when {
            version > BackupFormat.PAYLOAD_SCHEMA_VERSION -> Outcome.Failure(BackupError.UnsupportedFormat)
            version == 1 -> parseVersionOne(text)
            version == 2 -> parseVersionTwo(text)
            version == BackupFormat.PAYLOAD_SCHEMA_VERSION -> parseCurrent(text)
            else -> Outcome.Failure(BackupError.CorruptPayload)
        }
    }

    private fun parseCurrent(text: String): Outcome<DecodedBackup, BackupError> {
        val document = try {
            BackupJson.decodeFromString(PayloadDocument.serializer(), text)
        } catch (failure: SerializationException) {
            return Outcome.Failure(BackupError.CorruptPayload)
        } catch (failure: IllegalArgumentException) {
            return Outcome.Failure(BackupError.CorruptPayload)
        }
        // Only the one canonical encoding is accepted: re-encoding must reproduce the authenticated bytes exactly.
        if (BackupJson.encodeToString(PayloadDocument.serializer(), document) != text) {
            return Outcome.Failure(BackupError.CorruptPayload)
        }
        return finish(document)
    }

    private fun parseVersionOne(text: String): Outcome<DecodedBackup, BackupError> {
        val document = try {
            BackupJson.decodeFromString(PayloadDocumentV1.serializer(), text)
        } catch (failure: SerializationException) {
            return Outcome.Failure(BackupError.CorruptPayload)
        } catch (failure: IllegalArgumentException) {
            return Outcome.Failure(BackupError.CorruptPayload)
        }
        if (BackupJson.encodeToString(PayloadDocumentV1.serializer(), document) != text) {
            return Outcome.Failure(BackupError.CorruptPayload)
        }
        return finish(document.toV2().toCurrent())
    }

    private fun parseVersionTwo(text: String): Outcome<DecodedBackup, BackupError> {
        val document = try {
            BackupJson.decodeFromString(PayloadDocumentV2.serializer(), text)
        } catch (failure: SerializationException) {
            return Outcome.Failure(BackupError.CorruptPayload)
        } catch (failure: IllegalArgumentException) {
            return Outcome.Failure(BackupError.CorruptPayload)
        }
        if (BackupJson.encodeToString(PayloadDocumentV2.serializer(), document) != text) {
            return Outcome.Failure(BackupError.CorruptPayload)
        }
        return finish(document.toCurrent())
    }

    /** The shared tail: the export time, the model values, the record limit and the cross-record rules. */
    private fun finish(document: PayloadDocument): Outcome<DecodedBackup, BackupError> {
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

    /** The payload's declared schema version, or null when the text is not a JSON object carrying one. */
    private fun schemaVersionOf(text: String): Int? = try {
        BackupJson.parseToJsonElement(text).jsonObject["schemaVersion"]?.jsonPrimitive?.intOrNull
    } catch (failure: SerializationException) {
        null
    } catch (failure: IllegalArgumentException) {
        null
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
