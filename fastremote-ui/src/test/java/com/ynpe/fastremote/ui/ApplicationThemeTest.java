// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.settings.ApplicationTheme;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationThemeTest {
    @BeforeAll static void startToolkit() throws Exception {
        var ready = new CompletableFuture<Void>();
        try { Platform.startup(() -> ready.complete(null)); }
        catch (IllegalStateException started) { ready.complete(null); }
        ready.get(5, TimeUnit.SECONDS);
        Platform.setImplicitExit(false);
    }

    @Test void threeThemesSwitchExistingControlsWithoutChangingEditorPalette() throws Exception {
        var done = new CompletableFuture<Void>();
        Platform.runLater(() -> {
            try {
                var navigation = new ListView<String>();
                navigation.getItems().setAll("Appearance", "Connections", "Credentials");
                navigation.getStyleClass().add("settings-navigation");
                navigation.getSelectionModel().selectFirst();
                navigation.setPrefWidth(200);
                var field = new TextField("FastRemote");
                var choice = new ComboBox<String>();
                choice.getItems().setAll("Dark", "Slate", "Light");
                choice.setValue("Dark");
                var check = new CheckBox("Restore window bounds");
                check.setSelected(true);
                var button = new Button("Apply");
                button.setDefaultButton(true);
                var editor = new EmbeddedTextEditor(com.ynpe.fastremote.domain.settings.SftpSettings.defaults(), "preview.txt");
                var form = new VBox(16, new Label("Appearance"), new Separator(), choice, field, check, editor, button);
                form.getStyleClass().add("settings-card");
                HBox.setHgrow(form, Priority.ALWAYS);
                var root = new HBox(24, navigation, form);
                root.getStyleClass().add("settings-shell");
                root.setPadding(new javafx.geometry.Insets(20));
                var scene = new Scene(root, 800, 520);
                ThemeManager.style(root);
                ThemeManager.install(scene);
                assertEquals(3, ApplicationTheme.values().length);
                for (ApplicationTheme theme : new ApplicationTheme[] {
                        ApplicationTheme.DARK, ApplicationTheme.SLATE, ApplicationTheme.LIGHT,
                        ApplicationTheme.SLATE, ApplicationTheme.DARK}) {
                    ThemeManager.setTheme(theme);
                    root.applyCss(); root.layout();
                    assertEquals(Color.web("#12161c"),
                            editor.editorArea().getBackground().getFills().getFirst().getFill());
                    assertEquals(theme == ApplicationTheme.DARK,
                            root.getStylesheets().contains(MainView.darkStylesheet().toExternalForm()));
                    assertEquals(theme == ApplicationTheme.SLATE,
                            root.getStylesheets().contains(MainView.slateStylesheet().toExternalForm()));
                    if (theme != ApplicationTheme.LIGHT) {
                        assertEquals(Color.web(theme == ApplicationTheme.DARK ? "#18191a" : "#11151b"),
                                root.getBackground().getFills().getFirst().getFill());
                    }
                }
                assertEquals(Color.web("#3574f0"), button.getBackground().getFills().getFirst().getFill());
                assertEquals(Color.web("#cbcdd3"), ((javafx.scene.text.Text) field.lookup(".text")).getFill());
                // Inspect the actual JavaFX rendering of the reference controls without user data.
                var pixels = root.snapshot(null, null);
                var image = new java.awt.image.BufferedImage((int) pixels.getWidth(),
                        (int) pixels.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < image.getHeight(); y++)
                    for (int x = 0; x < image.getWidth(); x++)
                        image.setRGB(x, y, pixels.getPixelReader().getArgb(x, y));
                var path = java.nio.file.Path.of("build/theme-preview/dark.png");
                java.nio.file.Files.createDirectories(path.getParent());
                javax.imageio.ImageIO.write(image, "png", path.toFile());
                done.complete(null);
            } catch (Throwable error) { done.completeExceptionally(error); }
            finally { ThemeManager.setTheme(ApplicationTheme.LIGHT); }
        });
        done.get(15, TimeUnit.SECONDS);
    }
}
