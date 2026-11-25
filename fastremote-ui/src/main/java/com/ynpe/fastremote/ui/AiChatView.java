// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.i18n.Texts;
import com.ynpe.fastremote.ui.chat.ChatDocument;
import com.ynpe.fastremote.ui.chat.ChatMarkdown;
import com.ynpe.fastremote.ui.chat.StreamDocumentView;
import java.awt.Desktop;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/** Native chat adapter; every target uses the same document and selection implementation. */
public final class AiChatView extends StackPane implements AutoCloseable {
    static final int MAX_MESSAGES = 100;
    static final int MAX_CHARACTERS = 8 * 1024 * 1024;
    static final int MAX_MESSAGE_CHARACTERS = 512 * 1024;
    private final LinkedHashMap<String, Message> messages = new LinkedHashMap<>();
    private final Set<String> expanded = new HashSet<>();
    private final Consumer<String> status;
    private final Consumer<String> imageOpen;
    private final Consumer<String> clipboard;
    private final StreamDocumentView view;
    private final PauseTransition flush = new PauseTransition(Duration.millis(32));
    private final ThreadPoolExecutor parserExecutor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), r -> Thread.ofPlatform().daemon().name("chat-document-parser").unstarted(r),
            new ThreadPoolExecutor.DiscardOldestPolicy());
    private final ExecutorService links = Executors.newSingleThreadExecutor(r -> Thread.ofPlatform().daemon().name("chat-link-opener").unstarted(r));
    private final AtomicBoolean openingLink = new AtomicBoolean(), deliveryScheduled = new AtomicBoolean();
    private final AtomicReference<Projection> ready = new AtomicReference<>();
    private Map<String, Image> thumbnails = Map.of();
    private volatile boolean closed;
    private long generation, revision, appliedRevision;
    private final ChatMarkdown parser = new ChatMarkdown(AiChatView::highlightCode); // parser thread only
    private final Map<String, Image> decodedImages = new HashMap<>(); // parser thread only
    private final Map<String, Parsed> cache = new HashMap<>(); // parser thread only
    private record Parsed(String markdown, List<ChatMarkdown.Block> blocks) { }
    private record Projection(long generation, long revision, ChatDocument document, Map<String, Image> images) { }

    AiChatView(Consumer<String> status) { this(status, ignored -> { }); }
    AiChatView(Consumer<String> status, Consumer<String> imageOpen) {
        this(status, imageOpen, StreamDocumentView::copy);
    }
    AiChatView(Consumer<String> status, Consumer<String> imageOpen, Consumer<String> clipboard) {
        this.clipboard = Objects.requireNonNull(clipboard);
        this.status = Objects.requireNonNull(status); this.imageOpen = Objects.requireNonNull(imageOpen);
        getStyleClass().add("ai-chat-view");
        view = new StreamDocumentView(this::action, this::openLink, id -> thumbnails.get(id), ApplicationContextMenus.create(), clipboard);
        getChildren().add(view); flush.setOnFinished(e -> submit());
    }
    void replaceConversation(List<Message> replacement) {
        requireFx(); if (closed) return;
        generation++; messages.clear(); expanded.clear(); ready.set(null);
        for (Message message : replacement) messages.put(message.id(), message);
        bound(); thumbnails = Map.of(); view.replaceDocument(ChatDocument.empty()); schedule();
    }
    void appendMessage(Message message) {
        requireFx(); if (closed) return;
        messages.put(message.id(), message); bound(); schedule();
    }
    void updateMessage(String id, String markdown, boolean complete) {
        requireFx(); if (closed) return;
        Message old = messages.get(id); if (old == null) return;
        messages.put(id, new Message(id, old.kind(), old.author(), markdown, complete, old.groupId(), old.images()));
        bound(); schedule();
    }
    private void bound() {
        long length = messages.values().stream().mapToLong(m -> m.markdown().length()).sum();
        long imageBytes = messages.values().stream().flatMap(m -> m.images().stream()).mapToLong(i -> i.thumbnail.length).sum();
        while (messages.size() > MAX_MESSAGES || length > MAX_CHARACTERS || imageBytes > 16 * 1024 * 1024) {
            String id = messages.keySet().iterator().next(); Message removed = messages.remove(id); length -= removed.markdown().length();
            imageBytes -= removed.images().stream().mapToLong(i -> i.thumbnail.length).sum();
        }
        Set<String> groups = new HashSet<>(); String turn = "initial";
        for (Message m : messages.values()) {
            if (m.kind().equals("USER")) turn = m.id();
            groups.add(group(m, turn) + ":REASONING");
            if (m.groupId().startsWith("stream:")) groups.add(m.groupId() + ":" + m.id() + ":REASONING"); groups.add(group(m, turn) + ":TOOL");
        }
        expanded.retainAll(groups);
    }
    void clearConversation() {
        requireFx(); if (closed) return;
        generation++; revision++; flush.stop(); parserExecutor.getQueue().clear(); ready.set(null);
        messages.clear(); expanded.clear(); thumbnails = Map.of(); view.replaceDocument(ChatDocument.empty()); view.clearSelection();
        appliedRevision = revision;
        parserExecutor.execute(() -> { cache.clear(); decodedImages.clear(); });
    }
    void scrollToBottom() { view.scrollToBottom(); }
    void clearSelection() { view.clearSelection(); }
    void finishSelectionGesture() { view.finishSelectionGesture(); }
    StreamDocumentView documentView() { return view; }
    int messageCount() { return messages.size(); }
    boolean readyForTesting() { return !closed && appliedRevision == revision && flush.getStatus() != javafx.animation.Animation.Status.RUNNING; }
    private void schedule() { revision++; if (flush.getStatus() != javafx.animation.Animation.Status.RUNNING) flush.playFromStart(); }
    private void submit() {
        if (closed) return;
        List<Message> snapshot = List.copyOf(messages.values()); Set<String> open = Set.copyOf(expanded);
        long gen = generation, rev = revision;
        // Localized strings are captured with the snapshot; the parser does no UI work.
        String reasoning = Texts.text("web.reasoningSummary"), operations = Texts.text("web.operations");
        parserExecutor.execute(() -> {
            try {
                Projection projection = project(gen, rev, snapshot, open, reasoning, operations);
                if (closed) return; ready.set(projection); deliver();
            } catch (CancellationException ignored) {
                // Closing the view interrupts the worker and drops its pending projection.
            } catch (RuntimeException error) {
                Platform.runLater(() -> { if (!closed && gen == generation) status.accept(Texts.text("legacy.java.5318d622a6b0f3da")); });
            }
        });
    }
    private void deliver() {
        if (!deliveryScheduled.compareAndSet(false, true)) return;
        Platform.runLater(() -> {
            deliveryScheduled.set(false); Projection projection = ready.getAndSet(null);
            if (!closed && projection != null && projection.generation == generation && projection.revision > appliedRevision) {
                thumbnails = projection.images; view.replaceDocument(projection.document); appliedRevision = projection.revision;
            }
            if (ready.get() != null && !closed) deliver();
        });
    }
    private Projection project(long gen, long rev, List<Message> snapshot, Set<String> open, String reasoning, String operations) {
        var ids = new HashSet<String>(); snapshot.forEach(m -> ids.add(m.id())); cache.keySet().retainAll(ids);
        var imageIds = new HashSet<String>(); snapshot.forEach(m -> m.images().forEach(i -> imageIds.add(i.id())));
        decodedImages.keySet().retainAll(imageIds);
        Map<String, List<Message>> groups = new LinkedHashMap<>(); Map<String, String> messageGroups = new HashMap<>();
        Map<String, String> after = new HashMap<>(); String turn = "initial";
        for (Message message : snapshot) {
            if (message.groupId().startsWith("stream:")) continue;
            if (message.kind().equals("USER")) turn = message.id();
            String group = group(message, turn); messageGroups.put(message.id(), group);
            if (foldable(message)) groups.computeIfAbsent(group, ignored -> new ArrayList<>()).add(message);
            else if (message.kind().equals("ASSISTANT") || !after.containsKey(group)) after.put(group, message.id());
        }
        var rows = new ArrayList<ChatDocument.Row>(); var images = new HashMap<String, Image>(); var rendered = new HashSet<String>();
        for (Message message : snapshot) {
            if (message.groupId().startsWith("stream:")) {
                if (message.kind().equals("REASONING") && message.complete())
                    appendGroup(rows, List.of(message), message.groupId() + ":" + message.id(), open, reasoning, operations, images);
                else append(rows, message, images);
                continue;
            }
            if (foldable(message)) continue;
            append(rows, message, images);
            String group = messageGroups.get(message.id());
            if (message.id().equals(after.get(group))) {
                appendGroup(rows, groups.getOrDefault(group, List.of()), group, open, reasoning, operations, images); rendered.add(group);
            }
        }
        for (var group : groups.entrySet()) if (!rendered.contains(group.getKey()))
            appendGroup(rows, group.getValue(), group.getKey(), open, reasoning, operations, images);
        return new Projection(gen, rev, new ChatDocument(rows), Map.copyOf(images));
    }
    private static String group(Message message, String fallback) { return message.groupId().isBlank() ? "turn:" + fallback : message.groupId(); }
    private static boolean foldable(Message message) { return message.kind().equals("REASONING") || message.kind().equals("TOOL"); }
    private void appendGroup(List<ChatDocument.Row> rows, List<Message> group, String id, Set<String> open,
                             String reasoning, String operations, Map<String, Image> images) {
        for (String kind : List.of("REASONING", "TOOL")) {
            List<Message> matching = group.stream().filter(m -> m.kind().equals(kind)).toList(); if (matching.isEmpty()) continue;
            String key = id + ":" + kind; boolean isOpen = open.contains(key);
            String label = (isOpen ? "▾ " : "▸ ") + (kind.equals("REASONING") ? reasoning : operations) + " (" + matching.size() + ")";
            rows.add(new ChatDocument.Row("fold:" + key, "fold:" + key, kind, "fold", List.of(ChatDocument.Cell.text(label)), "fold:" + key, ""));
            if (isOpen) for (Message message : matching) append(rows, message, images);
        }
    }
    private void append(List<ChatDocument.Row> rows, Message message, Map<String, Image> images) {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException();
        int first = rows.size();
        if (!message.kind().equals("USER") && !message.kind().equals("ASSISTANT"))
            rows.add(new ChatDocument.Row(message.id() + ":author", message.id(), message.kind(), "author",
                    List.of(ChatDocument.Cell.text(message.author())), "", ""));
        Parsed parsed = cache.get(message.id());
        if (parsed == null || !parsed.markdown.equals(message.markdown())) {
            parsed = new Parsed(message.markdown(), parser.parse(message.markdown())); cache.put(message.id(), parsed);
        }
        int index = 0;
        for (var block : parsed.blocks()) rows.add(new ChatDocument.Row(message.id() + ":" + index++, message.id(), message.kind(), block.style(), block.cells(), "", "", block.code()));
        for (var image : message.images()) {
            Image decoded = decodedImages.computeIfAbsent(image.id(), ignored ->
                    new Image(new ByteArrayInputStream(image.thumbnail), 280, 160, true, true));
            if (!decoded.isError()) images.put(image.id(), decoded);
            rows.add(new ChatDocument.Row(message.id() + ":image:" + image.id(), message.id(), message.kind(), "image",
                    List.of(ChatDocument.Cell.text(image.alt())), "", image.id()));
        }
        rows.set(first, rows.get(first).withStyle(rows.get(first).style() + " message-first"));
        int last = rows.size() - 1;
        rows.set(last, rows.get(last).withStyle(rows.get(last).style() + " message-last"));
    }
    private static List<ChatDocument.Run> highlightCode(String text, String language) {
        // Highlight bounded code only; unsupported language labels remain readable plain text.
        String syntax = SyntaxHighlighting.language("code." + language.toLowerCase(Locale.ROOT));
        if (syntax == null || text.length() > 128 * 1024) return List.of(new ChatDocument.Run(text, "code", ""));
        var spans = SyntaxHighlighting.compute(text, syntax); var runs = new ArrayList<ChatDocument.Run>(); int offset = 0;
        for (var span : spans) {
            int end = Math.min(text.length(), offset + span.getLength());
            runs.add(new ChatDocument.Run(text.substring(offset, end), "code " + String.join(" ", span.getStyle()), "")); offset = end;
        }
        return runs;
    }
    private void action(String value) {
        if (value.startsWith("fold:")) { String key = value.substring(5); if (!expanded.remove(key)) expanded.add(key); schedule(); }
        else if (value.startsWith("image:")) imageOpen.accept(value.substring(6));
        else if (value.startsWith("copy:")) { Message m = messages.get(value.substring(5)); if (m != null) clipboard.accept(m.markdown()); }
        else if (value.equals("copy-all")) clipboard.accept(String.join("\n\n", messages.values().stream().map(m -> m.author() + "\n" + m.markdown()).toList()));
    }
    private void openLink(String value) {
        URI uri = validatedExternalUri(value);
        if (uri == null) { status.accept(Texts.text("legacy.java.a0f2b779f8ea463e")); return; }
        if (!openingLink.compareAndSet(false, true) || closed) return;
        links.execute(() -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) Desktop.getDesktop().browse(uri);
                else reportLinkFailure();
            } catch (IOException | SecurityException error) { reportLinkFailure(); }
            finally { openingLink.set(false); }
        });
    }
    private void reportLinkFailure() { Platform.runLater(() -> { if (!closed) status.accept(Texts.text("legacy.java.c9d050f4689ed402")); }); }
    static URI validatedExternalUri(String value) {
        try {
            URI uri = URI.create(Objects.requireNonNullElse(value, ""));
            if (("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) && uri.getHost() != null && uri.getUserInfo() == null) return uri;
        } catch (IllegalArgumentException ignored) { /* Model URLs are untrusted. */ }
        return null;
    }
    private static void requireFx() { if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Chat view requires the JavaFX thread"); }
    @Override public void close() {
        requireFx(); if (closed) return; closed = true; generation++; flush.stop(); parserExecutor.shutdownNow(); links.shutdownNow();
        messages.clear(); expanded.clear(); ready.set(null); thumbnails = Map.of(); view.close();
    }
    record Message(String id, String kind, String author, String markdown, boolean complete, String groupId, List<ImageAttachment> images) {
        Message(String id, String kind, String author, String markdown, boolean complete) { this(id, kind, author, markdown, complete, "", List.of()); }
        Message(String id, String kind, String author, String markdown, boolean complete, String groupId) { this(id, kind, author, markdown, complete, groupId, List.of()); }
        Message {
            Objects.requireNonNull(id); Objects.requireNonNull(kind); author = Objects.requireNonNullElse(author, "");
            markdown = Objects.requireNonNullElse(markdown, ""); groupId = Objects.requireNonNullElse(groupId, ""); images = List.copyOf(images);
            if (markdown.length() > MAX_MESSAGE_CHARACTERS) throw new IllegalArgumentException("Chat message exceeds display limit");
            if (images.size() > 8) throw new IllegalArgumentException("Too many chat attachments");
        }
    }
    record ImageAttachment(String id, byte[] thumbnail, String alt) {
        ImageAttachment { Objects.requireNonNull(id); thumbnail = thumbnail.clone(); alt = Objects.requireNonNullElse(alt, "");
            if (thumbnail.length > 4 * 1024 * 1024) throw new IllegalArgumentException("Chat thumbnail exceeds limit"); }
        @Override public byte[] thumbnail() { return thumbnail.clone(); }
    }
}
