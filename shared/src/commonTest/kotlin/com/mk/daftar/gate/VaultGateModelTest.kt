package com.mk.daftar.gate

import com.mk.daftar.core.domain.session.VaultSessionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class VaultGateModelTest {

    private val nonOpen = listOf(
        VaultSessionState.Locked,
        VaultSessionState.Opening,
        VaultSessionState.Closing,
        VaultSessionState.Unavailable,
    )

    @Test
    fun onlyAnOpenGenerationReachesThePrivateShell() {
        nonOpen.forEach { state ->
            assertFalse(vaultSurfaceFor(state) is VaultSurface.Shell, "$state must not reach the shell")
        }
        assertEquals(VaultSurface.Shell(generation = 7), vaultSurfaceFor(VaultSessionState.Open(generation = 7)))
    }

    @Test
    fun lockedUnavailableAndTransitionalStatesMapToNeutralSurfaces() {
        assertEquals(VaultSurface.Locked, vaultSurfaceFor(VaultSessionState.Locked))
        assertEquals(VaultSurface.Progress(closing = false), vaultSurfaceFor(VaultSessionState.Opening))
        assertEquals(VaultSurface.Progress(closing = true), vaultSurfaceFor(VaultSessionState.Closing))
        assertEquals(VaultSurface.Unavailable, vaultSurfaceFor(VaultSessionState.Unavailable))
    }

    @Test
    fun developerEntriesExistOnlyOnTheLockedSurfaceOfADebugBuild() {
        assertTrue(developerEntriesVisible(VaultSessionState.Locked, developerTools = true))
        assertFalse(
            developerEntriesVisible(VaultSessionState.Locked, developerTools = false),
            "release never offers them",
        )
        listOf(
            VaultSessionState.Opening,
            VaultSessionState.Closing,
            VaultSessionState.Unavailable,
            VaultSessionState.Open(1),
        )
            .forEach { state ->
                assertFalse(developerEntriesVisible(state, developerTools = true), "$state must not offer them")
            }
    }

    @Test
    fun theOpenSurfaceIsAShellNotAProgressOrLockSurface() {
        assertIs<VaultSurface.Shell>(vaultSurfaceFor(VaultSessionState.Open(generation = 3)))
    }
}
