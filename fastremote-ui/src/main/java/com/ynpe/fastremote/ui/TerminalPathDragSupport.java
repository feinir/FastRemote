// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;

/** Private drag payloads used to insert browser paths into matching terminal types. */
final class TerminalPathDragSupport {
    static final DataFormat LOCAL_PATHS = new DataFormat("application/x-fastremote-local-paths");
    static final DataFormat REMOTE_PATHS = new DataFormat("application/x-fastremote-remote-paths");

    private TerminalPathDragSupport() { }

    static void putLocalPaths(ClipboardContent content, List<String> paths) {
        put(content, LOCAL_PATHS, paths);
    }

    static void putRemotePaths(ClipboardContent content, UUID pairingId, List<String> paths) {
        if (paths.isEmpty()) return;
        put(content, REMOTE_PATHS, Objects.requireNonNull(pairingId, "pairingId") + "\n" + encode(paths));
    }

    static List<String> localPaths(Dragboard dragboard) {
        return read(dragboard, LOCAL_PATHS);
    }

    static List<String> remotePaths(Dragboard dragboard, UUID pairingId) {
        Object value = dragboard.getContent(REMOTE_PATHS);
        if (!(value instanceof String payload) || payload.isBlank()) return List.of();
        int separator = payload.indexOf('\n');
        if (separator < 0 || !payload.substring(0, separator).equals(pairingId.toString())) {
            return List.of();
        }
        return decode(payload.substring(separator + 1));
    }

    private static void put(ClipboardContent content, DataFormat format, List<String> paths) {
        if (paths.isEmpty()) return;
        put(content, format, encode(paths));
    }

    private static void put(ClipboardContent content, DataFormat format, String payload) {
        content.put(format, payload);
    }

    private static List<String> read(Dragboard dragboard, DataFormat format) {
        Object value = dragboard.getContent(format);
        if (!(value instanceof String payload) || payload.isBlank()) return List.of();
        return decode(payload);
    }

    private static String encode(List<String> paths) {
        return paths.stream().map(path -> Base64.getEncoder().encodeToString(
                path.getBytes(StandardCharsets.UTF_8))).collect(java.util.stream.Collectors.joining("\n"));
    }

    private static List<String> decode(String payload) {
        try {
            return payload.lines().map(encoded -> new String(
                    Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)).toList();
        } catch (IllegalArgumentException invalid) {
            return List.of();
        }
    }
}
