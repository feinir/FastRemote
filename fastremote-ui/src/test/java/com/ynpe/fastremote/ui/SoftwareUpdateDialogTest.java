// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class SoftwareUpdateDialogTest {
    @org.junit.jupiter.api.BeforeAll
    static void startToolkit() throws Exception {
        var started = new java.util.concurrent.CountDownLatch(1);
        try { javafx.application.Platform.startup(started::countDown); }
        catch (IllegalStateException alreadyStarted) { started.countDown(); }
        assertTrue(started.await(10, java.util.concurrent.TimeUnit.SECONDS));
        javafx.application.Platform.setImplicitExit(false);
    }

    @Test void websitePromptWaitsForUserAndNeverReturnsDownload() throws Exception {
        onFx(() -> {
            var page = java.net.URI.create("https://www.ynpe.com/download/");
            var dialog = SoftwareUpdateDialog.availableDialog(null, "1.2.9", release(), page);
            assertNull(dialog.getResult());
            var buttons = dialog.getDialogPane().getButtonTypes();
            assertEquals(SoftwareUpdateDialog.Choice.SKIP, dialog.getResultConverter().call(buttons.get(0)));
            assertEquals(SoftwareUpdateDialog.Choice.LATER, dialog.getResultConverter().call(buttons.get(1)));
            assertEquals(SoftwareUpdateDialog.Choice.OPEN_WEBSITE, dialog.getResultConverter().call(buttons.get(2)));
            assertEquals(SoftwareUpdateDialog.Choice.LATER, dialog.getResultConverter().call(null));
            var content = (javafx.scene.layout.VBox) dialog.getDialogPane().getContent();
            var link = (javafx.scene.control.Hyperlink) content.getChildren().stream()
                    .filter(javafx.scene.control.Hyperlink.class::isInstance).findFirst().orElseThrow();
            assertEquals(page.toString(), link.getText());
            dialog.show();
            assertNull(dialog.getResult());
            link.fire();
            assertEquals(SoftwareUpdateDialog.Choice.OPEN_WEBSITE, dialog.getResult());
            assertFalse(dialog.isShowing());
        });
    }

    private static com.ynpe.fastremote.domain.update.SoftwareRelease release() {
        return new com.ynpe.fastremote.domain.update.SoftwareRelease("1.3.0", "windows", "amd64",
                java.net.URI.create("https://download.example/package.msi"), "a".repeat(64), 42,
                "New version", java.time.Instant.parse("2026-09-28T00:00:00Z"));
    }

    private static void onFx(Runnable action) throws Exception {
        var task = new java.util.concurrent.FutureTask<Void>(() -> { action.run(); return null; });
        javafx.application.Platform.runLater(task);
        task.get(15, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Test
    void preservesEveryReleaseNoteLineAcrossLineEndingStyles() {
        assertEquals(List.of("第一条", "", "第二条", "第三条"),
                SoftwareUpdateDialog.releaseNoteLines("  第一条\r\n\r\n第二条\r第三条  "));
    }
}
