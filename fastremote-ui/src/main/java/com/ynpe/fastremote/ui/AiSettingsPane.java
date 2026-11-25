// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.ai.AiSettings;
import com.ynpe.fastremote.domain.ai.McpServerSettings;
import com.ynpe.fastremote.domain.ai.ModelProviderConfig;
import com.ynpe.fastremote.domain.ai.ModelProviderType;
import com.ynpe.fastremote.domain.ai.ChatGptCodexProvider;
import com.ynpe.fastremote.domain.automation.AutomationCapability;
import com.ynpe.fastremote.domain.automation.AutomationPermission;
import com.ynpe.fastremote.domain.automation.AutomationPermissions;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Region;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

/** AI settings surface embedded directly in the configuration center. */
final class AiSettingsPane {
    enum McpClient {
        CODEX("Codex / ChatGPT desktop"),
        CLAUDE_CODE("Claude Code"),
        CLAUDE_DESKTOP("Claude Desktop"),
        CURSOR("Cursor"),
        GENERIC("Generic HTTP MCP client");

        private final String label;

        McpClient(String label) { this.label = label; }

        @Override public String toString() { return label; }
    }

    private AiSettingsPane() { }

    static Editor create(Window owner, AiAssistantWorkflow workflow, AiSettings initial,
            Consumer<AiSettings> saved, Consumer<String> failure) {
        AtomicReference<AiSettings> current = new AtomicReference<>(initial);
        AtomicReference<Runnable> changed = new AtomicReference<>(() -> { });
        Label status = new Label();
        status.setWrapText(true);
        status.getStyleClass().add("component-help");

        ListView<ModelProviderConfig> providers = new ListView<>();
        providers.getItems().setAll(initial.providers().stream()
                .filter(value -> !value.id().equals(ChatGptCodexProvider.ID)).toList());
        providers.setPrefHeight(210.0);
        providers.setCellFactory(ignored -> providerCell());
        Button addProvider = new Button(com.ynpe.fastremote.i18n.Texts.text("legacy.java.50ef2f4cf6a46924"));
        Button editProvider = new Button(com.ynpe.fastremote.i18n.Texts.text("legacy.java.051836569928a9f9"));
        Button deleteProvider = new Button(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2f9daa828907b93f"));
        Button detectModels = new Button(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a4e47879fbd494b2"));
        editProvider.disableProperty().bind(providers.getSelectionModel().selectedItemProperty().isNull());
        deleteProvider.disableProperty().bind(providers.getSelectionModel().selectedItemProperty().isNull());
        AtomicBoolean discoveringModels = new AtomicBoolean();
        bindModelDiscoveryAvailability(detectModels, providers);
        Runnable refreshProviders = () -> workflow.loadSettings().whenComplete((settings, error) -> Platform.runLater(() -> {
            if (error != null) failure.accept(message(error));
            else {
                String selectedId = Optional.ofNullable(providers.getSelectionModel().getSelectedItem())
                        .map(ModelProviderConfig::id).orElse(null);
                current.set(settings);
                providers.getItems().setAll(settings.providers().stream()
                        .filter(value -> !value.id().equals(ChatGptCodexProvider.ID)).toList());
                if (selectedId != null) settings.providers().stream().filter(value -> value.id().equals(selectedId))
                        .findFirst().ifPresent(value -> providers.getSelectionModel().select(value));
                saved.accept(settings);
                changed.get().run();
            }
        }));
        Consumer<ModelProviderConfig> discover = provider -> {
            if (provider == null || !discoveringModels.compareAndSet(false, true)) return;
            status.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5e1bf52db649461c") + provider.name() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.12f449786e1c7a03"));
            workflow.discoverModels(provider.id()).whenComplete((models, error) -> Platform.runLater(() -> {
                discoveringModels.set(false);
                status.setText(error == null ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.a3aa7937b1709c32") + models.size() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.504b7f57c616cb8c")
                        : com.ynpe.fastremote.i18n.Texts.text("legacy.java.13ec9a891806d8c2") + message(error));
            }));
        };
        addProvider.setOnAction(event -> editProvider(owner, workflow, null, refreshProviders, discover, failure));
        editProvider.setOnAction(event -> editProvider(owner, workflow,
                providers.getSelectionModel().getSelectedItem(), refreshProviders, discover, failure));
        providers.setOnMouseClicked(event -> {
            ModelProviderConfig selected = providers.getSelectionModel().getSelectedItem();
            if (isProviderEditGesture(event.getButton(), event.getClickCount(), selected != null)) {
                editProvider(owner, workflow, selected, refreshProviders, discover, failure);
                event.consume();
            }
        });
        deleteProvider.setOnAction(event -> {
            ModelProviderConfig selected = providers.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            workflow.deleteProvider(selected.id()).whenComplete((settings, error) -> Platform.runLater(() -> {
                if (error != null) failure.accept(message(error));
                else {
                    current.set(settings); providers.getItems().setAll(settings.providers().stream()
                            .filter(value -> !value.id().equals(ChatGptCodexProvider.ID)).toList()); saved.accept(settings);
                    changed.get().run();
                    status.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.7b4c7b43c0d97899"));
                }
            }));
        });
        detectModels.setOnAction(event -> discover.accept(providers.getSelectionModel().getSelectedItem()));
        FlowPane providerActions = new FlowPane(7.0, 7.0, addProvider, editProvider, deleteProvider, detectModels);
        VBox providerPage = subPage(providers, providerActions, status);
        VBox.setVgrow(providers, Priority.ALWAYS);

