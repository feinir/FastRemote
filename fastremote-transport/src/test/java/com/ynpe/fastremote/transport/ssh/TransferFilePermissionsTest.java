// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.transport.ssh;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.OptionalInt;
import org.apache.sshd.sftp.client.SftpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TransferFilePermissionsTest {
    @TempDir Path directory;

    @Test void missingRemoteModeIsDifferentFromExplicitZeroAndPrivilegeBitsAreNotCopied() {
        assertTrue(TransferFilePermissions.remote(new SftpClient.Attributes()).isEmpty());
        assertEquals(OptionalInt.of(0), TransferFilePermissions.remote(new SftpClient.Attributes().perms(0)));
        assertEquals(OptionalInt.of(0751), TransferFilePermissions.remote(new SftpClient.Attributes().perms(04751)));
    }

    @Test void fileSystemsWithoutPosixAttributesKeepTheirNativeDefaults() throws Exception {
        try (var fs = FileSystems.newFileSystem(directory.resolve("fixture.zip"), Map.of("create", "true"))) {
            Path file = Files.writeString(fs.getPath("/file"), "content");
            assertTrue(TransferFilePermissions.local(file).isEmpty());
            TransferFilePermissions.applyLocal(file, OptionalInt.of(0755));
            assertEquals("content", Files.readString(file));
        }
    }
}
