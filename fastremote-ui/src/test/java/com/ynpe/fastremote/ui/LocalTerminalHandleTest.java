// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.terminal.TerminalTermination;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Test;

class LocalTerminalHandleTest {
    @Test
    void retainsTheLatestValidWorkingDirectoryForTerminalDuplication() {
        AtomicReference<Consumer<String>> listener = new AtomicReference<>();
        LocalTerminalHandle handle = new LocalTerminalHandle(
                new Pane(), new Pane(), () -> {},
                CompletableFuture.completedFuture(TerminalTermination.eof()),
                ignored -> {}, ignored -> CompletableFuture.completedFuture(null),
                () -> 0L, () -> 0L, ignored -> {}, Path.of("/tmp/start"), listener::set);

        assertEquals(Path.of("/tmp/start").toAbsolutePath().normalize(),
                handle.workingDirectory().orElseThrow());
        assertTrue(handle.reportedWorkingDirectory().isEmpty(), "Launch directory is not a shell report");
        listener.get().accept("/tmp/next/../current");
        assertEquals(Path.of("/tmp/current").toAbsolutePath().normalize(),
                handle.workingDirectory().orElseThrow());
        assertEquals(handle.workingDirectory(), handle.reportedWorkingDirectory());

        handle.close();
        listener.get().accept("/tmp/ignored-after-close");
        assertEquals(Path.of("/tmp/current").toAbsolutePath().normalize(),
                handle.workingDirectory().orElseThrow());
    }
}
