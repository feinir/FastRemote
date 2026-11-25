// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.settings.InterfaceLanguage;
import com.ynpe.fastremote.i18n.Localizer;
import com.ynpe.fastremote.i18n.Texts;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.stage.StageStyle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DialogSpacingTest {
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
    void standardPromptsRemainIconlessAcrossThemesAndHeaderLayouts() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            var previous = ThemeManager.currentTheme();
            try {
                for (var theme : com.ynpe.fastremote.domain.settings.ApplicationTheme.values()) {
                    ThemeManager.setTheme(theme);
                    for (var type : javafx.scene.control.Alert.AlertType.values()) {
                        for (String header : new String[] { null, "Confirm operation" }) {
                            Alert alert = new Alert(type, "Detail", ButtonType.OK, ButtonType.CANCEL);
                            alert.setHeaderText(header);
                            alert.getDialogPane().applyCss();
                            alert.getDialogPane().layout();
                            assertNull(alert.getGraphic(), theme + " " + type + " must not restore an icon");
                            assertEquals(List.of(ButtonType.OK, ButtonType.CANCEL),
                                    List.copyOf(alert.getButtonTypes()));
                        }
                    }
                    TextInputDialog input = new TextInputDialog("original-name.txt");
                    input.setHeaderText(null);
                    input.setContentText("Name:");
                    input.getDialogPane().applyCss();
                    input.getDialogPane().layout();
                    assertNull(input.getGraphic());
                    assertEquals("original-name.txt", input.getEditor().getText());
                }
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                ThemeManager.setTheme(previous);
                completed.countDown();
            }
        });
        assertTrue(completed.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("Iconless dialog assertion failed", failure.get());
    }

    @Test
    void generatedDialogHeadersWrapWithinTheSharedMaximumWidth() {
        Label header = new Label("A translated dialog explanation that must not determine an unlimited window width");

        DialogSpacing.configureHeaderLabel(header, 320.0);

        assertTrue(header.isWrapText());
        assertEquals(0.0, header.getMinWidth());
        assertTrue(header.getPrefWidth() <= 320.0);
        assertEquals(320.0, header.getMaxWidth());
        assertEquals(Double.MAX_VALUE, header.getMaxHeight());
    }

    @Test
    void fixedDialogDoesNotBindItsHeaderToTransientNativeGraphicCoordinates() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.INFORMATION);
                alert.setTitle("Software update");
                alert.setHeaderText("FastRemote 已是最新版本");
                DialogSpacing.prepare(alert, 460.0);
                alert.show();
                PauseTransition afterNativeLayout = new PauseTransition(Duration.millis(250));
                afterNativeLayout.setOnFinished(ignored -> {
                    try {
                        Label header = generatedHeaderLabel(alert.getDialogPane());
                        assertFalse(header.prefWidthProperty().isBound(),
                                "fixed dialog headers must keep their pre-show bounded width");
                        assertFalse(header.maxWidthProperty().isBound(),
                                "fixed dialog headers must not follow transient GTK graphic coordinates");
                        assertTrue(header.getWidth() >= 200.0,
                                () -> "fixed alert header collapsed to " + header.getWidth() + "px");
                    } catch (Throwable error) {
                        failure.set(error);
                    } finally {
                        alert.close();
                        completed.countDown();
                    }
                });
                afterNativeLayout.play();
            } catch (Throwable error) {
                failure.set(error);
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void fixedTextInputDialogKeepsItsGeneratedHeaderWidthAfterReveal() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                javafx.scene.control.TextInputDialog input =
                        new javafx.scene.control.TextInputDialog("/");
                input.setTitle("Add remote bookmark");
                input.setHeaderText("路径可以暂时不存在，跳转时再验证。");
                input.setContentText("Remote path:");
                DialogSpacing.prepare(input, 460.0);
                input.show();
                PauseTransition afterNativeLayout = new PauseTransition(Duration.millis(250));
                afterNativeLayout.setOnFinished(ignored -> {
                    try {
                        Label header = generatedHeaderLabel(input.getDialogPane());
                        assertFalse(header.prefWidthProperty().isBound());
                        assertFalse(header.maxWidthProperty().isBound());
                        assertTrue(header.getWidth() >= 240.0,
                                () -> "fixed text-input header collapsed to " + header.getWidth() + "px");
                    } catch (Throwable error) {
                        failure.set(error);
                    } finally {
                        input.close();
                        completed.countDown();
                    }
                });
                afterNativeLayout.play();
            } catch (Throwable error) {
                failure.set(error);
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void resizableDialogHeaderStillTracksLaterWidthChanges() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.INFORMATION);
                alert.setResizable(true);
                alert.setHeaderText("A resizable dialog header must reflow after the user changes its width");
                DialogSpacing.prepare(alert, 460.0);
                alert.show();
                Platform.runLater(() -> {
                    try {
                        Label header = generatedHeaderLabel(alert.getDialogPane());
                        assertTrue(header.prefWidthProperty().isBound());
                        assertTrue(header.maxWidthProperty().isBound());
                    } catch (Throwable error) {
                        failure.set(error);
                    } finally {
                        alert.close();
                        completed.countDown();
                    }
                });
            } catch (Throwable error) {
                failure.set(error);
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void headerTextWidthTracksTheSpaceLeftByTheHeaderGraphic() {
        Pane headerPanel = new Pane();
        Label header = new Label("Search, organize, and connect sessions");
        Region graphic = new Region();
        headerPanel.getChildren().addAll(header, graphic);
        headerPanel.resize(800.0, 80.0);
        header.resize(320.0, 40.0);
        header.relocate(20.0, 10.0);
        graphic.resize(40.0, 40.0);
        graphic.relocate(760.0, 10.0);

        assertEquals(740.0, DialogSpacing.availableHeaderTextWidth(headerPanel, header, 320.0));

        headerPanel.resize(980.0, 80.0);
        graphic.relocate(940.0, 10.0);
        assertEquals(920.0, DialogSpacing.availableHeaderTextWidth(headerPanel, header, 320.0));
    }

    private static Label generatedHeaderLabel(DialogPane pane) {
        pane.applyCss();
        javafx.scene.Node headerPanel = pane.lookup(".header-panel");
        javafx.scene.Node label = headerPanel == null ? null : headerPanel.lookup(".label");
        if (label instanceof Label generated) return generated;
        throw new AssertionError("generated dialog header label was not installed");
    }

    @Test
    void eachDialogCanOverrideItsOwnHeaderWidthWithoutChangingAutomaticSizing() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                javafx.scene.control.Dialog<Void> compact = new javafx.scene.control.Dialog<>();
                javafx.scene.control.Dialog<Void> ordinary = new javafx.scene.control.Dialog<>();
                javafx.scene.layout.Region content = new javafx.scene.layout.Region();
                content.setPrefWidth(380.0);
                ordinary.getDialogPane().setContent(content);

                DialogSpacing.prepare(compact, 320.0);
                DialogSpacing.prepare(ordinary);

                assertEquals(320.0, DialogSpacing.headerTextMaxWidth(compact.getDialogPane()));
                assertEquals(380.0, DialogSpacing.headerTextMaxWidth(ordinary.getDialogPane()));
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void choiceDialogCapturesItsContentWidthBeforeTheHeaderCanStretchIt() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                javafx.scene.control.ChoiceDialog<String> dialog = new javafx.scene.control.ChoiceDialog<>(
                        "Deutsch", List.of("English", "Deutsch", "Português (Brasil)"));
                dialog.setContentText("Befehlssprache:");

                DialogSpacing.prepare(dialog);

                double width = DialogSpacing.headerTextMaxWidth(dialog.getDialogPane());
                assertTrue(width >= DialogSpacing.MINIMUM_AUTOMATIC_HEADER_WIDTH);
                assertTrue(width < 800.0, "choice content must not inherit an already stretched header width");
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void wrappingContentLabelCannotGrowPastTheFormWidth() {
        Label explanation = DialogSpacing.wrappingContentLabel(
                "Alle Befehle der gewählten Sprache werden importiert; vorhandene IDs werden übersprungen.",
                460.0);

        assertTrue(explanation.isWrapText());
        assertEquals(0.0, explanation.getMinWidth());
        assertEquals(460.0, explanation.getPrefWidth());
        assertEquals(460.0, explanation.getMaxWidth());
    }

    @Test
    void explicitResizableDialogMinimumSurvivesTheInitialLayoutWidth() {
        assertEquals(450.0, DialogSpacing.resizableMinimumWidth(450.0, 612.4));
        assertEquals(613.0, DialogSpacing.resizableMinimumWidth(0.0, 612.4));
    }

    @Test
    void nativeDecorationUsesSceneInsteadOfAnOversizedFirstFrameDialogPane() {
        assertEquals(34.0, DialogSpacing.windowDecorationExtent(424.0, 390.0, 424.0));
        assertEquals(34.0, DialogSpacing.windowDecorationExtent(424.0, 390.0, 360.0));
        assertEquals(0.0, DialogSpacing.windowDecorationExtent(390.0, 390.0, 390.0));
    }

    @Test
    void nonResizablePromptDeclaresItsWidthBeforeGeneratedTextIsMeasured() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                javafx.scene.control.Alert prompt = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.INFORMATION,
                        "A wrapping activation reminder whose first Linux layout must not collapse");
                DialogSpacing.prepare(prompt);
                DialogSpacing.configureInitialWindowWidth(prompt, 520.0, 440.0);

                assertEquals(520.0, prompt.getDialogPane().getPrefWidth());
                assertEquals(440.0, prompt.getDialogPane().getMinWidth());
                assertEquals(520.0,
                        DialogSpacing.configuredResizableWindowWidth(prompt.getDialogPane()));
                assertEquals(440.0,
                        DialogSpacing.configuredResizableMinimumWidth(prompt.getDialogPane()));
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void everyPreparedDialogUsesThePlatformNativeTitleBar() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.INFORMATION);
                alert.setTitle("Activation successful");
                alert.setHeaderText("The license is active");
                DialogSpacing.prepare(alert);

                assertFalse(alert.getDialogPane().getStyleClass().contains("custom-window-dialog"));
                assertNull(alert.getDialogPane().getHeader());
                alert.show();
                assertEquals(StageStyle.DECORATED,
                        ((Stage) alert.getDialogPane().getScene().getWindow()).getStyle());
                alert.close();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void updateCheckingDialogActuallyDeclaresA400PixelContentWidth() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                javafx.scene.control.Dialog<Void> dialog = new javafx.scene.control.Dialog<>();
                dialog.setTitle("Check for updates");
                dialog.setHeaderText("Checking for updates");
                DialogSpacing.prepare(dialog);
                SoftwareUpdateDialog.configureCheckingGeometry(dialog);
                javafx.scene.control.ProgressBar progress = new javafx.scene.control.ProgressBar();
                progress.setMaxWidth(Double.MAX_VALUE);
                dialog.getDialogPane().setContent(progress);
                dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL);

                assertEquals(400.0, dialog.getDialogPane().getPrefWidth());
                assertEquals(400.0, dialog.getDialogPane().getMinWidth());
                assertEquals(400.0,
                        DialogSpacing.configuredResizableWindowWidth(dialog.getDialogPane()));
                dialog.show();
                PauseTransition afterNativeLayout = new PauseTransition(Duration.millis(250));
                afterNativeLayout.setOnFinished(ignored -> {
                    try {
                        assertTrue(dialog.getDialogPane().getWidth() >= 400.0,
                                () -> "the transient checking dialog content width was "
                                        + dialog.getDialogPane().getWidth());
                        assertTrue(dialog.getDialogPane().getScene().getWindow().getWidth() >= 400.0);
                    } catch (Throwable error) {
                        failure.set(error);
                    } finally {
                        dialog.close();
                        completed.countDown();
                    }
                });
                afterNativeLayout.play();
            } catch (Throwable error) {
                failure.set(error);
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void sessionManagerPaneAndContentCanFillAResizedWindow() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                DialogPane pane = new DialogPane();
                Region content = new Region();

                MainWindowController.configureSessionManagerResizableLayout(pane, content);

                assertEquals(0.0, pane.getMinWidth());
                assertEquals(0.0, pane.getMinHeight());
                assertEquals(Double.MAX_VALUE, pane.getMaxWidth());
                assertEquals(0.0, content.getMinWidth());
                assertEquals(0.0, content.getMinHeight());
                assertEquals(Double.MAX_VALUE, content.getMaxWidth());

                javafx.scene.control.Dialog<Void> dialog = new javafx.scene.control.Dialog<>();
                DialogSpacing.configureResizableWindowWidth(dialog, 480.0);
                assertEquals(480.0, dialog.getDialogPane().getPrefWidth());
                assertEquals(480.0,
                        DialogSpacing.configuredResizableWindowWidth(dialog.getDialogPane()));
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void quickConnectUsesPlatformSpecificResizeWithInitialContentGeometry() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                javafx.scene.control.Dialog<Void> dialog = new javafx.scene.control.Dialog<>();
                MainWindowController.configureQuickConnectDialogSizing(dialog);

                assertEquals(LocalizedTextInputContextMenus.isLinux(), dialog.isResizable());
                assertEquals(600.0, dialog.getDialogPane().getPrefWidth());
                assertEquals(600.0, dialog.getDialogPane().getMinWidth());
                assertEquals(675.0, dialog.getDialogPane().getPrefHeight());
                assertEquals(675.0, dialog.getDialogPane().getMinHeight());
                assertEquals(LocalizedTextInputContextMenus.isLinux()
                        ? Region.USE_COMPUTED_SIZE : 675.0, dialog.getDialogPane().getMaxHeight());
                assertEquals(675.0,
                        DialogSpacing.configuredResizableWindowHeight(dialog.getDialogPane()));
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void quickConnectFixesOnlyTheLastTwoDialogButtonsAtNinetyFivePixels() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                javafx.scene.control.Dialog<Void> dialog = new javafx.scene.control.Dialog<>();
                DialogSpacing.prepare(dialog);
                javafx.scene.control.DialogPane pane = dialog.getDialogPane();
                List<ButtonType> buttons = MainWindowController.quickConnectDialogButtonTypes();
                pane.getButtonTypes().setAll(buttons);

                MainWindowController.configureQuickConnectDialogButtonWidths(pane, buttons);
                MainWindowController.configureQuickConnectDialogSizing(dialog);
                dialog.show();
                Platform.runLater(() -> {
                    try {
                        assertTrue(ButtonBar.isButtonUniformSize(pane.lookupButton(buttons.get(0))));
                        assertTrue(ButtonBar.isButtonUniformSize(pane.lookupButton(buttons.get(1))));
                        assertFalse(ButtonBar.isButtonUniformSize(pane.lookupButton(buttons.get(2))));
                        assertFalse(ButtonBar.isButtonUniformSize(pane.lookupButton(buttons.get(3))));
                        assertEquals(pane.lookupButton(buttons.get(0)).getBoundsInParent().getWidth(),
                                pane.lookupButton(buttons.get(1)).getBoundsInParent().getWidth(), 0.001);
                        double compactWidth = -1.0;
                        for (int index = 2; index < buttons.size(); index++) {
                            Region button = (Region) pane.lookupButton(buttons.get(index));
                            assertEquals(95.0, button.getMinWidth());
                            assertEquals(95.0, button.getPrefWidth());
                            assertEquals(95.0, button.getMaxWidth());
                            double displayedWidth = button.getBoundsInParent().getWidth();
                            assertEquals(95.0, displayedWidth, 1.0);
                            if (compactWidth < 0.0) compactWidth = displayedWidth;
                            else assertEquals(compactWidth, displayedWidth, 1.0);
                        }
                    } catch (Throwable error) {
                        failure.set(error);
                    } finally {
                        dialog.close();
                        completed.countDown();
                    }
                });
            } catch (Throwable error) {
                failure.set(error);
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void resizableDialogUsesInitialContentGeometryAndFinalStageMinimumBeforeReveal()
            throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            javafx.scene.control.Dialog<Void> dialog = new javafx.scene.control.Dialog<>();
            try {
                dialog.setResizable(true);
                DialogSpacing.prepare(dialog);
                DialogSpacing.configureResizableWindowGeometry(dialog,
                        600.0, 390.0, 460.0, 300.0);
                Region content = new Region();
                content.setMinSize(0.0, 0.0);
                dialog.getDialogPane().setContent(content);
                dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
                dialog.show();
                PauseTransition afterNativeLayout = new PauseTransition(Duration.millis(250));
                afterNativeLayout.setOnFinished(ignored -> {
                    try {
                        DialogPane pane = dialog.getDialogPane();
                        Stage stage = (Stage) pane.getScene().getWindow();
                        assertTrue(pane.getWidth() >= 599.0,
                                () -> "native content width was " + pane.getWidth());
                        assertTrue(pane.getHeight() >= 389.0,
                                () -> "native content height was " + pane.getHeight());
                        double decorationWidth = stage.getWidth() - pane.getScene().getWidth();
                        double decorationHeight = stage.getHeight() - pane.getScene().getHeight();
                        assertEquals(460.0 + decorationWidth, stage.getMinWidth(), 1.0,
                                "the Stage must advertise its final minimum before reveal");
                        assertEquals(300.0 + decorationHeight, stage.getMinHeight(), 1.0,
                                "the Stage must advertise its final minimum before reveal");
                        assertEquals(600.0, DialogSpacing.configuredResizableWindowWidth(pane));
                        assertEquals(390.0, DialogSpacing.configuredResizableWindowHeight(pane));
                        assertEquals(460.0, DialogSpacing.configuredResizableMinimumWidth(pane));
                        assertEquals(300.0, DialogSpacing.configuredResizableMinimumHeight(pane));
                    } catch (Throwable error) {
                        failure.set(error);
                    } finally {
                        dialog.close();
                        Platform.runLater(completed::countDown);
                    }
                });
                afterNativeLayout.play();
            } catch (Throwable error) {
                failure.set(error);
                dialog.close();
                completed.countDown();
            }
        });
        assertTrue(completed.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void identifiesOnlyJavaFxStandardButtonSingletonsForLocalization() {
        assertEquals("dialog.ok", DialogSpacing.standardButtonKey(ButtonType.OK));
        assertEquals("dialog.cancel", DialogSpacing.standardButtonKey(ButtonType.CANCEL));
        assertEquals("dialog.close", DialogSpacing.standardButtonKey(ButtonType.CLOSE));
        assertEquals("dialog.yes", DialogSpacing.standardButtonKey(ButtonType.YES));
        assertEquals("dialog.no", DialogSpacing.standardButtonKey(ButtonType.NO));
        assertEquals("dialog.apply", DialogSpacing.standardButtonKey(ButtonType.APPLY));
        assertEquals("dialog.finish", DialogSpacing.standardButtonKey(ButtonType.FINISH));
        assertEquals("dialog.next", DialogSpacing.standardButtonKey(ButtonType.NEXT));
        assertEquals("dialog.previous", DialogSpacing.standardButtonKey(ButtonType.PREVIOUS));
        assertNull(DialogSpacing.standardButtonKey(
                new ButtonType("Save", ButtonBar.ButtonData.OK_DONE)));
    }

    @Test
    void applicationDialogWrappersLocalizeStandardButtonsIndependentlyOfTheSystemLocale()
            throws Exception {
        Localizer previous = Texts.localizer();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                Texts.configure(Localizer.forLanguage(InterfaceLanguage.GERMAN));
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.getButtonTypes().setAll(ButtonType.CANCEL);
                alert.show();
                assertEquals("Abbrechen",
                        ((Button) alert.getDialogPane().lookupButton(ButtonType.CANCEL)).getText());
                alert.close();

                Dialog<Void> dialog = new Dialog<>();
                dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL);
                dialog.show();
                assertEquals("Abbrechen",
                        ((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).getText());
                dialog.close();

                TextInputDialog input = new TextInputDialog("");
                input.show();
                assertEquals("Abbrechen",
                        ((Button) input.getDialogPane().lookupButton(ButtonType.CANCEL)).getText());
                input.close();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                Texts.configure(previous);
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }
}
