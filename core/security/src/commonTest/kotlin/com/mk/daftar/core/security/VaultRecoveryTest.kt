package com.mk.daftar.core.security

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The recovery key: a forgotten PIN is replaced with the key written down at setup, on this device only, without
 * touching the database key. A wrong or superseded key never opens the vault, and setup needs the current PIN.
 */
class VaultRecoveryTest {

    private suspend fun VaultHarness.created(): Pair<DefaultVaultSecurity, ByteArray> {
        val vault = vault()
        val lease = vault.create(pin("123456")).requireSuccess()
        val dek = lease.useDatabaseKey { it.copyOf() }
        vault.lock()
        return vault to dek
    }

    @Test
    fun theFormatRoundTripsAndForgivesCaseSpacesAndLookAlikes() {
        val secret = ByteArray(RecoveryKeyFormat.SECRET_BYTES) { (it * 7 + 3).toByte() }
        val text = RecoveryKeyFormat.format(secret)
        assertEquals(8, text.split('-').size)
        assertEquals(RecoveryKeyFormat.CHARACTERS + 7, text.length)
        assertContentEquals(secret, RecoveryKeyFormat.parse(text))
        val sloppy = text.lowercase().replace("-", " ").replace('0', 'o').replace('1', 'l')
        assertContentEquals(secret, RecoveryKeyFormat.parse(sloppy))
    }

    @Test
    fun aMistypedCharacterFailsTheChecksum() {
        val text = RecoveryKeyFormat.format(ByteArray(RecoveryKeyFormat.SECRET_BYTES) { it.toByte() })
        val typo = (if (text[0] == 'A') "B" else "A") + text.substring(1)
        assertNull(RecoveryKeyFormat.parse(typo))
        assertNull(RecoveryKeyFormat.parse(text.dropLast(1)))
    }

    @Test
    fun theRecoveryKeyReplacesAForgottenPinAndKeepsTheDatabaseKey() = runTest {
        val harness = VaultHarness()
        val (vault, dek) = harness.created()
        assertEquals(RecoveryState.Absent, vault.recoveryState().requireSuccess())

        val key = vault.setUpRecovery(pin("123456")).requireSuccess()
        assertEquals(RecoveryState.Active, vault.recoveryState().requireSuccess())
        assertNotNull(vault.recoveryPublicKey().requireSuccess())

        val recovered = vault.recover(key, pin("987654")).requireSuccess()
        recovered.useDatabaseKey { assertContentEquals(dek, it) }
        vault.lock()

        assertEquals(SecurityError.AuthenticationFailed, vault.unlock(pin("123456")).requireFailure())
        vault.unlock(pin("987654")).requireSuccess().useDatabaseKey { assertContentEquals(dek, it) }
    }

    @Test
    fun aWrongKeyIsRejectedAndChangesNothing() = runTest {
        val harness = VaultHarness()
        val (vault, _) = harness.created()
        vault.setUpRecovery(pin("123456")).requireSuccess()
        val envelope = harness.records.record?.copyOf()

        val stranger = RecoveryKeyFormat.format(ByteArray(RecoveryKeyFormat.SECRET_BYTES) { 9 })
        assertEquals(SecurityError.RecoveryKeyRejected, vault.recover(stranger, pin("987654")).requireFailure())
        assertEquals(SecurityError.RecoveryKeyRejected, vault.recover("not a key", pin("987654")).requireFailure())

        assertContentEquals(envelope, harness.records.record)
        vault.unlock(pin("123456")).requireSuccess()
    }

    @Test
    fun aNewRecoveryKeySupersedesThePreviousOne() = runTest {
        val harness = VaultHarness()
        val (vault, _) = harness.created()
        val first = vault.setUpRecovery(pin("123456")).requireSuccess()
        val second = vault.setUpRecovery(pin("123456")).requireSuccess()
        assertNotEquals(first, second)

        assertEquals(SecurityError.RecoveryKeyRejected, vault.recover(first, pin("987654")).requireFailure())
        vault.recover(second, pin("987654")).requireSuccess()
    }

    @Test
    fun setupNeedsTheCurrentPinAndAWrongOneCounts() = runTest {
        val harness = VaultHarness()
        val (vault, _) = harness.created()
        assertEquals(SecurityError.AuthenticationFailed, vault.setUpRecovery(pin("000000")).requireFailure())
        assertEquals(1, harness.storedRecord().failures)
        assertEquals(RecoveryState.Absent, vault.recoveryState().requireSuccess())
    }

    @Test
    fun recoveryWithoutASlotReportsItIsNotSetUp() = runTest {
        val harness = VaultHarness()
        val (vault, _) = harness.created()
        val key = RecoveryKeyFormat.format(ByteArray(RecoveryKeyFormat.SECRET_BYTES) { 1 })
        assertEquals(SecurityError.RecoveryNotSetUp, vault.recover(key, pin("987654")).requireFailure())
    }

    @Test
    fun recoveryClearsTheForgottenPinsPenalty() = runTest {
        val harness = VaultHarness()
        val (vault, _) = harness.created()
        val key = vault.setUpRecovery(pin("123456")).requireSuccess()
        repeat(5) { vault.unlock(pin("000000")) }
        assertIs<SecurityError.Throttled>(vault.unlock(pin("123456")).requireFailure())

        vault.recover(key, pin("987654")).requireSuccess()
        vault.lock()
        assertEquals(0, harness.storedRecord().failures)
        assertTrue(vault.unlock(pin("987654")) is com.mk.daftar.core.common.Outcome.Success)
    }
}
