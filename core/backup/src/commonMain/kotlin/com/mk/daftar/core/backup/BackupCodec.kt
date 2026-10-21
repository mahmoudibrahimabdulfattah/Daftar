package com.mk.daftar.core.backup

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.security.PasswordCrypto
import com.mk.daftar.core.security.SecretBytes
import com.mk.daftar.core.security.SecurityError
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

    /**
     * Opens a backup made while a recovery key existed, with that key instead of the forgotten password. A file without
     * a recovery slot, or a key that is not its own, fails with [BackupError.AuthenticationFailed].
     */
    suspend fun decryptWithRecoveryKey(file: ByteArray, recoveryKey: String): Outcome<DecodedBackup, BackupError> =
        Outcome.Failure(BackupError.AuthenticationFailed)
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
class BackupCodec(
    private val crypto: PasswordCrypto,
    /** The vault's recovery public key, read at export time. With one, exports use the version-two container. */
    private val recoveryPublicKey: suspend () -> ByteArray? = { null },
) : BackupEncryptor,
    BackupDecryptor {

    override suspend fun decrypt(file: ByteArray, password: ByteArray): Outcome<DecodedBackup, BackupError> =
        decode(file, password)

    override suspend fun encode(
        snapshot: BackupSnapshot,
        password: ByteArray,
        exportedAt: Instant,
        appVersion: String,
    ): Outcome<ByteArray, BackupError> {
        try {
            val salt = random(BackupFormat.SALT_BYTES) ?: return Outcome.Failure(BackupError.ResourceUnavailable)
            val nonce = random(BackupFormat.NONCE_BYTES) ?: return Outcome.Failure(BackupError.ResourceUnavailable)
            val publicKey = recoveryPublicKey()
            val standard =
                writeAt(snapshot, password, exportedAt, appVersion, salt, nonce, publicKey, BackupKdfProfile.STANDARD)
            if (standard !is Outcome.Failure || standard.error != BackupError.InsufficientMemory) return standard
            // Standard could not allocate its memory: one LowMemory attempt with the same salt and nonce. The file
            // records the profile it was written with, so the reader derives with exactly that profile.
            return writeAt(
                snapshot,
                password,
                exportedAt,
                appVersion,
                salt,
                nonce,
                publicKey,
                BackupKdfProfile.LOW_MEMORY,
            )
        } finally {
            password.fill(0)
        }
    }

    /** One export attempt at [profile]. The caller owns [password] and wipes it, so a retry still has it. */
    private suspend fun writeAt(
        snapshot: BackupSnapshot,
        password: ByteArray,
        exportedAt: Instant,
        appVersion: String,
        salt: ByteArray,
        nonce: ByteArray,
        publicKey: ByteArray?,
        profile: BackupKdfProfile,
    ): Outcome<ByteArray, BackupError> = if (publicKey == null) {
        writePlain(snapshot, password, exportedAt, appVersion, salt, nonce, profile)
    } else {
        writeWithRecovery(snapshot, password, exportedAt, appVersion, salt, nonce, publicKey, profile)
    }

    /**
     * The version-two export: a random backup key encrypts the payload, and is wrapped under the password key and sealed
     * to the recovery public key. Each unwrap is proved before the file is returned.
     */
    private suspend fun writeWithRecovery(
        snapshot: BackupSnapshot,
        password: ByteArray,
        exportedAt: Instant,
        appVersion: String,
        salt: ByteArray,
        nonce: ByteArray,
        publicKey: ByteArray,
        profile: BackupKdfProfile,
    ): Outcome<ByteArray, BackupError> {
        if (snapshot.recordCount > BackupFormat.MAX_RECORDS) return Outcome.Failure(BackupError.TooManyRecords)
        val plaintext = BackupJson.encodeToString(
            PayloadDocument.serializer(),
            snapshot.toDocument(exportedAt, appVersion),
        ).encodeToByteArray()
        try {
            if (plaintext.size > BackupFormat.MAX_PLAINTEXT_BYTES) return Outcome.Failure(BackupError.TooLarge)
            val backupKey = random(BackupFormat.KEY_BYTES) ?: return Outcome.Failure(BackupError.ResourceUnavailable)
            try {
                val wrapNonce =
                    random(BackupFormat.NONCE_BYTES) ?: return Outcome.Failure(BackupError.ResourceUnavailable)
                val passwordKey = when (val derived = deriveKey(password, salt, profile)) {
                    is Outcome.Failure -> return Outcome.Failure(derived.error)
                    is Outcome.Success -> derived.data
                }
                val prefix = BackupHeaderV2(
                    salt = salt,
                    nonce = nonce,
                    wrapNonce = wrapNonce,
                    wrappedKey = ByteArray(0),
                    recoverySealed = null,
                    ciphertextLength = 0,
                    memoryKiB = profile.memoryKiB,
                    passes = profile.passes,
                ).prefix()
                val wrapped = try {
                    crypto.seal(passwordKey, wrapNonce, prefix, backupKey)
                } finally {
                    passwordKey.wipe()
                }
                val sealed = when (val result = crypto.sealToRecovery(backupKey, publicKey)) {
                    is Outcome.Failure -> return Outcome.Failure(BackupError.ResourceUnavailable)
                    is Outcome.Success -> result.data
                }
                if (sealed.size !=
                    BackupHeaderV2.SEALED_KEY_BYTES
                ) {
                    return Outcome.Failure(BackupError.ResourceUnavailable)
                }
                val header = BackupHeaderV2(
                    salt = salt,
                    nonce = nonce,
                    wrapNonce = wrapNonce,
                    wrappedKey = wrapped,
                    recoverySealed = sealed,
                    ciphertextLength = plaintext.size + BackupFormat.TAG_BYTES,
                    memoryKiB = profile.memoryKiB,
                    passes = profile.passes,
                ).encode()
                val ciphertext = crypto.sealWithKey(backupKey, nonce, header, plaintext)
                return Outcome.Success(header + ciphertext)
            } finally {
                backupKey.fill(0)
            }
        } finally {
            plaintext.fill(0)
        }
    }

    /** The password-only export, container version one, at [profile]. */
    private suspend fun writePlain(
        snapshot: BackupSnapshot,
        password: ByteArray,
        exportedAt: Instant,
        appVersion: String,
        salt: ByteArray,
        nonce: ByteArray,
        profile: BackupKdfProfile,
    ): Outcome<ByteArray, BackupError> {
        if (snapshot.recordCount > BackupFormat.MAX_RECORDS) return Outcome.Failure(BackupError.TooManyRecords)
        val json = snapshot.toDocument(exportedAt, appVersion).let { document ->
            BackupJson.encodeToString(PayloadDocument.serializer(), document)
        }
        val plaintext = json.encodeToByteArray()
        try {
            if (plaintext.size > BackupFormat.MAX_PLAINTEXT_BYTES) return Outcome.Failure(BackupError.TooLarge)
            val key = when (val derived = deriveKey(password, salt, profile)) {
                is Outcome.Failure -> return Outcome.Failure(derived.error)
                is Outcome.Success -> derived.data
            }
            try {
                val header = BackupHeader(
                    salt = salt,
                    nonce = nonce,
                    ciphertextLength = plaintext.size + BackupFormat.TAG_BYTES,
                    memoryKiB = profile.memoryKiB,
                    passes = profile.passes,
                ).encode()
                val ciphertext = crypto.seal(key, nonce, header, plaintext)
                return Outcome.Success(header + ciphertext)
            } finally {
                key.wipe()
            }
        } finally {
            plaintext.fill(0)
        }
    }

    override suspend fun decryptWithRecoveryKey(
        file: ByteArray,
        recoveryKey: String,
    ): Outcome<DecodedBackup, BackupError> {
        if (containerVersionOf(file) != BackupHeaderV2.VERSION) {
            return Outcome.Failure(
                if (containerVersionOf(file) == null) BackupError.NotABackup else BackupError.AuthenticationFailed,
            )
        }
        val header = when (val parsed = BackupHeaderV2.decode(file)) {
            is Outcome.Failure -> return Outcome.Failure(parsed.error)
            is Outcome.Success -> parsed.data
        }
        val sealed = header.recoverySealed ?: return Outcome.Failure(BackupError.AuthenticationFailed)
        val backupKey = when (val opened = crypto.openWithRecovery(sealed, recoveryKey)) {
            is Outcome.Failure -> return Outcome.Failure(BackupError.AuthenticationFailed)
            is Outcome.Success -> opened.data
        }
        return openPayloadV2(file, header, backupKey)
    }

    /** Decrypts a version-two payload with its unwrapped [backupKey], which is wiped here. */
    private fun openPayloadV2(
        file: ByteArray,
        header: BackupHeaderV2,
        backupKey: ByteArray,
    ): Outcome<DecodedBackup, BackupError> {
        try {
            if (backupKey.size != BackupFormat.KEY_BYTES) return Outcome.Failure(BackupError.AuthenticationFailed)
            val associatedData = file.copyOfRange(0, BackupHeaderV2.HEADER_BYTES)
            val ciphertext = file.copyOfRange(BackupHeaderV2.HEADER_BYTES, file.size)
            val plaintext = crypto.openWithKey(backupKey, header.nonce, associatedData, ciphertext)
                ?: return Outcome.Failure(BackupError.AuthenticationFailed)
            try {
                return parse(plaintext)
            } finally {
                plaintext.fill(0)
            }
        } finally {
            backupKey.fill(0)
        }
    }

    /** A version-two file opened with its password: the password key unwraps the backup key. */
    private suspend fun decodeV2(file: ByteArray, password: ByteArray): Outcome<DecodedBackup, BackupError> {
        val header = when (val parsed = BackupHeaderV2.decode(file)) {
            is Outcome.Failure -> return Outcome.Failure(parsed.error)
            is Outcome.Success -> parsed.data
        }
        val passwordKey = when (val derived = deriveKey(password, header.salt, header.profile)) {
            is Outcome.Failure -> return Outcome.Failure(derived.error)
            is Outcome.Success -> derived.data
        }
        val backupKey = try {
            crypto.open(
                passwordKey,
                header.wrapNonce,
                file.copyOfRange(0, BackupHeaderV2.PREFIX_BYTES),
                header.wrappedKey,
            )
                ?: return Outcome.Failure(BackupError.AuthenticationFailed)
        } finally {
            passwordKey.wipe()
        }
        return openPayloadV2(file, header, backupKey)
    }

    /**
     * Encodes a password-only file with the given salt and nonce at [profile]. Production always draws them fresh through
     * [encode]. This entry point exists so the format tests can pin known-answer vectors, and it rejects any salt or nonce
     * of the wrong length.
     */
    internal suspend fun encodeWith(
        snapshot: BackupSnapshot,
        password: ByteArray,
        exportedAt: Instant,
        appVersion: String,
        salt: ByteArray,
        nonce: ByteArray,
        profile: BackupKdfProfile = BackupKdfProfile.STANDARD,
    ): Outcome<ByteArray, BackupError> {
        try {
            require(salt.size == BackupFormat.SALT_BYTES && nonce.size == BackupFormat.NONCE_BYTES)
            return writePlain(snapshot, password, exportedAt, appVersion, salt, nonce, profile)
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
            if (containerVersionOf(file) == BackupHeaderV2.VERSION) return decodeV2(file, password)
            val header = when (val parsed = BackupHeader.decode(file)) {
                is Outcome.Failure -> return Outcome.Failure(parsed.error)
                is Outcome.Success -> parsed.data
            }
            val key = when (val derived = deriveKey(password, header.salt, header.profile)) {
                is Outcome.Failure -> return Outcome.Failure(derived.error)
                is Outcome.Success -> derived.data
            }
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
     * versions one to four each have their own exact shape, and only the canonical encoding of that shape is accepted.
     */
    private fun parse(plaintext: ByteArray): Outcome<DecodedBackup, BackupError> {
        val text = plaintext.decodeToString()
        val version = schemaVersionOf(text) ?: return Outcome.Failure(BackupError.CorruptPayload)
        return when {
            version > BackupFormat.PAYLOAD_SCHEMA_VERSION -> Outcome.Failure(BackupError.UnsupportedFormat)
            version == 1 -> parseVersionOne(text)
            version == 2 -> parseVersionTwo(text)
            version == 3 -> parseVersionThree(text)
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
        return finish(document.toV2().toV3().toCurrent())
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
        return finish(document.toV3().toCurrent())
    }

    private fun parseVersionThree(text: String): Outcome<DecodedBackup, BackupError> {
        val document = try {
            BackupJson.decodeFromString(PayloadDocumentV3.serializer(), text)
        } catch (failure: SerializationException) {
            return Outcome.Failure(BackupError.CorruptPayload)
        } catch (failure: IllegalArgumentException) {
            return Outcome.Failure(BackupError.CorruptPayload)
        }
        if (BackupJson.encodeToString(PayloadDocumentV3.serializer(), document) != text) {
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

    private suspend fun random(size: Int): ByteArray? = when (val result = crypto.randomBytes(size)) {
        is Outcome.Failure -> null
        is Outcome.Success -> result.data
    }

    /** Argon2id v1.3 of [password] at [profile]. Parameters are validated by the security layer before allocation. */
    private suspend fun deriveKey(
        password: ByteArray,
        salt: ByteArray,
        profile: BackupKdfProfile,
    ): Outcome<SecretBytes, BackupError> =
        when (val derived = crypto.deriveKey(password, salt, profile.memoryKiB, profile.passes)) {
            is Outcome.Failure -> Outcome.Failure(
                if (derived.error == SecurityError.InsufficientMemory) {
                    BackupError.InsufficientMemory
                } else {
                    BackupError.ResourceUnavailable
                },
            )

            is Outcome.Success -> Outcome.Success(derived.data)
        }
}

/** The one JSON configuration of the format: no pretty printing, no unknown keys, and explicit nulls. */
internal val BackupJson: Json = Json {
    encodeDefaults = true
    explicitNulls = true
    ignoreUnknownKeys = false
    prettyPrint = false
}
