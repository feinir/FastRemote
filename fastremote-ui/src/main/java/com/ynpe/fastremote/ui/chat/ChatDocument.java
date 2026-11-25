// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui.chat;

import java.util.*;

/** Immutable display projection. Node recycling never changes document positions. */
public final class ChatDocument {
    public record Run(String text, String style, String link) {
        public Run { Objects.requireNonNull(text); Objects.requireNonNull(style); Objects.requireNonNull(link); }
        public Run(String text) { this(text, "", ""); }
    }
    public record Cell(List<Run> runs) {
        public Cell { runs = List.copyOf(runs); }
        public String text() { var out = new StringBuilder(); runs.forEach(r -> out.append(r.text())); return out.toString(); }
        public static Cell text(String text) { return new Cell(List.of(new Run(text))); }
    }
    public record CodeSnippet(String language, String text) {
        public CodeSnippet { Objects.requireNonNull(language); Objects.requireNonNull(text); }
    }
    public record Row(String key, String messageId, String kind, String style, List<Cell> cells,
                      String action, String imageId, Optional<CodeSnippet> code) {
        public Row { cells = List.copyOf(cells); Objects.requireNonNull(code); if (cells.isEmpty()) throw new IllegalArgumentException("Empty row"); }
        public Row(String key, String messageId, String kind, String style, List<Cell> cells, String action, String imageId) {
            this(key, messageId, kind, style, cells, action, imageId, Optional.empty());
        }
        public Row withStyle(String next) { return new Row(key, messageId, kind, next, cells, action, imageId, code); }
        public String text() { return String.join("\t", cells.stream().map(Cell::text).toList()); }
    }
    public record Anchor(String messageId, int offset) { }
    private record MessageRange(int start, int end) { }
    private final List<Row> rows;
    private final int[] starts;
    private final Map<String, Integer> indexes;
    private final Map<String, MessageRange> messages;
    private final String text;
    private final Map<String, String> userWidthSamples = new HashMap<>();
    private final Set<String> wideUserMessages = new HashSet<>();

    public ChatDocument(List<Row> source) {
        rows = List.copyOf(source);
        starts = new int[rows.size()];
        indexes = new HashMap<>(); messages = new HashMap<>();
        var out = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i); starts[i] = out.length();
            if (indexes.put(row.key(), i) != null) throw new IllegalArgumentException("Duplicate row identity");
            out.append(row.text());
            if (row.kind().equals("USER")) {
                if (row.code().isPresent() || !row.imageId().isEmpty()) wideUserMessages.add(row.messageId());
                // Measure only a bounded representative line on the FX thread, once cells are styled.
                // Sharing this sample keeps all virtual rows of one bubble the same width.
                row.text().lines().forEach(line -> {
                    String sample = line.substring(0, line.offsetByCodePoints(0, Math.min(128, line.codePointCount(0, line.length()))));
                    userWidthSamples.merge(row.messageId(), sample, (a, b) -> widthScore(a) >= widthScore(b) ? a : b);
                });
            }
            MessageRange old = messages.get(row.messageId());
            messages.put(row.messageId(), new MessageRange(old == null ? starts[i] : old.start, out.length()));
            if (i + 1 < rows.size() && !rows.get(i + 1).style().contains("continuation")) out.append('\n');
        }
        text = out.toString();
    }
    public static ChatDocument empty() { return new ChatDocument(List.of()); }
    public List<Row> rows() { return rows; }
    public String text() { return text; }
    public String userWidthSample(String messageId) { return userWidthSamples.getOrDefault(messageId, ""); }
    public boolean wideUserMessage(String messageId) { return wideUserMessages.contains(messageId); }
    private static int widthScore(String sample) { return sample.codePoints().map(c -> c > 0x2e7f ? 2 : 1).sum(); }
    public int start(int row) { return starts[row]; }
    public int index(String key) { return indexes.getOrDefault(key, -1); }
    public int rowAt(int offset) {
        if (rows.isEmpty()) return -1;
        int p = Arrays.binarySearch(starts, Math.clamp(offset, 0, text.length()));
        return p >= 0 ? p : Math.max(0, -p - 2);
    }
    public int cellStart(int row, int column) {
        int start = starts[row];
        for (int i = 0; i < column; i++) start += rows.get(row).cells().get(i).text().length() + 1;
        return start;
    }
    public Anchor anchor(int offset) {
        int row = rowAt(offset);
        if (row < 0) return new Anchor("", 0);
        String id = rows.get(row).messageId(); var range = messages.get(id);
        return new Anchor(id, Math.clamp(offset - range.start, 0, range.end - range.start));
    }
    public int resolve(Anchor anchor, ChatDocument previous) {
        var next = messages.get(anchor.messageId()); var old = previous.messages.get(anchor.messageId());
        if (next == null || old == null) return -1;
        String before = previous.text.substring(old.start, old.end);
        String after = text.substring(next.start, next.end);
        return next.start + remap(anchor.offset, before, after);
    }
    private static int nearest(String text, String context, int expected) {
        int from = Math.max(0, expected - 256), end = Math.min(text.length(), expected + 256 + context.length());
        int best = -1;
        for (int at = text.indexOf(context, from); at >= 0 && at + context.length() <= end; at = text.indexOf(context, at + 1))
            if (best < 0 || Math.abs(at - expected) < Math.abs(best - expected)) best = at;
        return best;
    }
    /** Preserve positions around a streaming edit, including markup disappearing on completion. */
    static int remap(int offset, String old, String next) {
        int prefix = 0;
        while (prefix < old.length() && prefix < next.length() && old.charAt(prefix) == next.charAt(prefix)) prefix++;
        if (offset <= prefix) return Math.min(offset, next.length());
        // Closing markup can remove delimiters while a new suffix arrives in the same batch.
        // Match nearby context on either side of the anchor before falling back to edge mapping.
        for (int size = Math.min(32, old.length()); size >= 2; size--) {
            if (offset + size <= old.length()) {
                int found = nearest(next, old.substring(offset, offset + size), offset);
                if (found >= 0) return found;
            }
            if (offset >= size) {
                int found = nearest(next, old.substring(offset - size, offset), offset - size);
                if (found >= 0) return found + size;
            }
        }
        int suffix = 0;
        while (suffix < old.length() - prefix && suffix < next.length() - prefix
                && old.charAt(old.length() - suffix - 1) == next.charAt(next.length() - suffix - 1)) suffix++;
        int result = offset >= old.length() - suffix ? next.length() - (old.length() - offset)
                : Math.min(offset, next.length() - suffix);
        return Math.clamp(result, 0, next.length());
    }
}
