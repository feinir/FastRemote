// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.automation.AutomationCapability;
import com.ynpe.fastremote.domain.ai.ModelProviderType;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AiSettingsPaneTest {
    private static void afterDialogLayout(javafx.scene.control.Dialog<?> dialog, Runnable action) {
        new javafx.animation.AnimationTimer() {
            private long started;
            private long stableSince;
            private double width = -1;
            private double height = -1;
            @Override public void handle(long now) {
                if (started == 0) started = now;
                var pane = dialog.getDialogPane();
                var window = pane.getScene().getWindow();
                // Native placement and DialogSpacing's minimum relaxation happen asynchronously.
                // Pulse counts alone do not mean GTK has applied the final client geometry.
                boolean ready = window.getOpacity() == 1.0 && pane.getMinWidth() == 560.0
                        && !pane.isNeedsLayout();
                if (!ready || pane.getWidth() != width || pane.getHeight() != height) {
                    stableSince = now;
                    width = pane.getWidth();
                    height = pane.getHeight();
                }
                if ((ready && now - stableSince >= TimeUnit.MILLISECONDS.toNanos(250))
                        || now - started >= TimeUnit.SECONDS.toNanos(3)) {
                    stop();
                    action.run();
                }
            }
        }.start();
    }

    @Test
    void providerDialogGrowsWhenSwitchingToKimiAndKeepsButtonsVisible() throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            var dialog = AiSettingsPane.createProviderDialog(null);
            try {
                dialog.setHeaderText("Configure model API; model name can be left blank for FastRemote to detect. "
                        + "Provider settings apply to every conversation that uses this service.");
                dialog.show();
                var grid = (javafx.scene.layout.GridPane) dialog.getDialogPane().getContent();
                var combo = (javafx.scene.control.ComboBox<ModelProviderType>) grid.getChildren().stream()
                        .filter(javafx.scene.control.ComboBox.class::isInstance).findFirst().orElseThrow();
                Label help = (Label) grid.getChildren().stream()
                        .filter(n -> Integer.valueOf(6).equals(javafx.scene.layout.GridPane.getRowIndex(n)))
                        .findFirst().orElseThrow();
                afterDialogLayout(dialog, () -> {
                    combo.setValue(ModelProviderType.KIMI_MOONSHOT);
                    help.textProperty().unbind();
                    help.setText("Use a Moonshot API Key. The default is the China endpoint; change the Base URL to match the region where your key was issued. Leave the model blank to discover available models.");
                    afterDialogLayout(dialog, () -> {
                        try {
                            var pane = dialog.getDialogPane();
                            pane.applyCss(); pane.layout();
                            assertTrue(pane.getWidth() >= 670, "default dialog must leave room for the form");
                            Label header = (Label) pane.lookup(".header-panel .label");
                            assertTrue(header.getWidth() >= 600, "header must use the available width: header="
                                    + header.getWidth() + ", pane=" + pane.getWidth()
                                    + ", scene=" + pane.getScene().getWidth() + ", header bounds="
                                    + header.getParent().getBoundsInLocal());
                            assertTrue(help.getHeight() >= help.prefHeight(help.getWidth()) - 1,
                                    "help must retain its full wrapped height");
                            var button = pane.lookupButton(javafx.scene.control.ButtonType.CANCEL);
                            assertTrue(button.localToScene(button.getBoundsInLocal()).getMaxY() <= pane.getScene().getHeight(),
                                    "buttons must fit inside the actual visible client area");
                            assertTrue(help.localToScene(help.getBoundsInLocal()).getMaxY()
                                    <= button.localToScene(button.getBoundsInLocal()).getMinY(), "help must stay above buttons");
                        } catch (Throwable error) { failure.set(error); }
                        finally { dialog.close(); finished.countDown(); }
                    });
                });
            } catch (Throwable error) { failure.set(error); dialog.close(); finished.countDown(); }
        });
        assertTrue(finished.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test
    void supportsGeminiApiKeyProvider() {
        assertEquals("Google Gemini API", AiSettingsPane.typeLabel(ModelProviderType.GEMINI_API));
        for (var type : java.util.List.of(ModelProviderType.GEMINI_API)) {
            assertEquals("https://generativelanguage.googleapis.com/v1beta", AiSettingsPane.defaultProviderUrl(type));
            assertFalse(AiSettingsPane.providerHelp(type).isBlank());
        }
    }

    @Test
    void kimiDefaultsToTheOfficialEndpointAndAllowsRegionalOverrides() {
        assertEquals("Kimi / 月之暗面", AiSettingsPane.typeLabel(ModelProviderType.KIMI_MOONSHOT));
        assertEquals("https://api.moonshot.cn/v1", AiSettingsPane.defaultProviderUrl(ModelProviderType.KIMI_MOONSHOT));
        assertEquals("https://api.moonshot.ai/v1",
                AiSettingsPane.providerUri(ModelProviderType.KIMI_MOONSHOT, "https://api.moonshot.ai/v1").toString());
        assertTrue(AiSettingsPane.providerHelp(ModelProviderType.KIMI_MOONSHOT).contains("API Key"));
        assertTrue(AiSettingsPane.providerHelp(ModelProviderType.QWEN_DASHSCOPE).contains("Host"));
        assertEquals("", AiSettingsPane.providerHelp(ModelProviderType.OLLAMA));
    }

    @Test
    void mainStatusBarUsesGreenForRunningAndRedForStopped() {
        Label indicator = new Label("●");
        Label text = new Label();

        MainWindowController.updateMcpStatusBar(indicator, text, true);
        assertEquals("MCP服务已启动", text.getText());
        assertEquals("MCP服务已启动", indicator.getAccessibleText());
        assertTrue(indicator.getStyleClass().contains("mcp-status-running"));

        MainWindowController.updateMcpStatusBar(indicator, text, false);
        assertEquals("MCP服务已停止", text.getText());
        assertEquals("MCP服务已停止", indicator.getAccessibleText());
        assertTrue(indicator.getStyleClass().contains("mcp-status-stopped"));
        assertFalse(indicator.getStyleClass().contains("mcp-status-running"));
    }

    @Test
    void rendersTheActualMcpEndpointForARunningService() {
        Label label = new Label();

        AiSettingsPane.updateMcpRuntimeStatus(label,
                AiAssistantWorkflow.McpServerStatus.running(47123), null);

        assertTrue(label.getText().contains("http://127.0.0.1:47123/mcp"));
        assertTrue(label.getStyleClass().contains("mcp-runtime-status-running"));
    }

    @Test
    void rendersAnMcpStartupFailureAsAnErrorState() {
        Label label = new Label();

        AiSettingsPane.updateMcpRuntimeStatus(label,
                AiAssistantWorkflow.McpServerStatus.failed("Address already in use"), null);

        assertTrue(label.getText().contains("Address already in use"));
        assertTrue(label.getStyleClass().contains("mcp-runtime-status-failed"));
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
    void usesIndependentAndConsistentLocalAndRemoteFilePermissionLabels() {
        assertEquals("本地文件读取", AiSettingsPane.capabilityLabel(AutomationCapability.READ_LOCAL));
        assertEquals("本地文件创建", AiSettingsPane.capabilityLabel(AutomationCapability.CREATE_LOCAL));
        assertEquals("本地文件编辑", AiSettingsPane.capabilityLabel(AutomationCapability.EDIT_LOCAL));
        assertEquals("本地文件删除", AiSettingsPane.capabilityLabel(AutomationCapability.DELETE_LOCAL));
        assertEquals("本地文件改名", AiSettingsPane.capabilityLabel(AutomationCapability.RENAME_LOCAL));
        assertEquals("远程文件读取", AiSettingsPane.capabilityLabel(AutomationCapability.READ_REMOTE));
        assertEquals("远程文件创建", AiSettingsPane.capabilityLabel(AutomationCapability.CREATE_REMOTE));
        assertEquals("远程文件编辑", AiSettingsPane.capabilityLabel(AutomationCapability.EDIT_REMOTE));
        assertEquals("远程文件删除", AiSettingsPane.capabilityLabel(AutomationCapability.DELETE_REMOTE));
        assertEquals("远程文件改名", AiSettingsPane.capabilityLabel(AutomationCapability.RENAME_REMOTE));
        assertEquals("查看工作区",
                AiSettingsPane.capabilityLabel(AutomationCapability.READ_WORKSPACE));
        assertEquals("编辑器写入",
                AiSettingsPane.capabilityLabel(AutomationCapability.EDIT_WORKSPACE));
        assertEquals("查看工作区日志",
                AiSettingsPane.capabilityLabel(AutomationCapability.READ_WORKSPACE_LOG));
    }

    @Test
    void hostPermissionLabelsAreShortInEveryPublishedLanguage() {
        var previous = com.ynpe.fastremote.i18n.Texts.localizer();
        try {
            for (var language : com.ynpe.fastremote.domain.settings.InterfaceLanguage.publishedLanguages()) {
                var localizer = com.ynpe.fastremote.i18n.Localizer.forLanguage(language);
                com.ynpe.fastremote.i18n.Texts.configure(localizer);
                var labels = java.util.List.of(
                        AiSettingsPane.capabilityLabel(AutomationCapability.CREATE_HOSTS),
                        AiSettingsPane.capabilityLabel(AutomationCapability.DELETE_HOSTS),
                        AiSettingsPane.capabilityLabel(AutomationCapability.EDIT_HOSTS));
                assertEquals(3, java.util.Set.copyOf(labels).size());
                for (String label : labels) {
                    var text = new javafx.scene.text.Text(label);
                    text.setFont(javafx.scene.text.Font.font(14));
                    assertTrue(text.getLayoutBounds().getWidth() <= 145, language + " " + label);
                }
                if (language == com.ynpe.fastremote.domain.settings.InterfaceLanguage.GERMAN) {
                    assertEquals(java.util.List.of("Hosts anlegen", "Hosts löschen", "Hosts ändern"), labels);
                }
            }
        } finally {
            com.ynpe.fastremote.i18n.Texts.configure(previous);
        }
    }

    @Test
    void opensProviderEditingOnlyForPrimaryDoubleClickOnASelectedProvider() {
        assertTrue(AiSettingsPane.isProviderEditGesture(MouseButton.PRIMARY, 2, true));
        assertFalse(AiSettingsPane.isProviderEditGesture(MouseButton.PRIMARY, 1, true));
        assertFalse(AiSettingsPane.isProviderEditGesture(MouseButton.SECONDARY, 2, true));
        assertFalse(AiSettingsPane.isProviderEditGesture(MouseButton.PRIMARY, 2, false));
    }

    @Test
    void providerFieldLabelsAlwaysKeepTheirNaturalTranslatedWidth() {
        assertEquals(Region.USE_PREF_SIZE, AiSettingsPane.providerLabelMinimumWidth());
    }

    @Test
    void modelDiscoveryButtonStaysEnabledDuringAnInFlightProbeSoFocusCannotBeEvicted() {
        ListView<String> providers = new ListView<>();
        providers.getItems().add("provider");
        Button detect = new Button("detect");
        AiSettingsPane.bindModelDiscoveryAvailability(detect, providers);

        assertTrue(detect.isDisabled());
        providers.getSelectionModel().selectFirst();
        assertFalse(detect.isDisabled());
    }

    @Test
    void mcpAuthorizationFieldGrowsWhileLocalizedButtonsKeepTheirNaturalWidth() {
        TextField key = new TextField();
        Button copy = new Button("Konfiguration kopieren");
        Button regenerate = new Button("Schlüssel neu generieren");

        HBox row = AiSettingsPane.mcpKeyRow(key, copy, regenerate);

        assertEquals(java.util.List.of(key, copy, regenerate), row.getChildren());
        assertEquals(Priority.ALWAYS, HBox.getHgrow(key));
        assertEquals(0.0, key.getMinWidth());
        assertEquals(Double.MAX_VALUE, key.getMaxWidth());
        assertEquals(Region.USE_PREF_SIZE, copy.getMinWidth());
        assertEquals(Region.USE_PREF_SIZE, regenerate.getMinWidth());
    }

    @Test
    void usesOfficialOpenAiBaseAddressAndLocalOllamaAddress() {
        assertEquals("https://api.openai.com/v1",
                AiSettingsPane.defaultProviderUrl(ModelProviderType.OPENAI_RESPONSES));
        assertEquals("",
                AiSettingsPane.defaultProviderUrl(ModelProviderType.OPENAI_COMPATIBLE));
        assertEquals("http://127.0.0.1:11434/v1",
                AiSettingsPane.defaultProviderUrl(ModelProviderType.OLLAMA));
    }

    @Test
    void acceptsQwenWorkspaceHostsWithoutChangingOtherProviderAddresses() {
        assertEquals("Qwen / DashScope", AiSettingsPane.typeLabel(ModelProviderType.QWEN_DASHSCOPE));
        assertEquals("", AiSettingsPane.defaultProviderUrl(ModelProviderType.QWEN_DASHSCOPE));
        assertEquals("https://ws-test.cn-beijing.maas.aliyuncs.com/compatible-mode/v1",
                AiSettingsPane.providerUri(ModelProviderType.QWEN_DASHSCOPE,
                        "ws-test.cn-beijing.maas.aliyuncs.com").toString());
        assertEquals("https://gateway.example/custom/v1",
                AiSettingsPane.providerUri(ModelProviderType.OPENAI_COMPATIBLE,
                        "https://gateway.example/custom/v1").toString());
    }

    @Test
    void installedRemoteRootHandlerKeepsEnterAwayFromTheEnclosingDialog() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                TextField input = new TextField("/srv/releases/");
                VBox dialogContent = new VBox(input);
                AtomicInteger additions = new AtomicInteger();
                AtomicInteger leakedEvents = new AtomicInteger();
                AiSettingsPane.installRemoteRootEnterHandler(input, additions::incrementAndGet);
                dialogContent.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
                    if (event.getCode() == KeyCode.ENTER) leakedEvents.incrementAndGet();
                });
                dialogContent.addEventHandler(KeyEvent.KEY_RELEASED, event -> {
                    if (event.getCode() == KeyCode.ENTER) leakedEvents.incrementAndGet();
                });

                javafx.event.Event.fireEvent(input, keyPressed(KeyCode.ENTER));
                javafx.event.Event.fireEvent(input, keyReleased(KeyCode.ENTER));

                assertEquals(0, leakedEvents.get());
                assertEquals(1, additions.get());
                completed.countDown();
            } catch (Throwable error) {
                failure.set(error);
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void staleMcpKeyLoadCannotOverwriteARegeneratedKey() {
        long initialLoad = 1;
        long regeneration = 2;

        assertFalse(AiSettingsPane.isCurrentMcpKeyRequest(initialLoad, regeneration));
        assertTrue(AiSettingsPane.isCurrentMcpKeyRequest(regeneration, regeneration));
    }

    @Test
    void mcpClientGuidesUseTheStandardAuthorizationBearerHeader() {
        String codex = AiSettingsPane.mcpClientConfiguration(AiSettingsPane.McpClient.CODEX, 37654, "secret-token");
        assertTrue(codex.contains("Authorization"));
        assertTrue(codex.contains("Bearer secret-token"));
        assertTrue(codex.contains("bearer_token_env_var = \"FASTREMOTE_MCP_TOKEN\""));
        assertFalse(codex.contains("bearer_token_env_var = \"secret-token\""));

        for (AiSettingsPane.McpClient client : AiSettingsPane.McpClient.values()) {
            String guide = AiSettingsPane.mcpClientConfiguration(client, 37654, "secret-token");
            assertTrue(guide.contains("Authorization"));
            assertTrue(guide.contains("Bearer secret-token"));
            assertTrue(guide.contains("http://127.0.0.1:37654/mcp"));
        }

        String claudeDesktop = AiSettingsPane.mcpClientConfiguration(
                AiSettingsPane.McpClient.CLAUDE_DESKTOP, 37654, "secret-token");
        assertTrue(claudeDesktop.contains("\"command\": \"npx\""));
        assertTrue(claudeDesktop.contains("\"mcp-remote\""));
        assertFalse(claudeDesktop.contains("\"url\""));
    }

    @Test
    void mcpAuthorizationDisplayAlwaysContainsExactlyOneBearerPrefix() {
        assertEquals("Bearer secret-token", AiSettingsPane.mcpAuthorizationValue("secret-token"));
        assertEquals("Bearer secret-token", AiSettingsPane.mcpAuthorizationValue("Bearer secret-token"));
        assertEquals("Bearer secret-token", AiSettingsPane.mcpAuthorizationValue("bearer   secret-token"));
    }

    @Test
    void mcpNetworkHelpClearlyDistinguishesLocalAndRemoteExposure() {
        assertTrue(AiSettingsPane.mcpNetworkHelp(false).contains("127.0.0.1"));
        assertTrue(AiSettingsPane.mcpNetworkHelp(true).contains("0.0.0.0"));
    }

    private static KeyEvent keyReleased(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code,
                false, false, false, false);
    }

    private static KeyEvent keyPressed(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code,
                false, false, false, false);
    }
}
