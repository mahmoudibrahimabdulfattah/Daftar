package com.mk.daftar.update

import com.mk.daftar.core.domain.update.UpdateStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdatePresentationTest {
    private val soft = UpdateStatus.Soft(8, "1.2.0", "market://store", null)
    private val force = UpdateStatus.Force(8, "1.2.0", "market://store", null)

    @Test
    fun forceReplacesTheVaultWhateverTheSessionDoes() {
        for (open in listOf(true, false)) {
            for (covered in listOf(true, false)) {
                for (resumed in listOf(true, false)) {
                    assertEquals(
                        UpdatePresentation.Force(force),
                        updatePresentation(force, sessionOpen = open, covered = covered, resumed = resumed),
                    )
                }
            }
        }
    }

    @Test
    fun softShowsOnlyOverAnOpenUncoveredResumedVault() {
        assertEquals(
            UpdatePresentation.Soft(soft),
            updatePresentation(soft, sessionOpen = true, covered = false, resumed = true),
        )
    }

    @Test
    fun softIsHeldBackWhileLockedCoveredOrPaused() {
        assertEquals(
            UpdatePresentation.Content,
            updatePresentation(soft, sessionOpen = false, covered = false, resumed = true),
        )
        assertEquals(
            UpdatePresentation.Content,
            updatePresentation(soft, sessionOpen = true, covered = true, resumed = true),
        )
        assertEquals(
            UpdatePresentation.Content,
            updatePresentation(soft, sessionOpen = true, covered = false, resumed = false),
        )
    }

    @Test
    fun upToDateNeverPresents() {
        assertEquals(
            UpdatePresentation.Content,
            updatePresentation(UpdateStatus.UpToDate, sessionOpen = true, covered = false, resumed = true),
        )
    }

    @Test
    fun aDismissedOrShownBuildDoesNotComeBackInTheSession() {
        assertEquals(UpdateStatus.UpToDate, settleUpdateStatus(UpdateStatus.UpToDate, soft, lastShownBuild = 8))
    }

    @Test
    fun anOpenPromptStaysOpenAcrossARecheck() {
        assertEquals(soft, settleUpdateStatus(soft, soft, lastShownBuild = 8))
    }

    @Test
    fun aNewerBuildIsOfferedAfterAShownOne() {
        val newer = UpdateStatus.Soft(9, "1.3.0", "market://store", null)
        assertEquals(newer, settleUpdateStatus(UpdateStatus.UpToDate, newer, lastShownBuild = 8))
    }

    @Test
    fun aForcedUpdateIsAlwaysKept() {
        assertEquals(force, settleUpdateStatus(UpdateStatus.UpToDate, force, lastShownBuild = 8))
    }
}
