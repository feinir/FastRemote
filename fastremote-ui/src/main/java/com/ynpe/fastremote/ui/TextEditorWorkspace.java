// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.settings.EditorTheme;
import com.ynpe.fastremote.domain.settings.SftpSettings;
import com.ynpe.fastremote.domain.transfer.RemoteFileEntry;
import com.ynpe.fastremote.domain.automation.AutomationWorkspaceItem;
import com.ynpe.fastremote.domain.automation.AutomationWorkspaceKind;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/** Owns editor documents, tab lifecycle and local/remote persistence. */
final class TextEditorWorkspace {
    static final int RECENT_LOCAL_FILE_LIMIT = 10;
    private static final ThreadPoolExecutor ENCODING_EXECUTOR = new ThreadPoolExecutor(
            1, 1, 20L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2),
            Thread.ofPlatform().daemon(true).name("fastremote-encoding-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy());
    private final WorkspacePaneCoordinator panes;
    private final ConnectionWorkflow workflow;
    private final Supplier<Window> owner;
    private final Supplier<SftpSettings> settings;
    private final Consumer<String> status;
    private final Runnable workspaceChanged;
    private final Consumer<Tab> contextInstaller;
    private final Consumer<Path> revealLocalFiles;
    private final Map<Tab, Document> documents = new LinkedHashMap<>();
    private final List<Path> recentLocalFiles = new ArrayList<>();
    private Menu recentLocalFilesMenu;
    private final Map<RemoteFilesHandle, LinkedHashSet<Path>> remoteTemporaryFiles = new IdentityHashMap<>();
    private final ObservableList<String> findHistory = FXCollections.observableArrayList();
    private final ObservableList<String> replaceHistory = FXCollections.observableArrayList();
    private int untitledSequence;

    TextEditorWorkspace(WorkspacePaneCoordinator panes, ConnectionWorkflow workflow, Supplier<Window> owner,
            Supplier<SftpSettings> settings, Consumer<String> status, Runnable workspaceChanged,
            Consumer<Tab> contextInstaller) {
        this(panes, workflow, owner, settings, status, workspaceChanged,
                contextInstaller, ignored -> { });
    }

    TextEditorWorkspace(WorkspacePaneCoordinator panes, ConnectionWorkflow workflow, Supplier<Window> owner,
            Supplier<SftpSettings> settings, Consumer<String> status, Runnable workspaceChanged,
            Consumer<Tab> contextInstaller, Consumer<Path> revealLocalFiles) {
        this.panes = Objects.requireNonNull(panes);
        this.workflow = Objects.requireNonNull(workflow);
        this.owner = Objects.requireNonNull(owner);
        this.settings = Objects.requireNonNull(settings);
        this.status = Objects.requireNonNull(status);
        this.workspaceChanged = Objects.requireNonNull(workspaceChanged);
        this.contextInstaller = Objects.requireNonNull(contextInstaller);
        this.revealLocalFiles = Objects.requireNonNull(revealLocalFiles);
    }

    void newDocument() { openDocument(Document.untitled(++untitledSequence, settings.get())); }

    void newDocument(Tab anchor) {
        openDocument(Document.untitled(++untitledSequence, settings.get()), anchor);
    }

    void openLocal(LocalFileEntry entry, Consumer<String> failure) {
        openLocal(entry, failure, null);
    }

    void openLocal(LocalFileEntry entry, Consumer<String> failure, Tab anchor) {
        if (entry == null || entry.directory()) { failure.accept(text("editor.error.regularFile")); return; }
        if (!withinLimit(entry.size(), failure)) return;
        Path path = entry.path().toAbsolutePath().normalize();
        Optional<Document> existing = find(Origin.LOCAL, path.toString(), null);
        if (existing.isPresent()) { recordRecentLocalFile(path); select(existing.get()); return; }
        readLocal(path, entry.name(), failure, false, anchor);
    }

    void openLocalBatch(List<LocalFileEntry> entries, Consumer<String> failure) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(failure, "failure");
        openLocalBatch(List.copyOf(entries), 0, failure);
    }

    private void openLocalBatch(List<LocalFileEntry> entries, int index, Consumer<String> failure) {
        if (index >= entries.size()) return;
        LocalFileEntry entry = entries.get(index);
        if (entry == null || entry.directory() || !withinLimit(entry.size(), failure)) {
            openLocalBatch(entries, index + 1, failure);
            return;
        }
        Path path = entry.path().toAbsolutePath().normalize();
        Optional<Document> existing = find(Origin.LOCAL, path.toString(), null);
        if (existing.isPresent()) {
            recordRecentLocalFile(path);
            select(existing.get());
            openLocalBatch(entries, index + 1, failure);
            return;
        }
        String context = localFileContext(path);
        status.accept(text("editor.status.opening") + ": " + context);
        workflow.readLocalFile(path, maximumBytes()).whenComplete((bytes, error) -> Platform.runLater(() -> {
            if (error != null) {
                failure.accept(text("editor.error.open") + ": " + context + " · " + safeMessage(error));
            } else {
                recordRecentLocalFile(path);
                openDocument(Document.local(entry.name(), path, bytes, settings.get()));
                status.accept(text("editor.status.opened") + " " + context);
            }
            openLocalBatch(entries, index + 1, failure);
        }));
    }

