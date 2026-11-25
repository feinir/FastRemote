// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Bounds untrusted status text before it can affect JavaFX layout or diagnostic memory. */
final class StatusMessagePresentation {
    private static final int MAX_STATUS_SUMMARY_COLUMNS = 100;
    private static final int MAX_DIAGNOSTIC_MESSAGE_CHARS = 2_000;
    private static final int MAX_AI_FAILURE_CHARS = 600;

    private StatusMessagePresentation() { }

    static String summary(String message) {
        return boundedByDisplayColumns(safeSingleLine(message), MAX_STATUS_SUMMARY_COLUMNS);
    }

    static String diagnostic(String message) {
        return boundedByCodePoints(safeSingleLine(message), MAX_DIAGNOSTIC_MESSAGE_CHARS);
    }

    static String aiFailure(String message, String fallback) {
        String safeMessage = Objects.requireNonNull(message, "message");
        String lowered = safeMessage.toLowerCase(Locale.ROOT);
        if (firstPayloadStart(lowered) >= 0 || lowered.contains("data:image/")) {
            return Objects.requireNonNull(fallback, "fallback");
        }
        return boundedByCodePoints(safeSingleLine(safeMessage), MAX_AI_FAILURE_CHARS);
    }

    private static String safeSingleLine(String message) {
        String safe = Objects.requireNonNull(message, "message")
                .replaceAll("[\\r\\n\\t\\u0000-\\u001f]+", " ")
                .replaceAll("\\s{2,}", " ")
                .strip();
        int payloadStart = firstPayloadStart(safe.toLowerCase(Locale.ROOT));
        if (payloadStart >= 0) safe = safe.substring(0, payloadStart).strip();
        return safe;
    }

    private static String boundedByCodePoints(String safe, int maximumCharacters) {
        if (safe.isEmpty()) return "";
        int codePoints = safe.codePointCount(0, safe.length());
        if (codePoints <= maximumCharacters) return safe;
        int end = safe.offsetByCodePoints(0, maximumCharacters - 1);
        return safe.substring(0, end).stripTrailing() + '…';
    }

    private static String boundedByDisplayColumns(String safe, int maximumColumns) {
        if (safe.isEmpty()) return "";
        if (displayColumns(safe) <= maximumColumns) return safe;

        int end = 0;
        int columns = 0;
        int contentColumns = maximumColumns - 1;
        while (end < safe.length()) {
            int codePoint = safe.codePointAt(end);
            int width = displayColumns(codePoint);
            if (columns + width > contentColumns) break;
            columns += width;
            end += Character.charCount(codePoint);
        }
        return safe.substring(0, end).stripTrailing() + '…';
    }

    private static int displayColumns(String value) {
        int columns = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            columns += displayColumns(codePoint);
            offset += Character.charCount(codePoint);
        }
        return columns;
    }

    private static int displayColumns(int codePoint) {
        int type = Character.getType(codePoint);
        if (type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK
                || codePoint == 0x200D) {
            return 0;
        }

        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        if (script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HANGUL
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.BOPOMOFO) {
            return 2;
        }
        if ((codePoint >= 0x2E80 && codePoint <= 0x303F)
                || (codePoint >= 0xFE10 && codePoint <= 0xFE6F)
                || (codePoint >= 0xFF01 && codePoint <= 0xFF60)
                || (codePoint >= 0xFFE0 && codePoint <= 0xFFE6)
                || (codePoint >= 0x1F000 && codePoint <= 0x1FAFF)) {
            return 2;
        }
        return 1;
    }

    private static int firstPayloadStart(String lowered) {
        int result = -1;
        for (String marker : List.of("<!doctype", "<html", "<head", "<body", "<style", "<script")) {
            int index = lowered.indexOf(marker);
            if (index >= 0 && (result < 0 || index < result)) result = index;
        }
        return result;
    }
}
