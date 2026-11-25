// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.ynpe.fastremote.domain.settings.GlobalSettings;
import com.ynpe.fastremote.domain.settings.TerminalColorPalette;
import com.ynpe.fastremote.i18n.Localizer;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class DataLocationSettingsPaneTest {
    @BeforeAll static void startFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); }
        catch (IllegalStateException alreadyStarted) { ready.countDown(); }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test void fixedLocationHasNoProgramDirectoryControl() throws Exception {
        Stub workflow = new Stub(false);
        AtomicReference<DataLocationSettingsPane> pane = new AtomicReference<>();
        fx(() -> pane.set(new DataLocationSettingsPane(Localizer.systemDefault(), workflow)));
        fx(() -> {
            assertFalse(control(pane.get(), ComboBox.class).isVisible());
            assertFalse(control(pane.get(), Button.class).isVisible());
            assertTrue(control(pane.get(), Button.class).isDisabled());
        });
    }

    @Test void macStyleSelectionOffersCustomButNoProgramDirectory() throws Exception {
        Stub workflow = new Stub(true);
        workflow.programAvailable = false;
        AtomicReference<DataLocationSettingsPane> pane = new AtomicReference<>();
        Localizer localizer = Localizer.systemDefault();
        fx(() -> pane.set(new DataLocationSettingsPane(localizer, workflow)));
        fx(() -> {
            var combo = control(pane.get(), ComboBox.class);
            assertEquals(List.of(localizer.text("dataLocation.user"), localizer.text("dataLocation.custom")), combo.getItems());
            combo.getSelectionModel().select(1);
            assertTrue(control(pane.get(), Button.class).isDisabled());
            assertTrue(pane.get().getChildren().stream().filter(node -> "browse".equals(node.getId())).findFirst().orElseThrow().isVisible());
        });
    }

    @Test void saveDisablesControlsUntilCompletionAndShowsRestartNotice() throws Exception {
        Stub workflow = new Stub(true);
        AtomicReference<DataLocationSettingsPane> pane = new AtomicReference<>();
        Localizer localizer = Localizer.systemDefault();
        fx(() -> pane.set(new DataLocationSettingsPane(localizer, workflow)));
        fx(() -> {
            control(pane.get(), ComboBox.class).getSelectionModel().select(1);
            control(pane.get(), Button.class).fire();
            assertEquals(Path.of("/program"), workflow.requested);
            assertTrue(control(pane.get(), Button.class).isDisabled());
            workflow.saved.complete(new DataLocationSettings(Path.of("/user"), Path.of("/user"), java.util.Optional.of(Path.of("/program")), true, Path.of("/program"), true));
        });
        fx(() -> {
            assertFalse(control(pane.get(), Button.class).isDisabled());
            assertTrue(pane.get().getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                    .anyMatch(label -> label.getText().equals(localizer.text("dataLocation.pending"))));
        });
    }

    @Test void failedSaveKeepsControlsAvailableForRetry() throws Exception {
        Stub workflow = new Stub(true);
        AtomicReference<DataLocationSettingsPane> pane = new AtomicReference<>();
        fx(() -> pane.set(new DataLocationSettingsPane(Localizer.systemDefault(), workflow)));
        fx(() -> {
            control(pane.get(), Button.class).fire();
            workflow.saved.completeExceptionally(new java.io.IOException("private diagnostic"));
        });
        fx(() -> {
            assertFalse(control(pane.get(), Button.class).isDisabled());
            assertTrue(pane.get().getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                    .noneMatch(label -> label.getText().contains("private diagnostic")));
        });
    }

    @Test void configurationManagementScrollsToCustomDirectoryInSmallWindow() throws Exception {
        Stub workflow = new Stub(true);
        workflow.programAvailable = false;
        AtomicReference<DataLocationSettingsPane> data = new AtomicReference<>();
        fx(() -> data.set(new DataLocationSettingsPane(Localizer.systemDefault(), workflow)));
        fx(() -> {
            control(data.get(), ComboBox.class).getSelectionModel().select(1);
            var actions = new javafx.scene.layout.VBox(12);
            for (int i = 0; i < 10; i++) actions.getChildren().add(new Button("Import/export " + i));
            actions.getChildren().add(data.get());
            var page = ConfigurationDialog.configurationManagementPage(Localizer.systemDefault(), actions);
            var footer = new Button("Confirm");
            var shell = new javafx.scene.layout.BorderPane(page);
            shell.setBottom(footer);
            var stage = new javafx.stage.Stage();
            try {
                stage.setScene(new javafx.scene.Scene(shell, 680, 500));
                stage.show();
                shell.applyCss();
                shell.layout();
                var scroll = (javafx.scene.control.ScrollPane) page.getChildren().getFirst();
                assertTrue(scroll.getContent().getLayoutBounds().getHeight() > scroll.getViewportBounds().getHeight());
                assertTrue(scroll.lookupAll(".scroll-bar").stream()
                        .filter(javafx.scene.control.ScrollBar.class::isInstance)
                        .map(javafx.scene.control.ScrollBar.class::cast)
                        .anyMatch(bar -> bar.getOrientation() == javafx.geometry.Orientation.VERTICAL && bar.isVisible()));
                double footerY = footer.localToScene(footer.getBoundsInLocal()).getMinY();
                scroll.setVvalue(scroll.getVmax());
                shell.layout();
                var browse = data.get().lookup("#browse");
                var bounds = browse.localToScene(browse.getBoundsInLocal());
                var viewport = scroll.lookup(".viewport");
                var visible = viewport.localToScene(viewport.getBoundsInLocal());
                assertTrue(bounds.getMinY() >= visible.getMinY());
                assertTrue(bounds.getMaxY() <= visible.getMaxY());
                assertEquals(footerY, footer.localToScene(footer.getBoundsInLocal()).getMinY());
                assertTrue(visible.getMaxY() <= footerY);
            } finally {
                stage.close();
            }
        });
    }

    private static <T> T control(DataLocationSettingsPane pane, Class<T> type) {
        return pane.getChildren().stream().filter(node -> !"browse".equals(node.getId())).filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }
    private static void fx(Runnable action) throws Exception {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try { action.run(); result.complete(null); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        result.get(10, TimeUnit.SECONDS);
    }
    private static final class Stub implements SettingsWorkflow {
        private final boolean allowed;
        boolean programAvailable = true;
        Path requested;
        final CompletableFuture<DataLocationSettings> saved = new CompletableFuture<>();
        Stub(boolean allowed) { this.allowed = allowed; }
        @Override public CompletionStage<DataLocationSettings> loadDataLocation() {
            return CompletableFuture.completedFuture(new DataLocationSettings(Path.of("/user"), Path.of("/user"),
                    allowed && programAvailable ? java.util.Optional.of(Path.of("/program")) : java.util.Optional.empty(), allowed, Path.of("/user"), false));
        }
        @Override public CompletionStage<DataLocationSettings> saveDataLocation(Path program) {
            requested = program; return saved;
        }
        @Override public CompletionStage<GlobalSettings> load() { throw new UnsupportedOperationException(); }
        @Override public CompletionStage<Void> save(GlobalSettings settings) { throw new UnsupportedOperationException(); }
        @Override public CompletionStage<List<TerminalColorPalette>> importTerminalColorSchemes(Path source) { throw new UnsupportedOperationException(); }
        @Override public void close() { }
    }
}
