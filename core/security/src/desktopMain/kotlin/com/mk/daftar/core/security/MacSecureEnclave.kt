package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.common.desktop.DesktopOs
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The bundled macOS helper (src/desktopNative/macos/DaftarMac.swift). Every returned buffer goes back to [daftar_free]. */
@Suppress("FunctionName", "ktlint:standard:function-naming")
private interface DaftarMac : Library {
    fun daftar_se_available(): Int

    fun daftar_biometry_state(): Int

    fun daftar_se_create(biometric: Int, blob: PointerByReference, length: IntByReference): Int

    fun daftar_se_seal(
        blob: ByteArray,
        blobLength: Int,
        aad: ByteArray,
        aadLength: Int,
        plain: ByteArray,
        plainLength: Int,
        sealed: PointerByReference,
        sealedLength: IntByReference,
    ): Int

    fun daftar_se_open(
        blob: ByteArray,
        blobLength: Int,
        reason: String?,
        aad: ByteArray,
        aadLength: Int,
        sealed: ByteArray,
        sealedLength: Int,
        plain: PointerByReference,
        plainLength: IntByReference,
    ): Int

    fun daftar_free(pointer: Pointer?, length: Int)
}

/** The helper's status codes. */
internal enum class EnclaveStatus(val code: Int) {
    OK(0),
    UNAVAILABLE(-1),
    FAILED(-2),
    CANCELLED(-3),
    LOCKED_OUT(-4),
    INVALIDATED(-5),
    NOT_ENROLLED(-6),
    ;

    companion object {
        fun of(code: Int): EnclaveStatus = entries.firstOrNull { it.code == code } ?: FAILED
    }
}

/**
 * The Secure Enclave of this Mac, through the bundled helper. Null on other systems, on Macs without an enclave, and
 * when the helper cannot be loaded, so callers fall back to their refusal path.
 */
internal object MacSecureEnclave {
    private val helper: DaftarMac? by lazy {
        if (DesktopOs.current != DesktopOs.MAC) return@lazy null
        runCatching {
            Native.load("daftarmac", DaftarMac::class.java, mapOf(Library.OPTION_STRING_ENCODING to "UTF-8"))
        }.getOrNull()
    }

    val available: Boolean by lazy { helper?.daftar_se_available() == 1 }

    /** Touch ID on this Mac: enrolled, present without a finger, or absent. */
    fun biometricCapability(): BiometricCapability = when (helper?.daftar_biometry_state()) {
        1 -> BiometricCapability.Available
        0 -> BiometricCapability.NotEnrolled
        else -> BiometricCapability.Unavailable
    }

    /** A new enclave key's device-bound blob. [biometric] keys need Touch ID for every use. */
    suspend fun create(biometric: Boolean): Pair<EnclaveStatus, ByteArray?> = call { mac ->
        val buffer = PointerByReference()
        val length = IntByReference()
        val status = EnclaveStatus.of(mac.daftar_se_create(if (biometric) 1 else 0, buffer, length))
        status to if (status == EnclaveStatus.OK) take(mac, buffer, length) else null
    }

    suspend fun seal(blob: ByteArray, aad: ByteArray, plain: ByteArray): Pair<EnclaveStatus, ByteArray?> = call { mac ->
        val buffer = PointerByReference()
        val length = IntByReference()
        val status = EnclaveStatus.of(
            mac.daftar_se_seal(blob, blob.size, aad, aad.size, plain, plain.size, buffer, length),
        )
        status to if (status == EnclaveStatus.OK) take(mac, buffer, length) else null
    }

    /** Opens [sealed]. A biometric key shows the Touch ID sheet with [reason] and blocks until the user decides. */
    suspend fun open(
        blob: ByteArray,
        reason: String?,
        aad: ByteArray,
        sealed: ByteArray,
    ): Pair<EnclaveStatus, ByteArray?> = call { mac ->
        val buffer = PointerByReference()
        val length = IntByReference()
        val status = EnclaveStatus.of(
            mac.daftar_se_open(blob, blob.size, reason, aad, aad.size, sealed, sealed.size, buffer, length),
        )
        status to if (status == EnclaveStatus.OK) take(mac, buffer, length) else null
    }

