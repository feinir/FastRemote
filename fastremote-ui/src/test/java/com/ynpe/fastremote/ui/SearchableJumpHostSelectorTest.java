// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.catalog.GroupColor;
import com.ynpe.fastremote.domain.catalog.GroupId;
import com.ynpe.fastremote.domain.catalog.SavedHost;
import com.ynpe.fastremote.domain.catalog.HostGroup;
import com.ynpe.fastremote.domain.connection.AuthenticationType;
import com.ynpe.fastremote.domain.connection.ConnectionProfile;
import com.ynpe.fastremote.domain.connection.Host;
import com.ynpe.fastremote.domain.connection.Port;
import com.ynpe.fastremote.domain.connection.Username;
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

class SearchableJumpHostSelectorTest {
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
    void filtersJumpHostsByNameHostAndFullGroupPathWithoutCaseSensitivity() {
        GroupId productionId = GroupId.create();
        HostGroup production = new HostGroup(
                productionId, Optional.empty(), "生产环境", 0, GroupColor.RED);
        HostGroup database = new HostGroup(
                GroupId.create(), Optional.of(productionId), "数据库", 0, GroupColor.PURPLE);
        SavedHost jump = SavedHost.create(
                "Bastion Alpha", Optional.of(database.id()), profile("EDGE.EXAMPLE.COM"));
        SavedHost other = SavedHost.create(
                "测试入口", Optional.empty(), profile("test.example.com"));
        List<SearchableJumpHostSelector.Option> options = SearchableJumpHostSelector.options(
                List.of(jump, other), List.of(production, database), Optional.empty());

        assertEquals(List.of(jump.id()), ids(SearchableJumpHostSelector.filter(options, "bastion")));
        assertEquals(List.of(jump.id()), ids(SearchableJumpHostSelector.filter(options, "edge.example")));
        assertEquals(List.of(jump.id()), ids(SearchableJumpHostSelector.filter(options, "生产环境")));
        assertEquals(List.of(jump.id()), ids(SearchableJumpHostSelector.filter(options, "数据库")));
        assertTrue(SearchableJumpHostSelector.filter(options, "bastion").getFirst().id().isEmpty());
        assertEquals(3, SearchableJumpHostSelector.filter(options, "").size());
    }

    @Test
    void excludesTheEditedHostButAlwaysKeepsDirectConnectionAvailable() {
        SavedHost host = SavedHost.create("self", Optional.empty(), profile("self.example.com"));

        List<SearchableJumpHostSelector.Option> options = SearchableJumpHostSelector.options(
                List.of(host), List.of(), Optional.of(host.id()));

        assertEquals(1, options.size());
        assertTrue(options.getFirst().id().isEmpty());
    }

    @Test
    void movesTheFixedDirectConnectionToTheBottomWhenThePopupOpensUpward() {
        SavedHost host = SavedHost.create("入口", Optional.empty(), profile("edge.example.com"));
        List<SearchableJumpHostSelector.Option> options = SearchableJumpHostSelector.options(
                List.of(host), List.of(), Optional.empty());

        List<SearchableJumpHostSelector.Option> upward =
                SearchableJumpHostSelector.optionsForDirection(options, true);

        assertEquals(Optional.of(host.id()), upward.getFirst().id());
        assertTrue(upward.getLast().id().isEmpty());
    }

