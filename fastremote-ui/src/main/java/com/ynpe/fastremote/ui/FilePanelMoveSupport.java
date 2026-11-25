// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.input.TransferMode;

/** Same-panel move routing; existing cross-panel transfer handlers retain their own payloads. */
final class FilePanelMoveSupport<E, P> {
    private static final PseudoClass DROP_TARGET = PseudoClass.getPseudoClass("file-move-target");
    private final Supplier<List<E>> sources;
    private final BiPredicate<E, P> allowed;
    private final BiFunction<E, P, CompletionStage<Void>> move;
    private final Consumer<Runnable> defer;
    private final BooleanSupplier active;
    private final BiConsumer<E, Throwable> failure;
    private final BiConsumer<E, P> moved;
    private final Runnable refresh;
    private boolean running;
    private Node highlighted;

    FilePanelMoveSupport(Supplier<List<E>> sources, BiPredicate<E, P> allowed,
            BiFunction<E, P, CompletionStage<Void>> move, Consumer<Runnable> defer,
            BooleanSupplier active, BiConsumer<E, Throwable> failure, BiConsumer<E, P> moved, Runnable refresh) {
        this.sources = sources;
        this.allowed = allowed;
        this.move = move;
        this.defer = defer;
        this.active = active;
        this.failure = failure;
        this.moved = moved;
        this.refresh = refresh;
    }

    void installTarget(Node node, Supplier<P> directory) {
        var previousOver = node.getOnDragOver();
        var previousDrop = node.getOnDragDropped();
        node.setOnDragOver(event -> {
            if (WorkspacePaneCoordinator.isWorkspaceDragPayload(event.getDragboard())) return;
            List<E> entries = sources.get();
            if (entries.isEmpty()) {
                clearHighlight();
                if (previousOver != null) previousOver.handle(event);
                return;
            }
            boolean accepted = accepts(entries, directory.get());
            clearHighlight();
            if (accepted) {
                event.acceptTransferModes(TransferMode.MOVE);
                highlighted = node;
                node.pseudoClassStateChanged(DROP_TARGET, true);
            }
            event.consume();
        });
        node.setOnDragDropped(event -> {
            if (WorkspacePaneCoordinator.isWorkspaceDragPayload(event.getDragboard())) return;
            clearHighlight();
            List<E> entries = List.copyOf(sources.get());
            if (entries.isEmpty()) {
                if (previousDrop != null) previousDrop.handle(event);
                return;
            }
            event.setDropCompleted(drop(entries, directory.get()));
            event.consume();
        });
        node.addEventHandler(javafx.scene.input.DragEvent.DRAG_EXITED, event -> {
            if (highlighted == node) clearHighlight();
        });
    }

    boolean accepts(List<E> entries, P directory) {
        return !running && active.getAsBoolean() && directory != null && !entries.isEmpty()
                && entries.stream().allMatch(entry -> allowed.test(entry, directory));
    }

    boolean drop(List<E> entries, P directory) {
        if (!accepts(entries, directory)) return false;
        List<E> snapshot = List.copyOf(entries);
        // The source owns DRAG_DONE. No filesystem operation or dialog runs during native DnD.
        defer.accept(() -> {
            if (!accepts(snapshot, directory)) return;
            running = true;
            moveNext(snapshot, directory, 0);
        });
        return true;
    }

    private void moveNext(List<E> entries, P directory, int index) {
        if (!active.getAsBoolean() || index == entries.size()) {
            running = false;
            if (active.getAsBoolean()) refresh.run();
            return;
        }
        E entry = entries.get(index);
        CompletionStage<Void> operation;
        try {
            operation = Objects.requireNonNull(move.apply(entry, directory));
        } catch (RuntimeException error) {
            operation = java.util.concurrent.CompletableFuture.failedFuture(error);
        }
        operation.whenComplete((ignored, error) -> Platform.runLater(() -> {
            if (active.getAsBoolean()) {
                if (error != null) failure.accept(entry, error);
                else moved.accept(entry, directory);
            }
            moveNext(entries, directory, index + 1);
        }));
    }

    void clearHighlight() {
        if (highlighted != null) highlighted.pseudoClassStateChanged(DROP_TARGET, false);
        highlighted = null;
    }
}