    void openRemote(RemoteFileEntry entry, String hostName, RemoteFilesHandle files, Consumer<String> failure,
            Consumer<String> remoteLog, Runnable refreshRemote) {
        openRemote(entry, hostName, files, failure, remoteLog, refreshRemote, null, ignored -> { });
    }

    void openRemote(RemoteFileEntry entry, String hostName, RemoteFilesHandle files, Consumer<String> failure,
            Consumer<String> remoteLog, Runnable refreshRemote, Tab anchor) {
        openRemote(entry, hostName, files, failure, remoteLog, refreshRemote, anchor, ignored -> { });
    }

    void openRemote(RemoteFileEntry entry, String hostName, RemoteFilesHandle files, Consumer<String> failure,
            Consumer<String> remoteLog, Runnable refreshRemote, Tab anchor,
            Consumer<String> revealRemote) {
        if (entry == null || entry.directory()) { failure.accept(text("editor.error.regularFile")); return; }
        if (!withinLimit(entry.size(), failure)) return;
        Optional<Document> existing = find(Origin.REMOTE, entry.path(), files);
        if (existing.isPresent()) { select(existing.get()); return; }
        int maximum = maximumBytes();
        String context = remoteFileContext(hostName, entry.path());
        status.accept(text("editor.status.opening") + ": " + context);
        files.readFile(entry.path(), maximum).whenComplete((bytes, error) -> Platform.runLater(() -> {
            if (error != null) {
                failure.accept(text("editor.error.open") + ": " + context + " · " + safeMessage(error));
                return;
            }
            Document document = Document.remote(entry.name(), entry.path(), hostName, files, refreshRemote,
                    remoteLog, revealRemote, bytes, settings.get());
            openDocument(document, anchor);
            status.accept(text("editor.status.opened") + " " + context);
        }));
    }

