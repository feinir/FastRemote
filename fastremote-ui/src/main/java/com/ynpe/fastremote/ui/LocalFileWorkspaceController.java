// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

import static com.ynpe.fastremote.ui.SftpBrowserModel.blankTableItem;
import static com.ynpe.fastremote.ui.SftpBrowserModel.hasTreePlaceholder;
import static com.ynpe.fastremote.ui.SftpBrowserModel.installTreeBranchLoader;
import static com.ynpe.fastremote.ui.SftpBrowserModel.localDirectoryStatistics;
import static com.ynpe.fastremote.ui.SftpBrowserModel.replaceTreeChildren;
import static com.ynpe.fastremote.ui.SftpBrowserModel.transferableLocalEntries;
import static com.ynpe.fastremote.ui.SftpBrowserModel.visibleTreeDirectories;
import static com.ynpe.fastremote.ui.SftpBrowserModel.withLocalNavigation;

/** Standalone local file workspace. It has no SSH, SFTP or transfer-queue dependency. */
final class LocalFileWorkspaceController {
    private static final double DEFAULT_TREE_WIDTH = 260.0;
    interface Actions {
        void openPairedTerminal(Path directory);
        void edit(LocalFileEntry entry);
        void status(String message, boolean error);
        void addFavorite(Path directory);
        void directoryChanged(Path directory);
        default boolean sandboxed() { return false; }
        default SandboxFolderBookmarks sandboxFolderBookmarks() { return SandboxFolderBookmarks.UNAVAILABLE; }
        default boolean showHiddenFiles() { return false; }
        default boolean showDirectoryTree() { return true; }
        default void setShowHiddenFiles(boolean show) { }
        default void setShowDirectoryTree(boolean show) { }
        default void manageFavorites() { }
        default List<String> favoritePaths() { return List.of(); }
    }

    private final LocalFileWorkflow workflow;
    private final Actions actions;
    private final Path initialDirectory;
    private final List<Path> clipboardPaths = new ArrayList<>();
    private final ArrayDeque<Path> backHistory = new ArrayDeque<>();
    private final ArrayDeque<Path> forwardHistory = new ArrayDeque<>();
    private List<LocalFileEntry> draggedEntries = List.of();
    private Runnable pendingDrop;
    private FilePanelMoveSupport<LocalFileEntry, Path> moves;
    private boolean closed;
    private boolean clipboardCut;
    private long requestSequence;
    private long treeGeneration;
    private Path currentDirectory;
    private Path pendingRevealFile;
    private boolean synchronizingTree;
    private ContextMenu favoritesMenu;
    private final Map<TreeItem<LocalFileEntry>, CompletableFuture<Void>> treeLoads =
            new IdentityHashMap<>();

    @FXML private VBox root;
    @FXML private TextField pathField;
    @FXML private Button upButton;
    @FXML private Button refreshButton;
    @FXML private Button favoritesButton;
    @FXML private Button terminalButton;
    @FXML private StackPane contentHost;
    @FXML private Label statisticsLabel;
    @FXML private ProgressIndicator progress;
    private TableView<LocalFileEntry> table;
    private TreeView<LocalFileEntry> tree;
    private Node tableSurface;

    LocalFileWorkspaceController(LocalFileWorkflow workflow, Actions actions,
            Path initialDirectory) {
        this.workflow = Objects.requireNonNull(workflow);
        this.actions = Objects.requireNonNull(actions);
        this.initialDirectory = normalize(initialDirectory == null
                ? Path.of(System.getProperty("user.home", ".")) : initialDirectory);
    }

    @FXML
    private void initialize() {
        configureToolbarButton(upButton, LucideIconView.Icon.FOLDER_UP);
        configureToolbarButton(refreshButton, LucideIconView.Icon.REFRESH_CW);
        configureToolbarButton(favoritesButton, LucideIconView.Icon.STAR);
        configureToolbarButton(terminalButton, LucideIconView.Icon.SQUARE_TERMINAL);
        table = MainWindowController.localFileTable();
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        MainWindowController.preventBlankRowSelection(table);
        tableSurface = MainWindowController.fileTableSurface(table);
        moves = new FilePanelMoveSupport<>(() -> draggedEntries, FilePanelMoves::localAllowed,
                (entry, directory) -> workflow.moveLocal(entry.path(), directory.resolve(entry.path().getFileName()), false),
                action -> pendingDrop = action, () -> !closed,
                (entry, error) -> fail(FilePanelMoves.failureMessage(entry.name(), error)),
                (entry, directory) -> currentDirectory = FilePanelMoves.localLocationAfterMove(
                        currentDirectory, entry, directory),
                () -> { refresh(); rebuildTree(); });
        moves.installTarget(table, () -> currentDirectory);
        configureRows();
        rebuildContent();
        pathField.setOnAction(ignored -> navigateText());
        upButton.setOnAction(ignored -> navigate(currentDirectory == null ? initialDirectory
                : Optional.ofNullable(currentDirectory.getParent()).orElse(currentDirectory)));
        refreshButton.setOnAction(ignored -> refreshViews());
        terminalButton.setOnAction(ignored -> actions.openPairedTerminal(currentDirectory));
        if (actions.sandboxed()) {
            root.getChildren().add(1, SandboxFileAccess.notice(this::chooseAuthorizedDirectory, null));
        }
        rebuildFavorites();
        table.setOnKeyPressed(event -> {
            handleFileKey(event, selected(), currentDirectory);
        });
        navigate(initialDirectory);
    }

