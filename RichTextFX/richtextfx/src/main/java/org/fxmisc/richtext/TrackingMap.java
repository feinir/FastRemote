package org.fxmisc.richtext;

import java.util.regex.Pattern;

/** Maps presentation-only inline gaps back to unchanged UTF-16 document offsets. */
final class TrackingMap {
    private static final Pattern GRAPHEME = Pattern.compile("\\X");
    private final int[] documentToDisplay;
    private final int[] displayToDocument;
    private final boolean[] boundaries;

    TrackingMap(String text) {
        boundaries = new boolean[text.length() + 1];
        var matcher = GRAPHEME.matcher(text);
        int count = 0;
        while (matcher.find()) {
            if (matcher.start() > 0) { boundaries[matcher.start()] = true; count++; }
        }
        documentToDisplay = new int[text.length() + 1];
        displayToDocument = new int[text.length() + count + 1];
        int display = 0;
        for (int offset = 0; offset <= text.length(); offset++) {
            if (boundaries[offset]) displayToDocument[display++] = offset;
            documentToDisplay[offset] = display;
            displayToDocument[display++] = offset;
        }
    }

    boolean gapBefore(int offset) { return boundaries[offset]; }
    int display(int offset) { return documentToDisplay[Math.max(0, Math.min(offset, documentToDisplay.length - 1))]; }
    int document(int offset) { return displayToDocument[Math.max(0, Math.min(offset, displayToDocument.length - 1))]; }
    int nextBoundary(int offset, int end) {
        for (int next = offset + 1; next < end; next++) if (boundaries[next]) return next;
        return end;
    }
}
