// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import static com.ynpe.fastremote.storage.SettingsYamlValues.*;

import com.ynpe.fastremote.domain.settings.TerminalPreferences;
import com.ynpe.fastremote.domain.settings.TextAppearance;
import com.ynpe.fastremote.domain.settings.SftpBookmarks;
import com.ynpe.fastremote.domain.settings.SftpSettings;
import com.ynpe.fastremote.domain.settings.SftpDoubleClickAction;
import com.ynpe.fastremote.domain.settings.EditorTheme;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** YAML mapping for Sftp settings. */
final class SftpSettingsCodec {
    private SftpSettingsCodec() {}

    static SftpSettings decode(Map<String, Object> settings) throws IOException {
        Object value = settings.get("sftp");
        if (value == null) return SftpSettings.defaults();
        if (!(value instanceof Map<?, ?> raw)) throw new IOException("Global setting 'sftp' must be a mapping");
        @SuppressWarnings("unchecked") Map<String, Object> sftp = (Map<String, Object>) raw;
        boolean showHidden = optionalBool(sftp, "showHiddenFiles", true);
        int concurrency = optionalInteger(sftp, "concurrentTransfers", SftpSettings.DEFAULT_CONCURRENT_TRANSFERS);
        long maxEditableFileBytes = optionalLong(sftp, "maxEditableFileBytes",
                SftpSettings.DEFAULT_MAX_EDITABLE_FILE_BYTES);
        String editorFontFamily = optionalString(sftp, "editorFontFamily",
                TerminalPreferences.defaultInterfaceFontFamily(java.util.Locale.getDefault()));
        int editorFontSize = optionalInteger(sftp, "editorFontSize",
                TerminalPreferences.defaults().fontSize());
        EditorTheme editorTheme = editorTheme(sftp);
        SftpDoubleClickAction doubleClickFileAction = sftpDoubleClickAction(sftp);
        SftpBookmarks global = bookmarks(sftp.get("globalBookmarks"), "globalBookmarks");
        Map<String, SftpBookmarks> hosts = new LinkedHashMap<>();
        Object hostValue = sftp.get("hostBookmarks");
        if (hostValue instanceof Map<?, ?> rawHosts) {
            for (Map.Entry<?, ?> entry : rawHosts.entrySet()) {
                if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                    throw new IOException("SFTP host bookmark key must be a non-empty string");
                }
                hosts.put(key, bookmarks(entry.getValue(), "hostBookmarks." + key));
            }
        } else if (hostValue != null) {
            throw new IOException("Global setting 'hostBookmarks' must be a mapping");
        }
        try {
            return new SftpSettings(showHidden, concurrency, global, hosts,
                    maxEditableFileBytes, editorFontFamily, editorFontSize, editorTheme,
                    doubleClickFileAction,
                    textAppearance(sftp.get("editorTextAppearance"), TextAppearance.editorDefaults()));
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Global SFTP settings are outside the supported range", invalid);
        }
    }

    static Map<String, Object> encode(SftpSettings settings) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("showHiddenFiles", settings.showHiddenFiles());
        values.put("concurrentTransfers", settings.concurrentTransfers());
        values.put("maxEditableFileBytes", settings.maxEditableFileBytes());
        values.put("editorFontFamily", settings.editorFontFamily());
        values.put("editorFontSize", settings.editorFontSize());
        values.put("editorTextAppearance", appearanceValues(settings.editorTextAppearance()));
        values.put("editorTheme", settings.editorTheme().name());
        values.put("doubleClickFileAction", settings.doubleClickFileAction().name());
        values.put("globalBookmarks", bookmarkValues(settings.globalBookmarks()));
        Map<String, Object> hosts = new LinkedHashMap<>();
        settings.hostBookmarks().forEach((key, bookmarks) -> hosts.put(key, bookmarkValues(bookmarks)));
        values.put("hostBookmarks", hosts);
        return values;
    }

    private static EditorTheme editorTheme(Map<String, Object> sftp) throws IOException {
        Object value = sftp.get("editorTheme");
        if (value == null) return EditorTheme.DARK;
        if (value instanceof String text) {
            try {
                return EditorTheme.valueOf(text);
            } catch (IllegalArgumentException invalid) {
                throw new IOException("Global setting 'editorTheme' must be DARK or LIGHT", invalid);
            }
        }
        throw new IOException("Global setting 'editorTheme' must be a string");
    }

    private static SftpDoubleClickAction sftpDoubleClickAction(Map<String, Object> sftp) throws IOException {
        Object value = sftp.get("doubleClickFileAction");
        if (value == null) return SftpDoubleClickAction.OPEN;
        if (value instanceof String text) {
            try {
                return SftpDoubleClickAction.valueOf(text);
            } catch (IllegalArgumentException invalid) {
                throw new IOException("Global setting 'doubleClickFileAction' must be OPEN or TRANSFER", invalid);
            }
        }
        throw new IOException("Global setting 'doubleClickFileAction' must be a string");
    }

    private static Map<String, Object> bookmarkValues(SftpBookmarks bookmarks) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("local", bookmarks.localPaths());
        values.put("remote", bookmarks.remotePaths());
        return values;
    }

    private static SftpBookmarks bookmarks(Object value, String name) throws IOException {
        if (value == null) return SftpBookmarks.empty();
        if (!(value instanceof Map<?, ?> raw)) throw new IOException("SFTP setting '" + name + "' must be a mapping");
        @SuppressWarnings("unchecked") Map<String, Object> values = (Map<String, Object>) raw;
        try {
            return new SftpBookmarks(stringList(values.get("local"), name + ".local"),
                    stringList(values.get("remote"), name + ".remote"));
        } catch (IllegalArgumentException invalid) {
            throw new IOException("SFTP bookmarks are invalid", invalid);
        }
    }
}