        Label chatGptStatus = new Label(com.ynpe.fastremote.i18n.Texts.text("ai.chatgpt.checking"));
        chatGptStatus.setWrapText(true);
        Button chatGptLogin = new Button(com.ynpe.fastremote.i18n.Texts.text("ai.chatgpt.login"));
        Button chatGptLogout = new Button(com.ynpe.fastremote.i18n.Texts.text("ai.chatgpt.logout"));
        AtomicBoolean chatGptAuthBusy = new AtomicBoolean();
        Consumer<AiAssistantWorkflow.ChatGptAccount> applyChatGptAccount = account -> {
            chatGptStatus.setText(account.signedIn()
                    ? com.ynpe.fastremote.i18n.Texts.text("ai.chatgpt.signedIn")
                            + account.email().orElse(com.ynpe.fastremote.i18n.Texts.text("ai.chatgpt.account"))
                            + account.planType().map(value -> " · " + value).orElse("")
                    : com.ynpe.fastremote.i18n.Texts.text("ai.chatgpt.signedOut"));
            chatGptLogin.setDisable(account.signedIn());
            chatGptLogout.setDisable(!account.signedIn());
        };
        workflow.loadChatGptAccount().whenComplete((account, error) -> Platform.runLater(() -> {
            if (error == null) applyChatGptAccount.accept(account);
            else {
                chatGptStatus.setText(com.ynpe.fastremote.i18n.Texts.text("ai.chatgpt.unavailable") + message(error));
                chatGptLogout.setDisable(true);
            }
        }));
        chatGptLogin.setOnAction(event -> {
            if (!chatGptAuthBusy.compareAndSet(false, true)) return;
            chatGptStatus.setText(com.ynpe.fastremote.i18n.Texts.text("ai.chatgpt.openingBrowser"));
            workflow.startChatGptLogin().whenComplete((started, startError) -> Platform.runLater(() -> {
                if (startError != null) {
                    chatGptAuthBusy.set(false);
                    chatGptStatus.setText(com.ynpe.fastremote.i18n.Texts.text("ai.chatgpt.loginFailed") + message(startError));
                    return;
                }
                if (!started.browserOpened()) {
                    ClipboardContent content = new ClipboardContent();
                    content.putString(started.authUrl().toString());
                    Clipboard.getSystemClipboard().setContent(content);
                    chatGptStatus.setText(com.ynpe.fastremote.i18n.Texts.text("ai.chatgpt.urlCopied"));
                } else {
                    chatGptStatus.setText(com.ynpe.fastremote.i18n.Texts.text("ai.chatgpt.waiting"));
                }
                workflow.awaitChatGptLogin().whenComplete((account, loginError) -> Platform.runLater(() -> {
                    chatGptAuthBusy.set(false);
                    if (loginError == null) applyChatGptAccount.accept(account);
                    else {
                        chatGptStatus.setText(com.ynpe.fastremote.i18n.Texts.text("ai.chatgpt.loginFailed") + message(loginError));
                    }
                }));
            }));
        });
        chatGptLogout.setOnAction(event -> {
            if (!chatGptAuthBusy.compareAndSet(false, true)) return;
            workflow.logoutChatGpt().whenComplete((ignored, error) -> Platform.runLater(() -> {
                chatGptAuthBusy.set(false);
                if (error == null) applyChatGptAccount.accept(
                        new AiAssistantWorkflow.ChatGptAccount(false, Optional.empty(), Optional.empty()));
                else {
                    chatGptStatus.setText(com.ynpe.fastremote.i18n.Texts.text("ai.chatgpt.logoutFailed") + message(error));
                }
            }));
        });
        VBox chatGptPage = subPage(chatGptStatus, new HBox(7.0, chatGptLogin, chatGptLogout));

