package com.mk.daftar.core.backup

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupExportStateTest {

    @Test
    fun aFinishedExportAwaitsAcknowledgmentWhateverTheVaultDid() {
        assertTrue(BackupExportState.Complete("daftar-2026-10-12.dftar").awaitsAcknowledgment)
        assertTrue(BackupExportState.Failed(BackupError.WriteFailed).awaitsAcknowledgment)
    }

    @Test
    fun anExportThatIsStillRunningOrIdleDoesNotAwait() {
        assertFalse(BackupExportState.Idle.awaitsAcknowledgment)
        assertFalse(BackupExportState.Preparing.awaitsAcknowledgment)
        assertFalse(BackupExportState.ChoosingDestination.awaitsAcknowledgment)
        assertFalse(BackupExportState.Writing.awaitsAcknowledgment)
    }
}
