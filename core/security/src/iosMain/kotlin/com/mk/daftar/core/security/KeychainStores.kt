package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.common.map

/** The sealed PIN envelope in one device-only Keychain item. [replace] is a single atomic item update. */
internal class KeychainRecordStore(account: String) : SecurityRecordStore {
    private val item = KeychainItem(account)

    override suspend fun read(): Outcome<ByteArray?, SecurityError> = item.read()

    override suspend fun createIfAbsent(record: ByteArray): Outcome<Boolean, SecurityError> =
        when (val existing = item.read()) {
            is Outcome.Failure -> Outcome.Failure(existing.error)

            is Outcome.Success -> if (existing.data != null) {
                Outcome.Success(false)
            } else {
                item.write(record).map { true }
            }
        }

    override suspend fun replace(record: ByteArray): Outcome<Unit, SecurityError> = item.write(record)

    companion object {
        /** The Keychain account of the legacy generation's envelope. */
        const val PIN_ENVELOPE_ACCOUNT = "pin-envelope.v1"
    }
}

/** The backoff record as one device-only, nonsynchronizing Keychain item. It is never stored in the database. */
internal class KeychainBackoffStore(account: String) : BackoffStore {
    private val item = KeychainItem(account)

    override suspend fun prepare(): Outcome<Unit, SecurityError> = Outcome.Success(Unit)

    override suspend fun read(): Outcome<ByteArray?, SecurityError> = item.read()

    override suspend fun write(record: ByteArray): Outcome<Unit, SecurityError> = item.write(record)

    companion object {
        /** The Keychain account of the legacy generation's backoff record. */
        const val BACKOFF_ACCOUNT = "backoff.v1"
    }
}

/**
 * The generation pointer as one device-only Keychain item. A Keychain item update is atomic, so the pointer is always
 * the old value or the new one, never a mix.
 */
internal class KeychainPointerStore : GenerationPointerStore {
    private val item = KeychainItem(POINTER_ACCOUNT)

    override suspend fun read(): Outcome<ByteArray?, SecurityError> = item.read()

    override suspend fun write(bytes: ByteArray): Outcome<Unit, SecurityError> = item.write(bytes)

    private companion object {
        const val POINTER_ACCOUNT = "daftar-generation.v1"
    }
}