        Spinner<Integer> maxToolRounds = new Spinner<>(AiSettings.MIN_MAX_TOOL_ROUNDS,
                AiSettings.MAX_MAX_TOOL_ROUNDS, initial.maxToolRounds());
        maxToolRounds.setEditable(true);
        SingleLineInputEnterSupport.commitSpinnerOnEnter(maxToolRounds);
        maxToolRounds.setPrefWidth(110.0);
        Spinner<Integer> maxTransferMiB = new Spinner<>(
                Math.toIntExact(AiSettings.MIN_MAX_TRANSFER_BYTES / AiSettings.MEBIBYTE),
                Math.toIntExact(AiSettings.MAX_MAX_TRANSFER_BYTES / AiSettings.MEBIBYTE),
                Math.toIntExact(initial.maxTransferBytes() / AiSettings.MEBIBYTE));
        maxTransferMiB.setEditable(true);
        SingleLineInputEnterSupport.commitSpinnerOnEnter(maxTransferMiB);
        maxTransferMiB.setPrefWidth(110.0);
        Label toolRoundsHelp = new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.31d3f05e319642fa"));
        toolRoundsHelp.setWrapText(true);
        GridPane executionGrid = new GridPane(); executionGrid.setHgap(10.0); executionGrid.setVgap(9.0);
        executionGrid.addRow(0, new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.df344e1eeda2563a")), maxToolRounds);
        executionGrid.addRow(1, new Label(com.ynpe.fastremote.i18n.Texts.text("ai.transfer.maxSizeMiB")),
                maxTransferMiB);
        VBox executionPage = subPage(executionGrid, toolRoundsHelp);

        EnumMap<AutomationCapability, ComboBox<AutomationPermission>> builtInFields =
                new EnumMap<>(AutomationCapability.class);
        EnumMap<AutomationCapability, ComboBox<AutomationPermission>> mcpFields =
                new EnumMap<>(AutomationCapability.class);
        GridPane permissionGrid = new GridPane();
        permissionGrid.setHgap(10.0); permissionGrid.setVgap(8.0);
        permissionGrid.addRow(0, new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5c5374dd958bcb79")), new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.c71cb4f8eef0140e")), new Label("MCP"));
        int permissionRow = 1;
        for (AutomationCapability capability : AutomationCapability.values()) {
            ComboBox<AutomationPermission> builtIn = permissionField(initial.builtInPermissions().permission(capability));
            ComboBox<AutomationPermission> mcp = permissionField(initial.mcpPermissions().permission(capability));
            builtInFields.put(capability, builtIn); mcpFields.put(capability, mcp);
            Label label = new Label(capabilityLabel(capability));
            label.setMinWidth(0.0);
            label.setWrapText(true);
            permissionGrid.addRow(permissionRow++, label, builtIn, mcp);
        }
        Label permissionWarning = new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.764b8befd11da283"));
        permissionWarning.setWrapText(true); permissionWarning.getStyleClass().add("inline-security-note");
        VBox permissionPage = subPage(permissionGrid, permissionWarning);

        ListView<Path> roots = ReorderableListView.create(initial.allowedLocalRoots());
        roots.setPrefHeight(170.0);
        Button addRoot = new Button(com.ynpe.fastremote.i18n.Texts.text("legacy.java.03593a6ad0533af0")); Button removeRoot = new Button(com.ynpe.fastremote.i18n.Texts.text("legacy.java.6135d4159e892541"));
        removeRoot.disableProperty().bind(roots.getSelectionModel().selectedItemProperty().isNull());
        addRoot.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser(); chooser.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5b6eed629a839fed"));
            java.io.File selected = PlatformFileChooser.showDialog(chooser, owner);
            if (selected != null) {
                Path path = selected.toPath().toAbsolutePath().normalize();
                if (!roots.getItems().contains(path) && roots.getItems().size() < 32) roots.getItems().add(path);
            }
        });
        removeRoot.setOnAction(event -> roots.getItems().remove(roots.getSelectionModel().getSelectedItem()));
        Label localCopy = new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.7f27a2ef260632f7"));
        localCopy.setWrapText(true);
        VBox localPage = subPage(localCopy, roots, new HBox(7.0, addRoot, removeRoot));

        ListView<String> remoteRoots = ReorderableListView.create(initial.allowedRemoteRoots());
        remoteRoots.setPrefHeight(150.0);
        TextField remoteRoot = new TextField(); remoteRoot.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a3a652de2da507e7"));
        Button addRemoteRoot = new Button(com.ynpe.fastremote.i18n.Texts.text("legacy.java.03593a6ad0533af0")); Button removeRemoteRoot = new Button(com.ynpe.fastremote.i18n.Texts.text("legacy.java.6135d4159e892541"));
        removeRemoteRoot.disableProperty().bind(remoteRoots.getSelectionModel().selectedItemProperty().isNull());
        Runnable addRemoteRootAction = () -> {
            String value = remoteRoot.getText().strip();
            try {
                String normalized = AiSettings.normalizeRemoteRoot(value);
                if (!remoteRoots.getItems().contains(normalized) && remoteRoots.getItems().size() < 64) {
                    remoteRoots.getItems().add(normalized); remoteRoot.clear();
                }
            } catch (RuntimeException invalid) { failure.accept(message(invalid)); }
        };
        addRemoteRoot.setOnAction(event -> addRemoteRootAction.run());
        installRemoteRootEnterHandler(remoteRoot, addRemoteRootAction);
        removeRemoteRoot.setOnAction(event -> remoteRoots.getItems().remove(
                remoteRoots.getSelectionModel().getSelectedItem()));
        Label remoteCopy = new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d66b792f0c076a70"));
        remoteCopy.setWrapText(true); HBox remoteActions = new HBox(7.0, remoteRoot, addRemoteRoot, removeRemoteRoot);
        HBox.setHgrow(remoteRoot, Priority.ALWAYS);
        VBox remotePage = subPage(remoteCopy, remoteRoots, remoteActions);

        CheckBox mcpEnabled = new CheckBox(com.ynpe.fastremote.i18n.Texts.text("legacy.java.4f16408e31f7f657")); mcpEnabled.setSelected(initial.mcpServer().enabled());
        CheckBox mcpAllowRemote = new CheckBox(com.ynpe.fastremote.i18n.Texts.text("ai.mcp.allowRemoteAccess"));
        mcpAllowRemote.setSelected(initial.mcpServer().allowRemoteAccess());
        Spinner<Integer> mcpPort = new Spinner<>(1024, 65535, initial.mcpServer().port()); mcpPort.setEditable(true);
        SingleLineInputEnterSupport.commitSpinnerOnEnter(mcpPort);
        Label mcpRuntimeStatus = new Label();
        mcpRuntimeStatus.setWrapText(true); mcpRuntimeStatus.setMinWidth(0.0);
        mcpRuntimeStatus.setMaxWidth(Double.MAX_VALUE);
        Runnable refreshMcpRuntimeStatus = () -> workflow.loadMcpServerStatus().whenComplete((runtime, error) ->
                Platform.runLater(() -> updateMcpRuntimeStatus(mcpRuntimeStatus, runtime, error)));
        refreshMcpRuntimeStatus.run();
        TextField mcpKey = new TextField(); mcpKey.setEditable(false); mcpKey.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.c31142b3725a79fa"));
        AtomicLong mcpKeyRequest = new AtomicLong();
        Button copyKey = new Button(com.ynpe.fastremote.i18n.Texts.text("legacy.java.63d90d977348ab1f")); copyKey.setDisable(true);
        Button regenerateKey = new Button(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3221a042ea3677a8"));
        AtomicBoolean regeneratingKey = new AtomicBoolean();
        copyKey.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent(); content.putString(mcpKey.getText());
            Clipboard.getSystemClipboard().setContent(content); status.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.c4c107a2f207786c"));
        });
        regenerateKey.setOnAction(event -> {
            if (!regeneratingKey.compareAndSet(false, true)) return;
            long request = mcpKeyRequest.incrementAndGet();
            workflow.regenerateMcpToken().whenComplete((result, error) -> Platform.runLater(() -> {
                regeneratingKey.set(false);
                refreshMcpRuntimeStatus.run();
                if (error != null) {
                    failure.accept(message(error));
                    long recoveryRequest = mcpKeyRequest.incrementAndGet();
                    workflow.loadMcpToken().whenComplete((key, recoveryError) -> Platform.runLater(() -> {
                        if (recoveryError == null && isCurrentMcpKeyRequest(recoveryRequest, mcpKeyRequest.get())) {
                            mcpKey.setText(mcpAuthorizationValue(key)); copyKey.setDisable(false);
                        }
                    }));
                    return;
                }
                current.set(result.settings()); saved.accept(result.settings());
                changed.get().run();
                if (isCurrentMcpKeyRequest(request, mcpKeyRequest.get())) {
                    mcpKey.setText(mcpAuthorizationValue(result.plaintext())); copyKey.setDisable(false);
                }
                status.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.40a81f54bc31e46b"));
            }));
        });
        long initialKeyRequest = mcpKeyRequest.incrementAndGet();
        workflow.loadMcpToken().whenComplete((key, error) -> Platform.runLater(() -> {
            if (error == null && isCurrentMcpKeyRequest(initialKeyRequest, mcpKeyRequest.get())) {
                mcpKey.setText(mcpAuthorizationValue(key)); copyKey.setDisable(false);
            }
        }));
        GridPane mcpGrid = new GridPane(); mcpGrid.setHgap(10); mcpGrid.setVgap(9);
        ColumnConstraints mcpLabelColumn = new ColumnConstraints(96.0, 96.0, 96.0);
        ColumnConstraints mcpValueColumn = new ColumnConstraints(); mcpValueColumn.setHgrow(Priority.ALWAYS);
        mcpGrid.getColumnConstraints().setAll(mcpLabelColumn, mcpValueColumn);
        mcpGrid.addRow(0, new Label(com.ynpe.fastremote.i18n.Texts.text("ai.mcp.runtimeStatus")), mcpRuntimeStatus);
        mcpGrid.addRow(1, new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.6320b4a8722a851f")), mcpEnabled);
        mcpGrid.addRow(2, new Label(com.ynpe.fastremote.i18n.Texts.text("ai.mcp.networkAccess")), mcpAllowRemote);
        mcpGrid.addRow(3, new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e71ac32b544b0ebf")), mcpPort);
        HBox mcpKeyRow = mcpKeyRow(mcpKey, copyKey, regenerateKey);
        mcpGrid.add(new Label("Authorization"), 0, 4); mcpGrid.add(mcpKeyRow, 1, 4);
        GridPane.setHgrow(mcpKeyRow, Priority.ALWAYS);
        Label mcpHelp = new Label(mcpNetworkHelp(mcpAllowRemote.isSelected()));
        mcpHelp.setWrapText(true); mcpHelp.setMinWidth(0.0); mcpHelp.setMaxWidth(Double.MAX_VALUE);
        Label remoteAccessRisk = new Label(com.ynpe.fastremote.i18n.Texts.text("ai.mcp.remoteAccessRisk"));
        remoteAccessRisk.setWrapText(true);
        remoteAccessRisk.setMinWidth(0.0);
        remoteAccessRisk.setMaxWidth(Double.MAX_VALUE);
        remoteAccessRisk.getStyleClass().add("settings-security-note");
        remoteAccessRisk.visibleProperty().bind(mcpAllowRemote.selectedProperty());
        remoteAccessRisk.managedProperty().bind(remoteAccessRisk.visibleProperty());
        mcpAllowRemote.selectedProperty().addListener((ignored, previous, selected) ->
                mcpHelp.setText(mcpNetworkHelp(selected)));
        ComboBox<McpClient> mcpClient = new ComboBox<>();
        mcpClient.getItems().setAll(McpClient.values());
        mcpClient.setValue(McpClient.CODEX);
        TextArea mcpClientConfig = new TextArea();
        mcpClientConfig.setEditable(false);
        mcpClientConfig.setWrapText(true);
        mcpClientConfig.setMinWidth(0.0);
        mcpClientConfig.setMaxWidth(Double.MAX_VALUE);
        mcpClientConfig.setPrefRowCount(12);
        mcpClientConfig.setMaxWidth(Double.MAX_VALUE);
        Button copyMcpConfig = new Button(com.ynpe.fastremote.i18n.Texts.text("ai.mcp.copyConfiguration"));
        Runnable refreshMcpClientConfig = () -> {
            mcpClientConfig.setText(mcpClientConfiguration(
                    mcpClient.getValue(), mcpPort.getValue(), mcpKey.getText()));
            mcpClientConfig.positionCaret(0);
            mcpClientConfig.setScrollTop(0.0);
            mcpClientConfig.setScrollLeft(0.0);
        };
        mcpClient.valueProperty().addListener((ignored, previous, value) -> refreshMcpClientConfig.run());
        mcpPort.valueProperty().addListener((ignored, previous, value) -> refreshMcpClientConfig.run());
        mcpKey.textProperty().addListener((ignored, previous, value) -> refreshMcpClientConfig.run());
        copyMcpConfig.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(mcpClientConfig.getText());
            Clipboard.getSystemClipboard().setContent(content);
            status.setText(com.ynpe.fastremote.i18n.Texts.text("ai.mcp.configurationCopied"));
        });
        refreshMcpClientConfig.run();
        Label clientHelp = new Label(com.ynpe.fastremote.i18n.Texts.text("ai.mcp.clientHelp"));
        clientHelp.setWrapText(true); clientHelp.setMinWidth(0.0); clientHelp.setMaxWidth(Double.MAX_VALUE);
        Label clientHeading = new Label(com.ynpe.fastremote.i18n.Texts.text("ai.mcp.clientConfiguration"));
        clientHeading.getStyleClass().add("settings-section-title");
        FlowPane clientActions = new FlowPane(Orientation.HORIZONTAL, 7.0, 7.0, mcpClient, copyMcpConfig);
        clientActions.setMinWidth(0.0);
        VBox mcpPage = subPage(mcpGrid, mcpHelp, remoteAccessRisk, clientHeading, clientHelp,
                clientActions, mcpClientConfig);
        mcpPage.setMinWidth(0.0);

        Supplier<AiSettings> selectedSettings = () -> {
            try {
                AiSettings base = current.get();
                McpServerSettings mcp = new McpServerSettings(
                        mcpEnabled.isSelected(), mcpAllowRemote.isSelected(), mcpPort.getValue(),
                        base.mcpServer().tokenHash(), base.mcpServer().tokenSecretId());
                return new AiSettings(base.providers(), base.selectedProviderId(),
                        permissions(builtInFields), permissions(mcpFields), List.copyOf(roots.getItems()),
                        List.copyOf(remoteRoots.getItems()),
                        maxTransferMiB.getValue() * AiSettings.MEBIBYTE, maxToolRounds.getValue(), mcp);
            } catch (RuntimeException invalid) { throw invalid; }
        };
        builtInFields.values().forEach(field -> field.valueProperty().addListener(
                (ignored, previous, value) -> changed.get().run()));
        mcpFields.values().forEach(field -> field.valueProperty().addListener(
                (ignored, previous, value) -> changed.get().run()));
        roots.getItems().addListener((javafx.collections.ListChangeListener<Path>) change -> changed.get().run());
        remoteRoots.getItems().addListener((javafx.collections.ListChangeListener<String>) change -> changed.get().run());
        maxToolRounds.valueProperty().addListener((ignored, previous, value) -> changed.get().run());
        maxToolRounds.getEditor().textProperty().addListener((ignored, previous, value) -> changed.get().run());
        maxTransferMiB.valueProperty().addListener((ignored, previous, value) -> changed.get().run());
        maxTransferMiB.getEditor().textProperty().addListener((ignored, previous, value) -> changed.get().run());
        mcpEnabled.selectedProperty().addListener((ignored, previous, value) -> changed.get().run());
        mcpAllowRemote.selectedProperty().addListener((ignored, previous, value) -> changed.get().run());
        mcpPort.valueProperty().addListener((ignored, previous, value) -> changed.get().run());
        mcpPort.getEditor().textProperty().addListener((ignored, previous, value) -> changed.get().run());
        Label heading = new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e2eb47024664bec8")); heading.getStyleClass().add("settings-title");
        Label copy = new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.0969111dd27dddb6"));
        copy.setWrapText(true); copy.getStyleClass().add("settings-subtitle");
        VBox content = new VBox(12.0, heading, copy,
                card(com.ynpe.fastremote.i18n.Texts.text("ai.chatgpt.title"),
                        com.ynpe.fastremote.i18n.Texts.text("ai.chatgpt.description"), chatGptPage),
                card(com.ynpe.fastremote.i18n.Texts.text("legacy.java.43de42b3b4b7260e"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.62cc50688fa9dfd5"), providerPage),
                card(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d3e0d83a0d31347b"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.10dd6b5f872a775f"), executionPage),
                card(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3ec27241b09d1993"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.216096105347eed7"), permissionPage),
                card(com.ynpe.fastremote.i18n.Texts.text("legacy.java.40084744bf5feb11"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.7defc722d3377a4a"), localPage),
                card(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b58ec18984353b1d"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.98df17db4bebddf4"), remotePage),
                card(com.ynpe.fastremote.i18n.Texts.text("legacy.java.6b354993357190fe"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.0ea03f4ab0b5c057"), mcpPage));
        content.setPadding(new Insets(20.0, 24.0, 20.0, 24.0));
        content.setMinWidth(0.0);
        ScrollPane scroll = new ScrollPane(content); scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); scroll.getStyleClass().add("settings-scroll");
        VBox host = new VBox(scroll); VBox.setVgrow(scroll, Priority.ALWAYS);
        return new Editor(host, current, selectedSettings, workflow, saved, changed,
                refreshMcpRuntimeStatus);
    }

    static void bindModelDiscoveryAvailability(Button button, ListView<?> providers) {
        button.disableProperty().bind(providers.getSelectionModel().selectedItemProperty().isNull());
    }

    static void updateMcpRuntimeStatus(Label label, AiAssistantWorkflow.McpServerStatus runtime, Throwable error) {
        label.getStyleClass().removeIf(style -> style.startsWith("mcp-runtime-status-"));
        if (!label.getStyleClass().contains("mcp-runtime-status")) {
            label.getStyleClass().add("mcp-runtime-status");
        }
        if (error != null || runtime == null) {
            String detail = error == null ? com.ynpe.fastremote.i18n.Texts.text("ai.mcp.statusUnknownFailure")
                    : message(error);
            label.setText(com.ynpe.fastremote.i18n.Texts.text("ai.mcp.statusUnavailable", detail));
            label.getStyleClass().add("mcp-runtime-status-failed");
            return;
        }
        switch (runtime.state()) {
            case STARTING -> {
                label.setText(com.ynpe.fastremote.i18n.Texts.text("ai.mcp.statusStarting"));
                label.getStyleClass().add("mcp-runtime-status-starting");
            }
            case RUNNING -> {
                String endpoint = "http://127.0.0.1:" + runtime.port().orElseThrow() + "/mcp";
                label.setText(com.ynpe.fastremote.i18n.Texts.text("ai.mcp.statusRunning", endpoint));
                label.getStyleClass().add("mcp-runtime-status-running");
            }
            case STOPPED -> {
                label.setText(com.ynpe.fastremote.i18n.Texts.text("ai.mcp.statusStopped"));
                label.getStyleClass().add("mcp-runtime-status-stopped");
            }
            case FAILED -> {
                label.setText(com.ynpe.fastremote.i18n.Texts.text("ai.mcp.statusFailed",
                        runtime.error().orElse(com.ynpe.fastremote.i18n.Texts.text("ai.mcp.statusUnknownFailure"))));
                label.getStyleClass().add("mcp-runtime-status-failed");
            }
        }
    }

    private static void editProvider(Window owner, AiAssistantWorkflow workflow, ModelProviderConfig existing,
            Runnable saved, Consumer<ModelProviderConfig> discover, Consumer<String> failure) {
        Dialog<ProviderInput> dialog = createProviderDialog(existing);
        dialog.initOwner(owner);
        dialog.showAndWait().ifPresent(input -> {
            try (input) {
                String id = existing == null ? UUID.randomUUID().toString() : existing.id();
                ModelProviderConfig provider = new ModelProviderConfig(id, input.name(), input.type(), providerUri(input.type(), input.url()),
                        input.model(), existing == null ? Optional.empty() : existing.apiKeySecretId(), input.insecure());
                Optional<char[]> key = input.apiKey().length == 0 ? Optional.empty() : Optional.of(input.copyApiKey());
                workflow.saveProvider(provider, key).whenComplete((result, error) -> Platform.runLater(() -> {
                    if (error != null) failure.accept(message(error));
                    else { saved.run(); discover.accept(result); }
                }));
            } catch (RuntimeException invalid) { failure.accept(message(invalid)); }
        });
    }

    static Dialog<ProviderInput> createProviderDialog(ModelProviderConfig existing) {
        Dialog<ProviderInput> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.c759cb13c41ec043") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.e9ed7c3cce82acd0"));
        dialog.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.72a66e6ee275fce0")); DialogSpacing.prepare(dialog);
        ButtonType accept = new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a3030bf8f16dc63c"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, accept);
        TextField name = new TextField(existing == null ? "" : existing.name());
        ComboBox<ModelProviderType> type = new ComboBox<>(); type.getItems().setAll(
                ModelProviderType.OPENAI_RESPONSES, ModelProviderType.OPENAI_COMPATIBLE,
                ModelProviderType.QWEN_DASHSCOPE, ModelProviderType.KIMI_MOONSHOT, ModelProviderType.GEMINI_API, ModelProviderType.OLLAMA);
        type.setCellFactory(ignored -> typeCell()); type.setButtonCell(typeCell());
        type.setValue(existing == null ? ModelProviderType.OLLAMA : existing.type());
        TextField url = new TextField(existing == null ? defaultProviderUrl(type.getValue()) : existing.baseUri().toString());
        url.promptTextProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
                () -> type.getValue() == ModelProviderType.QWEN_DASHSCOPE
                        ? "ws-….cn-beijing.maas.aliyuncs.com" : "", type.valueProperty()));
        type.valueProperty().addListener((ignored, previous, selected) -> {
            if (existing == null && (url.getText().isBlank() || url.getText().equals(defaultProviderUrl(previous)))) {
                url.setText(defaultProviderUrl(selected));
            }
        });
        TextField model = new TextField(existing == null ? "" : existing.model()); model.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.fffb2cd076731510"));
        PasswordField apiKey = new PasswordField();
        apiKey.setPromptText(existing != null && existing.apiKeySecretId().isPresent() ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.4f7154265708da53") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.14e15076c8dc219e"));
        CheckBox insecure = new CheckBox(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f504b74fe10e4059"));
        insecure.setSelected(existing != null && existing.allowInsecureHttp());
        GridPane grid = new GridPane(); grid.setHgap(12); grid.setVgap(9);
        Label nameLabel = providerFieldLabel(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d44e9b3d3b31d37b"));
        Label typeLabel = providerFieldLabel(com.ynpe.fastremote.i18n.Texts.text("legacy.java.ba40014ff496f64e"));
        Label urlLabel = providerFieldLabel(com.ynpe.fastremote.i18n.Texts.text("legacy.java.37aac5b4e639b93b"));
        urlLabel.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
                () -> type.getValue() == ModelProviderType.QWEN_DASHSCOPE ? "API Host"
                        : com.ynpe.fastremote.i18n.Texts.text("legacy.java.37aac5b4e639b93b"), type.valueProperty()));
        Label modelLabel = providerFieldLabel(com.ynpe.fastremote.i18n.Texts.text("legacy.java.1e2933c6b813322a"));
        Label apiKeyLabel = providerFieldLabel("API Key");
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(Region.USE_PREF_SIZE);
        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().setAll(labelColumn, fieldColumn);
        grid.addRow(0, nameLabel, name); grid.addRow(1, typeLabel, type);
        grid.addRow(2, urlLabel, url); grid.addRow(3, modelLabel, model);
        grid.addRow(4, apiKeyLabel, apiKey); grid.add(insecure, 1, 5);
        Label providerHelp = new Label();
        providerHelp.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
                () -> providerHelp(type.getValue()), type.valueProperty()));
        DialogSpacing.configureWrappingLabel(providerHelp);
        providerHelp.visibleProperty().bind(providerHelp.textProperty().isNotEmpty());
        providerHelp.managedProperty().bind(providerHelp.visibleProperty());
        grid.add(providerHelp, 0, 6, 2, 1);
        for (Node field : List.of(name, type, url, model, apiKey)) {
            GridPane.setHgrow(field, Priority.ALWAYS);
            if (field instanceof Region region) region.setMaxWidth(Double.MAX_VALUE);
        }
        dialog.getDialogPane().setContent(grid);
        configureProviderDialogLayout(dialog, providerHelp);
        DialogSpacing.focusFirstInputOnShown(dialog);
        dialog.setResultConverter(button -> button == accept ? new ProviderInput(name.getText(), type.getValue(),
                url.getText(), model.getText(), apiKey.getText().toCharArray(), insecure.isSelected()) : null);
        return dialog;
    }

    static void configureProviderDialogLayout(Dialog<?> dialog, Label help) {
        dialog.setResizable(true);
        DialogSpacing.prepare(dialog, 640.0);
        DialogSpacing.configureInitialWindowWidth(dialog, 680.0, 560.0);
        DialogSpacing.configureWrappingLabel(help);
        help.textProperty().addListener((observable, previous, current) -> DialogSpacing.growHeightToContent(dialog));
    }

    static boolean isProviderEditGesture(javafx.scene.input.MouseButton button, int clickCount, boolean hasSelection) {
        return button == javafx.scene.input.MouseButton.PRIMARY && clickCount == 2 && hasSelection;
    }

    static Label providerFieldLabel(String text) {
        Label label = new Label(text);
        label.setMinWidth(providerLabelMinimumWidth());
        return label;
    }

    static double providerLabelMinimumWidth() {
        return Region.USE_PREF_SIZE;
    }

    static HBox mcpKeyRow(TextField key, Button copy, Button regenerate) {
        key.setMinWidth(0.0);
        key.setMaxWidth(Double.MAX_VALUE);
        copy.setMinWidth(Region.USE_PREF_SIZE);
        regenerate.setMinWidth(Region.USE_PREF_SIZE);
        HBox row = new HBox(7.0, key, copy, regenerate);
        row.setMinWidth(0.0);
        row.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(key, Priority.ALWAYS);
        return row;
    }

    static boolean isCurrentMcpKeyRequest(long request, long current) {
        return request == current;
    }

    static String mcpNetworkHelp(boolean allowRemoteAccess) {
        return com.ynpe.fastremote.i18n.Texts.text(allowRemoteAccess
                ? "ai.mcp.remoteAccessHelp" : "legacy.java.82cf3fdb01d425a5");
    }

    static String mcpClientConfiguration(McpClient client, int port, String token) {
        String endpoint = "http://127.0.0.1:" + port + "/mcp";
        String safeToken = token == null || token.isBlank() ? "<FastRemote Key>" : token;
        String authorization = mcpAuthorizationValue(safeToken);
        return switch (client) {
            case CODEX -> """
                    %s
                      %s: Streamable HTTP
                      URL: %s
                      %s: %s
                      %s: Authorization
                      %s: %s

                    %s
                    [mcp_servers.fastremote]
                    url = \"%s\"
                    bearer_token_env_var = \"FASTREMOTE_MCP_TOKEN\"

                    %s
                    """.formatted(text("ai.mcp.guide.codexDirect"), text("ai.mcp.guide.type"), endpoint,
                    text("ai.mcp.guide.bearerEnvironment"), text("ai.mcp.guide.leaveBlank"),
                    text("ai.mcp.guide.headerName"), text("ai.mcp.guide.headerValue"), authorization,
                    text("ai.mcp.guide.codexSafer"), endpoint, text("ai.mcp.guide.codexRestart")).strip();
            case CLAUDE_CODE -> """
                    claude mcp add --transport http --scope user fastremote %s \\
                      --header \"Authorization: %s\"

                    %s: claude mcp get fastremote
                    """.formatted(endpoint, authorization, text("ai.mcp.guide.verifyWith")).strip();
            case CLAUDE_DESKTOP -> """
                    {
                      \"mcpServers\": {
                        \"fastremote\": {
                          \"command\": \"npx\",
                          \"args\": [
                            \"-y\",
                            \"mcp-remote\",
                            \"%s\",
                            \"--allow-http\",
                            \"--header\",
                            \"Authorization:${FASTREMOTE_AUTHORIZATION}\"
                          ],
                          \"env\": {
                            \"FASTREMOTE_AUTHORIZATION\": \"%s\"
                          }
                        }
                      }
                    }
                    """.formatted(endpoint, authorization).strip();
            case CURSOR -> """
                    {
                      \"mcpServers\": {
                        \"fastremote\": {
                          \"url\": \"%s\",
                          \"headers\": {
                            \"Authorization\": \"%s\"
                          }
                        }
                      }
                    }
                    """.formatted(endpoint, authorization).strip();
            case GENERIC -> """
                    %s: Streamable HTTP
                    URL: %s
                    %s: Authorization
                    %s: %s

                    %s:
                    Authorization: %s
                    """.formatted(text("ai.mcp.guide.transport"), endpoint, text("ai.mcp.guide.headerName"),
                    text("ai.mcp.guide.headerValue"), authorization, text("ai.mcp.guide.rawHeader"),
                    authorization).strip();
        };
    }

    private static String text(String key) { return com.ynpe.fastremote.i18n.Texts.text(key); }

    static String mcpAuthorizationValue(String token) {
        String safe = token == null ? "" : token.strip();
        return safe.regionMatches(true, 0, "Bearer ", 0, 7) ? "Bearer " + safe.substring(7).strip()
                : "Bearer " + safe;
    }

    static void installRemoteRootEnterHandler(TextField input, Runnable addRemoteRoot) {
        SingleLineInputEnterSupport.runOnEnter(input, addRemoteRoot);
    }

    private static ListCell<ModelProviderConfig> providerCell() {
        return new ListCell<>() { @Override protected void updateItem(ModelProviderConfig item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : item.name() + " · " + typeLabel(item.type())
                    + (item.model().isBlank() ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.e50b1d810f3438f2") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.c2c587ee8da5d876") + item.model()));
        }};
    }
    private static ListCell<ModelProviderType> typeCell() {
        return new ListCell<>() { @Override protected void updateItem(ModelProviderType item, boolean empty) {
            super.updateItem(item, empty); setText(empty || item == null ? null : typeLabel(item));
        }};
    }
    static String typeLabel(ModelProviderType value) { return switch (value) {
        case CHATGPT_CODEX -> "ChatGPT OAuth";
        case OPENAI_RESPONSES -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.9611598180366e41");
        case OPENAI_COMPATIBLE -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.f5c68c6f80e4c9d9");
        case QWEN_DASHSCOPE -> "Qwen / DashScope";
        case GEMINI_API -> "Google Gemini API";
        case KIMI_MOONSHOT -> com.ynpe.fastremote.i18n.Texts.text("ai.provider.kimiName");
        case OLLAMA -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.60fa74e27d07b2de");
    }; }
    static String defaultProviderUrl(ModelProviderType value) {
        return switch (value) {
            case CHATGPT_CODEX -> "https://chatgpt.com";
            case OPENAI_RESPONSES -> "https://api.openai.com/v1";
            case OPENAI_COMPATIBLE -> "";
            case QWEN_DASHSCOPE -> "";
            case KIMI_MOONSHOT -> "https://api.moonshot.cn/v1";
            case GEMINI_API -> "https://generativelanguage.googleapis.com/v1beta";
            case OLLAMA -> "http://127.0.0.1:11434/v1";
        };
    }
    static String providerHelp(ModelProviderType type) {
        return switch (type) {
            case QWEN_DASHSCOPE -> com.ynpe.fastremote.i18n.Texts.text("ai.provider.qwenHelp");
            case KIMI_MOONSHOT -> com.ynpe.fastremote.i18n.Texts.text("ai.provider.kimiHelp");
            case GEMINI_API -> com.ynpe.fastremote.i18n.Texts.text("ai.provider.geminiHelp");
            default -> "";
        };
    }
    static URI providerUri(ModelProviderType type, String value) {
        return type == ModelProviderType.QWEN_DASHSCOPE
                ? com.ynpe.fastremote.domain.ai.QwenEndpoint.parse(value) : URI.create(value);
    }
    private static ComboBox<AutomationPermission> permissionField(AutomationPermission selected) {
        ComboBox<AutomationPermission> field = new ComboBox<>(); field.getItems().setAll(AutomationPermission.values());
        field.setValue(selected); field.setCellFactory(ignored -> permissionCell()); field.setButtonCell(permissionCell());
        return field;
    }
    private static ListCell<AutomationPermission> permissionCell() {
        return new ListCell<>() { @Override protected void updateItem(AutomationPermission item, boolean empty) {
            super.updateItem(item, empty); setText(empty || item == null ? null : switch (item) {
                case DENY -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.b645c326ce3ecb93"); case ASK -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.afd3bad0c2201915"); case ALLOW -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.06adbde6b3cfd673");
            });
        }};
    }
    private static AutomationPermissions permissions(EnumMap<AutomationCapability, ComboBox<AutomationPermission>> fields) {
        EnumMap<AutomationCapability, AutomationPermission> values = new EnumMap<>(AutomationCapability.class);
        fields.forEach((capability, field) -> values.put(capability, field.getValue()));
        return new AutomationPermissions(values);
    }
    static String capabilityLabel(AutomationCapability value) { return switch (value) {
        case CREATE_HOSTS -> com.ynpe.fastremote.i18n.Texts.text("automation.hosts.create");
        case DELETE_HOSTS -> com.ynpe.fastremote.i18n.Texts.text("automation.hosts.delete");
        case EDIT_HOSTS -> com.ynpe.fastremote.i18n.Texts.text("automation.hosts.edit");
        case READ_HOST_METADATA -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.79a861c02d4b59d7"); case OPEN_HOST -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.2da0b2a8111d771b");
        case READ_LIBRARY -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.41c5d82b298ac107"); case MANAGE_LIBRARY -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.ab7865c33894ee8a");
        case MANAGE_WORKSPACE -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.53e889a28526b777");
        case READ_WORKSPACE -> com.ynpe.fastremote.i18n.Texts.text("automation.capability.readWorkspace");
        case EDIT_WORKSPACE -> com.ynpe.fastremote.i18n.Texts.text("automation.capability.editWorkspace");
        case READ_WORKSPACE_LOG -> com.ynpe.fastremote.i18n.Texts.text("automation.capability.readWorkspaceLog");
        case READ_TERMINAL_OUTPUT -> com.ynpe.fastremote.i18n.Texts.text("automation.capability.readTerminalOutput");
        case IMPORT_EXPORT -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.31a048a499dd1894");
        case READ_LOCAL -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.169a1f46a40f23dd"); case CREATE_LOCAL -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.bd1bd87d3f6eda2d");
        case EDIT_LOCAL -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.1a2cfaf59b236621"); case DELETE_LOCAL -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.467845c9b42cbe1e");
        case RENAME_LOCAL -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.185e41a99067bae6");
        case READ_REMOTE -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.6d7d0bc17205eb79"); case CREATE_REMOTE -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.2fed2f066b532974");
        case EDIT_REMOTE -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.69b9815a105395af"); case DELETE_REMOTE -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.5fa851c82456021a");
        case RENAME_REMOTE -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.b202cf5063131a85"); case RUN_COMMAND -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.be12f84c407f5a53");
        case UPLOAD -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.9e07e3c0532d4976"); case DOWNLOAD -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.4673a23061656125");
    }; }
    private static VBox subPage(Node... nodes) {
        VBox page = new VBox(10.0, nodes);
        page.setMinWidth(0.0);
        return page;
    }
    private static VBox card(String title, String description, Node... nodes) {
        Label heading = new Label(title); heading.setMinWidth(0.0); heading.setMaxWidth(Double.MAX_VALUE);
        heading.getStyleClass().add("settings-card-title");
        Label copy = new Label(description); copy.setWrapText(true); copy.setMinWidth(0.0);
        copy.setMaxWidth(Double.MAX_VALUE); copy.getStyleClass().add("settings-card-copy");
        VBox card = new VBox(8.0, heading, copy, new javafx.scene.control.Separator());
        card.getChildren().addAll(nodes); card.setMinWidth(0.0); card.getStyleClass().add("settings-card"); return card;
    }
    private static String message(Throwable failure) {
        Throwable value = failure;
        while (value.getCause() != null && (value instanceof java.util.concurrent.CompletionException
                || value instanceof java.util.concurrent.ExecutionException)) value = value.getCause();
        return value.getMessage() == null ? value.getClass().getSimpleName()
                : com.ynpe.fastremote.i18n.Texts.translateExternalText(value.getMessage());
    }
    private static final class ProviderInput implements AutoCloseable {
        private final String name; private final ModelProviderType type; private final String url; private final String model;
        private char[] apiKey; private final boolean insecure;
        private ProviderInput(String name, ModelProviderType type, String url, String model, char[] apiKey, boolean insecure) {
            this.name = name; this.type = type; this.url = url; this.model = model; this.apiKey = apiKey; this.insecure = insecure;
        }
        String name() { return name; } ModelProviderType type() { return type; } String url() { return url; }
        String model() { return model; } char[] apiKey() { return apiKey; } boolean insecure() { return insecure; }
        char[] copyApiKey() { return apiKey.clone(); }
        @Override public void close() { Arrays.fill(apiKey, '\0'); apiKey = new char[0]; }
    }

    static final class Editor {
        private final Node node;
        private final AtomicReference<AiSettings> baseline;
        private final Supplier<AiSettings> selected;
        private final AiAssistantWorkflow workflow;
        private final Consumer<AiSettings> saved;
        private final AtomicReference<Runnable> changed;
        private final Runnable refreshMcpRuntimeStatus;

        private Editor(Node node, AtomicReference<AiSettings> baseline, Supplier<AiSettings> selected,
                AiAssistantWorkflow workflow, Consumer<AiSettings> saved, AtomicReference<Runnable> changed,
                Runnable refreshMcpRuntimeStatus) {
            this.node = node; this.baseline = baseline; this.selected = selected;
            this.workflow = workflow; this.saved = saved; this.changed = changed;
            this.refreshMcpRuntimeStatus = refreshMcpRuntimeStatus;
        }
        Node node() { return node; }
        boolean isDirty() {
            try { return !selected.get().equals(baseline.get()); }
            catch (RuntimeException invalid) { return true; }
        }
        java.util.concurrent.CompletionStage<Void> save() {
            AiSettings value;
            try { value = selected.get(); }
            catch (RuntimeException invalid) { return java.util.concurrent.CompletableFuture.failedFuture(invalid); }
            return workflow.saveSettings(value).thenAccept(result -> {
                baseline.set(result); Platform.runLater(() -> saved.accept(result));
            }).whenComplete((ignored, error) -> refreshMcpRuntimeStatus.run());
        }
        void onChanged(Runnable listener) { changed.set(listener); }
    }
}
