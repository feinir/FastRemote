// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.settings;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Global SFTP preferences and ordered global/per-host path bookmarks. */
public record SftpSettings(
        boolean showHiddenFiles,
        int concurrentTransfers,
        SftpBookmarks globalBookmarks,
        Map<String, SftpBookmarks> hostBookmarks,
        long maxEditableFileBytes,
        String editorFontFamily,
        int editorFontSize,
        EditorTheme editorTheme,
        SftpDoubleClickAction doubleClickFileAction,
        TextAppearance editorTextAppearance) {
    public static final int DEFAULT_CONCURRENT_TRANSFERS = 4;
    public static final int MAX_CONCURRENT_TRANSFERS = 16;
    public static final int MAX_HOSTS = 2_000;
    public static final long DEFAULT_MAX_EDITABLE_FILE_BYTES = 8L * 1024L * 1024L;
    public static final long MIN_MAX_EDITABLE_FILE_BYTES = 1L * 1024L * 1024L;
    public static final long MAX_MAX_EDITABLE_FILE_BYTES = 64L * 1024L * 1024L;

    public SftpSettings {
        Objects.requireNonNull(editorTextAppearance, "editorTextAppearance");
        if (concurrentTransfers < 1 || concurrentTransfers > MAX_CONCURRENT_TRANSFERS) {
            throw new IllegalArgumentException("concurrentTransfers must be between 1 and 16");
        }
        Objects.requireNonNull(globalBookmarks, "globalBookmarks");
        Objects.requireNonNull(hostBookmarks, "hostBookmarks");
        if (maxEditableFileBytes < MIN_MAX_EDITABLE_FILE_BYTES
                || maxEditableFileBytes > MAX_MAX_EDITABLE_FILE_BYTES) {
            throw new IllegalArgumentException("maxEditableFileBytes must be between 1 MiB and 64 MiB");
        }
        editorFontFamily = Objects.requireNonNull(editorFontFamily, "editorFontFamily").strip();
        if (editorFontFamily.isEmpty()) throw new IllegalArgumentException("editorFontFamily must not be blank");
        if (editorFontSize < 9 || editorFontSize > 32) {
            throw new IllegalArgumentException("editorFontSize must be between 9 and 32");
        }
        Objects.requireNonNull(editorTheme, "editorTheme");
        Objects.requireNonNull(doubleClickFileAction, "doubleClickFileAction");
        if (hostBookmarks.size() > MAX_HOSTS) throw new IllegalArgumentException("too many SFTP bookmark hosts");
        LinkedHashMap<String, SftpBookmarks> copy = new LinkedHashMap<>();
        hostBookmarks.forEach((key, value) -> {
            if (key == null || key.isBlank() || key.length() > 512) {
                throw new IllegalArgumentException("invalid SFTP bookmark host key");
            }
            copy.put(key, Objects.requireNonNull(value, "host bookmark"));
        });
        hostBookmarks = Map.copyOf(copy);
    }

    public SftpSettings(boolean showHiddenFiles, int concurrentTransfers,
            SftpBookmarks globalBookmarks, Map<String, SftpBookmarks> hostBookmarks,
            long maxEditableFileBytes, String editorFontFamily, int editorFontSize,
            EditorTheme editorTheme, SftpDoubleClickAction doubleClickFileAction) {
        this(showHiddenFiles, concurrentTransfers, globalBookmarks, hostBookmarks,
                maxEditableFileBytes, editorFontFamily, editorFontSize, editorTheme,
                doubleClickFileAction, TextAppearance.editorDefaults());
    }

    public SftpSettings withEditorTextAppearance(TextAppearance appearance) {
        return new SftpSettings(showHiddenFiles, concurrentTransfers, globalBookmarks, hostBookmarks,
                maxEditableFileBytes, editorFontFamily, editorFontSize, editorTheme, doubleClickFileAction, appearance);
    }

    public SftpSettings(boolean showHiddenFiles, int concurrentTransfers,
            SftpBookmarks globalBookmarks, Map<String, SftpBookmarks> hostBookmarks) {
        this(showHiddenFiles, concurrentTransfers, globalBookmarks, hostBookmarks,
                DEFAULT_MAX_EDITABLE_FILE_BYTES, TerminalPreferences.defaultInterfaceFontFamily(java.util.Locale.getDefault()),
                TerminalPreferences.defaults().fontSize(), EditorTheme.DARK, SftpDoubleClickAction.OPEN);
    }

    public SftpSettings(boolean showHiddenFiles, int concurrentTransfers,
            SftpBookmarks globalBookmarks, Map<String, SftpBookmarks> hostBookmarks,
            long maxEditableFileBytes, String editorFontFamily, int editorFontSize) {
        this(showHiddenFiles, concurrentTransfers, globalBookmarks, hostBookmarks,
                maxEditableFileBytes, editorFontFamily, editorFontSize, EditorTheme.DARK);
    }

    public SftpSettings(boolean showHiddenFiles, int concurrentTransfers,
            SftpBookmarks globalBookmarks, Map<String, SftpBookmarks> hostBookmarks,
            long maxEditableFileBytes, String editorFontFamily, int editorFontSize,
            EditorTheme editorTheme) {
        this(showHiddenFiles, concurrentTransfers, globalBookmarks, hostBookmarks,
                maxEditableFileBytes, editorFontFamily, editorFontSize, editorTheme,
                SftpDoubleClickAction.OPEN);
    }

    public static SftpSettings defaults() {
        return new SftpSettings(true, DEFAULT_CONCURRENT_TRANSFERS, SftpBookmarks.empty(), Map.of());
    }

    public SftpBookmarks bookmarksFor(String hostKey) {
        return hostBookmarks.getOrDefault(hostKey, SftpBookmarks.empty());
    }

    public SftpSettings withHostBookmarks(String hostKey, SftpBookmarks bookmarks) {
        LinkedHashMap<String, SftpBookmarks> updated = new LinkedHashMap<>(hostBookmarks);
        if (bookmarks.localPaths().isEmpty() && bookmarks.remotePaths().isEmpty()) updated.remove(hostKey);
        else updated.put(hostKey, bookmarks);
        return new SftpSettings(showHiddenFiles, concurrentTransfers, globalBookmarks, updated,
                maxEditableFileBytes, editorFontFamily, editorFontSize, editorTheme, doubleClickFileAction, editorTextAppearance);
    }

    public SftpSettings withShowHiddenFiles(boolean value) {
        return new SftpSettings(value, concurrentTransfers, globalBookmarks, hostBookmarks,
                maxEditableFileBytes, editorFontFamily, editorFontSize, editorTheme, doubleClickFileAction, editorTextAppearance);
    }
}
