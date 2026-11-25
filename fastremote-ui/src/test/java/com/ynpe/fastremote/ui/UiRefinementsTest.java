// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.*;
import com.ynpe.fastremote.domain.settings.DiagnosticLogOutput;
import com.ynpe.fastremote.i18n.Localizer;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class UiRefinementsTest {
    @BeforeAll static void toolkit() throws Exception { SearchableSelectorInputMethodTest.toolkit(); }

    @Test void installationInstructionsPrecedeTheSeparateStatusCard() throws Exception {
        SearchableSelectorInputMethodTest.onFx(() -> {
            VBox content = CommandLineToolsDialog.content("Installed: /tmp/fr");
            assertEquals(2, content.getChildren().size());
            VBox instructions = (VBox) content.getChildren().getFirst();
            VBox status = (VBox) content.getChildren().getLast();
            assertTrue(instructions.getStyleClass().contains("settings-card"));
            assertTrue(status.getStyleClass().contains("settings-card"));
            assertTrue(((Label) instructions.getChildren().getFirst()).getText().contains("fr"));
            assertEquals("Installed: /tmp/fr", ((Label) status.getChildren().getFirst()).getText());
        });
    }

    @Test void logFolderActionRevealsFirstSelectedRowOrOpensTheDirectory() throws Exception {
        SearchableSelectorInputMethodTest.onFx(() -> {
            DiagnosticLogOutput output = DiagnosticLogOutput.discarding();
            for (boolean selected : new boolean[] {false, true}) {
                AtomicReference<Path> path = new AtomicReference<>();
                AtomicReference<Boolean> reveal = new AtomicReference<>();
                var pane = new DiagnosticSettingsPage.DiagnosticLogFilesPane(null, output, Localizer.systemDefault(),
                        (target, select) -> { path.set(target); reveal.set(select); return CompletableFuture.completedFuture(null); });
                @SuppressWarnings("unchecked")
                var files = (ListView<DiagnosticLogOutput.LogFile>) pane.node().lookup(".list-view");
                files.getItems().setAll(new DiagnosticLogOutput.LogFile("first.log", 1),
                        new DiagnosticLogOutput.LogFile("second.log", 2),
                        new DiagnosticLogOutput.LogFile("third.log", 3));
                if (selected) files.getSelectionModel().selectIndices(2, 0);
                Button open = (Button) pane.node().lookupAll(".button").stream()
                        .filter(node -> ((Button) node).getText().equals(Localizer.systemDefault().text("diagnostic.log.openFolder")))
                        .findFirst().orElseThrow();
                open.fire();
                assertEquals(selected ? output.directory().resolve("first.log") : output.directory(), path.get());
                assertEquals(selected, reveal.get());
            }
        });
    }
}