    void openLocalChooser() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(text("action.openFile"));
        File chosen = PlatformFileChooser.showOpenDialog(chooser, owner.get());
        if (chosen != null) readLocal(chosen.toPath().toAbsolutePath().normalize(), chosen.getName(), status);
    }

    void openLocalInNewTab(Path requestedPath, Consumer<String> failure) {
        Objects.requireNonNull(requestedPath, "requestedPath");
        Objects.requireNonNull(failure, "failure");
        Path path = requestedPath.toAbsolutePath().normalize();
        Path name = path.getFileName();
        if (name == null) {
            failure.accept(text("editor.error.regularFile"));
            return;
        }
        readLocal(path, name.toString(), failure, true);
    }

    void bindRecentMenu(Menu menu) {
        recentLocalFilesMenu = Objects.requireNonNull(menu);
        rebuildRecentMenu(menu);
    }

    private void readLocal(Path path, String name, Consumer<String> failure) {
        readLocal(path, name, failure, false, null);
    }

    private void readLocal(Path path, String name, Consumer<String> failure, boolean forceNewTab) {
        readLocal(path, name, failure, forceNewTab, null);
    }

    private void readLocal(Path path, String name, Consumer<String> failure,
            boolean forceNewTab, Tab anchor) {
        if (!forceNewTab) {
            Optional<Document> existing = find(Origin.LOCAL, path.toString(), null);
            if (existing.isPresent()) { recordRecentLocalFile(path); select(existing.get()); return; }
        }
        String context = localFileContext(path);
        status.accept(text("editor.status.opening") + ": " + context);
        workflow.readLocalFile(path, maximumBytes()).whenComplete((bytes, error) -> Platform.runLater(() -> {
            if (error != null) {
                failure.accept(text("editor.error.open") + ": " + context + " · " + safeMessage(error));
                return;
            }
            recordRecentLocalFile(path);
            openDocument(Document.local(name, path, bytes, settings.get()), anchor);
            status.accept(text("editor.status.opened") + " " + context);
        }));
    }

    void rebuildRecentMenu(Menu menu) {
        recentLocalFilesMenu = Objects.requireNonNull(menu);
        configureRecentMenu(menu, recentLocalFiles,
                path -> readLocal(path, path.getFileName().toString(), status),
                () -> {
                    recentLocalFiles.clear();
                    rebuildRecentMenu(menu);
                });
    }

    static void configureRecentMenu(Menu menu, List<Path> paths, Consumer<Path> opener, Runnable clearer) {
        menu.getItems().clear();
        for (Path path : paths) {
            MenuItem item = new MenuItem(path.toString());
            item.setMnemonicParsing(false);
            item.setOnAction(event -> opener.accept(path));
            menu.getItems().add(item);
        }
        if (!paths.isEmpty()) {
            MenuItem clear = new MenuItem(text("action.clearRecent"));
            clear.setOnAction(event -> clearer.run());
            menu.getItems().addAll(new SeparatorMenuItem(), clear);
        }
        menu.setDisable(paths.isEmpty());
    }

    private void recordRecentLocalFile(Path path) {
        updateRecentLocalFiles(recentLocalFiles, path);
        if (recentLocalFilesMenu != null) rebuildRecentMenu(recentLocalFilesMenu);
    }

    static void updateRecentLocalFiles(List<Path> paths, Path path) {
        paths.remove(path);
        paths.addFirst(path);
        while (paths.size() > RECENT_LOCAL_FILE_LIMIT) paths.removeLast();
    }

    boolean owns(Tab tab) { return documents.containsKey(tab); }
    List<AutomationWorkspaceItem> automationItems(Tab activeTab) {
        return documents.values().stream().map(document -> new AutomationWorkspaceItem(
                automationId(document), AutomationWorkspaceKind.EDITOR, document.name,
                document.locationLabel(), document.tab == activeTab, document.dirty(), true, !document.busy, false))
                .toList();
    }
    String automationText(String workspaceId) {
        return documents.values().stream().filter(document -> automationId(document).equals(workspaceId))
                .findFirst().map(document -> document.editor.text()).orElseThrow(() ->
                        new IllegalArgumentException("Open workspace editor was not found"));
    }
    boolean replaceAutomationText(String workspaceId, String content) {
        return documents.values().stream().filter(document -> automationId(document).equals(workspaceId))
                .findFirst().filter(document -> !document.busy).map(document -> {
                    document.editor.setText(content);
                    document.editor.requestEditorFocus();
                    return true;
                }).orElse(false);
    }
    boolean hasActive() { return active().isPresent(); }
    void cut() { active().ifPresent(document -> document.editor.cut()); }
    void copy() { active().ifPresent(document -> document.editor.copy()); }
    void paste() { active().ifPresent(document -> document.editor.paste()); }
    boolean insertText(Tab tab, String value) {
        Document document = documents.get(tab);
        if (document == null || document.busy) return false;
        document.editor.insertText(value);
        document.editor.requestEditorFocus();
        return true;
    }
    void find() { active().ifPresent(document -> document.editor.showFind()); }
    void replace() { active().ifPresent(document -> document.editor.showReplace()); }
    void save() { active().ifPresent(document -> save(document, false)); }
    void saveAs() { active().ifPresent(document -> saveLocallyAs(document, false)); }
    void saveAll() { documents.values().stream().filter(Document::dirty).toList().forEach(d -> save(d, false)); }
    void closeActive() { active().ifPresent(this::requestClose); }
    void requestClose(Tab tab) { Optional.ofNullable(documents.get(tab)).ifPresent(this::requestClose); }

    /**
     * Runs the normal editor close flow for every document. A save is asynchronous, so the
     * enclosing window stays open until that save finishes and the user requests close again.
     */
    boolean confirmCloseAll() {
        for (Document document : List.copyOf(documents.values())) {
            if (document.busy) return false;
            requestClose(document);
            if (documents.containsValue(document)) return false;
        }
        return true;
    }

    /** Releases documents after the enclosing window close has already been approved. */
    void dispose() {
        List.copyOf(documents.values()).forEach(this::remove);
    }

    void applySettings() {
        SftpSettings current = settings.get();
        documents.values().forEach(document -> {
            document.editor.applySettings(current);
            applyEditorShellTheme(document, current.editorTheme());
        });
    }

    private void openDocument(Document document) {
        openDocument(document, null);
    }

    private void openDocument(Document document, Tab anchor) {
        document.editor.setSearchHistories(findHistory, replaceHistory);
        BorderPane shell = new BorderPane(document.editor);
        shell.getStyleClass().add("embedded-editor-shell");
        document.shell = shell;
        Label fileName = new Label(document.locationLabel());
        fileName.getStyleClass().add("embedded-editor-location-label");
        fileName.setMinWidth(0.0);
        fileName.setMaxWidth(Double.MAX_VALUE);
        document.fileNameStrip = fileName;
        HBox fileStrip = new HBox(2.0, fileName);
        fileStrip.setAlignment(Pos.CENTER_LEFT);
        fileStrip.getStyleClass().add("embedded-editor-file-strip");
        HBox.setHgrow(fileName, Priority.ALWAYS);
        List<Button> statusActions = new ArrayList<>();
        if (document.origin == Origin.REMOTE) {
            Button saveLocal = editorStatusButton(LucideIconView.Icon.IMPORT,
                    text("editor.saveLocal"), "embedded-editor-status-save-local",
                    () -> saveLocallyAs(document, false));
            saveLocal.setId("editor-status-save-local");
            statusActions.add(saveLocal);
            fileStrip.getChildren().add(saveLocal);
        }
        Button save = editorStatusButton(LucideIconView.Icon.SAVE,
                text("action.save"), "embedded-editor-status-save",
                () -> save(document, false));
        save.setId("editor-status-save");
        statusActions.add(save);
        fileStrip.getChildren().add(save);
        document.statusActions = List.copyOf(statusActions);
        shell.setBottom(fileStrip);
        applyEditorShellTheme(document, settings.get().editorTheme());
        installEditorContextMenu(document);
        Tab tab = new Tab(document.name, shell);
        tab.setGraphic(WorkspaceTabGraphics.editor());
        document.tab = tab;
        document.editor.textProperty().addListener((ignored, before, after) -> updateTitle(document));
        tab.setOnCloseRequest(event -> { event.consume(); requestClose(document); });
        documents.put(tab, document);
        contextInstaller.accept(tab);
        if (anchor == null || !panes.addPaneBeside(tab, anchor,
                WorkspaceLayoutModel.DropEdge.RIGHT, true)) panes.addPane(tab);
        workspaceChanged.run();
        Platform.runLater(document.editor::requestEditorFocus);
    }

    private static Button editorStatusButton(
            LucideIconView.Icon iconType, String label, String colorClass, Runnable action) {
        Button button = new Button();
        LucideIconView icon = new LucideIconView(iconType);
        icon.getStyleClass().add("embedded-editor-status-icon");
        button.setGraphic(icon);
        button.setAccessibleText(label);
        button.setTooltip(new Tooltip(label));
        button.getStyleClass().addAll("embedded-editor-status-button", colorClass);
        button.setMinSize(16.0, 16.0);
        button.setPrefSize(16.0, 16.0);
        button.setMaxSize(16.0, 16.0);
        button.setOnAction(event -> action.run());
        return button;
    }

    private void installEditorContextMenu(Document document) {
        CheckMenuItem wrap = new CheckMenuItem(text("editor.wrap"));
        wrap.setId("editor-context-wrap");
        wrap.setSelected(document.editor.isWrapText());
        wrap.setOnAction(event -> document.editor.setWrapText(wrap.isSelected()));

        Menu syntax = new Menu(text("editor.syntax"));
        syntax.setId("editor-context-syntax");
        ApplicationContextMenus.styleSubmenu(syntax);
        ToggleGroup syntaxGroup = new ToggleGroup();
        for (String language : SyntaxHighlighting.supportedLanguages()) {
            RadioMenuItem item = new RadioMenuItem(SyntaxHighlighting.displayName(language));
            item.setToggleGroup(syntaxGroup);
            item.setSelected(language.equals(document.editor.syntaxLanguage()));
            item.setOnAction(event -> document.editor.setSyntaxLanguage(language));
            syntax.getItems().add(item);
        }

        Menu encoding = new Menu(text("editor.encoding"));
        encoding.setId("editor-context-encoding");
        ApplicationContextMenus.styleSubmenu(encoding);
        ToggleGroup encodingGroup = new ToggleGroup();
        Map<TextEncoding, RadioMenuItem> encodingItems = new LinkedHashMap<>();
        for (TextEncoding value : TextEncoding.values()) {
            RadioMenuItem item = new RadioMenuItem(value.toString());
            item.setToggleGroup(encodingGroup);
            item.setSelected(value == document.encoding);
            item.setOnAction(event -> {
                if (document.updatingControls || value == document.encoding) return;
                TextEncoding previous = document.encoding;
                changeEncoding(document, previous, value, () -> {
                    document.updatingControls = true;
                    encodingItems.get(previous).setSelected(true);
                    document.updatingControls = false;
                });
            });
            encodingItems.put(value, item);
            encoding.getItems().add(item);
        }

        MenuItem save = new MenuItem(text("action.save"));
        save.setId("editor-context-save");
        save.setOnAction(event -> save(document, false));
        MenuItem saveAndClose = new MenuItem(text("editor.saveAndClose"));
        saveAndClose.setId("editor-context-save-close");
        saveAndClose.setOnAction(event -> save(document, true));
        MenuItem close = new MenuItem(text("action.closeFile"));
        close.setId("editor-context-close");
        close.setOnAction(event -> requestClose(document));

        MenuItem reveal = new MenuItem(document.origin == Origin.REMOTE
                ? text("editor.context.showInSftp") : PlatformFileManagerLabel.reveal());
        MenuItem showInLocalFiles = new MenuItem(text("editor.context.showInLocalFiles"));
        MenuItem copyPath = new MenuItem(text("legacy.java.b97c49acb93028ec"));
        MenuItem copyName = new MenuItem(text("legacy.java.886775196a71d984"));
        reveal.setId("editor-context-reveal");
        showInLocalFiles.setId("editor-context-show-in-local-files");
        copyPath.setId("editor-context-copy-path");
        copyName.setId("editor-context-copy-name");
        reveal.setOnAction(event -> revealDocument(document));
        showInLocalFiles.setOnAction(event -> revealLocalFiles.accept(document.localPath));
        copyPath.setOnAction(event -> copyText(documentPath(document)));
        copyName.setOnAction(event -> copyText(document.name));
        List<MenuItem> actions = new ArrayList<>(List.of(wrap, syntax, encoding,
                new SeparatorMenuItem(), save, saveAndClose));
        if (document.origin == Origin.REMOTE) {
            MenuItem saveLocal = new MenuItem(text("editor.saveLocal"));
            saveLocal.setId("editor-context-save-local");
            saveLocal.setOnAction(event -> saveLocallyAs(document, false));
            actions.add(saveLocal);
        }
        actions.add(close);
        actions.add(new SeparatorMenuItem());
        if (document.origin == Origin.LOCAL) actions.add(showInLocalFiles);
        actions.addAll(List.of(reveal, copyPath, copyName));
        document.contextActions = List.copyOf(actions);
        Runnable refresh = () -> {
            boolean unavailable = document.origin == Origin.NEW;
            reveal.setDisable(unavailable);
            showInLocalFiles.setDisable(unavailable || document.busy);
            copyPath.setDisable(unavailable);
            copyName.setDisable(unavailable);
            wrap.setSelected(document.editor.isWrapText());
            encodingItems.get(document.encoding).setSelected(true);
            document.contextActions.stream()
                    .filter(item -> item != reveal && item != showInLocalFiles
                            && item != copyPath && item != copyName)
                    .forEach(item -> item.setDisable(document.busy));
            reveal.setText(document.origin == Origin.REMOTE
                    ? text("editor.context.showInSftp") : PlatformFileManagerLabel.reveal());
        };
        refresh.run();
        document.editor.appendContextMenuItems(actions, refresh);
    }

    private static void applyEditorShellTheme(Document document, EditorTheme theme) {
        if (document.shell == null) return;
        document.shell.getStyleClass().removeAll("editor-theme-dark", "editor-theme-light");
        document.shell.getStyleClass().add(theme == EditorTheme.DARK
                ? "editor-theme-dark" : "editor-theme-light");
    }

    private void revealDocument(Document document) {
        if (document.origin == Origin.NEW) return;
        if (document.origin == Origin.REMOTE) {
            document.revealRemote.accept(document.remotePath);
            return;
        }
        workflow.revealLocal(document.localPath, true).whenComplete((ignored, error) -> {
            if (error != null) Platform.runLater(() -> status.accept(text("legacy.java.9924447f0b807036")));
        });
    }

    private static String documentPath(Document document) {
        return document.origin == Origin.LOCAL ? document.localPath.toString() : document.remotePath;
    }

    private static String automationId(Document document) {
        return "editor-" + Integer.toUnsignedString(System.identityHashCode(document.tab), 36);
    }

    private static void copyText(String value) {
        if (value == null || value.isBlank()) return;
        ClipboardContent content = new ClipboardContent();
        content.putString(value);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void changeEncoding(Document document, TextEncoding previous, TextEncoding selected,
            Runnable restoreSelection) {
        if (document.sourceBytes == null) {
            document.encoding = selected;
            document.editor.refreshSyntaxHighlighting();
            return;
        }
        if (document.dirty()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, text("editor.encoding.changeBody"),
                    ButtonType.CANCEL, ButtonType.OK);
            alert.initOwner(owner.get());
            alert.setTitle(text("editor.encoding.changeTitle"));
            alert.setHeaderText(text("editor.encoding.changeHeader"));
            prepareDialog(alert);
            if (alert.showAndWait().filter(ButtonType.OK::equals).isEmpty()) {
                restoreSelection.run();
                return;
            }
        }
        setBusy(document, true);
        byte[] source = document.sourceBytes.clone();
        String reference = document.baseline.text();
        CompletionStage<Optional<String>> decoding;
        try {
            decoding = java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> selected.decodeChecked(source, reference), ENCODING_EXECUTOR);
        } catch (java.util.concurrent.RejectedExecutionException saturated) {
            decoding = java.util.concurrent.CompletableFuture.failedFuture(saturated);
        }
        decoding
                .whenComplete((decoded, error) -> Platform.runLater(() -> {
                    java.util.Arrays.fill(source, (byte) 0);
                    setBusy(document, false);
                    if (error != null || decoded == null || decoded.isEmpty()) {
                        restoreSelection.run();
                        status.accept(text("editor.encoding.invalid"));
                        Alert warning = new Alert(Alert.AlertType.WARNING, text("editor.encoding.invalid"), ButtonType.OK);
                        warning.initOwner(owner.get());
                        warning.setTitle(text("editor.encoding.changeTitle"));
                        warning.setHeaderText(text("editor.encoding.invalidHeader"));
                        prepareDialog(warning);
                        warning.show();
                        return;
                    }
                    document.encoding = selected;
                    document.editor.setText(decoded.get());
                    document.baseline = document.editor.content();
                    document.editor.refreshSyntaxHighlighting();
                    updateTitle(document);
                }));
    }

    private void requestClose(Document document) {
        if (document.busy) return;
        if (!document.dirty()) { remove(document); return; }
        ButtonType save = new ButtonType(text("action.save"), ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType(text("editor.discard"), ButtonBar.ButtonData.NO);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(owner.get());
        alert.setTitle(text("editor.unsaved.title"));
        alert.setHeaderText(text("editor.unsaved.header"));
        alert.setContentText(document.name);
        alert.getButtonTypes().setAll(save, discard, ButtonType.CANCEL);
        prepareDialog(alert);
        Optional<ButtonType> choice = alert.showAndWait();
        if (choice.filter(discard::equals).isPresent()) remove(document);
        else if (choice.filter(save::equals).isPresent()) save(document, true);
    }

    private void save(Document document, boolean closeAfter) {
        if (document.busy) return;
        if (document.origin == Origin.NEW) { saveLocallyAs(document, closeAfter); return; }
        EditorFileContent snapshot = document.editor.content();
        byte[] bytes = document.encoding.encode(snapshot.fileText());
        if (document.origin == Origin.LOCAL) {
            setBusy(document, true);
            workflow.writeLocalFileAtomic(document.localPath, bytes).whenComplete((ignored, error) ->
                    Platform.runLater(() -> completeSave(document, snapshot, bytes, error, closeAfter, false)));
            return;
        }
        ButtonType upload = new ButtonType(text("editor.remote.upload"), ButtonBar.ButtonData.OK_DONE);
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(owner.get());
        confirm.setTitle(text("editor.remote.uploadTitle"));
        confirm.setHeaderText(text("editor.remote.uploadHeader"));
        confirm.setContentText(document.remotePath);
        confirm.getButtonTypes().setAll(ButtonType.CANCEL, upload);
        prepareDialog(confirm);
        if (confirm.showAndWait().filter(upload::equals).isEmpty()) return;
        setBusy(document, true);
        Stage progress = progressWindow(text("editor.remote.uploading"));
        progress.show();
        CompletionStage<Void> prepare = document.pendingRemoteTemporary == null
                ? java.util.concurrent.CompletableFuture.completedFuture(null)
                : deleteTemporary(document, document.pendingRemoteTemporary).exceptionally(error -> null);
        prepare.thenCompose(ignored -> workflow.createEditorTemporaryFile(bytes))
                .thenCompose(temporary -> {
                    trackTemporary(document.remoteFiles, temporary);
                    document.pendingRemoteTemporary = temporary;
                    return document.remoteFiles.upload(temporary, document.remotePath)
                            .thenCompose(done -> deleteTemporary(document, temporary).exceptionally(error -> null));
                }).whenComplete((ignored, error) -> Platform.runLater(() -> {
                    progress.close();
                    completeSave(document, snapshot, bytes, error, closeAfter, true);
                }));
    }

    private void completeSave(Document document, EditorFileContent snapshot, byte[] bytes,
            Throwable error, boolean closeAfter, boolean remote) {
        setBusy(document, false);
        if (error == null) {
            document.baseline = snapshot;
            document.sourceBytes = bytes.clone();
            updateTitle(document);
            if (remote && document.refreshRemote != null) document.refreshRemote.run();
            String context = remote
                    ? remoteFileContext(document.remoteHostName, document.remotePath)
                    : localFileContext(document.localPath);
            status.accept((remote ? text("editor.remote.uploaded") : text("editor.status.saved")) + " " + context);
            if (closeAfter) remove(document);
            return;
        }
        if (remote) offerLocalRecovery(document, error, closeAfter);
        else showFailure(text("editor.error.save"), error, localFileContext(document.localPath));
    }

    private void offerLocalRecovery(Document document, Throwable error, boolean closeAfter) {
        String detail = text("editor.error.save") + ": "
                + remoteFileContext(document.remoteHostName, document.remotePath) + " · " + safeMessage(error);
        if (document.remoteLog != null) document.remoteLog.accept(detail);
        ButtonType saveLocal = new ButtonType(text("editor.saveLocal"), ButtonBar.ButtonData.YES);
        ButtonType keep = new ButtonType(text("editor.keepEditing"), ButtonBar.ButtonData.NO);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(owner.get());
        alert.setTitle(text("editor.remote.failedTitle"));
        alert.setHeaderText(text("editor.remote.failedHeader"));
        alert.setContentText(text("editor.error.save"));
        alert.getButtonTypes().setAll(saveLocal, keep);
        prepareDialog(alert);
        if (alert.showAndWait().filter(saveLocal::equals).isPresent()) saveLocallyAs(document, closeAfter);
    }

    private void saveLocallyAs(Document document, boolean closeAfter) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(text("action.saveAs"));
        chooser.setInitialFileName(document.name);
        File chosen = PlatformFileChooser.showSaveDialog(chooser, owner.get());
        if (chosen == null) return;
        Path path = chosen.toPath().toAbsolutePath().normalize();
        setBusy(document, true);
        EditorFileContent snapshot = document.editor.content();
        byte[] bytes = document.encoding.encode(snapshot.fileText());
        workflow.writeLocalFileAtomic(path, bytes)
                .whenComplete((ignored, error) -> Platform.runLater(() -> {
                    setBusy(document, false);
                    if (error != null) {
                        showFailure(text("editor.error.save"), error, localFileContext(path));
                        return;
                    }
                    if (document.origin != Origin.REMOTE) {
                        document.origin = Origin.LOCAL;
                        document.localPath = path;
                        document.name = path.getFileName().toString();
                        document.baseline = snapshot;
                        document.sourceBytes = bytes.clone();
                        updateTitle(document);
                    }
                    status.accept(text("editor.status.saved") + " " + localFileContext(path));
                    if (!closeAfter && document.origin == Origin.REMOTE
                            && document.pendingRemoteTemporary != null) {
                        deleteTemporary(document, document.pendingRemoteTemporary);
                    }
                    if (closeAfter) remove(document);
                }));
    }

    void cleanupRemoteTemporaryFiles(RemoteFilesHandle files) {
        List<Path> paths;
        synchronized (remoteTemporaryFiles) {
            paths = new ArrayList<>(remoteTemporaryFiles.getOrDefault(files, new LinkedHashSet<>()));
        }
        for (Path path : paths) workflow.deleteEditorTemporaryFile(path).whenComplete((ignored, error) -> {
            if (error == null) untrackTemporary(files, path);
        });
    }

    private CompletionStage<Void> deleteTemporary(Document document, Path path) {
        return workflow.deleteEditorTemporaryFile(path).whenComplete((ignored, error) -> {
            if (error == null) {
                untrackTemporary(document.remoteFiles, path);
                if (Objects.equals(document.pendingRemoteTemporary, path)) document.pendingRemoteTemporary = null;
            } else if (document.remoteLog != null) {
                document.remoteLog.accept(text("editor.temp.cleanupFailed") + ": " + safeMessage(error));
            }
        });
    }

    private void trackTemporary(RemoteFilesHandle files, Path path) {
        synchronized (remoteTemporaryFiles) {
            remoteTemporaryFiles.computeIfAbsent(files, ignored -> new LinkedHashSet<>()).add(path);
        }
    }

    private void untrackTemporary(RemoteFilesHandle files, Path path) {
        synchronized (remoteTemporaryFiles) {
            LinkedHashSet<Path> paths = remoteTemporaryFiles.get(files);
            if (paths == null) return;
            paths.remove(path);
            if (paths.isEmpty()) remoteTemporaryFiles.remove(files);
        }
    }

    private void setBusy(Document document, boolean value) {
        document.busy = value;
        document.tab.setClosable(!value);
        document.editor.setEditingDisabled(value);
        document.contextActions.forEach(item -> item.setDisable(value));
        document.statusActions.forEach(button -> button.setDisable(value));
    }

    private Stage progressWindow(String message) {
        Stage dialog = new Stage(StageStyle.UTILITY);
        dialog.initOwner(owner.get());
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle(message);
        ProgressIndicator indicator = new ProgressIndicator();
        indicator.setPrefSize(28.0, 28.0);
        HBox content = new HBox(12.0, indicator, new Label(message));
        content.setAlignment(Pos.CENTER_LEFT);
        content.setStyle("-fx-padding: 14px 18px;");
        dialog.setScene(new Scene(content));
        dialog.setResizable(false);
        DialogSpacing.centerOnOwnerWhenShown(dialog);
        return dialog;
    }

    static void prepareDialog(javafx.scene.control.Alert alert) {
        DialogSpacing.prepare(alert);
        DialogSpacing.localizeStandardButtons(alert.getDialogPane());
        DialogSpacing.fitButtonsToContent(alert.getDialogPane());
    }

    private void remove(Document document) {
        if (document.origin == Origin.REMOTE && document.pendingRemoteTemporary != null) {
            deleteTemporary(document, document.pendingRemoteTemporary);
        }
        document.editor.dispose();
        documents.remove(document.tab);
        panes.removePane(document.tab);
        workspaceChanged.run();
    }

    private void updateTitle(Document document) {
        document.tab.setText(document.name + (document.dirty() ? " *" : ""));
        if (document.fileNameStrip != null) document.fileNameStrip.setText(document.locationLabel());
    }

    private Optional<Document> active() { return Optional.ofNullable(documents.get(panes.selectedPane())); }
    private Optional<Document> find(Origin origin, String path, RemoteFilesHandle files) {
        return documents.values().stream().filter(document -> document.origin == origin
                && Objects.equals(document.pathKey(), path)
                && (origin != Origin.REMOTE || document.remoteFiles == files)).findFirst();
    }
    private void select(Document document) { panes.selectPane(document.tab); document.editor.requestEditorFocus(); }
    private boolean withinLimit(long size, Consumer<String> failure) {
        if (size >= 0 && size <= settings.get().maxEditableFileBytes()) return true;
        failure.accept(String.format(text("editor.error.tooLarge"), settings.get().maxEditableFileBytes() / 1024 / 1024));
        return false;
    }
    private int maximumBytes() { return Math.toIntExact(settings.get().maxEditableFileBytes()); }
    private void showFailure(String header, Throwable error) {
        showFailure(header, error, "");
    }
    private void showFailure(String header, Throwable error, String context) {
        status.accept(header + ": " + (context.isBlank() ? "" : context + " · ") + safeMessage(error));
        Alert alert = new Alert(Alert.AlertType.ERROR, safeMessage(error), ButtonType.OK);
        alert.initOwner(owner.get());
        alert.setHeaderText(header);
        prepareDialog(alert);
        alert.show();
    }
    static boolean isDirty(boolean associatedFile, String baseline, String content) {
        return associatedFile ? !Objects.equals(content, baseline) : content != null && !content.isEmpty();
    }
    private static String safeMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        String value = current.getMessage();
        return value == null || value.isBlank() ? current.getClass().getSimpleName() : value;
    }
    private static String text(String key, Object... arguments) {
        return com.ynpe.fastremote.i18n.Texts.text(key, arguments);
    }

    static String localFileContext(Path path) {
        return text("editor.log.localContext", path.toAbsolutePath().normalize());
    }

    static String remoteFileContext(String hostName, String path) {
        return text("editor.log.remoteContext", Objects.requireNonNullElse(hostName, ""), path);
    }

    private enum Origin { NEW, LOCAL, REMOTE }
    private static final class Document {
        private Origin origin;
        private String name;
        private Path localPath;
        private String remotePath;
        private String remoteHostName;
        private RemoteFilesHandle remoteFiles;
        private final Runnable refreshRemote;
        private final Consumer<String> remoteLog;
        private final Consumer<String> revealRemote;
        private EditorFileContent baseline;
        private byte[] sourceBytes;
        private TextEncoding encoding;
        private final EmbeddedTextEditor editor;
        private Tab tab;
        private BorderPane shell;
        private Label fileNameStrip;
        private List<MenuItem> contextActions = List.of();
        private List<Button> statusActions = List.of();
        private boolean busy;
        private boolean updatingControls;
        private Path pendingRemoteTemporary;
        private Document(Origin origin, String name, Path localPath, String remotePath, String remoteHostName,
                RemoteFilesHandle remoteFiles, Runnable refreshRemote, Consumer<String> remoteLog,
                Consumer<String> revealRemote, byte[] sourceBytes, TextEncoding encoding,
                String content, SftpSettings settings) {
            this.origin = origin; this.name = name; this.localPath = localPath; this.remotePath = remotePath;
            this.remoteHostName = remoteHostName;
            this.remoteFiles = remoteFiles; this.refreshRemote = refreshRemote; this.remoteLog = remoteLog;
            this.revealRemote = revealRemote == null ? ignored -> { } : revealRemote;
            this.sourceBytes = sourceBytes == null ? null : sourceBytes.clone(); this.encoding = encoding;
            this.editor = new EmbeddedTextEditor(settings, name);
            this.editor.setText(content);
            this.baseline = this.editor.content();
        }
        static Document untitled(int sequence, SftpSettings settings) {
            return new Document(Origin.NEW, text("editor.untitled") + " " + sequence,
                    null, null, null, null, null, null, null, null, TextEncoding.UTF8, "", settings);
        }
        static Document local(String name, Path path, byte[] bytes, SftpSettings settings) {
            TextEncoding encoding = TextEncoding.detect(bytes);
            return new Document(Origin.LOCAL, name, path, null, null, null, null, null,
                    null, bytes, encoding, encoding.decode(bytes), settings);
        }
        static Document remote(String name, String path, String hostName, RemoteFilesHandle files, Runnable refresh,
                Consumer<String> remoteLog, Consumer<String> revealRemote, byte[] bytes, SftpSettings settings) {
            TextEncoding encoding = TextEncoding.detect(bytes);
            return new Document(Origin.REMOTE, name, null, path, hostName, files, refresh, remoteLog,
                    revealRemote, bytes, encoding, encoding.decode(bytes), settings);
        }
        boolean dirty() {
            return origin == Origin.NEW ? !editor.text().isEmpty() : !baseline.equals(editor.content());
        }
        String pathKey() { return origin == Origin.LOCAL ? localPath.toString() : remotePath; }
        String locationLabel() { return origin == Origin.NEW ? text("editor.location.unassociated")
                : origin == Origin.LOCAL ? localPath.toString()
                : remoteLocationLabel(remoteHostName, remotePath); }
    }

    static String remoteLocationLabel(String hostName, String path) {
        return text("editor.location.remote") + " - "
                + Objects.requireNonNullElse(hostName, "") + " | " + path;
    }
}
