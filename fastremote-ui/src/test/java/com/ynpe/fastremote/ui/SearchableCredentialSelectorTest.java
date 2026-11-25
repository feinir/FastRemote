// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.catalog.GroupColor;
import com.ynpe.fastremote.domain.connection.AuthenticationType;
import com.ynpe.fastremote.domain.credential.CredentialGroup;
import com.ynpe.fastremote.domain.credential.CredentialGroupId;
import com.ynpe.fastremote.domain.credential.CredentialScope;
import com.ynpe.fastremote.domain.credential.GlobalCredential;
import com.ynpe.fastremote.domain.vault.SecretId;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.MenuButton;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.stage.PopupWindow;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SearchableCredentialSelectorTest {
    @BeforeAll
    static void startJavaFxToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(5, TimeUnit.SECONDS));
    }

    @Test
    void filtersCredentialsByLabelUsernameAndGroupWithoutAddingAFixedChoice() {
        CredentialGroup production = new CredentialGroup(
                CredentialGroupId.random(), "生产环境", 0, GroupColor.RED);
        GlobalCredential root = credential("Root Primary", "ROOT", Optional.of(production.id()));
        GlobalCredential deploy = credential("发布账号", "deploy", Optional.empty());

        List<SearchableCredentialSelector.Option> options = SearchableCredentialSelector.options(
                List.of(deploy, root), List.of(production));

        assertEquals(List.of(root.id()), ids(SearchableCredentialSelector.filter(options, "primary")));
        assertEquals(List.of(root.id()), ids(SearchableCredentialSelector.filter(options, "root")));
        assertEquals(List.of(root.id()), ids(SearchableCredentialSelector.filter(options, "生产")));
        assertEquals(List.of(deploy.id()), ids(SearchableCredentialSelector.filter(options, "DEPLOY")));
        assertEquals(2, SearchableCredentialSelector.filter(options, "").size());
    }

    @Test
    void popupSearchSelectsAFilteredCredentialWithTheKeyboard() throws InterruptedException {
        CredentialGroup production = new CredentialGroup(
                CredentialGroupId.random(), "My_Key", 0, GroupColor.RED);
        GlobalCredential credential = credential("vps_id_rsa", "deploy", Optional.of(production.id()));
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                var selector = new SearchableCredentialSelector(
                        new MenuButton(), List.of(credential), List.of(production), Optional.empty());
                assertTrue(selector.node().getItems().isEmpty(),
                        "selector must use the searchable popup instead of MenuButton items");
                assertEquals(PopupWindow.AnchorLocation.CONTENT_TOP_LEFT,
                        selector.popup().getAnchorLocation());
                selector.node().resize(620.0, 34.0);
                assertEquals(620.0, selector.popupContent().getPrefWidth());
                selector.searchField().setText("DEPLOY");
                assertEquals(1, selector.results().getItems().size());
                assertEquals("My_Key/vps_id_rsa", selector.results().getItems().getFirst().primaryText());
                selector.searchField().fireEvent(new javafx.scene.input.KeyEvent(
                        javafx.scene.input.KeyEvent.KEY_RELEASED, "", "", KeyCode.ENTER,
                        false, false, false, false));
                assertEquals(Optional.of(credential), selector.selectedCredential());
                StackPane root = new StackPane(selector.node());
                new Scene(root, 620, 60);
                root.applyCss();
                root.layout();
                assertEquals("My_Key/vps_id_rsa", ((Text) selector.node().lookup(".label .text")).getText());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void searchArrowUpMovesTheResultSelectionInsteadOfTheTextCaret() throws InterruptedException {
        GlobalCredential first = credential("first", "first", Optional.empty());
        GlobalCredential second = credential("second", "second", Optional.empty());
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                var selector = new SearchableCredentialSelector(
                        new MenuButton(), List.of(first, second), List.of(), Optional.empty());
                selector.searchField().setText("s");
                selector.searchField().positionCaret(1);
                selector.results().getSelectionModel().select(1);
                selector.searchField().fireEvent(new javafx.scene.input.KeyEvent(
                        javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", KeyCode.UP,
                        false, false, false, false));

                assertEquals(first, selector.results().getSelectionModel().getSelectedItem().credential());
                assertEquals(1, selector.searchField().getCaretPosition());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void rebuildKeepsThePreferredCredentialAndClearsAMissingOne() throws InterruptedException {
        GlobalCredential first = credential("第一项", "first", Optional.empty());
        GlobalCredential second = credential("第二项", "second", Optional.empty());
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                var selector = new SearchableCredentialSelector(
                        new MenuButton(), List.of(first, second), List.of(), Optional.of(second.id()));
                assertEquals(Optional.of(second), selector.selectedCredential());
                selector.rebuild(List.of(first), List.of(), Optional.of(second.id()));
                assertTrue(selector.selectedCredential().isEmpty());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    private static List<SecretId> ids(List<SearchableCredentialSelector.Option> options) {
        return options.stream().map(option -> option.credential().id()).toList();
    }

    private static GlobalCredential credential(
            String label, String username, Optional<CredentialGroupId> groupId) {
        return new GlobalCredential(
                SecretId.create(), label, username, AuthenticationType.PASSWORD,
                CredentialScope.REUSABLE, Optional.empty(), groupId, 0);
    }
}