    @Test
    void popupSearchCanSelectAFilteredHostWithTheKeyboard() throws InterruptedException {
        SavedHost host = SavedHost.create("edge__ops_01", Optional.empty(), profile("edge.example.com"));
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                var selector = new SearchableJumpHostSelector(
                        new MenuButton(), List.of(host), List.of(), Optional.empty(), Optional.empty());
                assertTrue(selector.node().getItems().isEmpty(),
                        "selector must not use MenuButton's cached-width ContextMenu");
                assertEquals(PopupWindow.AnchorLocation.CONTENT_TOP_LEFT,
                        selector.popup().getAnchorLocation());
                selector.node().resize(620.0, 34.0);
                assertEquals(620.0, selector.popupContent().getMinWidth());
                assertEquals(620.0, selector.popupContent().getPrefWidth());
                assertEquals(620.0, selector.popupContent().getMaxWidth());
                selector.node().resize(720.0, 34.0);
                assertEquals(720.0, selector.popupContent().getPrefWidth());
                selector.searchField().setText("EDGE.EXAMPLE");
                assertEquals(2, selector.results().getItems().size());
                assertTrue(selector.results().getItems().getFirst().id().isEmpty());
                assertEquals("通过 edge__ops_01", selector.results().getItems().get(1).primaryText());
                selector.results().getSelectionModel().select(1);
                selector.searchField().fireEvent(new javafx.scene.input.KeyEvent(
                        javafx.scene.input.KeyEvent.KEY_RELEASED, "", "", KeyCode.ENTER,
                        false, false, false, false));
                assertEquals(Optional.of(host.id()), selector.selectedHostId());
                StackPane root = new StackPane(selector.node());
                new Scene(root, 620, 60);
                root.applyCss();
                root.layout();
                assertEquals("通过 edge__ops_01", ((Text) selector.node().lookup(".label .text")).getText());
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
        SavedHost first = SavedHost.create("first", Optional.empty(), profile("first.example.com"));
        SavedHost second = SavedHost.create("second", Optional.empty(), profile("second.example.com"));
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                var selector = new SearchableJumpHostSelector(
                        new MenuButton(), List.of(first, second), List.of(), Optional.empty(), Optional.empty());
                selector.searchField().setText("s");
                selector.searchField().positionCaret(1);
                selector.results().getSelectionModel().select(2);
                selector.searchField().fireEvent(new javafx.scene.input.KeyEvent(
                        javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", KeyCode.UP,
                        false, false, false, false));

                assertEquals(Optional.of(first.id()), selector.results().getSelectionModel().getSelectedItem().id());
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
    void popupSearchLeavesAnImeOwnedEnterToTheInputMethod() throws InterruptedException {
        SavedHost host = SavedHost.create("入口主机", Optional.empty(), profile("edge.example.com"));
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                var selector = new SearchableJumpHostSelector(
                        new MenuButton(), List.of(host), List.of(), Optional.empty(), Optional.empty());
                selector.searchField().setText("edge");
                selector.results().getSelectionModel().select(1);
                selector.searchField().fireEvent(new javafx.scene.input.InputMethodEvent(
                        javafx.scene.input.InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                        List.of(new javafx.scene.input.InputMethodTextRun(
                                "raw", javafx.scene.input.InputMethodHighlight.UNSELECTED_RAW)), "", 3));
                selector.searchField().fireEvent(new javafx.scene.input.InputMethodEvent(
                        javafx.scene.input.InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                        List.of(), "raw-English", 0));
                java.util.concurrent.atomic.AtomicInteger leakedEnterPresses =
                        new java.util.concurrent.atomic.AtomicInteger();
                selector.searchField().addEventHandler(
                        javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                            if (event.getCode() == KeyCode.ENTER) leakedEnterPresses.incrementAndGet();
                        });
                javafx.scene.input.KeyEvent pressed = new javafx.scene.input.KeyEvent(
                        javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                        false, false, false, false);
                selector.searchField().fireEvent(pressed);
                selector.searchField().fireEvent(new javafx.scene.input.KeyEvent(
                        javafx.scene.input.KeyEvent.KEY_RELEASED, "", "", KeyCode.ENTER,
                        false, false, false, false));

                assertTrue(selector.selectedHostId().isEmpty());
                assertEquals(0, leakedEnterPresses.get());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    private static List<com.ynpe.fastremote.domain.catalog.HostId> ids(
            List<SearchableJumpHostSelector.Option> options) {
        return options.stream().flatMap(option -> option.id().stream()).toList();
    }

    private static ConnectionProfile profile(String host) {
        return new ConnectionProfile(
                new Host(host), new Port(22), new Username("ops"),
                AuthenticationType.PASSWORD, Optional.empty());
    }
}
