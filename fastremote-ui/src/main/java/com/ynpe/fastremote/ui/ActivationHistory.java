// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/** Most-recently-used ordering for close fallback selection. */
final class ActivationHistory<T> {
    private final ArrayDeque<T> values = new ArrayDeque<>();

    void activated(T value) {
        Objects.requireNonNull(value, "value");
        values.remove(value);
        values.addLast(value);
    }

    void removed(T value) {
        values.remove(value);
    }

    Optional<T> previous(T current, Collection<T> available) {
        Objects.requireNonNull(available, "available");
        return values.reversed().stream()
                .filter(value -> value != current && available.contains(value))
                .findFirst();
    }
}
