// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui.chat;

import com.ynpe.fastremote.i18n.Texts;
import com.ynpe.fastremote.ui.LucideIconView;
import java.text.BreakIterator;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.animation.AnimationTimer;
import javafx.beans.binding.Bindings;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Path;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.fxmisc.flowless.Cell;
import org.fxmisc.flowless.VirtualFlow;
import org.fxmisc.flowless.VirtualizedScrollPane;

/** A read-only, virtualized document. All cells share one logical text selection. */
public final class StreamDocumentView extends BorderPane implements AutoCloseable {
    private final ObservableList<ChatDocument.Row> rows = FXCollections.observableArrayList();
    private final VirtualFlow<ChatDocument.Row, RowCell> flow = VirtualFlow.createVertical(rows, RowCell::new);
    private final Consumer<String> action;
    private final Consumer<String> link;
    private final Consumer<String> clipboard;
    private final Function<String, Image> images;
    private ChatDocument document = ChatDocument.empty();
    private final LongProperty documentVersion = new SimpleLongProperty();
    private int anchor, caret;
    private boolean dragging, closed;
    private double dragX, dragY;
    private final ContextMenu menu;
    private final TextField search = new TextField();
    private final HBox findBar;
    private final EventHandler<MouseEvent> sceneMouse = this::sceneMouse;
    private final ChangeListener<Scene> sceneChanged = (v, before, after) -> {
        if (before != null) before.removeEventFilter(MouseEvent.ANY, sceneMouse);
        if (after != null && !closed) after.addEventFilter(MouseEvent.ANY, sceneMouse);
    };
    private final AnimationTimer autoscroll = new AnimationTimer() {
        private long last;
        @Override public void handle(long now) {
            if (!dragging || !isVisible()) { stop(); return; }
            if (last != 0 && now - last < 16_000_000) return;
            last = now;
            Point2D point = flow.sceneToLocal(dragX, dragY);
            double delta = point.getY() < 16 ? -12 : point.getY() > flow.getHeight() - 16 ? 12 : 0;
            if (delta != 0) { flow.scrollYBy(delta); flow.layout(); select(anchor, hit(dragX, dragY)); }
        }
    };

