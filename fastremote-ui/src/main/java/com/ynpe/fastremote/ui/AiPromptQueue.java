// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.ai.*;
import com.ynpe.fastremote.domain.ai.*;
import java.util.*;
import java.util.concurrent.*;

/** One conversation's bounded outbox. Restored and failed runs always require an explicit resume. */
final class AiPromptQueue {
    enum State { QUEUED, WAITING, UNCERTAIN, ACCEPTED }
    record Entry(UUID id, String text, List<AiChatImage> images, State state) {
        Entry { images = List.copyOf(images); new AiPendingPrompt(id, text, images, state != State.QUEUED); }
        List<AiImageInput> inputs() { return images.stream().map(image -> new AiImageInput(image.mediaType(),
                image.data(), image.width(), image.height(), image.thumbnailData())).toList(); }
        Entry with(State value) { return new Entry(id, text, images, value); }
    }
    private final List<Entry> entries = new ArrayList<>();
    private CompletionStage<Void> persistence = CompletableFuture.completedFuture(null);
    boolean paused;
    List<Entry> entries() { return List.copyOf(entries); }
    boolean isEmpty() { return entries.isEmpty(); }
    int size() { return entries.size(); }
    void restore(List<AiPendingPrompt> prompts) {
        if (prompts.size() > 8) throw new IllegalArgumentException("AI queue is too large");
        entries.clear();
        for (var prompt : prompts) entries.add(new Entry(prompt.id(), prompt.text(), prompt.images(),
                prompt.steering() ? State.UNCERTAIN : State.QUEUED));
        paused = !entries.isEmpty();
    }
    Entry add(String text, List<AiImageInput> images) {
        if (entries.size() >= 8) throw new IllegalStateException("AI queue is full");
        var entry = new Entry(UUID.randomUUID(), text, images.stream().map(image -> new AiChatImage(UUID.randomUUID(),
                image.mediaType(), image.data(), image.width(), image.height(), image.thumbnailData())).toList(), State.QUEUED);
        entries.add(entry); return entry;
    }
    Optional<Entry> find(UUID id) { return entries.stream().filter(entry -> entry.id().equals(id)).findFirst(); }
    void state(UUID id, State state) { replace(find(id).orElseThrow().with(state)); }
    void edit(UUID id, String text) {
        Entry entry = find(id).orElseThrow();
        if (entry.state() == State.WAITING || entry.state() == State.ACCEPTED) return;
        replace(new Entry(id, text, entry.images(), State.QUEUED));
    }
    private void replace(Entry replacement) {
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).id().equals(replacement.id())) {
            entries.set(i, replacement); return;
        }
    }
    void remove(UUID id) { entries.removeIf(entry -> entry.id().equals(id)); }
    Optional<Entry> next() {
        if (paused || entries.isEmpty() || entries.getFirst().state() != State.QUEUED) return Optional.empty();
        return Optional.of(entries.getFirst());
    }
    void finish(boolean failed) {
        entries.removeIf(entry -> entry.state() == State.ACCEPTED);
        for (var entry : List.copyOf(entries)) if (entry.state() == State.WAITING) {
            replace(entry.with(State.UNCERTAIN)); paused = true;
        }
        if (failed) paused = true;
    }
    void persistenceFailed() {
        paused = true;
        for (var entry : List.copyOf(entries)) if (entry.state() != State.QUEUED) replace(entry.with(State.UNCERTAIN));
    }
    CompletionStage<Void> save(AiAssistantWorkflow workflow, UUID sessionId) {
        var snapshot = entries.stream().map(entry -> new AiPendingPrompt(entry.id(), entry.text(), entry.images(),
                entry.state() != State.QUEUED)).toList();
        // Recover the persistence chain on a later explicit edit; never execute an unsaved message.
        persistence = persistence.handle((ignored, error) -> null).thenCompose(ignored -> workflow.savePending(sessionId, snapshot));
        return persistence;
    }
}
