// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.transfer;

import java.util.ArrayDeque;
import java.util.Objects;

/** Platform-independent normalization for paths interpreted by a remote SFTP server. */
public final class RemotePathSyntax {
    private static final int MAX_PATH_LENGTH = 16 * 1024;

    private RemotePathSyntax() { }

    public static String normalizeAbsolute(String value) {
        String safe = checked(value).replace('\\', '/');
        boolean driveAbsolute = isDriveAbsolute(safe);
        if (!safe.startsWith("/") && !driveAbsolute) {
            throw new IllegalArgumentException(
                    "Remote path must be an absolute POSIX path or Windows drive path");
        }

        String prefix = driveAbsolute
                ? Character.toUpperCase(safe.charAt(0)) + ":/"
                : "/";
        String remainder = safe.substring(driveAbsolute ? 3 : 1);
        ArrayDeque<String> components = new ArrayDeque<>();
        for (String component : remainder.split("/")) {
            if (component.isEmpty() || component.equals(".")) continue;
            if (component.equals("..")) {
                if (!components.isEmpty()) components.removeLast();
            } else {
                components.addLast(component);
            }
        }
        return components.isEmpty() ? prefix : prefix + String.join("/", components);
    }

    public static String normalizeBookmark(String value) {
        String safe = checked(value).replace('\\', '/');
        if (safe.startsWith("/") || isDriveAbsolute(safe)) return normalizeAbsolute(safe);
        while (safe.length() > 1 && safe.endsWith("/")) {
            safe = safe.substring(0, safe.length() - 1);
        }
        return safe;
    }

    public static boolean isWithin(String path, String root) {
        String normalizedPath = normalizeAbsolute(path);
        String normalizedRoot = normalizeAbsolute(root);
        if (normalizedPath.equals(normalizedRoot)) return true;
        if (normalizedRoot.equals("/")) return normalizedPath.startsWith("/");
        if (isDriveRoot(normalizedRoot)) return normalizedPath.startsWith(normalizedRoot);
        return normalizedPath.startsWith(normalizedRoot + "/");
    }

    private static String checked(String value) {
        String safe = Objects.requireNonNull(value, "remotePath").strip();
        if (safe.isEmpty() || safe.length() > MAX_PATH_LENGTH || safe.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Remote path is invalid");
        }
        return safe;
    }

    private static boolean isDriveAbsolute(String value) {
        return value.length() >= 3
                && Character.isLetter(value.charAt(0))
                && value.charAt(1) == ':'
                && value.charAt(2) == '/';
    }

    private static boolean isDriveRoot(String value) {
        return value.length() == 3 && isDriveAbsolute(value);
    }
}
