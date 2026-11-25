// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.settings.InterfaceLanguage;
import com.ynpe.fastremote.i18n.Localizer;
import com.ynpe.fastremote.i18n.Texts;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TreeItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LocalFileWorkspaceControllerTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); }
        catch (IllegalStateException alreadyStarted) { started.countDown(); }
        assertTrue(started.await(5, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test
    void recognizesFilesSuitableForTheEmbeddedEditor() {
        assertTrue(LocalFileWorkspaceController.isTextFile(file("notes.md")));
        assertTrue(LocalFileWorkspaceController.isTextFile(file("README")));
        assertTrue(LocalFileWorkspaceController.isTextFile(file("nginx.conf")));
        assertTrue(LocalFileWorkspaceController.isTextFile(file("settings.ini")));
        assertTrue(LocalFileWorkspaceController.isTextFile(file("index.php")));
        assertTrue(LocalFileWorkspaceController.isTextFile(file("main.swift")));
        assertTrue(LocalFileWorkspaceController.isTextFile(file("infra.tf")));
        assertFalse(LocalFileWorkspaceController.isTextFile(file("archive.zip")));
        assertFalse(LocalFileWorkspaceController.isTextFile(file("photo.png")));
    }

    @Test
    void suggestedNamesArePrefilledAndAvoidExistingEntries() {
        assertEquals("NewFolder", LocalFileWorkspaceController.uniqueSuggestedName(
                "NewFolder", true, List.of()));
        assertEquals("NewFolder (2)", LocalFileWorkspaceController.uniqueSuggestedName(
                "NewFolder", true, List.of(directory("NewFolder"))));
        assertEquals("NewFile (3).txt", LocalFileWorkspaceController.uniqueSuggestedName(
                "NewFile.txt", false, List.of(file("NewFile.txt"), file("NewFile (2).txt"))));
    }

    @Test
    void directoryTreeUsesAStableDefaultPixelWidth() {
        assertEquals(0.26, LocalFileWorkspaceController.defaultTreeDividerPosition(1_000.0), 0.001);
        assertEquals(0.13, LocalFileWorkspaceController.defaultTreeDividerPosition(2_000.0), 0.001);
        assertEquals(0.45, LocalFileWorkspaceController.defaultTreeDividerPosition(400.0), 0.001);
        assertEquals(0.1625,
                LocalFileWorkspaceController.treeDividerPosition(1_600.0, 260.0), 0.001);
    }

    @Test
    void confirmationUsesTheConfiguredInterfaceLanguageInsteadOfTheSystemLocale() throws Exception {
        Localizer previous = Texts.localizer();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            Alert alert = null;
            try {
                Texts.configure(Localizer.forLanguage(InterfaceLanguage.GERMAN));
                alert = LocalFileWorkspaceController.confirmationAlert("Test message");
                alert.show();
                assertEquals("Bestätigen", alert.getTitle());
                assertEquals("OK", ((Button) alert.getDialogPane().lookupButton(ButtonType.OK)).getText());
                assertEquals("Abbrechen",
                        ((Button) alert.getDialogPane().lookupButton(ButtonType.CANCEL)).getText());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                if (alert != null) alert.close();
                Texts.configure(previous);
                done.countDown();
            }
        });
        assertTrue(done.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void resolvingCurrentPathLoadsEveryTreeLevel() throws Exception {
        Path rootPath = Path.of(System.getProperty("user.home", "."))
                .toAbsolutePath().normalize().getRoot();
        Path alpha = rootPath.resolve("alpha");
        Path beta = alpha.resolve("beta");
        LocalFileWorkspaceController controller = new LocalFileWorkspaceController(
                new TreeWorkflow(rootPath), new EmptyActions(), beta);
        TreeItem<LocalFileEntry> root = MainWindowController.localTreeItem(
                new LocalFileEntry(rootPath.toString(), rootPath, true, false, 0L, Optional.empty()));

        AtomicReference<TreeItem<LocalFileEntry>> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> controller.ensureTreeNode(root, beta).whenComplete((resolved, error) -> {
            result.set(resolved);
            failure.set(error);
            done.countDown();
        }));
        assertTrue(done.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
        TreeItem<LocalFileEntry> resolved = result.get();
        assertEquals(beta, resolved.getValue().path());
        assertEquals(alpha, resolved.getParent().getValue().path());
    }

    @Test
    void loadsStandaloneFxmlWithoutAnSftpSession() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                var loaded = LocalFileWorkspaceView.load(new EmptyWorkflow(), new EmptyActions(),
                        Path.of(System.getProperty("user.home", ".")));
                assertTrue(loaded.root().getStyleClass().contains("local-file-workspace"));
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private static LocalFileEntry file(String name) {
        return new LocalFileEntry(name, Path.of(name), false, false, 12L, Optional.of(Instant.EPOCH));
    }

    private static LocalFileEntry directory(String name) {
        return new LocalFileEntry(name, Path.of(name), true, false, 0L, Optional.empty());
    }

    private static final class EmptyActions implements LocalFileWorkspaceController.Actions {
        @Override public void openPairedTerminal(Path directory) { }
        @Override public void edit(LocalFileEntry entry) { }
        @Override public void status(String message, boolean error) { }
        @Override public void addFavorite(Path directory) { }
        @Override public void directoryChanged(Path directory) { }
    }

    private static class EmptyWorkflow implements LocalFileWorkflow {
        @Override public java.util.concurrent.CompletionStage<List<LocalFileEntry>> localRoots() {
            return CompletableFuture.completedFuture(List.of());
        }
        @Override public java.util.concurrent.CompletionStage<List<LocalFileEntry>> listLocal(Path directory) {
            return CompletableFuture.completedFuture(List.of());
        }
        @Override public java.util.concurrent.CompletionStage<Void> createLocalDirectory(Path path) { return done(); }
        @Override public java.util.concurrent.CompletionStage<Void> createLocalFile(Path path) { return done(); }
        @Override public java.util.concurrent.CompletionStage<Void> deleteLocal(Path path) { return done(); }
        @Override public java.util.concurrent.CompletionStage<Void> renameLocal(Path source, Path target) { return done(); }
        @Override public java.util.concurrent.CompletionStage<Void> revealLocal(Path path, boolean selectFile) { return done(); }
        private static java.util.concurrent.CompletionStage<Void> done() {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class TreeWorkflow extends EmptyWorkflow {
        private final Path root;

        private TreeWorkflow(Path root) { this.root = root; }

        @Override public java.util.concurrent.CompletionStage<List<LocalFileEntry>> listLocal(Path directory) {
            Path normalized = directory.toAbsolutePath().normalize();
            if (normalized.equals(root)) {
                Path alpha = root.resolve("alpha");
                return CompletableFuture.completedFuture(List.of(
                        new LocalFileEntry("alpha", alpha, true, false, 0L, Optional.empty())));
            }
            if (normalized.equals(root.resolve("alpha"))) {
                Path beta = normalized.resolve("beta");
                return CompletableFuture.completedFuture(List.of(
                        new LocalFileEntry("beta", beta, true, false, 0L, Optional.empty())));
            }
            return CompletableFuture.completedFuture(List.of());
        }
    }
}
