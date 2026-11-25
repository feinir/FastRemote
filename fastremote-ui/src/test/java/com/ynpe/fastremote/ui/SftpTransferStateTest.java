// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SftpTransferStateTest {
    @Test
    void enforcesTransferContextMenuPermissions() {
        assertTrue(SftpTransferState.WAITING.canPause());
        assertTrue(SftpTransferState.RUNNING.canPause());
        assertTrue(SftpTransferState.RUNNING.canCancel());
        assertFalse(SftpTransferState.RUNNING.canDelete());
        assertFalse(SftpTransferState.RUNNING.canResume());
        assertFalse(SftpTransferState.RUNNING.canRetry());

        assertTrue(SftpTransferState.PAUSED.canCancel());
        assertTrue(SftpTransferState.PAUSED.canResume());
        assertTrue(SftpTransferState.PAUSED.canDelete());
        assertFalse(SftpTransferState.PAUSED.canPause());
        assertFalse(SftpTransferState.PAUSED.canRetry());

        assertFalse(SftpTransferState.CANCELLED.canRetry());
        for (SftpTransferState terminal : java.util.List.of(SftpTransferState.COMPLETED, SftpTransferState.FAILED)) {
            assertTrue(terminal.canRetry());
        }
        for (SftpTransferState terminal : java.util.List.of(
                SftpTransferState.CANCELLED, SftpTransferState.COMPLETED, SftpTransferState.FAILED)) {
            assertTrue(terminal.canDelete());
            assertFalse(terminal.canCancel());
            assertFalse(terminal.canPause());
            assertFalse(terminal.canResume());
        }
    }
}
