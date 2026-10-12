package com.mk.daftar.feature.backup.restore

import com.mk.daftar.core.backup.BackupError
import com.mk.daftar.core.backup.RestoreFilePicker
import com.mk.daftar.core.backup.StagedCiphertext
import com.mk.daftar.core.common.Outcome

/**
 * A one-shot, in-memory hand-off of a verified cloud download to the restore wizard. The bytes are ciphertext only and
 * are never written to disk. [take] transfers the single pending file; [clear] and [take] wipe nothing else.
 */
class RestoreHandoff {
    private var pending: StagedCiphertext? = null

    /** True while a verified download waits for the wizard to pick it up. */
    val hasPending: Boolean
        get() = pending != null

    /** Keeps a private copy of [ciphertext] for the wizard. A previous pending file is wiped first. */
    fun offer(ciphertext: ByteArray) {
        pending?.discard()
        pending = MemoryStagedCiphertext(ciphertext.copyOf())
        ciphertext.fill(0)
    }

    /** Returns the pending file once, or null. */
    fun take(): StagedCiphertext? {
        val staged = pending
        pending = null
        return staged
    }

    /** Wipes a pending file that will not be used, such as when the recovery surface closes. */
    fun clear() {
        pending?.discard()
        pending = null
    }
}

/** Uses the pending hand-off first, and otherwise the system picker. The hand-off is consumed by one pick. */
class HandoffRestoreFilePicker(private val handoff: RestoreHandoff, private val delegate: RestoreFilePicker) :
    RestoreFilePicker {
    override suspend fun pick(): Outcome<StagedCiphertext?, BackupError> {
        handoff.take()?.let { return Outcome.Success(it) }
        return delegate.pick()
    }
}

/** A verified ciphertext held in memory. [discard] wipes it; reads return a copy the caller owns. */
internal class MemoryStagedCiphertext(private val bytes: ByteArray) : StagedCiphertext {
    override val size: Int = bytes.size

    override suspend fun readAll(): ByteArray = bytes.copyOf()

    override fun discard() {
        bytes.fill(0)
    }
}
