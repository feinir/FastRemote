// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InsertionOrderedDialogPaneTest {
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
    void keepsQuickConnectButtonsInDeclaredOrderAcrossPlatforms() throws Exception {
        AtomicReference<List<String>> labels = new AtomicReference<>();
        AtomicReference<String> order = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                InsertionOrderedDialogPane pane = new InsertionOrderedDialogPane();
                ButtonType save = new ButtonType("存入主机库", ButtonBar.ButtonData.APPLY);
                ButtonType connect = new ButtonType("连接", ButtonBar.ButtonData.OK_DONE);
                pane.getButtonTypes().setAll(ButtonType.CANCEL, save, connect);
                ButtonBar bar = (ButtonBar) pane.createButtonBar();
                order.set(bar.getButtonOrder());
                labels.set(bar.getButtons().stream()
                        .map(node -> ((Button) node).getText())
                        .toList());
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        assertEquals(ButtonBar.BUTTON_ORDER_NONE, order.get());
        assertEquals(List.of("取消", "存入主机库", "连接"), labels.get());
    }

    @Test
    void keepsSessionEditorActionsTogetherWithTestBeforeCancel() throws Exception {
        AtomicReference<List<String>> labels = new AtomicReference<>();
        AtomicReference<String> order = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                InsertionOrderedDialogPane pane = new InsertionOrderedDialogPane();
                ButtonType test = new ButtonType("测试连接", ButtonBar.ButtonData.OTHER);
                ButtonType save = new ButtonType("创建主机", ButtonBar.ButtonData.OK_DONE);
                pane.getButtonTypes().setAll(test, ButtonType.CANCEL, save);
                ButtonBar bar = (ButtonBar) pane.createButtonBar();
                order.set(bar.getButtonOrder());
                labels.set(bar.getButtons().stream()
                        .map(node -> ((Button) node).getText())
                        .toList());
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        assertEquals(ButtonBar.BUTTON_ORDER_NONE, order.get());
        assertEquals(List.of("测试连接", "取消", "创建主机"), labels.get());
    }

    @Test
    void quickConnectButtonsKeepTheirFullEnglishTextWidth() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                InsertionOrderedDialogPane pane = new InsertionOrderedDialogPane();
                pane.setPrefWidth(760.0);
                pane.getButtonTypes().setAll(
                        new ButtonType("Test Connection", ButtonBar.ButtonData.OTHER),
                        new ButtonType("Save to Hosts Library", ButtonBar.ButtonData.APPLY),
                        ButtonType.CANCEL,
                        new ButtonType("Connect", ButtonBar.ButtonData.OK_DONE));
                new Scene(pane);
                DialogSpacing.fitButtonsToContent(pane);
                for (ButtonType type : pane.getButtonTypes()) {
                    Button button = (Button) pane.lookupButton(type);
                    assertEquals(Region.USE_PREF_SIZE, button.getMinWidth());
                    assertTrue(button.prefWidth(-1) >= button.getLayoutBounds().getWidth());
                }
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }
}