    public StreamDocumentView(Consumer<String> action, Consumer<String> link, Function<String, Image> images) {
        this(action, link, images, new ContextMenu());
    }
    public StreamDocumentView(Consumer<String> action, Consumer<String> link, Function<String, Image> images, ContextMenu menu) {
        this(action, link, images, menu, StreamDocumentView::copy);
    }
    public StreamDocumentView(Consumer<String> action, Consumer<String> link, Function<String, Image> images,
            ContextMenu menu, Consumer<String> clipboard) {
        this.clipboard = Objects.requireNonNull(clipboard);
        this.menu = Objects.requireNonNull(menu);
        this.action = Objects.requireNonNull(action); this.link = Objects.requireNonNull(link); this.images = images;
        getStyleClass().add("chat-document");
        setFocusTraversable(true); setAccessibleRole(AccessibleRole.TEXT_AREA);
        setAccessibleText(Texts.text("web.chatContent"));
        flow.setFocusTraversable(false);
        focusedProperty().addListener((observable, before, after) -> flow.visibleCells().forEach(RowCell::paintSelection));
        var scroller = new VirtualizedScrollPane<>(flow);
        setCenter(scroller);
        search.setPromptText(Texts.text("web.findChat"));
        search.setOnAction(e -> find(false));
        Button previous = new Button("↑"), next = new Button("↓"), close = new Button("×");
        previous.setAccessibleText(Texts.text("web.previousMatch")); next.setAccessibleText(Texts.text("web.nextMatch"));
        close.setAccessibleText(Texts.text("web.closeFind"));
        previous.setOnAction(e -> find(true)); next.setOnAction(e -> find(false));
        close.setOnAction(e -> { setTop(null); requestFocus(); });
        findBar = new HBox(6, search, previous, next, close); findBar.getStyleClass().add("chat-find");
        HBox.setHgrow(search, Priority.ALWAYS);
        addEventHandler(MouseEvent.MOUSE_PRESSED, this::pressed);
        addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!dragging) return;
            dragX = e.getSceneX(); dragY = e.getSceneY(); select(anchor, hit(dragX, dragY)); e.consume();
        });
        addEventHandler(MouseEvent.MOUSE_RELEASED, e -> finishSelectionGesture());
        addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (buttonTarget(e.getTarget())) return;
            if (e.getButton() != MouseButton.PRIMARY || anchor != caret || !e.isStillSincePress()) return;
            int index = document.rowAt(caret);
            if (index < 0) return;
            var row = rows.get(index);
            if (!row.action().isEmpty()) action.accept(row.action());
            else if (!row.imageId().isEmpty()) action.accept("image:" + row.imageId());
            else {
                int offset = document.start(index);
                for (var cell : row.cells()) {
                    for (var run : cell.runs()) {
                        if (caret >= offset && caret < offset + run.text().length() && !run.link().isEmpty()) {
                            link.accept(run.link()); return;
                        }
                        offset += run.text().length();
                    }
                    offset++;
                }
            }
        });
        addEventHandler(KeyEvent.KEY_PRESSED, this::key);
        setOnContextMenuRequested(e -> {
            if (descendant(e.getTarget(), findBar)) return;
            int index = document.rowAt(hit(e.getSceneX(), e.getSceneY()));
            menu.getItems().setAll(item("web.copy", this::copySelection), item("web.selectAll", () -> select(0, document.text().length())),
                    item("web.findEllipsis", this::showFind));
            menu.getItems().getFirst().setDisable(anchor == caret);
            if (index >= 0 && rows.get(index).action().isEmpty()) {
                String messageId = rows.get(index).messageId();
                menu.getItems().add(item("web.copyThisMessage", () -> action.accept("copy:" + messageId)));
            }
            menu.getItems().add(item("web.copyAllMessages", () -> action.accept("copy-all")));
            menu.show(this, e.getScreenX(), e.getScreenY()); e.consume();
        });
        sceneProperty().addListener(sceneChanged);
    }
    private static MenuItem item(String key, Runnable run) {
        var item = new MenuItem(Texts.text(key)); item.setOnAction(e -> run.run()); return item;
    }
    public ChatDocument document() { return document; }
    public String selectedText() { return document.text().substring(Math.min(anchor, caret), Math.max(anchor, caret)); }
    public int selectionStart() { return Math.min(anchor, caret); }
    public int selectionEnd() { return Math.max(anchor, caret); }
    public int visibleCellCount() { return flow.visibleCells().size(); }

    public void replaceDocument(ChatDocument next) {
        if (closed) return;
        boolean follow = rows.isEmpty() || (anchor == caret && atBottom());
        String visibleKey = null; double visibleOffset = 0;
        if (!rows.isEmpty() && !flow.visibleCells().isEmpty()) {
            var first = flow.visibleCells().getFirst(); visibleKey = first.row.key();
            visibleOffset = flow.cellToViewport(first, first.getNode().getLayoutBounds()).getMinY();
        }
        int nextAnchor = next.resolve(document.anchor(anchor), document);
        int nextCaret = next.resolve(document.anchor(caret), document);
        var old = document.rows(); var replacement = next.rows();
        int prefix = 0, suffix = 0;
        while (prefix < old.size() && prefix < replacement.size() && old.get(prefix).equals(replacement.get(prefix))) prefix++;
        while (suffix < old.size() - prefix && suffix < replacement.size() - prefix
                && old.get(old.size() - 1 - suffix).equals(replacement.get(replacement.size() - 1 - suffix))) suffix++;
        document = next;
        if (old.size() - suffix > prefix) rows.subList(prefix, old.size() - suffix).clear();
        rows.addAll(prefix, replacement.subList(prefix, replacement.size() - suffix));
        documentVersion.set(documentVersion.get() + 1);
        select(Math.max(0, nextAnchor), Math.max(0, nextCaret));
        if (follow) scrollToBottom();
        else if (visibleKey != null) {
            int index = next.index(visibleKey);
            if (index >= 0) flow.showAtOffset(index, visibleOffset);
        }
        notifyAccessibleAttributeChanged(AccessibleAttribute.TEXT);
    }
    private boolean atBottom() {
        return flow.getLastVisibleIndex() >= rows.size() - 1
                && flow.totalHeightEstimateProperty().getValue() - flow.estimatedScrollYProperty().getValue() <= flow.getHeight() + 8;
    }
    public void scrollToBottom() { if (!rows.isEmpty()) flow.showAsLast(rows.size() - 1); }
    public void clearSelection() { select(caret, caret); finishSelectionGesture(); }
    public void finishSelectionGesture() { dragging = false; autoscroll.stop(); }
    public void select(int from, int to) {
        anchor = boundary(Math.clamp(from, 0, document.text().length()));
        caret = boundary(Math.clamp(to, 0, document.text().length()));
        flow.visibleCells().forEach(RowCell::paintSelection);
        notifyAccessibleAttributeChanged(AccessibleAttribute.SELECTION_START);
        notifyAccessibleAttributeChanged(AccessibleAttribute.SELECTION_END);
        notifyAccessibleAttributeChanged(AccessibleAttribute.CARET_OFFSET);
    }
    private int boundary(int position) {
        if (position > 0 && position < document.text().length() && Character.isLowSurrogate(document.text().charAt(position))
                && Character.isHighSurrogate(document.text().charAt(position - 1))) return position - 1;
        return position;
    }
    public void copySelection() { if (anchor != caret) clipboard.accept(selectedText()); }
    public static void copy(String text) { var content = new ClipboardContent(); content.putString(text); Clipboard.getSystemClipboard().setContent(content); }
    private static boolean descendant(Object target, Node parent) {
        for (Node node = target instanceof Node n ? n : null; node != null; node = node.getParent()) if (node == parent) return true;
        return false;
    }
    private static boolean buttonTarget(Object target) {
        for (Node n = target instanceof Node node ? node : null; n != null; n = n.getParent())
            if (n instanceof ButtonBase) return true;
        return false;
    }
    private void sceneMouse(MouseEvent e) {
        if (e.getEventType() == MouseEvent.MOUSE_RELEASED) finishSelectionGesture();
        else if (e.getEventType() == MouseEvent.MOUSE_PRESSED && e.getButton() == MouseButton.PRIMARY && !descendant(e.getTarget(), this)) clearSelection();
    }
    private void pressed(MouseEvent e) {
        if (e.getButton() != MouseButton.PRIMARY || descendant(e.getTarget(), findBar) || buttonTarget(e.getTarget())) return;
        requestFocus(); menu.hide(); int position = hit(e.getSceneX(), e.getSceneY());
        if (e.getClickCount() >= 3) {
            int row = document.rowAt(position); if (row >= 0) select(document.start(row), document.start(row) + rows.get(row).text().length());
        } else if (e.getClickCount() == 2) {
            BreakIterator words = BreakIterator.getWordInstance(); words.setText(document.text());
            int start = words.preceding(Math.min(position + 1, document.text().length()));
            int end = words.following(position); select(Math.max(0, start), end < 0 ? document.text().length() : end);
        } else select(e.isShiftDown() ? anchor : position, position);
        dragging = true; dragX = e.getSceneX(); dragY = e.getSceneY(); autoscroll.start(); e.consume();
    }
    private int hit(double sceneX, double sceneY) {
        RowCell nearest = null; double distance = Double.MAX_VALUE;
        for (RowCell cell : flow.visibleCells()) {
            Bounds b = cell.node.localToScene(cell.node.getBoundsInLocal());
            double d = sceneY < b.getMinY() ? b.getMinY() - sceneY : Math.max(0, sceneY - b.getMaxY());
            if (d < distance) { nearest = cell; distance = d; }
        }
        return nearest == null ? 0 : nearest.hit(sceneX, sceneY);
    }
    private void showFind() { setTop(findBar); search.requestFocus(); search.selectAll(); }
    private void find(boolean backwards) {
        String needle = search.getText(); if (needle.isEmpty()) return;
        String text = document.text();
        // regionMatches avoids locale-dependent case folding changing UTF-16 offsets.
        int found = -1, n = text.length();
        for (int step = 0; step <= n; step++) {
            int p = Math.floorMod((backwards ? selectionStart() - 1 - step : selectionEnd() + step), n + 1);
            if (text.regionMatches(true, p, needle, 0, needle.length())) { found = p; break; }
        }
        if (found >= 0) { select(found, found + needle.length()); flow.show(document.rowAt(found)); }
    }
    private void key(KeyEvent e) {
        if (descendant(e.getTarget(), findBar)) { if (e.getCode() == KeyCode.ESCAPE) { setTop(null); requestFocus(); e.consume(); } return; }
        if (buttonTarget(e.getTarget())) return;
        if (e.isShortcutDown()) {
            if (e.getCode() == KeyCode.C) { copySelection(); e.consume(); return; }
            if (e.getCode() == KeyCode.A) { select(0, document.text().length()); e.consume(); return; }
            if (e.getCode() == KeyCode.F) { showFind(); e.consume(); return; }
        }
        int next = caret;
        switch (e.getCode()) {
            case LEFT, RIGHT -> {
                boolean forward = e.getCode() == KeyCode.RIGHT;
                if (!e.isShiftDown() && anchor != caret) next = forward ? selectionEnd() : selectionStart();
                else {
                    BreakIterator it = e.isControlDown() || e.isAltDown() ? BreakIterator.getWordInstance() : BreakIterator.getCharacterInstance();
                    it.setText(document.text()); next = forward ? it.following(caret) : it.preceding(caret);
                    if (next < 0) next = forward ? document.text().length() : 0;
                }
            }
            case HOME -> next = e.isShortcutDown() ? 0 : rowEdge(false);
            case END -> next = e.isShortcutDown() ? document.text().length() : rowEdge(true);
            case UP, DOWN -> next = vertical(e.getCode() == KeyCode.DOWN);
            case PAGE_UP, PAGE_DOWN -> { flow.scrollYBy((e.getCode() == KeyCode.PAGE_DOWN ? 1 : -1) * flow.getHeight() * .85); e.consume(); return; }
            case ENTER, SPACE -> { int index = document.rowAt(caret); if (index >= 0 && !rows.get(index).action().isEmpty()) action.accept(rows.get(index).action()); e.consume(); return; }
            case ESCAPE -> { clearSelection(); menu.hide(); e.consume(); return; }
            default -> { return; }
        }
        select(e.isShiftDown() ? anchor : next, next);
        int row = document.rowAt(caret); if (row >= 0) flow.show(row); e.consume();
    }
    private int rowEdge(boolean end) { int row = document.rowAt(caret); return row < 0 ? 0 : document.start(row) + (end ? rows.get(row).text().length() : 0); }
    private int vertical(boolean down) {
        int index = document.rowAt(caret); if (index < 0) return 0;
        RowCell cell = flow.getCell(index); Point2D point = cell.caretPoint(caret);
        int candidate = hit(point.getX(), point.getY() + (down ? 22 : -22));
        if (candidate != caret) return candidate;
        int next = Math.clamp(index + (down ? 1 : -1), 0, rows.size() - 1);
        return Math.min(document.start(next) + caret - document.start(index), document.start(next) + rows.get(next).text().length());
    }
    @Override public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        return switch (attribute) {
            case TEXT -> document.text(); case SELECTION_START -> selectionStart(); case SELECTION_END -> selectionEnd();
            case CARET_OFFSET -> caret; case EDITABLE -> false;
            default -> super.queryAccessibleAttribute(attribute, parameters);
        };
    }
    @Override public void executeAccessibleAction(AccessibleAction a, Object... p) {
        if (a == AccessibleAction.SET_TEXT_SELECTION) select((Integer)p[0], (Integer)p[1]);
        else super.executeAccessibleAction(a, p);
    }
    @Override public void close() {
        if (closed) return; closed = true; finishSelectionGesture(); menu.hide();
        if (getScene() != null) getScene().removeEventFilter(MouseEvent.ANY, sceneMouse);
        sceneProperty().removeListener(sceneChanged); flow.dispose(); rows.clear(); document = ChatDocument.empty();
    }

    private final class RowCell implements Cell<ChatDocument.Row, VBox> {
        private final ChatDocument.Row row;
        private final VBox node = new VBox();
        private final VBox body = new VBox();
        private final List<Segment> segments = new ArrayList<>();
        RowCell(ChatDocument.Row row) {
            this.row = row; node.setMinWidth(0); node.setMaxWidth(Double.MAX_VALUE);
            node.getStyleClass().addAll("chat-row", "chat-" + row.kind().toLowerCase(Locale.ROOT));
            for (String style : row.style().split(" +")) if (!style.isEmpty()) node.getStyleClass().add("chat-" + style);
            body.getStyleClass().add("chat-body"); body.setMinWidth(0); node.getChildren().add(body);
            row.code().ifPresent(code -> {
                Label language = new Label(code.language()); language.getStyleClass().add("chat-code-language");
                Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
                LucideIconView icon = new LucideIconView(); icon.setIcon(LucideIconView.Icon.COPY);
                icon.setScaleX(.55); icon.setScaleY(.55);
                Button copy = new Button(Texts.text("web.copy"), new Group(icon));
                copy.getStyleClass().add("chat-code-copy"); copy.setMinWidth(Region.USE_PREF_SIZE);
                copy.setAccessibleText(Texts.text("chat.copyCode")); copy.setTooltip(new Tooltip(Texts.text("chat.copyCode")));
                copy.setOnAction(e -> {
                    int index = document.index(row.key());
                    if (index >= 0) document.rows().get(index).code().ifPresent(current -> {
                        clipboard.accept(current.text()); copy.setText(Texts.text("chat.copied"));
                    });
                });
                copy.setOnMouseExited(e -> copy.setText(Texts.text("web.copy")));
                HBox header = new HBox(6, language, spacer, copy); header.setAlignment(Pos.CENTER_LEFT);
                header.getStyleClass().add("chat-code-header"); body.getChildren().add(header);
            });
            var columns = new HBox(); columns.setMinWidth(0); body.getChildren().add(columns);
            for (var content : row.cells()) {
                Segment segment = new Segment(content); segments.add(segment); HBox.setHgrow(segment, Priority.ALWAYS);
                segment.prefWidthProperty().bind(columns.widthProperty().divide(row.cells().size())); columns.getChildren().add(segment);
            }
            if (row.kind().equals("USER")) {
                node.setFillWidth(false); node.setAlignment(Pos.TOP_RIGHT);
                Text probe = new Text();
                probe.getStyleClass().add("chat-width-probe"); probe.setManaged(false); probe.setVisible(false);
                node.getChildren().add(probe);
                body.prefWidthProperty().bind(Bindings.createDoubleBinding(() -> {
                    probe.setText(document.userWidthSample(row.messageId()));
                    double preferred = document.wideUserMessage(row.messageId()) ? 640 : probe.getLayoutBounds().getWidth() + 28;
                    return Math.max(1, Math.min(Math.max(1, node.getWidth() - 28) * .84, Math.min(640, Math.max(72, preferred))));
                }, node.widthProperty(), probe.fontProperty(), documentVersion));
                body.maxWidthProperty().bind(body.prefWidthProperty());
            }
            if (!row.imageId().isEmpty()) {
                Image image = images.apply(row.imageId());
                if (image != null) {
                    ImageView thumbnail = new ImageView(image); thumbnail.setPreserveRatio(true); thumbnail.setFitHeight(160);
                    thumbnail.fitWidthProperty().bind(body.widthProperty().subtract(24).map(w -> Math.max(1, Math.min(280, w.doubleValue()))));
                    body.getChildren().add(thumbnail);
                }
            }
        }
        @Override public VBox getNode() { return node; }
        private int hit(double x, double y) {
            int index = document.index(row.key()); if (index < 0) return 0;
            for (int column = 0; column < segments.size(); column++) {
                Segment segment = segments.get(column); Bounds bounds = segment.localToScene(segment.getBoundsInLocal());
                if (x <= bounds.getMaxX() || column == segments.size() - 1) {
                    Point2D local = segment.text.sceneToLocal(x, y);
                    int insertion = segment.text.hitTest(local).getInsertionIndex();
                    return document.cellStart(index, column) + Math.clamp(insertion, 0, row.cells().get(column).text().length());
                }
            }
            return document.start(index);
        }
        private Point2D caretPoint(int offset) {
            int index = document.index(row.key());
            for (int column = 0; column < segments.size(); column++) {
                Segment segment = segments.get(column); int start = document.cellStart(index, column);
                if (offset <= start + row.cells().get(column).text().length()) {
                    Path shape = new Path(segment.text.caretShape(Math.max(0, offset - start), true)); Bounds b = shape.getBoundsInLocal();
                    return segment.text.localToScene(b.getMinX(), (b.getMinY() + b.getMaxY()) / 2);
                }
            }
            return node.localToScene(0, 0);
        }
        private void paintSelection() { segments.forEach(Segment::paint); }
        private final class Segment extends Pane {
            private final TextFlow text = new TextFlow();
            private final Path selection = new Path();
            private final Path cursor = new Path();
            Segment(ChatDocument.Cell content) {
                setMinWidth(0); selection.setMouseTransparent(true); selection.getStyleClass().add("chat-selection");
                cursor.setMouseTransparent(true); cursor.getStyleClass().add("chat-caret");
                text.setMouseTransparent(true); text.setLineSpacing(row.style().contains("code") ? 0 : 2);
                for (var run : content.runs()) {
                    Text t = new Text(run.text()); t.getStyleClass().add("chat-text");
                    for (String style : run.style().split(" +")) if (!style.isEmpty()) t.getStyleClass().add("chat-" + style);
                    text.getChildren().add(t);
                }
                if (content.text().isEmpty()) { Text blank = new Text("\u200b"); blank.getStyleClass().add("chat-text"); text.getChildren().add(blank); }
                getChildren().addAll(selection, text, cursor);
            }
            @Override public Orientation getContentBias() { return Orientation.HORIZONTAL; }
            @Override protected double computePrefHeight(double width) { return Math.max(19, text.prefHeight(Math.max(1, width))); }
            @Override protected double computePrefWidth(double height) { return 1; }
            @Override protected void layoutChildren() { text.resizeRelocate(0, 0, getWidth(), getHeight()); text.layout(); paint(); }
            void paint() {
                int index = document.index(row.key()), column = segments.indexOf(this);
                if (index < 0 || column < 0) return;
                int start = document.cellStart(index, column), length = row.cells().get(column).text().length();
                int from = Math.clamp(selectionStart() - start, 0, length), to = Math.clamp(selectionEnd() - start, 0, length);
                selection.getElements().setAll(from < to ? text.rangeShape(from, to) : new javafx.scene.shape.PathElement[0]);
                boolean showCaret = StreamDocumentView.this.isFocused() && anchor == caret && caret >= start && caret <= start + length;
                cursor.getElements().setAll(showCaret ? text.caretShape(caret - start, true) : new javafx.scene.shape.PathElement[0]);
            }
        }
    }
}
