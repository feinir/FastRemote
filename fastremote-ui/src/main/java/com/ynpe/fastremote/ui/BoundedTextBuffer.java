// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

/** Character-bounded text history that evicts a sizeable head block instead of trimming every append. */
final class BoundedTextBuffer {
    private final int maximumCharacters;
    private final int evictionCharacters;
    private final StringBuilder text = new StringBuilder();

    BoundedTextBuffer(int maximumCharacters, double evictionFraction) {
        if (maximumCharacters < 1) throw new IllegalArgumentException("maximumCharacters must be positive");
        if (evictionFraction <= 0.0 || evictionFraction >= 1.0) {
            throw new IllegalArgumentException("evictionFraction must be between zero and one");
        }
        this.maximumCharacters = maximumCharacters;
        this.evictionCharacters = Math.max(1, (int) (maximumCharacters * evictionFraction));
    }

    synchronized void append(String value) {
        if (value == null || value.isEmpty()) return;
        text.append(value);
        trimIfNecessary();
    }

    synchronized String snapshot() {
        return text.toString();
    }

    synchronized int length() {
        return text.length();
    }

    synchronized void clear() {
        text.setLength(0);
    }

    private void trimIfNecessary() {
        if (text.length() <= maximumCharacters) return;
        int deleteThrough = Math.max(evictionCharacters, text.length() - maximumCharacters);
        deleteThrough = Math.min(deleteThrough, text.length());
        // Terminal and protocol text may contain LF on every host, so trimming must not depend
        // on the JVM host's native line separator.
        int lineEnd = text.indexOf("\n", deleteThrough);
        if (lineEnd >= 0) deleteThrough = lineEnd + 1;
        text.delete(0, deleteThrough);
    }
}