    private suspend fun call(block: (DaftarMac) -> Pair<EnclaveStatus, ByteArray?>): Pair<EnclaveStatus, ByteArray?> {
        val mac = helper ?: return EnclaveStatus.UNAVAILABLE to null
        return withContext(Dispatchers.IO) { block(mac) }
    }

    /** Copies the helper's buffer, then zeroes and frees it. */
    private fun take(mac: DaftarMac, buffer: PointerByReference, length: IntByReference): ByteArray {
        val bytes = buffer.value.getByteArray(0, length.value)
        mac.daftar_free(buffer.value, length.value)
        return bytes
    }
}

/**
 * Seals the PIN envelope (and, under its own key, the backoff record) with a non-exportable Secure Enclave key. The
 * key's blob is stored in [blobFile]; it is useless anywhere but in this Mac's enclave. No biometric or presence
 * requirement: the app PIN is the only gate on the PIN path. A missing blob over an existing vault is reported as
 * [SecurityError.DeviceKeyUnavailable]; a replacement is created only by [prepare], before a vault exists.
 */
internal class MacSecureEnclaveProtector(blobFile: File) : PlatformEnvelopeProtector {
    private val blob = DurableFile(blobFile)

    override suspend fun prepare(): Outcome<Unit, SecurityError> {
        when (val existing = blob.read()) {
            is Outcome.Failure -> return Outcome.Failure(existing.error)
            is Outcome.Success -> if (existing.data != null) return Outcome.Success(Unit)
        }
        val (status, created) = MacSecureEnclave.create(biometric = false)
        if (status != EnclaveStatus.OK || created == null) return Outcome.Failure(SecurityError.UnsupportedHardware)
        return blob.writeAtomic(created)
    }

    override suspend fun seal(plaintext: ByteArray): Outcome<ByteArray, SecurityError> {
        val key = readBlob() ?: return Outcome.Failure(SecurityError.DeviceKeyUnavailable)
        val (status, sealed) = MacSecureEnclave.seal(key, AD, plaintext)
        return if (status == EnclaveStatus.OK && sealed != null) {
            Outcome.Success(sealed)
        } else {
            Outcome.Failure(SecurityError.DeviceKeyUnavailable)
        }
    }

    override suspend fun unseal(sealed: ByteArray): Outcome<ByteArray, SecurityError> {
        val key = readBlob() ?: return Outcome.Failure(SecurityError.DeviceKeyUnavailable)
        val (status, plain) = MacSecureEnclave.open(key, null, AD, sealed)
        return when {
            status == EnclaveStatus.OK && plain != null -> Outcome.Success(plain)

            status == EnclaveStatus.INVALIDATED || status == EnclaveStatus.UNAVAILABLE ->
                Outcome.Failure(SecurityError.DeviceKeyUnavailable)

            else -> Outcome.Failure(SecurityError.CorruptState)
        }
    }

    private suspend fun readBlob(): ByteArray? = (blob.read() as? Outcome.Success)?.data

    private companion object {
        val AD = "DFSE1".encodeToByteArray()
    }
}

/** The Touch ID operation the desktop prompter labels: it sets the reason the system sheet shows. */
class MacBiometricCrypto internal constructor() : BiometricCrypto {
    @Volatile
    var reason: String? = null
}

/**
 * Touch ID keys: one Secure Enclave key per biometric key ID, created with `.biometryCurrentSet`, so adding or removing
 * a finger invalidates it. Each use shows the system Touch ID sheet. Blobs live in [directory].
 */
internal class MacTouchIdKeyStore(private val directory: File, private val prefix: String) : BiometricKeyStore {
    override suspend fun capability(): BiometricCapability =
        if (MacSecureEnclave.available) MacSecureEnclave.biometricCapability() else BiometricCapability.Unavailable

