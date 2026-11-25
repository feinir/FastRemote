// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/** Uses the desktop portal on Linux and preserves JavaFX choosers as a technical fallback. */
final class PlatformFileChooser {
    private static final ThreadPoolExecutor PORTAL_EXECUTOR = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), runnable -> {
                Thread thread = new Thread(runnable, "fastremote-file-portal");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    private PlatformFileChooser() { }

    static File showOpenDialog(FileChooser chooser, Window owner) {
        Objects.requireNonNull(chooser, "chooser");
        DesktopPortalFileChooser.Outcome outcome = portalOutcome(request(chooser, false, false));
        if (outcome instanceof DesktopPortalFileChooser.Outcome.Selected selected) {
            return selected.paths().getFirst().toFile();
        }
        if (outcome instanceof DesktopPortalFileChooser.Outcome.Cancelled) return null;
        return LinuxDialogModality.nativeDialog(owner, chooser::showOpenDialog);
    }

    static List<File> showOpenMultipleDialog(FileChooser chooser, Window owner) {
        Objects.requireNonNull(chooser, "chooser");
        DesktopPortalFileChooser.Outcome outcome = portalOutcome(request(chooser, true, false));
        if (outcome instanceof DesktopPortalFileChooser.Outcome.Selected selected) {
            return selected.paths().stream().map(Path::toFile).toList();
        }
        if (outcome instanceof DesktopPortalFileChooser.Outcome.Cancelled) return null;
        return LinuxDialogModality.nativeDialog(owner, chooser::showOpenMultipleDialog);
    }

    static File showSaveDialog(FileChooser chooser, Window owner) {
        Objects.requireNonNull(chooser, "chooser");
        DesktopPortalFileChooser.Outcome outcome = portalOutcome(request(chooser, false, true));
        if (outcome instanceof DesktopPortalFileChooser.Outcome.Selected selected) {
            return selected.paths().getFirst().toFile();
        }
        if (outcome instanceof DesktopPortalFileChooser.Outcome.Cancelled) return null;
        return LinuxDialogModality.nativeDialog(owner, chooser::showSaveDialog);
    }

    static File showDialog(DirectoryChooser chooser, Window owner) {
        Objects.requireNonNull(chooser, "chooser");
        DesktopPortalFileChooser.Request request = new DesktopPortalFileChooser.Request(
                DesktopPortalFileChooser.Mode.OPEN, chooser.getTitle(), false, true,
                chooser.getInitialDirectory() == null ? null : chooser.getInitialDirectory().toPath(),
                null, List.of(), null);
        DesktopPortalFileChooser.Outcome outcome = portalOutcome(request);
        if (outcome instanceof DesktopPortalFileChooser.Outcome.Selected selected) {
            return selected.paths().getFirst().toFile();
        }
        if (outcome instanceof DesktopPortalFileChooser.Outcome.Cancelled) return null;
        return LinuxDialogModality.nativeDialog(owner, chooser::showDialog);
    }

    static DesktopPortalFileChooser.Request request(
            FileChooser chooser, boolean multiple, boolean save) {
        List<DesktopPortalFileChooser.Filter> filters = chooser.getExtensionFilters().stream()
                .map(filter -> new DesktopPortalFileChooser.Filter(
                        filter.getDescription(), normalizePatterns(filter.getExtensions())))
                .toList();
        FileChooser.ExtensionFilter selected = chooser.getSelectedExtensionFilter();
        Integer selectedIndex = selected == null ? null : chooser.getExtensionFilters().indexOf(selected);
        if (selectedIndex != null && selectedIndex < 0) selectedIndex = null;
        return new DesktopPortalFileChooser.Request(
                save ? DesktopPortalFileChooser.Mode.SAVE : DesktopPortalFileChooser.Mode.OPEN,
                chooser.getTitle(), multiple, false,
                chooser.getInitialDirectory() == null ? null : chooser.getInitialDirectory().toPath(),
                chooser.getInitialFileName(), filters, selectedIndex);
    }

    private static List<String> normalizePatterns(List<String> patterns) {
        return patterns.stream().map(pattern -> "*.*".equals(pattern) ? "*" : pattern).toList();
    }

    private static DesktopPortalFileChooser.Outcome portalOutcome(
            DesktopPortalFileChooser.Request request) {
        return portalOutcome(request, LocalizedTextInputContextMenus.isLinux(),
                DesktopPortalFileChooser::choose);
    }

    static DesktopPortalFileChooser.Outcome portalOutcome(
            DesktopPortalFileChooser.Request request,
            boolean portalEnabled,
            Function<DesktopPortalFileChooser.Request, DesktopPortalFileChooser.Outcome> chooser) {
        Objects.requireNonNull(chooser, "chooser");
        if (!portalEnabled || !Platform.isFxApplicationThread()
                || !Platform.canStartNestedEventLoop()) {
            return DesktopPortalFileChooser.Outcome.failed();
        }
        Object nestedLoopKey = new Object();
        try {
            PORTAL_EXECUTOR.execute(() -> {
                DesktopPortalFileChooser.Outcome outcome = chooseSafely(chooser, request);
                Platform.runLater(() -> Platform.exitNestedEventLoop(nestedLoopKey, outcome));
            });
        } catch (RejectedExecutionException saturated) {
            return DesktopPortalFileChooser.Outcome.failed();
        }
        Object result = Platform.enterNestedEventLoop(nestedLoopKey);
        return result instanceof DesktopPortalFileChooser.Outcome outcome
                ? outcome : DesktopPortalFileChooser.Outcome.failed();
    }

    private static DesktopPortalFileChooser.Outcome chooseSafely(
            Function<DesktopPortalFileChooser.Request, DesktopPortalFileChooser.Outcome> chooser,
            DesktopPortalFileChooser.Request request) {
        try {
            return chooser.apply(request);
        } catch (RuntimeException | LinkageError unavailable) {
            return DesktopPortalFileChooser.Outcome.failed();
        }
    }
}
