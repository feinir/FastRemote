// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javafx.application.Platform;
import javafx.scene.control.TextArea;

/**
 * Asynchronous, bounded JavaFX text log adapted from WalkControlClient's proven GUIPrintStream pattern.
 * Producers only append to memory; one daemon flusher coalesces updates and schedules at most one FX task.
 */
final class ThrottledTextLog implements AutoCloseable {
    static final long FLUSH_INTERVAL_MILLIS = 100L;
    private static final double EVICTION_FRACTION = 0.5;

    private final Object pendingLock = new Object();
    private final BoundedTextBuffer history;
    private final BoundedTextBuffer pending;
    private final String flusherThreadName;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean uiUpdatePending = new AtomicBoolean();
    private final AtomicLong version = new AtomicLong();
    private final AtomicLong renderedVersion = new AtomicLong(-1L);
    private volatile ScheduledExecutorService scheduler;
    private volatile TextArea view;

    ThrottledTextLog(int maximumCharacters, String flusherThreadName) {
        if (maximumCharacters < 1) {
            throw new IllegalArgumentException("maximumCharacters must be positive");
        }
        this.flusherThreadName = Objects.requireNonNull(flusherThreadName, "flusherThreadName");
        if (flusherThreadName.isBlank()) throw new IllegalArgumentException("flusherThreadName is blank");
        history = new BoundedTextBuffer(maximumCharacters, EVICTION_FRACTION);
        pending = new BoundedTextBuffer(maximumCharacters, EVICTION_FRACTION);
    }

    void append(String text) {
        if (text == null || text.isEmpty() || closed.get()) return;
        synchronized (pendingLock) {
            pending.append(text);
        }
        startIfNecessary();
    }

    String snapshot() {
        flushPendingToHistory();
        return history.snapshot();
    }

    void attach(TextArea target) {
        view = Objects.requireNonNull(target, "target");
        String current = snapshot();
        target.setText(current);
        target.positionCaret(target.getLength());
        renderedVersion.set(version.get());
        startIfNecessary();
    }

    void detach(TextArea target) {
        if (view == target) view = null;
    }

    void clear() {
        long current;
        synchronized (pendingLock) {
            pending.clear();
            history.clear();
            current = version.incrementAndGet();
        }
        TextArea target = view;
        if (target == null) return;
        Runnable clearView = () -> {
            if (view == target) {
                target.clear();
                renderedVersion.set(current);
            }
        };
        if (Platform.isFxApplicationThread()) clearView.run();
        else Platform.runLater(clearView);
    }

    private void startIfNecessary() {
        if (closed.get() || !started.compareAndSet(false, true)) return;
        ScheduledExecutorService created = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, flusherThreadName);
            thread.setDaemon(true);
            return thread;
        });
        scheduler = created;
        created.scheduleAtFixedRate(this::flushTick, FLUSH_INTERVAL_MILLIS,
                FLUSH_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void flushTick() {
        if (closed.get()) return;
        flushPendingToHistory();
        TextArea target = view;
        long currentVersion = version.get();
        if (target == null || renderedVersion.get() == currentVersion
                || !uiUpdatePending.compareAndSet(false, true)) return;
        String snapshot = history.snapshot();
        Platform.runLater(() -> {
            try {
                if (view != target || closed.get() || version.get() != currentVersion) return;
                target.setText(snapshot);
                target.positionCaret(target.getLength());
                renderedVersion.set(currentVersion);
            } finally {
                uiUpdatePending.set(false);
            }
        });
    }

    private void flushPendingToHistory() {
        synchronized (pendingLock) {
            if (pending.length() == 0) return;
            String chunk = pending.snapshot();
            pending.clear();
            history.append(chunk);
            version.incrementAndGet();
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        ScheduledExecutorService current = scheduler;
        if (current != null) current.shutdownNow();
        view = null;
        synchronized (pendingLock) {
            pending.clear();
            history.clear();
        }
    }
}