    private static void configureToolbarButton(javafx.scene.control.Labeled button,
            LucideIconView.Icon iconType) {
        String label = button.getText();
        LucideIconView icon = new LucideIconView(iconType);
        icon.getStyleClass().add("sftp-toolbar-icon");
        button.setGraphic(icon);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setAccessibleText(label);
        button.setTooltip(new Tooltip(label));
        button.getStyleClass().add("sftp-toolbar-icon-button");
    }

    Path currentDirectory() { return currentDirectory; }

    List<CommandPalette.Entry> paletteEntries(com.ynpe.fastremote.i18n.Localizer localizer,
            boolean pinyin, Node focus, String detail, java.util.function.BooleanSupplier available,
            Runnable unavailable) {
        if (closed || currentDirectory == null) return List.of();
        boolean fromTree = WorkspacePaletteActions.contains(tree, focus);
        TreeItem<LocalFileEntry> treeItem = fromTree
                ? tree.getSelectionModel().getSelectedItem() : null;
        Path directory = treeItem != null && treeItem.getValue() != null
                ? treeItem.getValue().path() : currentDirectory;
        List<LocalFileEntry> selection = !fromTree ? List.copyOf(selected())
                : treeItem == null ? List.of()
                : treeItem.getValue() == null ? List.of()
                : transferableLocalEntries(List.of(treeItem.getValue()));
        var operations = new java.util.EnumMap<WorkspacePaletteActions.Action, Runnable>(WorkspacePaletteActions.Action.class);
        operations.put(WorkspacePaletteActions.Action.TERMINAL, () -> actions.openPairedTerminal(directory));
        operations.put(WorkspacePaletteActions.Action.COPY_PATH, () -> copyText(selection.isEmpty()
                ? List.of(directory.toString()) : selection.stream().map(value -> value.path().toString()).toList()));
        if (!selection.isEmpty()) operations.put(WorkspacePaletteActions.Action.COPY_NAME,
                () -> copyText(selection.stream().map(LocalFileEntry::name).toList()));
        operations.put(WorkspacePaletteActions.Action.FAVORITE, () -> addFavorite(directory));
        operations.put(WorkspacePaletteActions.Action.REFRESH, this::refreshViews);
        operations.put(WorkspacePaletteActions.Action.NEW_FOLDER, () -> create(directory, true));
        Path reveal = selection.size() == 1 ? selection.getFirst().path() : directory;
        operations.put(WorkspacePaletteActions.Action.REVEAL,
                () -> run(workflow.revealLocal(reveal, selection.size() == 1), false));
        return WorkspacePaletteActions.entries(localizer, pinyin, "local-files", detail,
                actions.favoritePaths(), path -> {
                    try { navigateTo(Path.of(path)); }
                    catch (InvalidPathException invalid) { unavailable.run(); }
                }, operations,
                () -> !closed && available.getAsBoolean(), unavailable);
    }

