// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.ai.*;
import com.ynpe.fastremote.domain.ai.*;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Ordered streaming projection; model callbacks never mutate a different conversation's view. */
final class AiRunPresentation implements AiStreamListener {
    interface Handle { void update(String text, boolean complete); }
    interface View { Handle append(String kind, String text, boolean complete, String group, List<AiImageInput> images); }
    record Saved(String kind, String text, List<AiImageInput> images) { }
    private static final int TEXT_LIMIT = 500 * 1024;
    private final String group = "stream:" + UUID.randomUUID();
    private final View view;
    private final Consumer<AiSteeringInput> accepted;
    private final List<Item> items = new ArrayList<>();
    private final Map<String, Item> tools = new HashMap<>();
    private final ArrayDeque<Runnable> events = new ArrayDeque<>();
    private boolean scheduled, closed;
    private Item answer, reasoning;
    private final PauseTransition pulse = new PauseTransition(Duration.millis(32));
    private static final class Item {
        final String kind;
        final List<AiImageInput> images;
        final StringBuilder text = new StringBuilder();
        Handle handle;
        boolean complete, dirty = true;
        Item(String kind, List<AiImageInput> images) { this.kind = kind; this.images = List.copyOf(images); }
    }
    AiRunPresentation(View view, Consumer<AiSteeringInput> accepted) {
        this.view = view; this.accepted = accepted;
        pulse.setOnFinished(event -> flush());
    }
    String groupId() { return group; }
    private void enqueue(Runnable event) {
        synchronized (events) {
            if (closed) return;
            events.addLast(event);
            if (scheduled) return;
            scheduled = true;
        }
        Platform.runLater(() -> { if (!closed) pulse.playFromStart(); });
    }
    private void flush() {
        List<Runnable> batch;
        synchronized (events) { batch = List.copyOf(events); events.clear(); scheduled = false; }
        batch.forEach(Runnable::run);
        for (Item item : items) {
            if (!item.dirty) continue;
            item.dirty = false;
            if (item.handle == null) item.handle = view.append(item.kind, item.text.toString(), item.complete, group, item.images);
            else item.handle.update(item.text.toString(), item.complete);
        }
    }
    private Item add(String kind, String text, List<AiImageInput> images) {
        Item item = new Item(kind, images); item.text.append(text); items.add(item); return item;
    }
    private void boundary() {
        if (answer != null) { answer.complete = true; answer.dirty = true; }
        if (reasoning != null) { reasoning.complete = true; reasoning.dirty = true; }
        answer = null; reasoning = null;
    }
    private void delta(boolean thought, String delta) {
        if (delta == null || delta.isEmpty()) return;
        enqueue(() -> {
            int offset = 0;
            while (offset < delta.length()) {
                Item item = thought ? reasoning : answer;
                if (item == null || item.text.length() >= TEXT_LIMIT) {
                    if (item != null) { item.complete = true; item.dirty = true; }
                    item = add(thought ? "REASONING" : "ASSISTANT", "", List.of());
                    if (thought) reasoning = item; else answer = item;
                }
                int end = Math.min(delta.length(), offset + TEXT_LIMIT - item.text.length());
                if (end < delta.length() && Character.isHighSurrogate(delta.charAt(end - 1))) end++;
                item.text.append(delta, offset, end); item.dirty = true; offset = end;
            }
        });
    }
    @Override public void onSegmentStarted(String id) { enqueue(this::boundary); }
    @Override public void onTextDelta(String delta) { delta(false, delta); }
    @Override public void onReasoningDelta(String delta) { delta(true, delta); }
    @Override public void onToolStarted(String id, String name) {
        enqueue(() -> { boundary(); tools.put(id, add("TOOL", "… " + name, List.of())); });
    }
    @Override public void onToolProgress(String id, String message) {
        enqueue(() -> {
            Item item = tools.get(id);
            if (item == null || message == null || message.isBlank()) return;
            item.text.setLength(0); item.text.append("… ").append(message, 0, Math.min(message.length(), 4096));
            item.dirty = true;
        });
    }
    @Override public void onToolFinished(String id, AiToolExecution execution) {
        enqueue(() -> {
            Item item = tools.remove(id);
            if (item == null) { boundary(); item = add("TOOL", "", List.of()); }
            item.text.setLength(0); item.text.append(toolText(execution));
            item.complete = true; item.dirty = true;
        });
    }
    private static String toolText(AiToolExecution execution) {
        if (!execution.succeeded()) return "✗ " + execution.summary();
        return switch (execution.name()) {
            case "run_terminal_command", "run_local_terminal_command" ->
                    "↪ " + com.ynpe.fastremote.i18n.Texts.text("ai.run.terminalSubmitted");
            case "read_workspace_operation" ->
                    "↪ " + com.ynpe.fastremote.i18n.Texts.text("ai.run.operationStatusRead");
            default -> "✓ " + execution.summary();
        };
    }
    @Override public void onToolExecution(AiToolExecution execution) { onToolFinished(UUID.randomUUID().toString(), execution); }
    @Override public void onSteeringAccepted(AiSteeringInput input) {
        enqueue(() -> { boundary(); var item = add("USER", input.text(), input.images()); item.complete = true; accepted.accept(input); });
    }
    List<Saved> finish(AiAssistantReply reply) {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Must finish on FX thread");
        pulse.stop();
        synchronized (events) { closed = true; }
        flush();
        if (reply != null) {
            if (items.stream().noneMatch(item -> item.kind.equals("REASONING")) && !reply.reasoningSummary().isBlank())
                add("REASONING", reply.reasoningSummary(), List.of());
            if (items.stream().noneMatch(item -> item.kind.equals("ASSISTANT")) && !reply.text().isBlank())
                add("ASSISTANT", reply.text(), List.of());
            if (items.stream().noneMatch(item -> item.kind.equals("TOOL"))) for (var tool : reply.toolExecutions())
                add("TOOL", toolText(tool), List.of());
        }
        for (var tool : tools.values()) tool.text.append(" — ").append(com.ynpe.fastremote.i18n.Texts.text("ai.run.unfinished"));
        items.forEach(item -> { item.complete = true; item.dirty = true; }); flush();
        return items.stream().map(item -> new Saved(item.kind, item.text.toString(), item.images)).toList();
    }
}
