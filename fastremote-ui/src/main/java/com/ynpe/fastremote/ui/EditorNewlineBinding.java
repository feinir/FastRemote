// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.beans.property.ReadOnlyLongWrapper;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.PlainTextChange;
import org.fxmisc.richtext.model.TwoDimensional.Bias;
import org.fxmisc.richtext.util.UndoUtils;
import org.fxmisc.undo.UndoManagerFactory;
import org.reactfx.EventStream;
import org.reactfx.Subscription;

/** Keeps delimiters in the same bounded undo transaction as the text that owns them. */
final class EditorNewlineBinding {
    private final CodeArea area;
    private EditorLineEndings endings = EditorLineEndings.repeated(0, EditorLineEndings.LF);
    private byte preferred = EditorLineEndings.LF;
    private List<Change> replay;
    private boolean loading;
    private Subscription historyBudget = () -> { };
    private long retainedCharacters;
    final ReadOnlyLongWrapper revision = new ReadOnlyLongWrapper();

    EditorNewlineBinding(CodeArea area) {
        this.area = area;
        installHistory();
    }

    private void installHistory() {
        historyBudget.unsubscribe();
        retainedCharacters = 0;
        EventStream<List<Change>> changes = area.multiPlainChanges().filter(events -> !loading).map(this::record);
        area.setUndoManager(UndoManagerFactory.fixedSizeHistoryFactory(500).createMultiChangeUM(
                changes,
                Change::invert, this::apply, Change::merge, Change::isIdentity,
                UndoUtils.DEFAULT_PREVENT_MERGE_DELAY));
        historyBudget = changes.subscribe(batch -> {
            if (replay != null) return;
            for (Change change : batch) {
                retainedCharacters += (long) change.text().getRemoved().length() + change.text().getInserted().length();
            }
            // Conservative cumulative budget, including edits later merged or evicted.
            // A huge replacement must not leave many full-file copies in undo history.
            if (retainedCharacters > 4L * 1024 * 1024) {
                area.getUndoManager().forgetHistory();
                retainedCharacters = 0;
            }
        });
    }

    void load(String raw) {
        EditorFileContent content = EditorFileContent.read(raw);
        loading = true;
        try {
            endings = content.endings();
            preferred = endings.preferred();
            area.replaceText(content.text());
            installHistory();
            area.moveTo(0);
        } finally {
            loading = false;
        }
        revision.set(revision.get() + 1);
    }

    void close() {
        historyBudget.unsubscribe();
        area.getUndoManager().close();
    }

    EditorFileContent content() { return new EditorFileContent(area.getText(), endings); }

    private List<Change> record(List<PlainTextChange> changes) {
        // MultiChange positions refer to successive intermediate documents. Reconstruct only
        // for compound operations; ordinary typing uses RichTextFX's paragraph index directly.
        StringBuilder intermediate = null;
        if (changes.size() > 1) {
            intermediate = new StringBuilder(area.getText());
            for (int i = changes.size() - 1; i >= 0; i--) {
                PlainTextChange c = changes.get(i);
                intermediate.replace(c.getPosition(), c.getInsertionEnd(), c.getRemoved());
            }
        }
        List<Change> recorded = new ArrayList<>(changes.size());
        for (int i = 0; i < changes.size(); i++) {
            PlainTextChange c = changes.get(i);
            int index = intermediate == null
                    ? area.offsetToPosition(c.getPosition(), Bias.Forward).getMajor()
                    : countLines(intermediate, c.getPosition());
            int removedCount = countLines(c.getRemoved(), c.getRemoved().length());
            EditorLineEndings removed = endings.slice(index, removedCount);
            EditorLineEndings inserted = replay == null
                    ? EditorLineEndings.repeated(countLines(c.getInserted(), c.getInserted().length()), preferred)
                    : replay.get(i).inserted();
            endings = endings.replace(index, removedCount, inserted);
            recorded.add(new Change(c, removed, inserted));
            if (intermediate != null) intermediate.replace(c.getPosition(), c.getRemovalEnd(), c.getInserted());
        }
        revision.set(revision.get() + 1);
        return recorded;
    }

    private void apply(List<Change> changes) {
        replay = changes;
        try {
            UndoUtils.applyMultiPlainTextChange(area).accept(changes.stream().map(Change::text).toList());
        } finally {
            replay = null;
        }
    }

    private static int countLines(CharSequence text, int end) {
        int count = 0;
        for (int i = 0; i < end; i++) if (text.charAt(i) == '\n') count++;
        return count;
    }

    private record Change(PlainTextChange text, EditorLineEndings removed, EditorLineEndings inserted) {
        Change invert() { return new Change(text.invert(), inserted, removed); }
        boolean isIdentity() { return text.isIdentity() && removed.equals(inserted); }
        Optional<Change> merge(Change next) {
            // Keep delimiter edits intact; ordinary character typing retains native coalescing.
            if (removed.size() != 0 || inserted.size() != 0
                    || next.removed.size() != 0 || next.inserted.size() != 0) return Optional.empty();
            return text.mergeWith(next.text).map(merged -> new Change(merged, removed, inserted));
        }
    }
}
