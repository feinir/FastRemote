// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.TextArea;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ThrottledTextLogTest {
    @BeforeAll
    static void startJavaFxToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(5, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test
    void boundsSftpFileLogAt512KiBAndKeepsTheNewestEntries() {
        ThrottledTextLog log = new ThrottledTextLog(
                OpenSession.FILE_LOG_MAXIMUM_CHARACTERS, "test-sftp-log-flusher");
        try {
            for (int index = 0; index < 80_000; index++) {
                log.append("delete file: /large/source/tree/file-" + index + System.lineSeparator());
            }

            String snapshot = log.snapshot();
            assertTrue(snapshot.length() <= 512 * 1024);
            assertTrue(snapshot.endsWith("file-79999" + System.lineSeparator()));
        } finally {
            log.close();
        }
    }

    @Test
    void boundsDiagnosticLogAt1MiB() {
        ThrottledTextLog log = new ThrottledTextLog(
                MainWindowController.DIAGNOSTIC_LOG_MAXIMUM_CHARACTERS,
                "test-diagnostic-log-flusher");
        try {
            String entry = "x".repeat(1023) + System.lineSeparator();
            for (int index = 0; index < 3_000; index++) log.append(entry);

            assertTrue(log.snapshot().length() <= 1024 * 1024);
        } finally {
            log.close();
        }
    }

    @Test
    void clearDiscardsHistoryAndPendingTextBeforeReattaching() throws Exception {
        try (ThrottledTextLog log = new ThrottledTextLog(1024, "test-clear-detached-log")) {
            log.append("old history\n");
            assertEquals("old history\n", log.snapshot());
            log.append("pending entry\n");

            log.clear();
            log.clear();

            assertEquals("", log.snapshot());
            onFxThread(() -> {
                TextArea target = new TextArea();
                log.attach(target);
                assertEquals("", target.getText());
                log.detach(target);
            });
        }
    }

    @Test
    void clearEmptiesAttachedViewAndContinuesShowingOnlyNewEntries() throws Exception {
        try (ThrottledTextLog log = new ThrottledTextLog(1024, "test-clear-attached-log")) {
            AtomicReference<TextArea> view = new AtomicReference<>();
            CountDownLatch rendered = new CountDownLatch(1);
            onFxThread(() -> {
                TextArea target = new TextArea();
                view.set(target);
                log.append("old history\n");
                log.attach(target);
                assertEquals("old history\n", target.getText());
                log.append("pending entry\n");

                ContextMenu menu = SftpLogContextMenu.create(target, log::clear);
                target.setContextMenu(menu);
                MenuItem selectAll = menu.getItems().stream()
                        .filter(item -> "text-input-context-selectAll".equals(item.getId()))
                        .findFirst().orElseThrow();
                selectAll.fire();
                assertEquals("old history\n", target.getSelectedText());
                menu.getItems().stream()
                        .filter(item -> "sftp-log-clear".equals(item.getId()))
                        .findFirst().orElseThrow().fire();

                assertEquals("", target.getText());
                assertEquals("", log.snapshot());
                target.textProperty().addListener((ignored, previous, current) -> {
                    if (current.equals("new entry\n")) rendered.countDown();
                });
            });
            log.append("new entry\n");
            assertTrue(rendered.await(5, TimeUnit.SECONDS));
            onFxThread(() -> {
                assertEquals("new entry\n", view.get().getText());
                log.detach(view.get());
                TextArea replacement = new TextArea();
                log.attach(replacement);
                assertEquals("new entry\n", replacement.getText());
            });
        }
    }

    private static void onFxThread(Runnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(action, null);
        Platform.runLater(task);
        task.get(5, TimeUnit.SECONDS);
    }

    @Test
    void closeReleasesBufferedTextAndRejectsLaterAppends() {
        ThrottledTextLog log = new ThrottledTextLog(1024, "test-closing-log-flusher");
        log.append("before close");

        log.close();
        log.append("after close");

        assertTrue(log.snapshot().isEmpty());
    }

    @Test
    void backgroundProducerFlushesToTextAreaOnJavaFxThread() throws Exception {
        ThrottledTextLog log = new ThrottledTextLog(512 * 1024, "test-background-log-flusher");
        AtomicReference<TextArea> view = new AtomicReference<>();
        CountDownLatch attached = new CountDownLatch(1);
        CountDownLatch rendered = new CountDownLatch(1);
        Platform.runLater(() -> {
            TextArea target = new TextArea();
            target.textProperty().addListener((ignored, previous, current) -> {
                if (current.contains("background delete")) rendered.countDown();
            });
            view.set(target);
            log.attach(target);
            attached.countDown();
        });
        assertTrue(attached.await(5, TimeUnit.SECONDS));

        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread producer = new Thread(() -> {
            try {
                log.append("background delete" + System.lineSeparator());
            } catch (Throwable error) {
                failure.set(error);
            }
        }, "fastremote-file-test");
        producer.start();
        producer.join();

        try {
            assertTrue(rendered.await(5, TimeUnit.SECONDS));
            assertTrue(failure.get() == null, () -> "background append failed: " + failure.get());
            assertTrue(view.get().getText().contains("background delete"));
        } finally {
            log.close();
        }
    }
}
