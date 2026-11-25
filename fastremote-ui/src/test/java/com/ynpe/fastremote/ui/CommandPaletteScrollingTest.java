// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.skin.VirtualFlow;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CommandPaletteScrollingTest {
    @Test
    void firstResultIsSelectedAndOneClickOnAnotherRowsTextAcceptsItAfterCellRefresh() throws Exception {
        var completed = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        Platform.runLater(() -> {
            Stage owner = new Stage();
            owner.setScene(new Scene(new javafx.scene.layout.StackPane(), 900, 600)); owner.show();
            var first = CommandPalette.Entry.content("first", "Panel", java.util.List.of(), LucideIconView.Icon.FOLDER, () -> {});
            var second = CommandPalette.Entry.content("second", "Panel", java.util.List.of(), LucideIconView.Icon.FOLDER, () -> {});
            Platform.runLater(() -> {
                Stage palette = null;
                try {
                    palette = (Stage) javafx.stage.Window.getWindows().stream()
                            .filter(window -> window instanceof Stage stage && stage.getOwner() == owner).findFirst().orElseThrow();
                    palette.getScene().getRoot().applyCss(); palette.getScene().getRoot().layout();
                    var search = (javafx.scene.control.TextField) palette.getScene().lookup(".command-palette-search");
                    var results = (javafx.scene.control.ListView<?>) palette.getScene().lookup(".command-palette-results");
                    assertEquals(first, results.getSelectionModel().getSelectedItem());
                    search.setText("second");
                    assertEquals(second, results.getSelectionModel().getSelectedItem());
                    search.clear();
                    assertEquals(first, results.getSelectionModel().getSelectedItem());
                    results.applyCss(); results.layout();
                    var cell = results.lookupAll(".list-cell").stream()
                            .filter(node -> node instanceof javafx.scene.control.ListCell<?> row && row.getIndex() == 1)
                            .map(node -> (javafx.scene.control.ListCell<?>) node).findFirst().orElseThrow();
                    var clickedText = cell.lookup(".command-palette-title");
                    // Selection/virtualization can refresh a row between press and click.
                    results.getSelectionModel().select(1);
                    cell.updateIndex(-1); cell.updateIndex(1);
                    assertTrue(clickedText == cell.lookup(".command-palette-title"));
                    clickedText.fireEvent(new javafx.scene.input.MouseEvent(
                            javafx.scene.input.MouseEvent.MOUSE_CLICKED, 5, 5, 5, 5,
                            javafx.scene.input.MouseButton.PRIMARY, 1,
                            false, false, false, false, false, false, false, false, false, true,
                            new javafx.scene.input.PickResult(clickedText, 5, 5)));
                    assertTrue(!palette.isShowing(), "A single click must accept the new selection");
                } catch (Throwable error) {
                    failure.set(error);
                    if (palette != null) palette.hide();
                }
            });
            try { assertEquals(second, CommandPalette.show(owner, java.util.List.of(first, second)).orElseThrow()); }
            catch (Throwable error) { failure.compareAndSet(null, error); }
            finally { owner.hide(); completed.countDown(); }
        });
        assertTrue(completed.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("Palette single click failed", failure.get());
    }

    @Test
    void keyboardDrillDownStaysInOnePaletteAndEscapeRestoresRootSearch() throws Exception {
        var completed = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        Platform.runLater(() -> {
            Stage owner = new Stage();
            owner.setScene(new Scene(new javafx.scene.layout.StackPane(), 900, 600));
            owner.show();
            var path = CommandPalette.Entry.content("/srv/projects", "Panel", java.util.List.of("path"),
                    LucideIconView.Icon.FOLDER, () -> {});
            var go = CommandPalette.Entry.submenu("goto", "Go to", "Panel", java.util.List.of("go"),
                    LucideIconView.Icon.FOLDER, java.util.List.of(path));
            Platform.runLater(() -> {
                Stage palette = null;
                try {
                    palette = (Stage) javafx.stage.Window.getWindows().stream()
                            .filter(window -> window instanceof Stage stage && stage.getOwner() == owner)
                            .findFirst().orElseThrow();
                    var scene = palette.getScene();
                    var search = (javafx.scene.control.TextField) scene.lookup(".command-palette-search");
                    var results = (javafx.scene.control.ListView<?>) scene.lookup(".command-palette-results");
                    search.setText("go");
                    search.fireEvent(key(javafx.scene.input.KeyCode.ENTER, true));
                    assertEquals(java.util.List.of(path), java.util.List.copyOf(results.getItems()));
                    search.setText("projects");
                    search.fireEvent(key(javafx.scene.input.KeyCode.ESCAPE, false));
                    assertEquals("go", search.getText());
                    assertEquals(java.util.List.of(go), java.util.List.copyOf(results.getItems()));
                    search.fireEvent(key(javafx.scene.input.KeyCode.ENTER, true));
                    search.fireEvent(key(javafx.scene.input.KeyCode.ENTER, true));
                } catch (Throwable error) {
                    failure.set(error);
                    if (palette != null) palette.hide();
                }
            });
            try {
                assertEquals(path, CommandPalette.show(owner, java.util.List.of(go)).orElseThrow());
            } catch (Throwable error) { failure.compareAndSet(null, error); }
            finally { owner.hide(); completed.countDown(); }
        });
        assertTrue(completed.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("Palette keyboard navigation failed", failure.get());
    }

    private static javafx.scene.input.KeyEvent key(javafx.scene.input.KeyCode code, boolean released) {
        return new javafx.scene.input.KeyEvent(released ? javafx.scene.input.KeyEvent.KEY_RELEASED
                : javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
    }

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
    void keepsVisibleSelectionsStationaryAndScrollsOnlyAcrossViewportEdges() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                CommandPalette.EdgeAwareListView<String> results = new CommandPalette.EdgeAwareListView<>();
                results.setFixedCellSize(40.0);
                for (int index = 0; index < 30; index++) results.getItems().add("result-" + index);
                stage.setScene(new Scene(results, 320.0, 202.0));
                stage.show();
                results.applyCss();
                results.layout();

                @SuppressWarnings("unchecked")
                VirtualFlow<IndexedCell<?>> flow =
                        (VirtualFlow<IndexedCell<?>>) results.lookup(".virtual-flow");
                int initialFirst = flow.getFirstVisibleCell().getIndex();
                int initialLast = flow.getLastVisibleCell().getIndex();

                results.ensureVisible(initialLast - 1);
                assertEquals(initialFirst, flow.getFirstVisibleCell().getIndex());

                int belowViewport = initialLast + 1;
                results.ensureVisible(belowViewport);
                results.layout();
                assertTrue(flow.getLastVisibleCell().getIndex() >= belowViewport);
                assertNotEquals(belowViewport, flow.getFirstVisibleCell().getIndex(),
                        "crossing the lower edge must not pin the selection to the first row");

                int scrolledFirst = flow.getFirstVisibleCell().getIndex();
                results.ensureVisible(scrolledFirst + 1);
                assertEquals(scrolledFirst, flow.getFirstVisibleCell().getIndex());

                results.ensureVisible(scrolledFirst - 1);
                results.layout();
                assertTrue(flow.getFirstVisibleCell().getIndex() <= scrolledFirst - 1);
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                stage.hide();
                completed.countDown();
            }
        });
        assertTrue(completed.await(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }
}