    private void handleFileKey(javafx.scene.input.KeyEvent event, List<LocalFileEntry> values, Path directory) {
        if (closed) return;
        boolean mutable = !values.isEmpty() && values.stream().allMatch(entry -> entry.path().getParent() != null);
        if (localShortcut("localFiles.copy").match(event)) {
            if (mutable) copy(values, false);
        } else if (localShortcut("localFiles.cut").match(event)) {
            if (mutable) copy(values, true);
        } else if (localShortcut("localFiles.paste").match(event)) paste(directory);
        else if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) delete(values);
        else if (localShortcut("localFiles.rename").match(event)
                || event.getCode() == KeyCode.F2) rename(values);
        else if (localShortcut("localFiles.refresh").match(event)) refreshViews();
        else if (localShortcut("localFiles.newFolder").match(event)) {
            if (directory != null) create(directory, true);
        } else if (localShortcut("editor.edit").match(event)) {
            if (values.size() == 1 && !values.getFirst().directory()) actions.edit(values.getFirst());
        } else if (localShortcut("sftp.context.hiddenFiles").match(event)) {
            actions.setShowHiddenFiles(!actions.showHiddenFiles());
            refresh();
            rebuildTree();
        } else return;
        event.consume();
    }

    private void relocateCurrentDirectory(Path source, Path target) {
        if (currentDirectory != null) {
            currentDirectory = SftpBrowserModel.localDirectoryAfterRename(currentDirectory, source, target);
        }
    }

    private void configureRows() {
        table.setRowFactory(ignored -> {
            TableRow<LocalFileEntry> row = new TableRow<>();
            MainWindowController.guardBlankFileRow(table, row);
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() != 2 || row.isEmpty() || blankTableItem(row.getItem())) return;
                LocalFileEntry entry = row.getItem();
                if (entry.directory()) navigate(entry.path());
                else if (isTextFile(entry)) actions.edit(entry);
                else run(workflow.openLocal(entry.path()), false);
            });
            row.setOnContextMenuRequested(event -> {
                LocalFileEntry clicked = row.isEmpty() || blankTableItem(row.getItem())
                        ? null : row.getItem();
                if (clicked != null && !table.getSelectionModel().isSelected(row.getIndex())) {
                    table.getSelectionModel().clearAndSelect(row.getIndex());
                }
                contextMenu(clicked)
                        .show(row, event.getScreenX(), event.getScreenY());
                event.consume();
            });
            row.setOnDragDetected(event -> {
                if (row.isEmpty() || blankTableItem(row.getItem())) return;
                if (!table.getSelectionModel().isSelected(row.getIndex())) {
                    table.getSelectionModel().clearAndSelect(row.getIndex());
                }
                List<LocalFileEntry> dragged = selected();
                if (dragged.isEmpty()) return;
                draggedEntries = List.copyOf(dragged);
                pendingDrop = null;
                ClipboardContent content = new ClipboardContent();
                List<Path> paths = dragged.stream().map(LocalFileEntry::path).toList();
                content.putFiles(paths.stream().map(Path::toFile).toList());
                TerminalPathDragSupport.putLocalPaths(content,
                        paths.stream().map(Path::toString).toList());
                row.startDragAndDrop(TransferMode.COPY_OR_MOVE).setContent(content);
                event.consume();
            });
            moves.installTarget(row, () -> FilePanelMoves.localTarget(row.getItem(), currentDirectory));
            row.setOnDragDone(event -> finishDrag());
            return row;
        });
    }

    private ContextMenu contextMenu(LocalFileEntry clicked) {
        return contextMenu(clicked, false);
    }

    ContextMenu contextMenu(LocalFileEntry clicked, boolean fromTree) {
        // Freeze the action targets when opening the menu; asynchronous navigation can change the table.
        List<LocalFileEntry> values = fromTree
                ? transferableLocalEntries(clicked == null ? List.of() : List.of(clicked))
                : clicked == null ? List.of() : selected();
        Path directory = fromTree && clicked != null ? clicked.path() : currentDirectory;
        boolean mutable = !values.isEmpty() && values.stream().allMatch(entry -> entry.path().getParent() != null);
        ContextMenu menu = ApplicationContextMenus.create();
        Menu go = navigationMenu();
        MenuItem open = item(clicked != null && clicked.directory() ? "localFiles.open" : "editor.edit",
                () -> { if (clicked != null && clicked.directory()) navigate(clicked.path()); else if (clicked != null) actions.edit(clicked); });
        MenuItem external = item("localFiles.openExternal", () -> run(workflow.openLocal(clicked.path()), false));
        MenuItem terminal = item("localFiles.terminal", () -> actions.openPairedTerminal(
                clicked != null && clicked.directory() ? clicked.path() : currentDirectory));
        MenuItem cut = item("localFiles.cut", () -> copy(values, true));
        MenuItem copy = item("localFiles.copy", () -> copy(values, false));
        MenuItem paste = item("localFiles.paste", () -> paste(directory));
        MenuItem rename = item("localFiles.rename", () -> rename(values));
        MenuItem delete = item("localFiles.delete", () -> delete(values));
        MenuItem newFolder = item("localFiles.newFolder", () -> create(directory, true));
        MenuItem newFile = item("localFiles.newFile", () -> create(directory, false));
        MenuItem refresh = item("localFiles.refresh", this::refreshViews);
        MenuItem favorite = item("localFiles.addFavorite", () -> addFavorite(directory));
        MenuItem reveal = new MenuItem(PlatformFileManagerLabel.reveal());
        reveal.setOnAction(ignored -> run(workflow.revealLocal(
                clicked == null ? directory : clicked.path(), clicked != null), false));
        MenuItem copyPath = item("localFiles.copyPath", () -> copyText(values.stream()
                .map(value -> value.path().toString()).toList()));
        MenuItem copyName = item("localFiles.copyName", () -> copyText(values.stream()
                .map(LocalFileEntry::name).toList()));
        CheckMenuItem hidden = new CheckMenuItem(text("sftp.context.hiddenFiles"));
        hidden.setSelected(actions.showHiddenFiles());
        hidden.setAccelerator(localShortcut("sftp.context.hiddenFiles"));
        hidden.setOnAction(ignored -> {
            actions.setShowHiddenFiles(hidden.isSelected());
            refresh();
            rebuildTree();
        });
        CheckMenuItem directoryTree = new CheckMenuItem(text("sftp.context.directoryTree"));
        directoryTree.setSelected(actions.showDirectoryTree());
        directoryTree.setOnAction(ignored -> {
            actions.setShowDirectoryTree(directoryTree.isSelected());
            rebuildContent();
        });
        boolean selected = !values.isEmpty();
        open.setDisable(clicked == null);
        external.setDisable(clicked == null || clicked.directory());
        cut.setDisable(!mutable); copy.setDisable(!mutable); rename.setDisable(!mutable || values.size() != 1);
        delete.setDisable(!mutable); paste.setDisable(directory == null || !hasPasteItems());
        newFolder.setDisable(directory == null); newFile.setDisable(directory == null);
        favorite.setDisable(directory == null);
        reveal.setDisable(directory == null && clicked == null);
        copyPath.setDisable(!selected); copyName.setDisable(!selected);
        menu.getItems().addAll(go, open, external, terminal, refresh, favorite, new SeparatorMenuItem(), cut, copy, paste,
                new SeparatorMenuItem(), rename, delete, newFolder, newFile,
                new SeparatorMenuItem(), copyPath, copyName, reveal,
                new SeparatorMenuItem(), hidden, directoryTree);
        return menu;
    }

    private void navigateText() {
        try { navigate(Path.of(pathField.getText())); }
        catch (InvalidPathException invalid) { fail(text("localFiles.invalidPath")); }
    }

    private void navigate(Path directory) {
        navigate(directory, true);
    }

    private void navigate(Path directory, boolean recordHistory) {
        Path target = normalize(directory);
        long request = ++requestSequence;
        busy(true);
        workflow.listLocal(target).whenComplete((entries, error) -> Platform.runLater(() -> {
            if (closed || request != requestSequence) return;
            busy(false);
            if (error != null) { fail(message(error)); return; }
            Path previous = currentDirectory;
            currentDirectory = target;
            if (recordHistory && previous != null && !previous.equals(target)) {
                backHistory.addLast(previous);
                forwardHistory.clear();
            }
            pathField.setText(target.toString());
            List<LocalFileEntry> visible = entries.stream()
                    .filter(entry -> actions.showHiddenFiles() || !entry.hidden()).toList();
            table.setItems(FXCollections.observableArrayList(withLocalNavigation(target, visible)));
            if (pendingRevealFile != null
                    && Objects.equals(pendingRevealFile.getParent(), target)) {
                Path reveal = pendingRevealFile;
                pendingRevealFile = null;
                table.getItems().stream().filter(entry -> reveal.equals(normalize(entry.path())))
                        .findFirst().ifPresent(entry -> {
                            table.getSelectionModel().clearSelection();
                            table.getSelectionModel().select(entry);
                            table.scrollTo(entry);
                        });
            }
            statisticsLabel.setText(localDirectoryStatistics(visible).summary());
            selectTreePath(target, entries);
            actions.directoryChanged(target);
        }));
    }

    private void refresh() { if (currentDirectory != null) navigate(currentDirectory, false); }

    private void chooseAuthorizedDirectory() {
        chooseAuthorizedDirectory(Path.of(System.getProperty("user.home", ".")));
    }

    private void chooseAuthorizedDirectory(Path suggestedFolder) {
        SandboxFileAccess.chooseDirectory(pathField.getScene().getWindow(), suggestedFolder,
                actions.sandboxFolderBookmarks(), this::navigate, this::fail);
    }

    private void navigateShortcut(Path directory) {
        if (actions.sandboxed() && !actions.sandboxFolderBookmarks().covers(directory)) {
            chooseAuthorizedDirectory(directory);
        } else {
            navigate(directory);
        }
    }

    private void rebuildFavorites() {
        MenuItem add = item("localFiles.addFavorite", () -> addFavorite(currentDirectory));
        Path homePath = normalize(Path.of(System.getProperty("user.home", ".")));
        Path rootPath = homePath.getRoot() == null ? homePath : homePath.getRoot();
        MenuItem root = new MenuItem(text("legacy.java.42ee2863b6d776ff"));
        root.setOnAction(ignored -> navigateShortcut(rootPath));
        MenuItem home = new MenuItem("home");
        home.setOnAction(ignored -> navigateShortcut(homePath));
        MenuItem manage = item("localFiles.manageFavorites", actions::manageFavorites);
        favoritesMenu = ApplicationContextMenus.create(add, manage, new SeparatorMenuItem(),
                root, home, new SeparatorMenuItem());
        if (actions.sandboxed()) {
            SandboxFileAccess.savedFolders(actions.sandboxFolderBookmarks(), this::navigate)
                    .ifPresent(menu -> favoritesMenu.getItems().add(2, menu));
        }
        actions.favoritePaths().stream()
                .distinct().forEach(value -> {
            MenuItem favorite = new MenuItem(value);
            favorite.setMnemonicParsing(false);
            favorite.setOnAction(ignored -> { try { navigate(Path.of(value)); } catch (InvalidPathException failure) { fail(text("localFiles.invalidPath")); } });
            favoritesMenu.getItems().add(favorite);
        });
        favoritesButton.setOnAction(ignored -> {
            if (favoritesMenu.isShowing()) favoritesMenu.hide();
            else {
                rebuildFavorites();
                favoritesMenu.show(favoritesButton, javafx.geometry.Side.BOTTOM, 0.0, 4.0);
            }
        });
    }

    private void addFavorite(Path directory) {
        if (directory == null) return;
        actions.addFavorite(directory);
        rebuildFavorites();
    }

    private void create(Path parent, boolean directory) {
        if (parent == null || closed) return;
        busy(true);
        workflow.listLocal(parent).whenComplete((entries, error) -> Platform.runLater(() -> {
            if (closed) return;
            busy(false);
            if (error != null) { fail(message(error)); return; }
            String base = directory ? "NewFolder" : "NewFile.txt";
            TextInputDialog dialog = new TextInputDialog(uniqueSuggestedName(base, directory, entries));
            dialog.setOnShown(event -> Platform.runLater(dialog.getEditor()::selectAll));
            dialog.setTitle(text(directory ? "localFiles.newFolder" : "localFiles.newFile"));
            dialog.setHeaderText(null);
            dialog.setContentText(text("localFiles.name"));
            dialog.showAndWait().map(String::trim).filter(value -> !value.isEmpty()).ifPresent(value -> {
                Path target = safeChild(parent, value);
                if (target == null || closed) return;
                run(directory ? workflow.createLocalDirectory(target) : workflow.createLocalFile(target), true);
            });
        }));
    }

    private Menu navigationMenu() {
        Menu go = new Menu(text("legacy.java.d69b5ab453f7d2d6"));
        MenuItem back = new MenuItem(text("legacy.java.572cf45ba43634b3"));
        MenuItem forward = new MenuItem(text("legacy.java.d681c6e2947ae79b"));
        MenuItem up = new MenuItem(text("legacy.java.da88ef9c3ec580d2"));
        MenuItem root = new MenuItem(text("legacy.java.42ee2863b6d776ff"));
        MenuItem home = new MenuItem("home");
        back.setDisable(backHistory.isEmpty());
        forward.setDisable(forwardHistory.isEmpty());
        back.setOnAction(ignored -> navigateHistory(backHistory, forwardHistory));
        forward.setOnAction(ignored -> navigateHistory(forwardHistory, backHistory));
        up.setOnAction(ignored -> {
            if (currentDirectory != null) navigate(Optional.ofNullable(currentDirectory.getParent())
                    .orElse(currentDirectory));
        });
        Path homePath = normalize(Path.of(System.getProperty("user.home", ".")));
        root.setOnAction(ignored -> navigateShortcut(homePath.getRoot() == null ? homePath : homePath.getRoot()));
        home.setOnAction(ignored -> navigateShortcut(homePath));
        go.getItems().setAll(back, forward, up, root, home);
        List<String> paths = actions.favoritePaths().stream().distinct().limit(20).toList();
        if (!paths.isEmpty()) {
            go.getItems().add(new SeparatorMenuItem());
            paths.forEach(value -> {
                MenuItem item = new MenuItem(value);
                item.setMnemonicParsing(false);
                item.setOnAction(ignored -> {
                    try { navigate(Path.of(value)); }
                    catch (InvalidPathException failure) { fail(text("localFiles.invalidPath")); }
                });
                go.getItems().add(item);
            });
        }
        return ApplicationContextMenus.styleSubmenu(go);
    }

    private void navigateHistory(ArrayDeque<Path> source, ArrayDeque<Path> destination) {
        if (source.isEmpty() || currentDirectory == null) return;
        destination.addLast(currentDirectory);
        navigate(source.removeLast(), false);
    }

    static String uniqueSuggestedName(
            String base, boolean directory, List<LocalFileEntry> entries) {
        java.util.Set<String> existing = entries.stream()
                .filter(entry -> !blankTableItem(entry))
                .map(LocalFileEntry::name)
                .collect(java.util.stream.Collectors.toSet());
        if (!existing.contains(base)) return base;
        int extension = directory ? -1 : base.lastIndexOf('.');
        String stem = extension > 0 ? base.substring(0, extension) : base;
        String suffix = extension > 0 ? base.substring(extension) : "";
        for (int index = 2; index < 10_000; index++) {
            String candidate = stem + " (" + index + ")" + suffix;
            if (!existing.contains(candidate)) return candidate;
        }
        return base;
    }

    private void rename(List<LocalFileEntry> values) {
        if (values.size() != 1) return;
        LocalFileEntry entry = values.getFirst();
        if (entry.path().getParent() == null || closed) return;
        TextInputDialog dialog = new TextInputDialog(entry.name());
        dialog.setOnShown(event -> Platform.runLater(dialog.getEditor()::selectAll));
        dialog.setTitle(text("localFiles.rename"));
        dialog.setHeaderText(null);
        dialog.setContentText(text("localFiles.name"));
        dialog.showAndWait().map(String::trim).filter(value -> !value.isEmpty())
                .map(name -> safeChild(entry.path().getParent(), name)).filter(Objects::nonNull)
                .filter(target -> !target.equals(entry.path()) && !closed)
                .ifPresent(target -> {
                    busy(true);
                    workflow.renameLocal(entry.path(), target).whenComplete((ignored, error) -> Platform.runLater(() -> {
                        if (closed) return;
                        if (error == null) relocateCurrentDirectory(entry.path(), target);
                        refreshViews();
                        if (error != null) fail(message(error));
                    }));
                });
    }

    private void delete(List<LocalFileEntry> values) {
        if (closed || values.isEmpty() || values.stream().anyMatch(entry -> entry.path().getParent() == null)
                || !confirm("localFiles.deleteConfirm", values.size())) return;
        deleteNext(values, 0);
    }

    private void deleteNext(List<LocalFileEntry> values, int index) {
        deleteNext(values, index, new ArrayList<>());
    }

    private void deleteNext(List<LocalFileEntry> values, int index, List<Path> deleted) {
        if (closed) return;
        if (index >= values.size()) { refreshAfterDeletion(deleted); return; }
        LocalFileEntry entry = values.get(index);
        busy(true);
        workflow.moveLocalToTrash(entry.path()).whenComplete((trashed, error) -> Platform.runLater(() -> {
            if (closed) return;
            if (error == null && Boolean.TRUE.equals(trashed)) {
                leaveDeletedDirectory(entry.path());
                deleted.add(entry.path());
                deleteNext(values, index + 1, deleted);
                return;
            }
            busy(false);
            if (!confirm("localFiles.permanentDeleteConfirm", 1)) { refreshAfterDeletion(deleted); return; }
            workflow.deleteLocal(entry.path()).whenComplete((ignored, deleteError) -> Platform.runLater(() -> {
                if (closed) return;
                if (deleteError != null) { refreshAfterDeletion(deleted); fail(message(deleteError)); return; }
                leaveDeletedDirectory(entry.path());
                deleted.add(entry.path());
                deleteNext(values, index + 1, deleted);
            }));
        }));
    }

    /**
     * Drops deleted folders from the tree and reloads the list, whose listing merges the current folder's tree
     * children. Rebuilding the tree roots would collapse every expanded folder.
     */
    private void refreshAfterDeletion(List<Path> deleted) {
        if (tree != null && tree.getRoot() != null && !deleted.isEmpty()) {
            synchronizingTree = true;
            try {
                SftpBrowserModel.pruneLocalTreeEntries(tree.getRoot(), java.util.Set.copyOf(deleted));
            } finally {
                synchronizingTree = false;
            }
        }
        refresh();
    }

    private void leaveDeletedDirectory(Path deleted) {
        if (currentDirectory != null) {
            currentDirectory = SftpBrowserModel.localDirectoryAfterDeletion(currentDirectory, List.of(deleted));
        }
    }

    private void refreshViews() {
        refresh();
        rebuildTree();
    }

    private void copy(List<LocalFileEntry> values, boolean cut) {
        if (values.isEmpty()) return;
        clipboardPaths.clear();
        clipboardPaths.addAll(values.stream().map(LocalFileEntry::path)
                .map(LocalFileWorkspaceController::normalize).toList());
        clipboardCut = cut;
        ClipboardContent content = new ClipboardContent();
        content.putFiles(clipboardPaths.stream().map(Path::toFile).toList());
        Clipboard.getSystemClipboard().setContent(content);
    }

    private boolean hasPasteItems() {
        return Clipboard.getSystemClipboard().hasFiles();
    }

    private void paste(Path directory) {
        if (directory == null || closed || !hasPasteItems()) return;
        List<Path> sources = Clipboard.getSystemClipboard().getFiles().stream()
                .map(java.io.File::toPath).map(LocalFileWorkspaceController::normalize).toList();
        if (sources.isEmpty()) return;
        pasteNext(sources, 0, clipboardCut && sources.equals(clipboardPaths), normalize(directory));
    }

    private void pasteNext(List<Path> sources, int index, boolean move, Path directory) {
        if (closed) return;
        if (index >= sources.size()) {
            if (move && sources.equals(clipboardPaths)) { clipboardPaths.clear(); clipboardCut = false; }
            refreshViews();
            return;
        }
        Path source = sources.get(index).toAbsolutePath().normalize();
        if (source.getFileName() == null || directory.startsWith(source)) {
            refreshViews();
            fail(text("localFiles.invalidPath"));
            return;
        }
        Path target = directory.resolve(source.getFileName()).normalize();
        if (source.equals(target)) { pasteNext(sources, index + 1, move, directory); return; }
        busy(true);
        workflow.listLocal(directory).whenComplete((entries, listError) -> Platform.runLater(() -> {
            if (closed) return;
            if (listError != null) { refreshViews(); fail(message(listError)); return; }
            boolean targetExists = entries.stream().anyMatch(entry -> normalize(entry.path()).equals(target));
            boolean replace = targetExists && confirm("localFiles.replaceConfirm", 1);
            if (closed) return;
            if (targetExists && !replace) { pasteNext(sources, index + 1, move, directory); return; }
            CompletionStage<Void> operation = move ? workflow.moveLocal(source, target, replace)
                    : workflow.copyLocal(source, target, replace);
            operation.whenComplete((ignored, error) -> Platform.runLater(() -> {
                if (closed) return;
                if (error != null) { refreshViews(); fail(message(error)); return; }
                if (move) relocateCurrentDirectory(source, target);
                pasteNext(sources, index + 1, move, directory);
            }));
        }));
    }

    private List<LocalFileEntry> selected() {
        return transferableLocalEntries(List.copyOf(table.getSelectionModel().getSelectedItems()));
    }

    void navigateTo(Path directory) { navigate(directory); }

    void revealFile(Path file) {
        Path target = normalize(file);
        Path parent = target.getParent();
        if (parent == null) return;
        pendingRevealFile = target;
        navigate(parent, false);
    }

    void applyDisplaySettings(boolean showHiddenFiles, boolean showDirectoryTree) {
        rebuildContent();
        refresh();
    }

    private void rebuildContent() {
        treeLoads.clear();
        contentHost.getChildren().clear();
        if (tableSurface.getParent() instanceof SplitPane parent) {
            parent.getItems().remove(tableSurface);
        } else if (tableSurface.getParent() instanceof Pane parent) {
            parent.getChildren().remove(tableSurface);
        }
        if (!actions.showDirectoryTree()) {
            tree = null;
            contentHost.getChildren().setAll(tableSurface);
            return;
        }
        tree = new CenteredTreeView<>();
        tree.setShowRoot(false);
        tree.setCellFactory(ignored -> configureTreeCell(MainWindowController.localFileTreeCell()));
        TreeItem<LocalFileEntry> hiddenRoot = new TreeItem<>();
        tree.setRoot(hiddenRoot);
        installTreeBranchLoader(hiddenRoot, this::loadTreeChildren);
        tree.getSelectionModel().selectedItemProperty().addListener((ignored, previous, selected) -> {
            if (!synchronizingTree && selected != null && selected.getValue() != null) {
                navigate(selected.getValue().path());
            }
        });
        tree.setOnKeyPressed(event -> {
            TreeItem<LocalFileEntry> selected = tree.getSelectionModel().getSelectedItem();
            LocalFileEntry entry = selected == null ? null : selected.getValue();
            handleFileKey(event, entry == null ? List.of() : transferableLocalEntries(List.of(entry)),
                    entry == null ? currentDirectory : entry.path());
        });
        tree.setOnContextMenuRequested(event -> {
            contextMenu(null, true).show(tree, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        SplitPane split = new SplitPane(tree, tableSurface);
        split.widthProperty().addListener((ignored, previous, width) -> {
            if (width.doubleValue() <= 0.0) return;
            double treeWidth = previous.doubleValue() <= 0.0
                    ? DEFAULT_TREE_WIDTH
                    : split.getDividerPositions()[0] * previous.doubleValue();
            split.setDividerPositions(treeDividerPosition(width.doubleValue(), treeWidth));
        });
        contentHost.getChildren().setAll(split);
        rebuildTree();
    }

    static double defaultTreeDividerPosition(double totalWidth) {
        return treeDividerPosition(totalWidth, DEFAULT_TREE_WIDTH);
    }

    static double treeDividerPosition(double totalWidth, double treeWidth) {
        if (totalWidth <= 0.0) return 0.36;
        return Math.max(0.10, Math.min(0.45, treeWidth / totalWidth));
    }

    private TreeCell<LocalFileEntry> configureTreeCell(TreeCell<LocalFileEntry> cell) {
        cell.setOnContextMenuRequested(event -> {
            if (cell.getItem() == null) return;
            synchronizingTree = true;
            try { tree.getSelectionModel().select(cell.getTreeItem()); }
            finally { synchronizingTree = false; }
            tree.requestFocus();
            contextMenu(cell.getItem(), true).show(cell, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        cell.setOnDragDetected(event -> {
            LocalFileEntry entry = cell.getItem();
            if (entry == null || blankTableItem(entry)) return;
            draggedEntries = List.of(entry);
            pendingDrop = null;
            ClipboardContent content = new ClipboardContent();
            content.putFiles(List.of(entry.path().toFile()));
            TerminalPathDragSupport.putLocalPaths(content, List.of(entry.path().toString()));
            cell.startDragAndDrop(TransferMode.COPY_OR_MOVE).setContent(content);
            event.consume();
        });
        moves.installTarget(cell, () -> cell.getItem() != null && cell.getItem().directory()
                ? cell.getItem().path() : null);
        cell.setOnDragDone(event -> finishDrag());
        return cell;
    }

    private void finishDrag() {
        draggedEntries = List.of();
        moves.clearHighlight();
        Runnable action = pendingDrop;
        pendingDrop = null;
        if (action != null && !closed) Platform.runLater(action);
    }

    void close() {
        closed = true;
        pendingDrop = null;
        draggedEntries = List.of();
        if (moves != null) moves.clearHighlight();
    }

    private void rebuildTree() {
        long generation = ++treeGeneration;
        TreeView<LocalFileEntry> currentTree = tree;
        if (currentTree == null || currentTree.getRoot() == null) return;
        workflow.localRoots().whenComplete((roots, error) -> Platform.runLater(() -> {
            if (closed || currentTree != tree || generation != treeGeneration) return;
            if (error != null) { fail(message(error)); return; }
            synchronizingTree = true;
            try {
                currentTree.getRoot().getChildren().setAll(roots.stream()
                        .filter(entry -> actions.showHiddenFiles() || !entry.hidden())
                        .map(MainWindowController::localTreeItem).toList());
            } finally {
                synchronizingTree = false;
            }
            if (currentDirectory != null) selectTreePath(currentDirectory, null);
        }));
    }

    private void loadTreeChildren(TreeItem<LocalFileEntry> item) {
        loadTreeChildrenAsync(item);
    }

    private CompletionStage<Void> loadTreeChildrenAsync(TreeItem<LocalFileEntry> item) {
        LocalFileEntry directory = item.getValue();
        if (directory == null || !directory.directory()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> inProgress = treeLoads.get(item);
        if (inProgress != null) return inProgress;
        if (!hasTreePlaceholder(item)) return CompletableFuture.completedFuture(null);
        CompletableFuture<Void> loaded = new CompletableFuture<>();
        long generation = treeGeneration;
        treeLoads.put(item, loaded);
        workflow.listLocal(directory.path()).whenComplete((entries, error) -> Platform.runLater(() -> {
            treeLoads.remove(item);
            if (closed || generation != treeGeneration) {
                loaded.completeExceptionally(new java.util.concurrent.CancellationException("Local tree was replaced"));
                return;
            }
            if (error != null) {
                item.setExpanded(false);
                loaded.completeExceptionally(error);
                return;
            }
            replaceTreeChildren(item, visibleTreeDirectories(entries, LocalFileEntry::directory,
                    LocalFileEntry::hidden, actions.showHiddenFiles()).stream()
                    .map(MainWindowController::localTreeItem).toList());
            loaded.complete(null);
        }));
        return loaded;
    }

    /**
     * Selects {@code target} in the tree. When the file list has just loaded it, {@code listing} fills and expands
     * the node directly instead of leaving it collapsed or listing the directory a second time.
     */
    private void selectTreePath(Path target, List<LocalFileEntry> listing) {
        long generation = treeGeneration;
        TreeView<LocalFileEntry> currentTree = tree;
        if (currentTree == null || currentTree.getRoot() == null) return;
        TreeItem<LocalFileEntry> match = currentTree.getRoot().getChildren().stream()
                .filter(item -> target.startsWith(item.getValue().path()))
                .max(Comparator.comparingInt(item -> item.getValue().path().getNameCount()))
                .orElse(null);
        if (match == null) return;
        ensureTreeNode(match, target).whenComplete((exact, error) -> Platform.runLater(() -> {
            if (closed || generation != treeGeneration || error != null || exact == null || currentTree != tree
                    || currentDirectory == null || !currentDirectory.equals(target)) return;
            synchronizingTree = true;
            try {
                if (listing != null && !treeLoads.containsKey(exact)) {
                    SftpBrowserModel.populateTreeChildren(exact, visibleTreeDirectories(listing,
                            LocalFileEntry::directory, LocalFileEntry::hidden, actions.showHiddenFiles()),
                            LocalFileEntry::path, MainWindowController::localTreeItem);
                    exact.setExpanded(true);
                }
                SftpBrowserModel.expandParents(exact);
                currentTree.getSelectionModel().select(exact);
                if (currentTree instanceof CenteredTreeView<?> centered) {
                    centered.centerSelection();
                } else {
                    currentTree.scrollTo(currentTree.getRow(exact));
                }
            } finally {
                synchronizingTree = false;
            }
        }));
    }

    CompletionStage<TreeItem<LocalFileEntry>> ensureTreeNode(
            TreeItem<LocalFileEntry> current, Path target) {
        if (current.getValue().path().equals(target)) {
            return CompletableFuture.completedFuture(current);
        }
        return loadTreeChildrenAsync(current).thenCompose(ignored -> {
            TreeItem<LocalFileEntry> child = current.getChildren().stream()
                    .filter(item -> item.getValue() != null
                            && target.startsWith(item.getValue().path()))
                    .max(Comparator.comparingInt(
                            item -> item.getValue().path().getNameCount()))
                    .orElse(null);
            if (child == null) child = MainWindowController.addLazyLocalTreeChild(current, target);
            return child == null ? CompletableFuture.completedFuture(current)
                    : ensureTreeNode(child, target);
        });
    }

    private static void copyText(List<String> values) {
        if (values.isEmpty()) return;
        ClipboardContent content = new ClipboardContent();
        content.putString(String.join(System.lineSeparator(), values));
        Clipboard.getSystemClipboard().setContent(content);
    }

    private Path safeChild(Path parent, String name) {
        try {
            Path child = Path.of(name);
            if (child.getNameCount() != 1 || child.isAbsolute() || ".".equals(name) || "..".equals(name)
                    || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
                fail(text("localFiles.invalidName")); return null;
            }
            return parent.resolve(child).normalize();
        } catch (InvalidPathException invalid) { fail(text("localFiles.invalidName")); return null; }
    }

    private void run(CompletionStage<?> operation, boolean refreshAfter) {
        busy(true);
        operation.whenComplete((ignored, error) -> Platform.runLater(() -> {
            if (closed) return;
            busy(false);
            if (refreshAfter) refreshViews();
            if (error != null) fail(message(error));
        }));
    }

    private boolean confirm(String key, int count) {
        Alert alert = confirmationAlert(text(key, count));
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    static Alert confirmationAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle(text("action.confirm"));
        alert.setHeaderText(null);
        return alert;
    }

    private void busy(boolean value) { progress.setManaged(value); progress.setVisible(value); }
    private void fail(String value) { busy(false); actions.status(value, true); }
    private static String message(Throwable error) {
        Throwable value = error;
        while (value.getCause() != null) value = value.getCause();
        return value.getMessage() == null ? value.getClass().getSimpleName() : value.getMessage();
    }
    private static Path normalize(Path path) { return path.toAbsolutePath().normalize(); }
    static boolean isTextFile(LocalFileEntry entry) {
        if (entry == null || entry.directory()) return false;
        String name = entry.name().toLowerCase(java.util.Locale.ROOT);
        if (SyntaxHighlighting.language(name) != null) return true;
        int dot = name.lastIndexOf('.');
        if (dot < 0) return true;
        return switch (name.substring(dot + 1)) {
            case "txt", "text", "log", "out", "csv", "tsv" -> true;
            default -> false;
        };
    }
    private static String text(String key, Object... args) { return com.ynpe.fastremote.i18n.Texts.text(key, args); }
    private static final java.util.Map<String, KeyCombination> LOCAL_SHORTCUTS = java.util.Map.of(
            "localFiles.cut", KeyCombination.valueOf("Shortcut+X"),
            "localFiles.copy", KeyCombination.valueOf("Shortcut+C"),
            "localFiles.paste", KeyCombination.valueOf("Shortcut+V"),
            "localFiles.rename", KeyCombination.valueOf(ShortcutAction.renameDefault(System.getProperty("os.name", ""))),
            "localFiles.delete", KeyCombination.valueOf("Delete"),
            "localFiles.refresh", KeyCombination.valueOf("F5"),
            "localFiles.newFolder", KeyCombination.valueOf("Shortcut+Shift+N"),
            "editor.edit", KeyCombination.valueOf("Shortcut+E"),
            "sftp.context.hiddenFiles", KeyCombination.valueOf("Alt+Shift+H"));

    private static KeyCombination localShortcut(String key) { return LOCAL_SHORTCUTS.get(key); }

    private static MenuItem item(String key, Runnable action) {
        MenuItem item = new MenuItem(text(key));
        item.setId(key);
        item.setAccelerator(localShortcut(key));
        item.setOnAction(ignored -> action.run());
        return item;
    }
}
