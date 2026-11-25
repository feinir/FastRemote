// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.settings.InterfaceLanguage;
import com.ynpe.fastremote.i18n.Localizer;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Region;
import javafx.scene.Parent;
import javafx.scene.effect.DropShadow;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LocalizedTextInputContextMenusTest {
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
    void everyPublishedInterfaceLanguageOwnsItsTextInputMenuCopy() throws Exception {
        runOnJavaFxThread(() -> {
            TextField input = new TextField("value");
            input.selectRange(0, 1);
            for (InterfaceLanguage language : InterfaceLanguage.publishedLanguages()) {
                Localizer localizer = Localizer.forLanguage(language);
                ContextMenu menu = LocalizedTextInputContextMenus.createMenu(input, localizer);
                for (String action : java.util.List.of(
                        "undo", "redo", "cut", "copy", "paste", "delete", "selectAll")) {
                    assertEquals(localizer.text("textInput.context." + action),
                            item(menu, action).getText(), language.persistedTag() + ":" + action);
                }
            }
            ContextMenu english = LocalizedTextInputContextMenus.createMenu(
                    input, Localizer.forLanguage(InterfaceLanguage.ENGLISH));
            assertTrue(english.getStyleClass().contains(ApplicationContextMenus.STYLE_CLASS));
            assertTrue(english.getStyleClass().contains("text-input-context-menu"));
            assertEquals("Undo", item(english, "undo").getText());
            assertEquals("Select All", item(english, "selectAll").getText());
        });
    }

    @Test
    void applicationContextMenuStylingIsSharedAndIdempotent() throws Exception {
        runOnJavaFxThread(() -> {
            ContextMenu menu = ApplicationContextMenus.create(new MenuItem("Action"));
            ApplicationContextMenus.style(menu);
            ApplicationContextMenus.style(menu);
            assertEquals(1, menu.getStyleClass().stream()
                    .filter(ApplicationContextMenus.STYLE_CLASS::equals)
                    .count());

            javafx.scene.control.TreeView<String> tree = new javafx.scene.control.TreeView<>();
            ContextMenu treeMenu = LibraryTreeSupport.withExpansionActions(
                    new ContextMenu(new MenuItem("Create")), tree);
            assertTrue(treeMenu.getStyleClass().contains(ApplicationContextMenus.STYLE_CLASS));
        });
    }

    @Test
    void applicationContextMenusReplaceThePreviousPopupAndDismissOnOwnerPress() throws Exception {
        runOnJavaFxThread(() -> {
            StackPane owner = new StackPane();
            Stage stage = new Stage();
            stage.setScene(new Scene(owner, 240, 120));
            stage.show();
            ContextMenu first = ApplicationContextMenus.create(new MenuItem("First"));
            ContextMenu second = ApplicationContextMenus.create(new MenuItem("Second"));
            try {
                first.show(owner, 30, 30);
                assertTrue(ApplicationContextMenus.isShowing(first));
                if (!LocalizedTextInputContextMenus.isLinux()) {
                    assertTrue(first.getSkin() != null && first.getSkin().getNode().getScene() != null);
                }
                second.show(owner, 60, 60);
                assertFalse(ApplicationContextMenus.isShowing(first));
                assertTrue(ApplicationContextMenus.isShowing(second));
                assertEquals(second, ApplicationContextMenus.activeMenu());

                owner.fireEvent(mousePress(MouseButton.PRIMARY));
                assertFalse(ApplicationContextMenus.isShowing(second));
                assertNull(ApplicationContextMenus.activeMenu());
            } finally {
                first.hide();
                second.hide();
                stage.hide();
            }
        });
    }

    @Test
    void applicationContextMenuNeverUsesAnInvisiblePopupGrab() throws Exception {
        runOnJavaFxThread(() -> {
            StackPane owner = new StackPane();
            Stage stage = new Stage();
            stage.setScene(new Scene(owner, 240, 120));
            stage.show();
            ContextMenu popup = ApplicationContextMenus.create(new MenuItem("Action"));
            try {
                assertEquals(1.0, popup.getOpacity(), 0.0);
                popup.show(owner, 30, 30);
                assertTrue(ApplicationContextMenus.isShowing(popup));
                owner.fireEvent(mousePress(MouseButton.PRIMARY));
                assertFalse(ApplicationContextMenus.isShowing(popup));
            } finally {
                popup.hide();
                stage.hide();
            }
        });
    }

    @Test
    void dynamicMenuContentIsBuiltAndLaidOutBeforeNativeShowing() throws Exception {
        runOnJavaFxThread(() -> {
            StackPane owner = new StackPane();
            Stage stage = new Stage();
            stage.setScene(new Scene(owner, 240, 120));
            stage.getScene().getStylesheets().add(MainView.stylesheet().toExternalForm());
            ThemeManager.install(stage.getScene());
            stage.show();
            ContextMenu menu = ApplicationContextMenus.create();
            ApplicationContextMenus.prepareBeforeShow(menu, () -> menu.getItems().setAll(
                    new MenuItem("A dynamically populated menu item")));
            AtomicReference<Double> widthBeforeNativeShow = new AtomicReference<>();
            menu.addEventHandler(javafx.stage.WindowEvent.WINDOW_SHOWING, ignored -> {
                assertEquals(1, menu.getItems().size());
                widthBeforeNativeShow.set(menu.getSkin().getNode().getLayoutBounds().getWidth());
            });
            try {
                menu.show(owner, 30, 30);
                assertTrue(menu.isShowing());
                assertTrue(widthBeforeNativeShow.get() > 0.0);
                assertEquals(menu.getSkin().getNode().prefWidth(-1.0),
                        widthBeforeNativeShow.get(), 1.01);
            } finally {
                menu.hide();
                stage.hide();
            }
        });
    }

    @Test
    void nativeMenuCanExtendBeyondOwnerAndFireCheckItem() throws Exception {
        runOnJavaFxThread(() -> {
            StackPane owner = new StackPane();
            owner.getStylesheets().add(MainView.stylesheet().toExternalForm());
            Stage stage = new Stage();
            stage.setScene(new Scene(owner, 320, 180));
            stage.show();
            javafx.scene.control.CheckMenuItem check =
                    new javafx.scene.control.CheckMenuItem("Hidden files");
            java.util.concurrent.atomic.AtomicInteger actions =
                    new java.util.concurrent.atomic.AtomicInteger();
            ContextMenu menu = ApplicationContextMenus.create(check);
            check.setOnAction(ignored -> {
                actions.incrementAndGet();
            });
            try {
                javafx.geometry.Bounds bounds = owner.localToScreen(owner.getBoundsInLocal());
                menu.show(owner, bounds.getMaxX() - 20, bounds.getMaxY() - 10);
                assertTrue(menu.isShowing());
                assertEquals(stage, menu.getOwnerWindow());
                assertTrue(menu.getX() + menu.getWidth() > bounds.getMaxX());
                assertTrue(menu.getY() + menu.getHeight() > bounds.getMaxY());
                Node row = menu.getSkin().getNode().lookup(".menu-item");
                assertTrue(row != null);
                row.fireEvent(mouseRelease(MouseButton.PRIMARY));
                assertTrue(check.isSelected());
                assertEquals(1, actions.get());
                assertFalse(ApplicationContextMenus.isShowing(menu));
            } finally {
                menu.hide();
                stage.hide();
            }
        });
    }

    @Test
    void spacerFileRowCannotEnterTheSelectionModelOnSecondaryPress() throws Exception {
        runOnJavaFxThread(() -> {
            LocalFileEntry file = new LocalFileEntry("real.txt", Path.of("real.txt"), false,
                    false, 1L, Optional.empty(), false);
            LocalFileEntry spacer = SftpBrowserModel.withLocalNavigation(
                    Path.of("."), List.of()).get(1);
            TableView<LocalFileEntry> table = new TableView<>();
            table.getItems().setAll(file, spacer);
            class ExposedRow extends TableRow<LocalFileEntry> {
                void expose(LocalFileEntry entry) { updateItem(entry, false); }
            }
            ExposedRow row = new ExposedRow();
            row.expose(spacer);
            MainWindowController.guardBlankFileRow(table, row);
            table.getSelectionModel().select(0);

            MouseEvent press = mousePress(MouseButton.SECONDARY);
            row.fireEvent(press);

            assertTrue(table.getSelectionModel().isEmpty());
        });
    }

    @Test
    void applicationSubmenuReceivesTheSamePopupStyleAsItsParent() throws Exception {
        runOnJavaFxThread(() -> {
            Stage stage = new Stage();
            Menu submenuItem = ApplicationContextMenus.styleSubmenu(
                    new Menu("Go to", null, new MenuItem("Home")));
            ApplicationContextMenus.styleSubmenu(submenuItem);
            ContextMenu parent = ApplicationContextMenus.create(submenuItem);
            try {
                StackPane owner = new StackPane();
                stage.setScene(new Scene(owner, 240, 120));
                stage.getScene().getStylesheets().add(MainView.stylesheet().toExternalForm());
                ThemeManager.install(stage.getScene());
                ThemeManager.setTheme(com.ynpe.fastremote.domain.settings.ApplicationTheme.DARK);
                stage.show();
                AtomicReference<java.util.List<String>> stylesBeforeNativeShow = new AtomicReference<>();
                AtomicReference<javafx.scene.paint.Paint> sceneFillBeforeNativeShow =
                        new AtomicReference<>();
                parent.addEventHandler(javafx.stage.WindowEvent.WINDOW_SHOWING, ignored -> {
                    assertTrue(parent.getSkin() != null);
                    stylesBeforeNativeShow.set(List.copyOf(parent.getScene().getStylesheets()));
                    sceneFillBeforeNativeShow.set(parent.getScene().getFill());
                });

                parent.show(owner, 40, 40);
                assertEquals(stage.getScene().getStylesheets(), stylesBeforeNativeShow.get());
                Parent popupRoot = (Parent) parent.getSkin().getNode();
                popupRoot.applyCss();
                popupRoot.layout();
                assertEquals(javafx.scene.paint.Color.web("#232427"),
                        ((Region) popupRoot).getBackground().getFills().getFirst().getFill());
                assertNull(sceneFillBeforeNativeShow.get());
                Node submenuRow = popupRoot.lookupAll(".menu-item").stream()
                        .filter(node -> node.getProperties().get(MenuItem.class) == submenuItem)
                        .findFirst()
                        .orElseThrow();
                submenuRow.fireEvent(new MouseEvent(
                        MouseEvent.MOUSE_ENTERED,
                        1, 1, 41, 41,
                        MouseButton.NONE,
                        0,
                        false, false, false, false,
                        false, false, false,
                        false, false, false,
                        null));

                ContextMenu submenu = Window.getWindows().stream()
                        .filter(ContextMenu.class::isInstance)
                        .map(ContextMenu.class::cast)
                        .filter(candidate -> candidate != parent && candidate.isShowing())
                        .filter(candidate -> candidate.getOwnerWindow() == parent)
                        .findFirst()
                        .orElseThrow();
                Region submenuRoot = (Region) submenu.getSkin().getNode();
                submenuRoot.applyCss();
                assertEquals(parent.getScene().getStylesheets(), submenu.getScene().getStylesheets());
                assertTrue(submenuRoot.getStylesheets()
                        .contains(MainView.stylesheet().toExternalForm()));
                assertEquals(javafx.scene.paint.Color.web("#232427"),
                        submenuRoot.getBackground().getFills().getFirst().getFill());
                assertEquals(10.0, submenuRoot.getBackground().getFills().getFirst()
                        .getRadii().getTopLeftHorizontalRadius(), 0.01);
                assertEquals(10.0, submenuRoot.getBorder().getStrokes().getFirst()
                        .getRadii().getTopLeftHorizontalRadius(), 0.01);
            } finally {
                parent.hide();
                stage.hide();
            }
        });
        runOnJavaFxThread(() -> {
            ThemeManager.setTheme(com.ynpe.fastremote.domain.settings.ApplicationTheme.DARK);
            ThemeManager.setTheme(com.ynpe.fastremote.domain.settings.ApplicationTheme.LIGHT);
        });
    }

    @Test
    void editorLightSubmenuInheritsItsFinalThemeBeforeShowing() throws Exception {
        runOnJavaFxThread(() -> {
            Stage stage = new Stage();
            Menu submenuItem = ApplicationContextMenus.styleSubmenu(
                    new Menu("Syntax", null, new MenuItem("Plain text")));
            ContextMenu parent = ApplicationContextMenus.create(submenuItem);
            parent.getStyleClass().addAll("embedded-editor-context-menu", "editor-context-light");
            try {
                StackPane owner = new StackPane();
                stage.setScene(new Scene(owner, 240, 120));
                stage.getScene().getStylesheets().add(MainView.stylesheet().toExternalForm());
                ThemeManager.install(stage.getScene());
                ThemeManager.setTheme(com.ynpe.fastremote.domain.settings.ApplicationTheme.DARK);
                stage.show();

                assertTrue(parent.getStyleClass().contains("editor-context-light"));
                AtomicReference<java.util.List<String>> lightStylesBeforeNativeShow = new AtomicReference<>();
                parent.addEventHandler(javafx.stage.WindowEvent.WINDOW_SHOWING, ignored ->
                        lightStylesBeforeNativeShow.set(List.copyOf(parent.getScene().getStylesheets())));
                AtomicReference<ContextMenu> preparedSubmenu = new AtomicReference<>();
                AtomicReference<javafx.scene.paint.Paint> submenuFillBeforeNativeShow =
                        new AtomicReference<>();
                AtomicReference<javafx.scene.paint.Paint> submenuSceneFillBeforeNativeShow =
                        new AtomicReference<>();
                AtomicReference<javafx.scene.paint.Paint> submenuFillAfterPreparation =
                        new AtomicReference<>();
                AtomicReference<java.util.List<String>> submenuStylesAfterPreparation =
                        new AtomicReference<>();
                submenuItem.getItems().getFirst().parentPopupProperty().addListener(
                        (ignored, previous, candidate) -> {
                            if (candidate != null && candidate != parent) {
                                preparedSubmenu.set(candidate);
                                submenuStylesAfterPreparation.set(List.copyOf(
                                        ((Parent) candidate.getStyleableNode()).getStylesheets()));
                                Region preparedSurface = (Region) candidate.getSkin().getNode();
                                submenuFillAfterPreparation.set(preparedSurface.getBackground() == null
                                        ? null
                                        : preparedSurface.getBackground().getFills().stream()
                                                .findFirst()
                                                .map(javafx.scene.layout.BackgroundFill::getFill)
                                                .orElse(null));
                                candidate.addEventHandler(
                                        javafx.stage.WindowEvent.WINDOW_SHOWING,
                                        event -> {
                                            submenuFillBeforeNativeShow.set(
                                                    ((Region) candidate.getSkin().getNode())
                                                            .getBackground() == null ? null
                                                            : ((Region) candidate.getSkin().getNode())
                                                            .getBackground().getFills().stream()
                                                            .findFirst()
                                                            .map(javafx.scene.layout.BackgroundFill::getFill)
                                                            .orElse(null));
                                            submenuSceneFillBeforeNativeShow.set(
                                                    candidate.getScene().getFill());
                                        });
                            }
                        });
                parent.show(owner, 40, 40);
                assertEquals(stage.getScene().getStylesheets(), lightStylesBeforeNativeShow.get());
                Parent popupRoot = (Parent) parent.getSkin().getNode();
                Node submenuRow = popupRoot.lookupAll(".menu-item").stream()
                        .filter(node -> node.getProperties().get(MenuItem.class) == submenuItem)
                        .findFirst()
                        .orElseThrow();
                submenuRow.fireEvent(new MouseEvent(
                        MouseEvent.MOUSE_ENTERED,
                        1, 1, 41, 41,
                        MouseButton.NONE,
                        0,
                        false, false, false, false,
                        false, false, false,
                        false, false, false,
                        null));

                ContextMenu submenu = Window.getWindows().stream()
                        .filter(ContextMenu.class::isInstance)
                        .map(ContextMenu.class::cast)
                        .filter(candidate -> candidate != parent && candidate.isShowing())
                        .filter(candidate -> candidate.getOwnerWindow() == parent)
                        .findFirst()
                        .orElseThrow();
                assertEquals(submenu, preparedSubmenu.get());
                assertTrue(submenu.getStyleClass().contains(ApplicationContextMenus.STYLE_CLASS));
                assertTrue(submenu.getStyleClass().contains("editor-context-light"));
                assertTrue(submenuStylesAfterPreparation.get()
                                .contains(MainView.stylesheet().toExternalForm()),
                        submenuStylesAfterPreparation.get().toString());
                assertEquals(javafx.scene.paint.Color.WHITE, submenuFillAfterPreparation.get(),
                        submenuStylesAfterPreparation.get().toString());
                assertEquals(javafx.scene.paint.Color.WHITE, submenuFillBeforeNativeShow.get());
                assertNull(submenuSceneFillBeforeNativeShow.get());
                Region submenuRoot = (Region) submenu.getSkin().getNode();
                submenuRoot.applyCss();
                assertEquals(parent.getScene().getStylesheets(), submenu.getScene().getStylesheets());
                assertEquals(javafx.scene.paint.Color.WHITE,
                        submenuRoot.getBackground().getFills().getFirst().getFill());
            } finally {
                parent.hide();
                stage.hide();
                ThemeManager.setTheme(com.ynpe.fastremote.domain.settings.ApplicationTheme.LIGHT);
            }
        });
    }

    @Test
    void menuBarPopupUsesTheSameRoundedContextMenuSurfaceInBothThemes() throws Exception {
        runOnJavaFxThread(() -> {
            Stage stage = new Stage();
            javafx.scene.control.Menu topMenu = new javafx.scene.control.Menu(
                    "Tools", null, new MenuItem("Settings"));
            javafx.scene.control.MenuBar menuBar = new javafx.scene.control.MenuBar(topMenu);
            try {
                stage.setScene(new Scene(new StackPane(menuBar), 320, 120));
                stage.getScene().getStylesheets().add(MainView.stylesheet().toExternalForm());
                ThemeManager.install(stage.getScene());
                ThemeManager.setTheme(com.ynpe.fastremote.domain.settings.ApplicationTheme.LIGHT);
                stage.show();

                topMenu.show();
                Region lightRoot = menuPopupRoot(stage);
                lightRoot.applyCss();
                assertEquals(javafx.scene.paint.Color.WHITE,
                        lightRoot.getBackground().getFills().getFirst().getFill());
                assertEquals(10.0, lightRoot.getBackground().getFills().getFirst()
                        .getRadii().getTopLeftHorizontalRadius(), 0.01);

                topMenu.hide();
                ThemeManager.setTheme(com.ynpe.fastremote.domain.settings.ApplicationTheme.DARK);
                topMenu.show();
                Region darkRoot = menuPopupRoot(stage);
                darkRoot.applyCss();
                assertEquals(javafx.scene.paint.Color.web("#232427"),
                        darkRoot.getBackground().getFills().getFirst().getFill());
                assertEquals(10.0, darkRoot.getBackground().getFills().getFirst()
                        .getRadii().getTopLeftHorizontalRadius(), 0.01);
            } finally {
                topMenu.hide();
                stage.hide();
                ThemeManager.setTheme(com.ynpe.fastremote.domain.settings.ApplicationTheme.LIGHT);
            }
        });
    }

    private static Region menuPopupRoot(Stage owner) {
        return Window.getWindows().stream()
                .filter(ContextMenu.class::isInstance)
                .map(ContextMenu.class::cast)
                .filter(ContextMenu::isShowing)
                .filter(candidate -> candidate.getOwnerWindow() == owner)
                .map(candidate -> (Region) candidate.getSkin().getNode())
                .findFirst()
                .orElseThrow();
    }

    @Test
    void menuStateRespectsSelectionEditingAndPasswordMasking() throws Exception {
        runOnJavaFxThread(() -> {
            Localizer english = Localizer.forLanguage(InterfaceLanguage.ENGLISH);
            TextField editable = new TextField("value");
            editable.selectRange(0, 1);
            ContextMenu editableMenu = LocalizedTextInputContextMenus.createMenu(editable, english);
            assertFalse(item(editableMenu, "cut").isDisable());
            assertFalse(item(editableMenu, "copy").isDisable());
            assertFalse(item(editableMenu, "delete").isDisable());
            assertFalse(item(editableMenu, "selectAll").isDisable());
            item(editableMenu, "delete").fire();
            assertEquals("alue", editable.getText());

            TextField readOnly = new TextField("value");
            readOnly.setEditable(false);
            readOnly.selectRange(0, 1);
            ContextMenu readOnlyMenu = LocalizedTextInputContextMenus.createMenu(readOnly, english);
            assertTrue(item(readOnlyMenu, "cut").isDisable());
            assertFalse(item(readOnlyMenu, "copy").isDisable());
            assertTrue(item(readOnlyMenu, "paste").isDisable());
            assertTrue(item(readOnlyMenu, "delete").isDisable());

            PasswordField password = new PasswordField();
            password.setText("secret");
            password.selectAll();
            ContextMenu passwordMenu = LocalizedTextInputContextMenus.createMenu(password, english);
            assertTrue(item(passwordMenu, "cut").isDisable());
            assertTrue(item(passwordMenu, "copy").isDisable());
        });
    }

    @Test
    void specializedAndDisabledTextControlsKeepTheirOwnContextMenuBehavior() throws Exception {
        runOnJavaFxThread(() -> {
            TextField input = new TextField();
            assertTrue(LocalizedTextInputContextMenus.usesApplicationMenu(input));

            input.setContextMenu(new ContextMenu());
            assertFalse(LocalizedTextInputContextMenus.usesApplicationMenu(input));
            input.setContextMenu(null);
            input.setOnContextMenuRequested(event -> { });
            assertFalse(LocalizedTextInputContextMenus.usesApplicationMenu(input));
            input.setOnContextMenuRequested(null);
            input.setDisable(true);
            assertFalse(LocalizedTextInputContextMenus.usesApplicationMenu(input));
        });
    }

    @Test
    void installationFollowsWindowScenesAndHasAnExplicitReleasePath() throws Exception {
        runOnJavaFxThread(() -> {
            LocalizedTextInputContextMenus installation = LocalizedTextInputContextMenus.install(
                    Localizer.forLanguage(InterfaceLanguage.ENGLISH));
            Stage stage = new Stage();
            Scene scene = new Scene(new StackPane(new TextField()));
            stage.setScene(scene);
            stage.show();
            assertTrue(installation.isInstalledOn(scene));
            stage.hide();
            assertFalse(installation.isInstalledOn(scene));
            installation.close();
        });
    }

    @Test
    void repeatedRequestsReplaceTheSingleActiveMenuAcrossTextInputs() throws Exception {
        runOnJavaFxThread(() -> {
            TextField firstInput = new TextField("first");
            TextField secondInput = new TextField("second");
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(firstInput, secondInput), 320, 180));
            stage.show();
            LocalizedTextInputContextMenus installation = LocalizedTextInputContextMenus.install(
                    Localizer.forLanguage(InterfaceLanguage.ENGLISH));

            requestMenu(firstInput, 40, 50);
            ContextMenu firstMenu = installation.activeMenu();
            assertTrue(ApplicationContextMenus.isShowing(firstMenu));

            firstInput.selectRange(0, firstInput.getLength());
            requestMenu(firstInput, 80, 70);
            ContextMenu replacement = installation.activeMenu();
            assertNotSame(firstMenu, replacement);
            assertFalse(ApplicationContextMenus.isShowing(firstMenu));
            assertTrue(ApplicationContextMenus.isShowing(replacement));
            assertTrue(item(replacement, "selectAll").isDisable());

            requestMenu(secondInput, 120, 90);
            ContextMenu secondInputMenu = installation.activeMenu();
            assertNotSame(replacement, secondInputMenu);
            assertFalse(ApplicationContextMenus.isShowing(replacement));
            assertTrue(ApplicationContextMenus.isShowing(secondInputMenu));

            stage.hide();
            assertFalse(ApplicationContextMenus.isShowing(secondInputMenu));
            assertNull(installation.activeMenu());
            installation.close();
        });
    }

    @Test
    void closingInstallationHidesItsActiveMenu() throws Exception {
        runOnJavaFxThread(() -> {
            TextField input = new TextField("value");
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(input), 240, 120));
            stage.show();
            LocalizedTextInputContextMenus installation = LocalizedTextInputContextMenus.install(
                    Localizer.forLanguage(InterfaceLanguage.ENGLISH));

            requestMenu(input, 40, 50);
            ContextMenu menu = installation.activeMenu();
            assertTrue(ApplicationContextMenus.isShowing(menu));
            installation.close();
            assertFalse(ApplicationContextMenus.isShowing(menu));
            assertNull(installation.activeMenu());
            stage.hide();
        });
    }

    @Test
    void primaryPressInTheOwningTextInputClosesItsMenuWithoutConsumingTheClick() throws Exception {
        runOnJavaFxThread(() -> {
            TextField input = new TextField("value");
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(input), 240, 120));
            stage.show();
            LocalizedTextInputContextMenus installation = LocalizedTextInputContextMenus.install(
                    Localizer.forLanguage(InterfaceLanguage.ENGLISH));

            requestMenu(input, 40, 50);
            ContextMenu menu = installation.activeMenu();
            assertTrue(ApplicationContextMenus.isShowing(menu));

            MouseEvent primaryPress = mousePress(MouseButton.PRIMARY);
            input.fireEvent(primaryPress);
            assertFalse(ApplicationContextMenus.isShowing(menu));
            assertNull(installation.activeMenu());

            installation.close();
            stage.hide();
        });
    }

    @Test
    void popupUsesTheSharedMenuFontSizeFamilyAndShadow() throws Exception {
        runOnJavaFxThread(() -> {
            TextField input = new TextField("value");
            StackPane root = new StackPane(input);
            root.getStylesheets().add(MainView.stylesheet().toExternalForm());
            InterfaceFontPolicy.apply(root);
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 240, 120));
            stage.show();
            LocalizedTextInputContextMenus installation = LocalizedTextInputContextMenus.install(
                    Localizer.forLanguage(InterfaceLanguage.ENGLISH));

            requestMenu(input, 40, 50);
            ContextMenu menu = installation.activeMenu();
            Parent popupRoot = (Parent) menu.getSkin().getNode();
            popupRoot.applyCss();
            Label label = (Label) popupRoot.lookup(".menu-item .label");
            assertEquals(13.0, label.getFont().getSize());
            assertEquals(InterfaceFontPolicy.HARMONY_SC_FAMILY, label.getFont().getFamily());
            assertTrue(popupRoot.getEffect() instanceof DropShadow);

            installation.close();
            stage.hide();
        });
    }

    private static void requestMenu(TextField input, double screenX, double screenY) {
        ContextMenuEvent request = new ContextMenuEvent(
                ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                1,
                1,
                screenX,
                screenY,
                false,
                new PickResult(input, 1, 1));
        input.fireEvent(request);
    }

    private static MouseEvent mousePress(MouseButton button) {
        return new MouseEvent(
                MouseEvent.MOUSE_PRESSED,
                1, 1, 1, 1,
                button,
                1,
                false, false, false, false,
                button == MouseButton.PRIMARY,
                button == MouseButton.MIDDLE,
                button == MouseButton.SECONDARY,
                false, false, true,
                null);
    }

    private static MouseEvent mouseRelease(MouseButton button) {
        return new MouseEvent(MouseEvent.MOUSE_RELEASED, 1, 1, 1, 1, button, 1,
                false, false, false, false, false, false, false,
                false, false, true, null);
    }

    private static MenuItem item(ContextMenu menu, String action) {
        return menu.getItems().stream()
                .filter(candidate -> ("text-input-context-" + action).equals(candidate.getId()))
                .findFirst().orElseThrow();
    }

    private static void runOnJavaFxThread(Runnable assertion) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                assertion.run();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }
}
