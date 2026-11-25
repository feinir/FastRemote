// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.command.CommandPaletteUsage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Skin;
import javafx.scene.control.TextField;
import javafx.scene.control.skin.ListViewSkin;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/** Keyboard-first global action and saved-host search. */
final class CommandPalette {
    private CommandPalette() { }

    static Optional<Entry> show(Window owner, List<Entry> entries) {
        return show(owner, entries, Map.of());
    }

    static Optional<Entry> show(
            Window owner, List<Entry> entries, Map<String, CommandPaletteUsage> usageByKey) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(usageByKey, "usageByKey");
        Stage palette = new Stage(StageStyle.TRANSPARENT);
        palette.initOwner(owner);
        palette.initModality(Modality.WINDOW_MODAL);
        palette.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.4b495d8b87b864fa"));
        palette.setResizable(false);
        AtomicReference<Entry> selectedResult = new AtomicReference<>();
        Navigation navigation = new Navigation(entries);
        Label scope = new Label();
        scope.getStyleClass().add("command-palette-scope");
        scope.setMaxWidth(Double.MAX_VALUE);
        scope.setManaged(false);
        scope.setVisible(false);

        TextField search = new TextField();
        search.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b012cda0afba8764"));
        search.setAccessibleText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.69b2f1be38073226"));
        search.getStyleClass().add("command-palette-search");
        EdgeAwareListView<Entry> results = new EdgeAwareListView<>();
        results.setAccessibleText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.64bca89339a9195c"));
        results.getStyleClass().add("command-palette-results");
        results.setCellFactory(ignored -> entryCell());
        results.setPlaceholder(new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.cc95185bf173902d")));
        results.setPrefHeight(390.0);
        Label count = new Label();

        Runnable rebuild = () -> {
            results.getItems().setAll(filter(navigation.entries(), search.getText(), usageByKey));
            results.setPlaceholder(new Label(com.ynpe.fastremote.i18n.Texts.text(
                    navigation.inSubmenu() && navigation.entries().isEmpty()
                            ? "workspace.goTo.empty" : "legacy.java.cc95185bf173902d")));
            count.setText(results.getItems().size() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.de7e6a48a54d3352"));
            if (!results.getItems().isEmpty()) results.getSelectionModel().selectFirst();
        };
        Runnable accept = () -> {
            Entry selected = results.getSelectionModel().getSelectedItem();
            if (selected != null) {
                if (navigation.enter(selected, search.getText())) {
                    scope.setText(selected.title() + " · " + selected.detail());
                    scope.setManaged(true);
                    scope.setVisible(true);
                    search.clear();
                    rebuild.run();
                    search.requestFocus();
                    return;
                }
                selectedResult.set(selected);
                palette.hide();
            }
        };
        search.textProperty().addListener((ignored, previous, value) -> rebuild.run());
        InputMethodEnterState searchEnterState = InputMethodEnterState.track(search);
        search.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.DOWN) {
                selectRelative(results, 1);
                event.consume();
            } else if (event.getCode() == KeyCode.UP) {
                selectRelative(results, -1);
                event.consume();
            }
        });
        search.addEventFilter(javafx.scene.input.KeyEvent.KEY_RELEASED, event -> {
            if (event.getCode() != KeyCode.ENTER) return;
            if (searchEnterState.inputMethodOwnsKeyRelease(event.getCode())) return;
            accept.run();
            event.consume();
        });
        results.setOnKeyReleased(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                accept.run();
                event.consume();
            }
        });
        results.setOnMouseClicked(event -> {
            ListCell<?> clicked = clickedResultCell(event.getTarget(), results);
            if (shouldAcceptMouseClick(event.getButton(), event.getClickCount(),
                    clicked != null && !clicked.isEmpty())) {
                results.getSelectionModel().select(clicked.getIndex());
                accept.run();
                event.consume();
            }
        });

        LucideIconView searchIcon = new LucideIconView(LucideIconView.Icon.COMMAND);
        searchIcon.getStyleClass().add("command-palette-search-icon");
        HBox searchBar = new HBox(8.0, searchIcon, search);
        searchBar.getStyleClass().add("command-palette-search-bar");
        HBox.setHgrow(search, Priority.ALWAYS);

        count.getStyleClass().add("command-palette-count");
        Label hint = new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.84edf823b4993de6"));
        hint.getStyleClass().add("command-palette-hint");
        Region footerSpace = new Region();
        HBox footer = new HBox(count, footerSpace, hint);
        footer.getStyleClass().add("command-palette-footer");
        HBox.setHgrow(footerSpace, Priority.ALWAYS);

        VBox shell = new VBox(searchBar, scope, results, footer);
        shell.getStyleClass().add("command-palette-shell");
        VBox.setVgrow(results, Priority.ALWAYS);
        StackPane root = new StackPane(shell);
        root.getStyleClass().add("command-palette-overlay");
        StackPane.setMargin(shell, new Insets(18.0));
        double width = owner == null ? 760.0 : Math.min(820.0, Math.max(520.0, owner.getWidth() - 96.0));
        double height = owner == null ? 520.0 : Math.min(560.0, Math.max(380.0, owner.getHeight() - 150.0));
        root.setPrefSize(width, height);
        Scene scene = new Scene(root, width, height, Color.TRANSPARENT);
        ThemeManager.style(root);
        palette.setScene(scene);
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                if (navigation.inSubmenu()) {
                    search.setText(navigation.back());
                    scope.setManaged(false);
                    scope.setVisible(false);
                    rebuild.run();
                    search.requestFocus();
                } else palette.hide();
                event.consume();
            }
        });
        palette.focusedProperty().addListener((ignored, wasFocused, focused) -> {
            if (Boolean.TRUE.equals(wasFocused) && !focused) palette.hide();
        });
        rebuild.run();
        count.setText(results.getItems().size() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.de7e6a48a54d3352"));
        palette.setOpacity(0.0);
        palette.setOnShown(event -> {
            centerAboveOwner(palette, owner);
            Platform.runLater(() -> {
                search.requestFocus();
                palette.setOpacity(1.0);
            });
        });
        LinuxDialogModality.prepare(palette);
        palette.showAndWait();
        return Optional.ofNullable(selectedResult.get());
    }

    static boolean shouldAcceptMouseClick(MouseButton button, int clickCount, boolean clickedNonEmptyResult) {
        return button == MouseButton.PRIMARY && clickCount == 1 && clickedNonEmptyResult;
    }

    private static ListCell<?> clickedResultCell(Object target, ListView<?> results) {
        if (!(target instanceof Node node)) return null;
        for (Node current = node; current != null && current != results; current = current.getParent()) {
            if (current instanceof ListCell<?> cell && cell.getListView() == results) return cell;
        }
        return null;
    }

    private static void centerAboveOwner(Stage palette, Window owner) {
        javafx.geometry.Rectangle2D bounds = owner == null
                ? Screen.getPrimary().getVisualBounds()
                : new javafx.geometry.Rectangle2D(owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight());
        palette.setX(Math.rint(bounds.getMinX() + (bounds.getWidth() - palette.getWidth()) / 2.0));
        palette.setY(Math.rint(bounds.getMinY() + Math.max(46.0, bounds.getHeight() * 0.10)));
    }

    static List<Entry> filter(List<Entry> entries, String query) {
        return filter(entries, query, Map.of());
    }

    static List<Entry> filter(
            List<Entry> entries, String query, Map<String, CommandPaletteUsage> usageByKey) {
        String normalized = normalize(query);
        String[] tokens = normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
        List<ScoredEntry> matches = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            int score = normalized.isEmpty() ? 0 : score(entry, tokens, normalized);
            if (score < Integer.MAX_VALUE) {
                CommandPaletteUsage usage = usageByKey.get(entry.usageKey());
                boolean contextual = entry.defaultSection() == DefaultSection.CONTEXT;
                long useCount = usage == null || contextual ? 0 : usage.useCount();
                Instant lastUsedAt = usage == null || contextual ? Instant.EPOCH : usage.lastUsedAt();
                int adjustedScore = score - (int) Math.min(8, useCount);
                int defaultSection = contextual ? 0 : normalized.isEmpty() ? entry.defaultSection().ordinal() : 1;
                matches.add(new ScoredEntry(
                        entry, defaultSection, adjustedScore, useCount, lastUsedAt, index));
            }
        }
        return matches.stream().sorted(Comparator.comparingInt(ScoredEntry::defaultSection)
                        .thenComparingInt(ScoredEntry::score)
                        .thenComparing(Comparator.comparingLong(ScoredEntry::useCount).reversed())
                        .thenComparing(Comparator.comparing(ScoredEntry::lastUsedAt).reversed())
                        .thenComparingInt(ScoredEntry::originalIndex))
                .map(ScoredEntry::entry).toList();
    }

    static List<String> enabledSearchTerms(boolean pinyinEnabled, String... terms) {
        return java.util.Arrays.stream(terms)
                .filter(term -> !term.startsWith("pinyin:") || pinyinEnabled)
                .map(term -> term.startsWith("pinyin:") ? term.substring("pinyin:".length()) : term)
                .toList();
    }

    private static int score(Entry entry, String[] tokens, String query) {
        String title = normalize(entry.title());
        int score = title.equals(query) ? -20 : 0;
        List<String> terms = new ArrayList<>(entry.searchTerms().size() + 2);
        terms.add(title);
        terms.add(normalize(entry.detail()));
        entry.searchTerms().stream().map(CommandPalette::normalize).forEach(terms::add);
        for (String token : tokens) {
            int best = Integer.MAX_VALUE;
            for (int index = 0; index < terms.size(); index++) {
                int candidate = termScore(terms.get(index), token, index == 0 ? 0 : index == 1 ? 8 : 3);
                best = Math.min(best, candidate);
            }
            if (best == Integer.MAX_VALUE) return Integer.MAX_VALUE;
            score += best;
        }
        return score;
    }

    private static int termScore(String term, String token, int base) {
        if (term.equals(token)) return base;
        if (term.startsWith(token)) return base + 2;
        if (term.contains(token)) return base + 4;
        return Integer.MAX_VALUE;
    }

    private static String normalize(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC)
                .strip().toLowerCase(Locale.ROOT);
    }

    private static void selectRelative(EdgeAwareListView<Entry> results, int offset) {
        int size = results.getItems().size();
        if (size == 0) return;
        int selected = results.getSelectionModel().getSelectedIndex();
        int next = Math.max(0, Math.min(size - 1, selected + offset));
        results.getSelectionModel().select(next);
        results.ensureVisible(next);
    }

    /** Keeps keyboard selection stationary until it crosses a viewport edge. */
    static final class EdgeAwareListView<T> extends ListView<T> {
        @Override
        protected Skin<?> createDefaultSkin() {
            return new EdgeAwareListViewSkin<>(this);
        }

        void ensureVisible(int index) {
            Skin<?> currentSkin = getSkin();
            if (currentSkin instanceof EdgeAwareListViewSkin<?> edgeAwareSkin) {
                edgeAwareSkin.ensureVisible(index);
                return;
            }
            scrollTo(index);
        }
    }

    private static final class EdgeAwareListViewSkin<T> extends ListViewSkin<T> {
        private EdgeAwareListViewSkin(ListView<T> listView) {
            super(listView);
        }

        private void ensureVisible(int index) {
            getVirtualFlow().scrollTo(index);
        }
    }

    private static ListCell<Entry> entryCell() {
        return new ListCell<>() {
            private final LucideIconView icon = new LucideIconView();
            private final Label title = new Label();
            private final Label detail = new Label();
            private final VBox copy = new VBox(2.0, title, detail);
            private final HBox row = new HBox(10.0, icon, copy);
            {
                icon.getStyleClass().add("command-palette-icon");
                title.getStyleClass().add("command-palette-title");
                detail.getStyleClass().add("command-palette-detail");
                row.getStyleClass().add("command-palette-row");
                HBox.setHgrow(copy, Priority.ALWAYS);
            }

            @Override
            protected void updateItem(Entry entry, boolean empty) {
                super.updateItem(entry, empty);
                if (empty || entry == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                icon.setIcon(entry.icon());
                title.setText(entry.title() + (entry.submenu() ? " ›" : ""));
                detail.setText(entry.detail());
                if (getGraphic() != row) setGraphic(row);
                setText(null);
            }
        };
    }

    record Entry(
            String usageKey,
            DefaultSection defaultSection,
            String title,
            String detail,
            List<String> searchTerms,
            LucideIconView.Icon icon,
            Runnable action, List<Entry> children, boolean submenu) {
        Entry {
            usageKey = Objects.requireNonNull(usageKey, "usageKey").strip();
            Objects.requireNonNull(defaultSection, "defaultSection");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(detail, "detail");
            searchTerms = List.copyOf(Objects.requireNonNull(searchTerms, "searchTerms"));
            Objects.requireNonNull(icon, "icon");
            Objects.requireNonNull(action, "action");
            children = List.copyOf(children);
            if (usageKey.isEmpty() || usageKey.length() > 256) {
                throw new IllegalArgumentException("usageKey must contain 1 to 256 characters");
            }
            if (title.isBlank()) throw new IllegalArgumentException("title must not be blank");
        }

        Entry(String usageKey, DefaultSection section, String title, String detail,
                List<String> searchTerms, LucideIconView.Icon icon, Runnable action) {
            this(usageKey, section, title, detail, searchTerms, icon, action, List.of(), false);
        }

        static Entry contextual(String id, String title, String detail, List<String> terms,
                LucideIconView.Icon icon, Runnable action) {
            return new Entry(id, DefaultSection.CONTEXT, title, detail, terms, icon, action);
        }

        static Entry submenu(String id, String title, String detail, List<String> terms,
                LucideIconView.Icon icon, List<Entry> children) {
            return new Entry(id, DefaultSection.CONTEXT, title, detail, terms, icon, () -> {}, children, true);
        }

        Entry(String title, String detail, List<String> searchTerms,
                LucideIconView.Icon icon, Runnable action) {
            this(defaultUsageKey(title, detail), DefaultSection.GENERAL,
                    title, detail, searchTerms, icon, action);
        }

        Entry(String title, String detail, String keywords, LucideIconView.Icon icon, Runnable action) {
            this(defaultUsageKey(title, detail), DefaultSection.GENERAL,
                    title, detail, List.of(keywords), icon, action);
        }

        static Entry content(String title, String detail, List<String> searchTerms,
                LucideIconView.Icon icon, Runnable action) {
            return new Entry(defaultUsageKey(title, detail), DefaultSection.CONTENT,
                    title, detail, searchTerms, icon, action);
        }
    }

    private enum DefaultSection {
        CONTEXT,
        GENERAL,
        CONTENT
    }

    /** One bounded drill-down level; leaving it restores the original search. */
    static final class Navigation {
        private final List<Entry> root;
        private Entry parent;
        private String rootQuery = "";

        Navigation(List<Entry> root) { this.root = List.copyOf(root); }
        List<Entry> entries() { return parent == null ? root : parent.children(); }
        boolean inSubmenu() { return parent != null; }
        boolean enter(Entry entry, String query) {
            if (!entry.submenu() || parent != null) return false;
            rootQuery = query;
            parent = entry;
            return true;
        }
        String back() { parent = null; return rootQuery; }
    }

    private static String defaultUsageKey(String title, String detail) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (normalize(title) + '\0' + normalize(detail)).getBytes(StandardCharsets.UTF_8));
            return "entry:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private record ScoredEntry(
            Entry entry, int defaultSection, int score,
            long useCount, Instant lastUsedAt, int originalIndex) { }
}
