package com.mk.daftar.core.data.restore

import com.mk.daftar.core.backup.BackupPreferences
import com.mk.daftar.core.backup.BackupSnapshot
import com.mk.daftar.core.backup.RestoreFailure
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.domain.repository.InactivityTimeout
import com.mk.daftar.core.domain.repository.ThemeMode
import com.mk.daftar.core.security.GenerationPointer
import com.mk.daftar.core.security.PinEntryBuffer
import com.mk.daftar.core.security.SecretBytes
import com.mk.daftar.core.security.SecurityError
import com.mk.daftar.core.security.VaultGeneration
import kotlin.coroutines.cancellation.CancellationException

/** An in-memory pointer with the same write semantics as the registry: a fault can be reported with or without landing. */
internal class FakeGenerationStore : GenerationStore {
    var pointer: GenerationPointer = GenerationPointer.DEFAULT
    var readFailure: SecurityError? = null
    var writeFault: SecurityError? = null
    var landOnFault: Boolean = false
    val erased = mutableListOf<VaultGeneration>()
    val events = mutableListOf<String>()

    /** Every pointer value a write produced, in order, including the one that reported a failure. */
    val history = mutableListOf<GenerationPointer>()

    /** When set, the first pointer read after a faulted write fails too, as an unreadable store would. */
    var readsFailAfterFault: SecurityError? = null
    private var counter = 0

    override fun newGeneration(): VaultGeneration {
        counter++
        return VaultGeneration.fresh(ByteArray(VaultGeneration.RANDOM_BYTES) { (counter * 17 + it).toByte() })
    }

    override suspend fun pointer(): Outcome<GenerationPointer, SecurityError> {
        readFailure?.let { return Outcome.Failure(it) }
        return Outcome.Success(pointer)
    }

    override suspend fun update(
        transform: (GenerationPointer) -> GenerationPointer,
    ): Outcome<GenerationPointer, SecurityError> {
        val next = transform(pointer)
        events += "update"
        history += next
        val fault = writeFault
        if (fault != null) {
            writeFault = null
            if (landOnFault) pointer = next
            readFailure = readsFailAfterFault
            return Outcome.Failure(fault)
        }
        pointer = next
        return Outcome.Success(next)
    }

    override suspend fun erase(generation: VaultGeneration): Outcome<Unit, SecurityError> {
        erased += generation
        events += "erase"
        return Outcome.Success(Unit)
    }

    override suspend fun finishPendingWork(): Outcome<Unit, SecurityError> {
        events += "finish"
        pointer = pointer.copy(previous = null, preferences = null)
        return Outcome.Success(Unit)
    }
}

/** Scripted staging: [failure] is returned, [cancel] throws, and both record which generations were built. */
internal class FakeStaging : StagingFactory {
    var failure: RestoreFailure? = null
    var cancel: Boolean = false
    val built = mutableListOf<VaultGeneration>()

    override suspend fun build(
        generation: VaultGeneration,
        snapshot: BackupSnapshot,
        pin: SecretBytes,
    ): Outcome<Unit, RestoreFailure> {
        built += generation
        if (cancel) throw CancellationException("cancelled while building")
        return failure?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)
    }
}

internal fun emptySnapshot(
    theme: ThemeMode = ThemeMode.DARK,
    language: AppLanguage = AppLanguage.EN,
    timeout: InactivityTimeout = InactivityTimeout.SECONDS_300,
): BackupSnapshot = BackupSnapshot(
    preferences = BackupPreferences(themeMode = theme, language = language, inactivityTimeout = timeout),
    goldHoldings = emptyList(),
    banks = emptyList(),
    bankAccounts = emptyList(),
    cashBalances = emptyList(),
    zakatPayments = emptyList(),
    personalNotes = emptyList(),
    remoteQuotes = emptyList(),
    manualQuotes = emptyList(),
)

/** A PIN for the engine. The engine never reads its digits: the staging fake only passes it along. */
internal fun testPin(): SecretBytes {
    val buffer = PinEntryBuffer()
    "123456".forEach { buffer.append(it.code) }
    return checkNotNull(buffer.snapshot())
}
