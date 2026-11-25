// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.ai.AiImageInput;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.ByteArrayOutputStream;
import com.ynpe.fastremote.domain.ai.AiReasoningEffort;
import com.ynpe.fastremote.domain.ai.ModelProviderConfig;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/** Owns the static AI assistant FXML and forwards actions to its feature coordinator. */
public final class AiAssistantController {
    interface Actions {
        void togglePlacement();
        void createChat();
        void configure();
        void sendOrInterrupt();
        default void stop() { }
        default void steer() { }
        default void resumeQueue() { }
        default void editQueued(java.util.UUID id, String text) { }
        default void deleteQueued(java.util.UUID id) { }
        default void steerQueued(java.util.UUID id) { }
        void imageStateChanged();
        void imageError(String message);
        default void sessionsCollapsedChanged(boolean collapsed) { }
        default void selectChat(java.util.UUID id) { }
        default void associateHost(java.util.Optional<com.ynpe.fastremote.domain.ai.AiHostAssociation> host) { }

    }

    @FXML private javafx.scene.control.SplitPane aiChatSplit;
    private boolean sidebarWidthManuallyAdjusted;
    private boolean sessionsCollapsed;
    @FXML private javafx.scene.layout.VBox aiChatMain;
    @FXML private HBox aiCollapsedToolbar;
    @FXML private javafx.scene.layout.VBox aiBatchFooter;
    private AiSessionListController sessionManagement;
    @FXML private javafx.scene.control.TextField aiSessionSearch;
    private java.util.UUID activeSessionId;
    private double expandedDividerOffset = 210;
    @FXML private javafx.scene.layout.VBox aiConversationSidebar;
    @FXML private Button aiToggleSessionsButton;
    @FXML private ComboBox<ModelProviderConfig> aiProviderField;
    @FXML private ComboBox<String> aiModelField;
    @FXML private ComboBox<AiReasoningEffort> aiReasoningField;
    @FXML private CheckBox aiReasoningSummaryField;
    @FXML private StackPane aiConversationHost;
    @FXML private TextArea aiPromptField;
    @FXML private Region aiPromptResizeHandle;
    @FXML private Button aiSendButton;
    @FXML private Button aiStopButton;
    @FXML private javafx.scene.control.MenuButton aiSendOptions;
    @FXML private javafx.scene.layout.VBox aiPendingPane;
    @FXML private javafx.scene.control.ScrollPane aiPendingScroll;
    @FXML private LucideIconView aiSendIcon;
    @FXML private Tooltip aiSendTooltip;
    @FXML private Button aiNewChatButton;
    @FXML private javafx.scene.control.ListView<com.ynpe.fastremote.domain.ai.AiChatSession> aiSessionList;
    @FXML private Button aiPlacementButton;
    @FXML private LucideIconView aiPlacementIcon;
    @FXML private Tooltip aiPlacementTooltip;
    @FXML private Label aiChatTitleLabel;
    @FXML private FlowPane aiImagePreviewPane;

    @FXML private javafx.scene.control.MenuButton aiAssociatedHostField;
    private AiHostAssociationSelector hostSelector;
    private Actions actions;
    private final List<StagedImage> stagedImages = new ArrayList<>();
    private final ExecutorService imageEncoder = Executors.newSingleThreadExecutor(runnable ->
            Thread.ofVirtual().name("fastremote-ai-image-encoder").unstarted(runnable));
    private final AtomicBoolean closed = new AtomicBoolean();
    private boolean imagesAllowed;
    private long imageGeneration;

