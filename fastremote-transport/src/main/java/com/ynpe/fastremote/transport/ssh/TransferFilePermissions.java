// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.transport.ssh;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.OptionalInt;
import org.apache.sshd.sftp.client.SftpClient;

/** Copies ordinary rwx bits only; ownership, ACLs and privilege bits are not portable. */
final class TransferFilePermissions {
    static OptionalInt local(Path path, LinkOption... options) throws IOException {
        var view = Files.getFileAttributeView(path, PosixFileAttributeView.class, options);
        if (view == null) return OptionalInt.empty();
        int mode = 0;
        for (var permission : view.readAttributes().permissions()) mode |= bit(permission);
        return OptionalInt.of(mode);
    }

    static OptionalInt remote(SftpClient.Attributes attributes) {
        return attributes.getFlags().contains(SftpClient.Attribute.Perms)
                ? OptionalInt.of(attributes.getPermissions() & 0777) : OptionalInt.empty();
    }

    static void applyRemote(SftpClient client, String path, OptionalInt mode) throws IOException {
        if (mode.isPresent()) client.setStat(path, new SftpClient.Attributes().perms(mode.getAsInt()));
    }

    static void applyLocal(Path path, OptionalInt mode) throws IOException {
        if (mode.isEmpty()) return;
        var view = Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null) return;
        var permissions = EnumSet.noneOf(PosixFilePermission.class);
        for (var permission : PosixFilePermission.values()) {
            if ((mode.getAsInt() & bit(permission)) != 0) permissions.add(permission);
        }
        view.setPermissions(permissions);
    }

    private static int bit(PosixFilePermission permission) {
        return switch (permission) {
            case OWNER_READ -> 0400;
            case OWNER_WRITE -> 0200;
            case OWNER_EXECUTE -> 0100;
            case GROUP_READ -> 0040;
            case GROUP_WRITE -> 0020;
            case GROUP_EXECUTE -> 0010;
            case OTHERS_READ -> 0004;
            case OTHERS_WRITE -> 0002;
            case OTHERS_EXECUTE -> 0001;
        };
    }

    private TransferFilePermissions() { }
}
