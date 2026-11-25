// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.transfer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RemotePathSyntaxTest {
    @Test
    void checksContainmentForPosixAndWindowsDriveRootsAtPathBoundaries() {
        assertTrue(RemotePathSyntax.isWithin("/srv/releases/app", "/srv/releases"));
        assertFalse(RemotePathSyntax.isWithin("/srv/releases-old", "/srv/releases"));
        assertTrue(RemotePathSyntax.isWithin("d:\\FastRemote\\logs", "D:/FastRemote/"));
        assertTrue(RemotePathSyntax.isWithin("D:/anything", "D:\\"));
        assertFalse(RemotePathSyntax.isWithin("D:/FastRemote-old", "D:/FastRemote"));
        assertFalse(RemotePathSyntax.isWithin("E:/FastRemote/logs", "D:/FastRemote"));
    }
}
