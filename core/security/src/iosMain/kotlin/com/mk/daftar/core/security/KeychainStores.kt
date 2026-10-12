package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.common.map

/** The sealed PIN envelope in one device-only Keychain item. [replace] is a single atomic item update. */
internal class KeychainRecordStore : SecurityRecordStore {
    private val item = KeychainItem(PIN_ENVELOPE_ACCOUNT)

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

    private companion object {
        const val PIN_ENVELOPE_ACCOUNT = "pin-envelope.v1"
    }
}

/** The backoff record as one device-only, nonsynchronizing Keychain item. It is never stored in the database. */
internal class KeychainBackoffStore : BackoffStore {
    private val item = KeychainItem(BACKOFF_ACCOUNT)

    override suspend fun prepare(): Outcome<Unit, SecurityError> = Outcome.Success(Unit)

    override suspend fun read(): Outcome<ByteArray?, SecurityError> = item.read()

    override suspend fun write(record: ByteArray): Outcome<Unit, SecurityError> = item.write(record)

    private companion object {
        const val BACKOFF_ACCOUNT = "backoff.v1"
    }
}