    @FXML private void initialize() {
        aiChatSplit.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != javafx.scene.input.MouseButton.PRIMARY) return;
            for (javafx.scene.Node node = event.getTarget() instanceof javafx.scene.Node target ? target : null;
                    node != null && node != aiChatSplit; node = node.getParent()) {
                if (node.getStyleClass().contains("split-pane-divider") && node.getParent() == aiChatSplit) {
                    sidebarWidthManuallyAdjusted = true;
                    break;
                }
            }
        });
        aiChatSplit.widthProperty().addListener((ignored, previous, width) -> {
            if (!sessionsCollapsed && !sidebarWidthManuallyAdjusted && width.doubleValue() > 0) {
                aiChatSplit.setDividerPositions(Math.min(260.0, width.doubleValue() * .25) / width.doubleValue());
            }
        });
        hostSelector = new AiHostAssociationSelector(aiAssociatedHostField, host -> actions().associateHost(host));
        aiSessionList.getSelectionModel().selectedItemProperty().addListener((ignored, previous, selected) -> {
            if (selected != null && (sessionManagement == null || !sessionManagement.batch())) actions().selectChat(selected.id());
        });
        AiPromptResizeSupport.install(aiPromptField, aiPromptResizeHandle);
        aiPromptField.addEventFilter(KeyEvent.KEY_PRESSED, this::handlePaste);
    }

    void bind(Actions actions) {
        if (this.actions != null) throw new IllegalStateException("AI assistant actions are already bound");
        this.actions = Objects.requireNonNull(actions, "actions");
    }

    void updateAssociatedHosts(List<com.ynpe.fastremote.domain.catalog.SavedHost> hosts,
            List<com.ynpe.fastremote.domain.catalog.HostGroup> groups,
            List<com.ynpe.fastremote.domain.ai.AiHostAssociation> quick,
            java.util.Optional<com.ynpe.fastremote.domain.ai.AiHostAssociation> selected) {
        hostSelector.update(hosts, groups, quick, selected);
    }
    void setAssociatedHostDisabled(boolean disabled) { aiAssociatedHostField.setDisable(disabled); }

    void setDialogPresentation(boolean dialog) {
        aiPlacementButton.setManaged(!dialog);
        aiPlacementButton.setVisible(!dialog);
    }

    @FXML private void togglePlacement() { actions().togglePlacement(); }
    @FXML private void toggleSessions() {
        setSessionsCollapsed(!sessionsCollapsed);
        actions().sessionsCollapsedChanged(sessionsCollapsed);
    }

    void setSessionsCollapsed(boolean collapsed) {
        if (sessionsCollapsed == collapsed) return;
        // Move focus before removing the focused sidebar/toggle subtree. Floating docks
        // otherwise interpret the transient null focus as a click outside and close.
        aiPromptField.requestFocus();
        if (!sessionsCollapsed && aiConversationSidebar.getWidth() > 0 && aiChatSplit.getWidth() > 0) {
            expandedDividerOffset = aiChatSplit.getDividerPositions()[0] * aiChatSplit.getWidth();
        }
        sessionsCollapsed = collapsed;
        aiCollapsedToolbar.setVisible(sessionsCollapsed);
        aiCollapsedToolbar.setManaged(sessionsCollapsed);
        if (sessionsCollapsed) {
            if (sessionManagement != null) sessionManagement.exitBatch();
            aiChatSplit.getItems().remove(aiConversationSidebar);
        } else {
            aiChatSplit.getItems().addFirst(aiConversationSidebar);
            if (aiChatSplit.getWidth() > 0) aiChatSplit.setDividerPositions(expandedDividerOffset / aiChatSplit.getWidth());
        }
        aiSessionList.refresh();
    }

    void collapseSessions() {
        setSessionsCollapsed(true);
    }

    Button toggleSessionsButton() { return aiToggleSessionsButton; }
    boolean sessionsCollapsed() { return sessionsCollapsed; }
    boolean batchSessions() { return sessionManagement != null && sessionManagement.batch(); }

    @FXML private void toggleSessionSearch() {
        boolean visible = !aiSessionSearch.isVisible();
        aiSessionSearch.setVisible(visible); aiSessionSearch.setManaged(visible);
        if (visible) aiSessionSearch.requestFocus(); else aiSessionSearch.clear();
    }

    @FXML private void createChat() { actions().createChat(); }
    @FXML private void configure() { actions().configure(); }
    @FXML private void stop() { actions().stop(); }
    @FXML private void steer() { actions().steer(); }
    void updateExecutionControls(boolean running, boolean stopping, boolean sendDisabled, AiPromptQueue queue) {
        aiStopButton.setVisible(running); aiStopButton.setManaged(running); aiStopButton.setDisable(stopping);
        aiSendOptions.setVisible(running); aiSendOptions.setManaged(running); aiSendOptions.setDisable(sendDisabled);
        aiPendingPane.getChildren().clear();
        boolean visible = queue != null && !queue.isEmpty();
        aiPendingScroll.setVisible(visible); aiPendingScroll.setManaged(visible);
        aiPendingScroll.setPrefViewportHeight(queue == null ? 0 : Math.min(132, 32 * (queue.size() + (queue.paused && !running ? 1 : 0))));
        if (!visible) return;
        if (queue.paused && !running) {
            Button resume = new Button(com.ynpe.fastremote.i18n.Texts.text("ai.queue.resume"));
            resume.setOnAction(event -> actions().resumeQueue()); aiPendingPane.getChildren().add(resume);
        }
        for (var entry : queue.entries()) {
            if (entry.state() == AiPromptQueue.State.ACCEPTED) continue;
            String key = switch (entry.state()) {
                case QUEUED -> "ai.queue.queued";
                case WAITING -> "ai.queue.waiting";
                default -> "ai.queue.uncertain";
            };
            Label text = new Label(com.ynpe.fastremote.i18n.Texts.text(key) + " · "
                    + entry.text().replace('\n', ' ') + (entry.images().isEmpty() ? "" : " [" + entry.images().size() + "]"));
            text.setMaxWidth(Double.MAX_VALUE); text.setMinWidth(40); HBox.setHgrow(text, javafx.scene.layout.Priority.ALWAYS);
            Button edit = new Button(com.ynpe.fastremote.i18n.Texts.text("ai.queue.edit"));
            edit.setMinWidth(Region.USE_PREF_SIZE);
            edit.setDisable(entry.state() == AiPromptQueue.State.WAITING);
            edit.setOnAction(event -> {
                var dialog = new javafx.scene.control.Dialog<String>();
                var editor = new TextArea(entry.text()); editor.setWrapText(true); editor.setPrefRowCount(5);
                editor.setPrefWidth(460);
                editor.setTextFormatter(new javafx.scene.control.TextFormatter<String>(
                        change -> change.getControlNewText().length() <= 512 * 1024 ? change : null));
                dialog.setTitle(com.ynpe.fastremote.i18n.Texts.text("ai.queue.edit")); dialog.setHeaderText(null);
                dialog.getDialogPane().setContent(editor);
                dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
                dialog.setResultConverter(button -> button == ButtonType.OK ? editor.getText() : null);
                DialogSpacing.prepare(dialog);
                if (aiPromptField.getScene() != null) dialog.initOwner(aiPromptField.getScene().getWindow());
                dialog.showAndWait().ifPresent(value -> actions().editQueued(entry.id(), value));
            });
            Button remove = new Button(com.ynpe.fastremote.i18n.Texts.text("ai.queue.delete"));
            remove.setMinWidth(Region.USE_PREF_SIZE);
            remove.setDisable(entry.state() == AiPromptQueue.State.WAITING);
            remove.setOnAction(event -> actions().deleteQueued(entry.id()));
            HBox row = new HBox(5, text, edit, remove);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            if (running && entry.state() == AiPromptQueue.State.QUEUED) {
                Button steer = new Button(com.ynpe.fastremote.i18n.Texts.text("ai.queue.steer"));
                steer.setMinWidth(Region.USE_PREF_SIZE);
                steer.setOnAction(event -> actions().steerQueued(entry.id())); row.getChildren().add(steer);
            }
            aiPendingPane.getChildren().add(row);
        }
    }

    @FXML private void sendOrInterrupt() { actions().sendOrInterrupt(); }

    private Actions actions() {
        if (actions == null) throw new IllegalStateException("AI assistant actions are not bound");
        return actions;
    }

    private void handlePaste(KeyEvent event) {
        if (event.getCode() != KeyCode.V || !event.isShortcutDown()) return;
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (!clipboard.hasImage()) return;
        event.consume();
        stageClipboardImage(clipboard.getImage());
    }

    void stageClipboardImage(Image source) {
        if (closed.get()) return;
        if (!imagesAllowed) {
            actions().imageError(com.ynpe.fastremote.i18n.Texts.text("ai.image.unsupportedModel"));
            return;
        }
        if (stagedImages.size() >= 4) {
            actions().imageError(com.ynpe.fastremote.i18n.Texts.text("ai.image.tooMany"));
            return;
        }
        int width = (int) source.getWidth();
        int height = (int) source.getHeight();
        if (width < 1 || height < 1 || (long) width * height > AiImageInput.MAX_PIXELS) {
            actions().imageError(com.ynpe.fastremote.i18n.Texts.text("ai.image.tooLarge"));
            return;
        }
        BufferedImage copy = SwingFXUtils.fromFXImage(source, null);
        long generation = imageGeneration;
        imageEncoder.execute(() -> encodePng(source, copy, width, height, generation));
    }

    private void encodePng(Image source, BufferedImage copy, int width, int height, long generation) {
        try {
            byte[] data = png(copy);
            double scale = Math.min(1.0, Math.min(320.0 / width, 200.0 / height));
            int thumbnailWidth = Math.max(1, (int) Math.round(width * scale));
            int thumbnailHeight = Math.max(1, (int) Math.round(height * scale));
            BufferedImage thumbnail = new BufferedImage(thumbnailWidth, thumbnailHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = thumbnail.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.drawImage(copy, 0, 0, thumbnailWidth, thumbnailHeight, null);
            } finally {
                graphics.dispose();
            }
            AiImageInput input = new AiImageInput("image/png", data, width, height, png(thumbnail));
            if (!closed.get()) Platform.runLater(() -> { if (generation == imageGeneration) addImage(source, input); });
        } catch (Exception failure) {
            Platform.runLater(() -> actions().imageError(com.ynpe.fastremote.i18n.Texts.text("ai.image.tooLarge")));
        }
    }

    private static byte[] png(BufferedImage image) throws java.io.IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) throw new IllegalStateException("PNG encoder unavailable");
            return output.toByteArray();
        }
    }

    private void addImage(Image source, AiImageInput input) {
        if (closed.get() || stagedImages.size() >= 4) return;
        StagedImage staged = new StagedImage(source, input);
        stagedImages.add(staged);
        ImageView preview = new ImageView(source);
        preview.setFitWidth(74);
        preview.setFitHeight(58);
        preview.setPreserveRatio(true);
        preview.getStyleClass().add("ai-image-preview");
        preview.setOnMouseClicked(ignored -> showOriginal(staged.source()));
        Button remove = new Button("×");
        remove.setAccessibleText(com.ynpe.fastremote.i18n.Texts.text("ai.image.remove"));
        remove.getStyleClass().add("ai-image-remove-button");
        HBox card = new HBox(3, preview, remove);
        card.getStyleClass().add("ai-image-preview-card");
        remove.setOnAction(ignored -> {
            stagedImages.remove(staged);
            aiImagePreviewPane.getChildren().remove(card);
            refreshPreviewVisibility();
        });
        aiImagePreviewPane.getChildren().add(card);
        refreshPreviewVisibility();
    }

    private void showOriginal(Image image) {
        Dialog<Void> dialog = originalImageDialog(image);
        if (aiPromptField.getScene() != null) dialog.initOwner(aiPromptField.getScene().getWindow());
        dialog.show();
    }

    static Dialog<Void> originalImageDialog(Image image) {
        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        view.setFitWidth(Math.min(image.getWidth(), 1100));
        view.setFitHeight(Math.min(image.getHeight(), 760));
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(com.ynpe.fastremote.i18n.Texts.text("ai.image.original"));
        dialog.setResizable(false);
        DialogSpacing.prepare(dialog);
        dialog.getDialogPane().setContent(view);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        return dialog;
    }

    void showImage(AiImageInput image) {
        showOriginal(new Image(new java.io.ByteArrayInputStream(image.data())));
    }

    private void refreshPreviewVisibility() {
        boolean visible = !stagedImages.isEmpty();
        aiImagePreviewPane.setManaged(visible);
        aiImagePreviewPane.setVisible(visible);
        actions().imageStateChanged();
    }

    void setImagesAllowed(boolean imagesAllowed) { this.imagesAllowed = imagesAllowed; }
    boolean hasImages() { return !stagedImages.isEmpty(); }
    List<AiImageInput> images() { return stagedImages.stream().map(StagedImage::input).toList(); }
    void restoreImages(List<AiImageInput> images) {
        clearImages();
        for (var input : images) addImage(new Image(new java.io.ByteArrayInputStream(input.data())), input);
    }
    List<AiImageInput> takeImages() {
        List<AiImageInput> result = stagedImages.stream().map(StagedImage::input).toList();
        clearImages();
        return result;
    }
    void clearImages() {
        imageGeneration++;
        stagedImages.clear();
        aiImagePreviewPane.getChildren().clear();
        refreshPreviewVisibility();
    }
    void close() {
        if (!closed.compareAndSet(false, true)) return;
        imageEncoder.shutdownNow();
        clearImages();
    }

    private record StagedImage(Image source, AiImageInput input) { }

    ComboBox<ModelProviderConfig> providerField() { return aiProviderField; }
    ComboBox<String> modelField() { return aiModelField; }
    ComboBox<AiReasoningEffort> reasoningField() { return aiReasoningField; }
    CheckBox reasoningSummaryField() { return aiReasoningSummaryField; }
    StackPane conversationHost() { return aiConversationHost; }
    TextArea promptField() { return aiPromptField; }
    Region promptResizeHandle() { return aiPromptResizeHandle; }
    Button sendButton() { return aiSendButton; }
    LucideIconView sendIcon() { return aiSendIcon; }
    Tooltip sendTooltip() { return aiSendTooltip; }
    Button newChatButton() { return aiNewChatButton; }
    void configureSessionManagement(AiSessionListController.Actions actions) {
        sessionManagement = new AiSessionListController(aiSessionList, aiBatchFooter, actions);
        aiSessionSearch.textProperty().addListener((ignored, previous, text) -> sessionManagement.filter(text));
    }

    void updateSessions(List<com.ynpe.fastremote.domain.ai.AiChatSession> sessions, java.util.UUID activeId) {
        boolean changed = !Objects.equals(activeSessionId, activeId);
        activeSessionId = activeId;
        updateSessions(sessions);
        if (sessionManagement != null) sessionManagement.update(activeId);
        else if (changed) aiSessionList.refresh();
    }

    void updateSessions(List<com.ynpe.fastremote.domain.ai.AiChatSession> sessions) {
        if (sessionManagement != null) sessionManagement.sessions(sessions, activeSessionId);
        else if (!aiSessionList.getItems().equals(sessions)) aiSessionList.getItems().setAll(sessions);
    }

    javafx.scene.control.ListView<com.ynpe.fastremote.domain.ai.AiChatSession> sessionList() { return aiSessionList; }

    void setSessionsDisabled(boolean disabled) { aiSessionList.setDisable(disabled); }
    Button placementButton() { return aiPlacementButton; }
    LucideIconView placementIcon() { return aiPlacementIcon; }
    Tooltip placementTooltip() { return aiPlacementTooltip; }
    Label chatTitleLabel() { return aiChatTitleLabel; }
}
