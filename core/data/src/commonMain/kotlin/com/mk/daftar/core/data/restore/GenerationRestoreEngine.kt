package com.mk.daftar.core.data.restore

import com.mk.daftar.core.backup.BackupPreferences
import com.mk.daftar.core.backup.BackupSnapshot
import com.mk.daftar.core.backup.RestoreEngine
import com.mk.daftar.core.backup.RestoreFailure
import com.mk.daftar.core.backup.StagedRestore
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.security.GenerationPointer
import com.mk.daftar.core.security.SecretBytes
import com.mk.daftar.core.security.SecurityError
import com.mk.daftar.core.security.VaultGeneration
import com.mk.daftar.core.security.VaultGenerations
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The pointer and erasure operations the restore protocol needs. [VaultGenerations] is the production implementation. */
internal interface GenerationStore {
    fun newGeneration(): VaultGeneration

    suspend fun pointer(): Outcome<GenerationPointer, SecurityError>

    suspend fun update(transform: (GenerationPointer) -> GenerationPointer): Outcome<GenerationPointer, SecurityError>

    suspend fun erase(generation: VaultGeneration): Outcome<Unit, SecurityError>

    suspend fun finishPendingWork(): Outcome<Unit, SecurityError>
}

/**
 * Builds one complete staged generation: a new key, the backup written and read back, and the PIN proven on a fresh
 * security instance. Production writes it through Room; the protocol itself never touches a database.
 */
internal fun interface StagingFactory {
    /** Returns only after the generation is complete and verified. On failure the caller erases the generation. */
    suspend fun build(
        generation: VaultGeneration,
        snapshot: BackupSnapshot,
        pin: SecretBytes,
    ): Outcome<Unit, RestoreFailure>
}

/** Production [GenerationStore], delegating to the security layer's registry. */
internal class VaultGenerationStore(private val generations: VaultGenerations) : GenerationStore {
    override fun newGeneration(): VaultGeneration = generations.newGeneration()

    override suspend fun pointer(): Outcome<GenerationPointer, SecurityError> = generations.pointer()

    override suspend fun update(
        transform: (GenerationPointer) -> GenerationPointer,
    ): Outcome<GenerationPointer, SecurityError> = generations.update(transform)

    override suspend fun erase(generation: VaultGeneration): Outcome<Unit, SecurityError> =
        generations.erase(generation)

    override suspend fun finishPendingWork(): Outcome<Unit, SecurityError> = generations.finishPendingWork()
}

/**
 * Replaces the vault with a restored backup, one generation at a time.
 *
 * [stage] records the new generation as staging before any of its keys exist, builds it through [staging], and erases it
 * on any failure. The original vault is not touched until [commit].
 *
 * [commit] is the single replacement point: one durable pointer write makes the staged generation active and retires the
 * original. The session is revoked first, so no database is open on either side. After that point nothing is cancelled;
 * the settings are installed and the original is erased, and any step not finished is completed at the next start from
 * the pointer alone.
 */
internal class GenerationRestoreEngine(
    private val store: GenerationStore,
    private val staging: StagingFactory,
    private val lockSession: suspend () -> Unit,
    private val preferencesPayload: (BackupPreferences) -> String,
) : RestoreEngine {

    /** One restore at a time. [commit] holds it, so no staging can overlap a replacement. */
    private val mutex = Mutex()

    override suspend fun stage(snapshot: BackupSnapshot, pin: SecretBytes): Outcome<StagedRestore, RestoreFailure> =
        mutex.withLock {
            val generation = store.newGeneration()
            // Recorded before any key of the generation exists, so an interrupted staging is erased at the next start.
            when (store.update { it.copy(staging = generation) }) {
                is Outcome.Failure -> return@withLock Outcome.Failure(RestoreFailure.Staging)
                is Outcome.Success -> Unit
            }
            var verified = false
            try {
                when (val built = staging.build(generation, snapshot, pin)) {
                    is Outcome.Failure -> Outcome.Failure(built.error)

                    is Outcome.Success -> {
                        verified = true
                        Outcome.Success(GenerationStagedRestore(generation, snapshot.preferences))
                    }
                }
            } finally {
                // A failure, or a cancellation while the new vault was being built, erases what was created.
                if (!verified) withContext(NonCancellable) { eraseStaged(generation) }
            }
        }

    override suspend fun commit(staged: StagedRestore): Outcome<Unit, RestoreFailure> = mutex.withLock {
        val target = staged as? GenerationStagedRestore ?: return@withLock Outcome.Failure(RestoreFailure.NotReplaced)
        // The original session, if any, closes its database and key before the pointer can name another generation.
        lockSession()
        withContext(NonCancellable) {
            val before = when (val pointer = store.pointer()) {
                is Outcome.Failure -> return@withContext Outcome.Failure(RestoreFailure.NotReplaced)
                is Outcome.Success -> pointer.data
            }
            if (before.staging != target.generation || before.active == target.generation) {
                return@withContext Outcome.Failure(RestoreFailure.NotReplaced)
            }
            val payload = preferencesPayload(target.preferences)
            val written = store.update {
                it.copy(active = target.generation, previous = it.active, staging = null, preferences = payload)
            }
            when (written) {
                is Outcome.Success -> Unit

                is Outcome.Failure -> {
                    // A failed write may still have landed. The pointer decides which vault is live.
                    return@withContext when (val now = store.pointer()) {
                        is Outcome.Failure -> Outcome.Failure(RestoreFailure.RecoveryRequired)

                        is Outcome.Success -> if (now.data.active == target.generation) {
                            Outcome.Success(Unit)
                        } else {
                            eraseStaged(target.generation)
                            Outcome.Failure(RestoreFailure.NotReplaced)
                        }
                    }
                }
            }
            // The replacement has taken effect. Remaining work is recorded in the pointer, so a failure here is
            // completed at the next start rather than reported as a failed restore.
            store.finishPendingWork()
            Outcome.Success(Unit)
        }
    }

    override suspend fun discard(staged: StagedRestore) {
        val target = staged as? GenerationStagedRestore ?: return
        mutex.withLock { eraseStaged(target.generation) }
    }

    /** Erases a generation that will not become active, then drops it from the pointer's staging field. */
    private suspend fun eraseStaged(generation: VaultGeneration) {
        store.erase(generation)
        store.update { if (it.staging == generation) it.copy(staging = null) else it }
    }
}

/** A verified generation that [GenerationRestoreEngine.commit] can make active. */
internal class GenerationStagedRestore(val generation: VaultGeneration, val preferences: BackupPreferences) :
    StagedRestore
