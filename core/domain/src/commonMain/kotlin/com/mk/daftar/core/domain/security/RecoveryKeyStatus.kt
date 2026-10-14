package com.mk.daftar.core.domain.security

/** Whether the vault has a recovery key, so screens can suggest one. Reads no secret. */
fun interface RecoveryKeyStatus {
    /** True when set up, false when not, null when it cannot be read. */
    suspend fun isSetUp(): Boolean?
}
