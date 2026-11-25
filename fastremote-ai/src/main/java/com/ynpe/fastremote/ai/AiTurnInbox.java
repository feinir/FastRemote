// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ai;

import java.util.*;

/** A bounded mailbox. Closing and accepting use the same lock so end-of-turn input cannot disappear. */
public final class AiTurnInbox {
    private final LinkedHashMap<UUID, AiSteeringInput> pending = new LinkedHashMap<>();
    private final Set<UUID> accepted = new HashSet<>();
    private boolean closed;
    public synchronized boolean offer(AiSteeringInput input) {
        if (accepted.contains(input.id())) return true;
        if (closed) return false;
        if (pending.size() >= 8 || accepted.size() >= 64) throw new IllegalStateException("Too many pending updates");
        accepted.add(input.id()); pending.put(input.id(), input); return true;
    }
    public synchronized List<AiSteeringInput> drain() {
        var result = List.copyOf(pending.values()); pending.clear(); return result;
    }
    public synchronized boolean finishIfEmpty() {
        if (!pending.isEmpty()) return false;
        closed = true; return true;
    }
    public synchronized boolean hasPending() { return !pending.isEmpty(); }
    public synchronized void close() { closed = true; }
}
