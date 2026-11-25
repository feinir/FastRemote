// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.Arrays;

/** Immutable delimiter sequence: uniform files need no per-line allocation. */
final class EditorLineEndings {
    static final byte LF = 0;
    static final byte CRLF = 1;
    static final byte CR = 2;
    private final int size;
    private final byte uniform;
    private final byte[] mixed;

    private EditorLineEndings(int size, byte uniform, byte[] mixed) {
        this.size = size;
        this.uniform = uniform;
        this.mixed = mixed;
    }

    static EditorLineEndings repeated(int count, byte kind) {
        return new EditorLineEndings(count, count == 0 ? LF : kind, null);
    }

    static EditorLineEndings from(String raw) {
        int count = 0;
        byte first = LF;
        boolean same = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '\r' && c != '\n') continue;
            byte kind = c == '\n' ? LF : i + 1 < raw.length() && raw.charAt(i + 1) == '\n' ? CRLF : CR;
            if (kind == CRLF) i++;
            if (count++ == 0) first = kind;
            else if (kind != first) same = false;
        }
        if (same) return repeated(count, first);
        byte[] kinds = new byte[count];
        int index = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '\r' && c != '\n') continue;
            byte kind = c == '\n' ? LF : i + 1 < raw.length() && raw.charAt(i + 1) == '\n' ? CRLF : CR;
            if (kind == CRLF) i++;
            kinds[index++] = kind;
        }
        return new EditorLineEndings(count, first, kinds);
    }

    int size() { return size; }
    int storageBytes() { return mixed == null ? 0 : mixed.length; }
    byte kind(int index) { return mixed == null ? uniform : mixed[index]; }
    byte preferred() { return size == 0 ? LF : kind(0); }

    EditorLineEndings slice(int start, int count) {
        java.util.Objects.checkFromIndexSize(start, count, size);
        if (mixed == null) return repeated(count, uniform);
        return compact(Arrays.copyOfRange(mixed, start, start + count));
    }

    EditorLineEndings replace(int start, int removed, EditorLineEndings inserted) {
        java.util.Objects.checkFromIndexSize(start, removed, size);
        if (removed == 0 && inserted.size == 0) return this;
        int length = size - removed + inserted.size;
        if (mixed == null && inserted.mixed == null
                && (inserted.size == 0 || size == removed || uniform == inserted.uniform)) {
            return repeated(length, inserted.size == 0 ? uniform : inserted.uniform);
        }
        byte[] result = new byte[length];
        for (int i = 0; i < start; i++) result[i] = kind(i);
        for (int i = 0; i < inserted.size; i++) result[start + i] = inserted.kind(i);
        for (int i = start + removed; i < size; i++) result[i - removed + inserted.size] = kind(i);
        return compact(result);
    }

    private static EditorLineEndings compact(byte[] kinds) {
        if (kinds.length == 0) return repeated(0, LF);
        for (byte kind : kinds) {
            if (kind != kinds[0]) return new EditorLineEndings(kinds.length, kinds[0], kinds);
        }
        return repeated(kinds.length, kinds[0]);
    }

    String restore(String normalized) {
        if (mixed == null && uniform == LF) return normalized;
        StringBuilder result = new StringBuilder(normalized.length());
        int index = 0;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c != '\n') result.append(c);
            else {
                byte kind = kind(index++);
                if (kind != LF) result.append('\r');
                if (kind != CR) result.append('\n');
            }
        }
        if (index != size) throw new IllegalStateException("Editor newline count mismatch");
        return result.toString();
    }

    @Override public boolean equals(Object other) {
        return other instanceof EditorLineEndings that && size == that.size
                && uniform == that.uniform && Arrays.equals(mixed, that.mixed);
    }

    @Override public int hashCode() { return 31 * (31 * size + uniform) + Arrays.hashCode(mixed); }
}