    override suspend fun createKey(keyId: ByteArray): Outcome<Unit, BiometricError> {
        val file = DurableFile(fileFor(keyId))
        if (file.exists()) return Outcome.Failure(BiometricError.Unavailable)
        val (status, created) = MacSecureEnclave.create(biometric = true)
        if (status != EnclaveStatus.OK || created == null) {
            return Outcome.Failure(
                if (status == EnclaveStatus.NOT_ENROLLED) BiometricError.NotEnrolled else BiometricError.Unavailable,
            )
        }
        return when (file.writeAtomic(created)) {
            is Outcome.Failure -> Outcome.Failure(BiometricError.StorageFailure)
            is Outcome.Success -> Outcome.Success(Unit)
        }
    }

    override suspend fun deleteKey(keyId: ByteArray): Outcome<Unit, BiometricError> =
        when (DurableFile(fileFor(keyId)).delete()) {
            is Outcome.Failure -> Outcome.Failure(BiometricError.StorageFailure)
            is Outcome.Success -> Outcome.Success(Unit)
        }

    override suspend fun wrap(
        keyId: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
        prompter: BiometricPrompter,
    ): Outcome<ByteArray, BiometricError> {
        val key = blobOf(keyId) ?: return Outcome.Failure(BiometricError.EnrollmentChanged)
        val (status, sealed) = MacSecureEnclave.seal(key, aad, plaintext)
        if (status != EnclaveStatus.OK || sealed == null) return Outcome.Failure(BiometricError.EnrollmentChanged)
        // Public-key sealing proves nothing. Enrollment commits only after an authenticated unwrap matches.
        return when (val verified = unwrap(keyId, aad, sealed, prompter)) {
            is Outcome.Failure -> Outcome.Failure(verified.error)

            is Outcome.Success -> if (verified.data.contentEquals(plaintext)) {
                Outcome.Success(sealed)
            } else {
                Outcome.Failure(BiometricError.CorruptSlot)
            }
        }
    }

    override suspend fun unwrap(
        keyId: ByteArray,
        aad: ByteArray,
        sealed: ByteArray,
        prompter: BiometricPrompter,
    ): Outcome<ByteArray, BiometricError> {
        val key = blobOf(keyId) ?: return Outcome.Failure(BiometricError.EnrollmentChanged)
        val crypto = MacBiometricCrypto()
        prompter.authorize(crypto).failureOrNull()?.let { return Outcome.Failure(it) }
        val (status, plain) = MacSecureEnclave.open(key, crypto.reason, aad, sealed)
        return when (status) {
            EnclaveStatus.OK -> if (plain !=
                null
            ) {
                Outcome.Success(plain)
            } else {
                Outcome.Failure(BiometricError.CorruptSlot)
            }

            EnclaveStatus.CANCELLED -> Outcome.Failure(BiometricError.Cancelled)

            EnclaveStatus.LOCKED_OUT -> Outcome.Failure(BiometricError.LockedOut)

            EnclaveStatus.NOT_ENROLLED -> Outcome.Failure(BiometricError.NotEnrolled)

            EnclaveStatus.UNAVAILABLE -> Outcome.Failure(BiometricError.Unavailable)

            EnclaveStatus.INVALIDATED -> Outcome.Failure(BiometricError.EnrollmentChanged)

            EnclaveStatus.FAILED -> Outcome.Failure(BiometricError.AuthenticationFailed)
        }
    }

    /** Removes every Touch ID key blob of this generation. */
    fun eraseAll() {
        directory.listFiles { file -> file.name.startsWith(prefix) }?.forEach { it.delete() }
    }

    private suspend fun blobOf(keyId: ByteArray): ByteArray? =
        (DurableFile(fileFor(keyId)).read() as? Outcome.Success)?.data

    private fun fileFor(keyId: ByteArray): File = directory.resolve(
        prefix + keyId.joinToString("") {
            (it.toInt() and 0xFF).toString(16).padStart(2, '0')
        } + ".blob",
    )
}
