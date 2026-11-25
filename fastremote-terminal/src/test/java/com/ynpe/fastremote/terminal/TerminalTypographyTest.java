// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.terminal;

import com.techsenger.jeditermfx.ui.JediTermFxWidget;
import com.ynpe.fastremote.domain.settings.TerminalPreferences;
import com.ynpe.fastremote.domain.settings.TextAppearance;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class TerminalTypographyTest {
    @BeforeAll static void toolkit() throws Exception {
        var ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); } catch (IllegalStateException running) { ready.countDown(); }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }

    @Test void fractionalSignedSpacingIsUsedForVisibleColumnsAndRows() throws Exception {
        var done = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        Platform.runLater(() -> {
            var baseline = new JediTermFxWidget(80, 24,
                    new FastRemoteTerminal.FastRemoteTerminalSettings(TerminalPreferences.defaults()));
            try {
                int originalWidth = baseline.getTerminalPanel().getPixelWidth();
                int originalHeight = baseline.getTerminalPanel().getPixelHeight();
                double naturalWidth = Math.floor(originalWidth / 80.0);
                double inset = originalWidth - naturalWidth * 80;
                double naturalHeight = originalHeight / 24.0;
                for (double spacing : new double[]{0.1, 0.2, -0.1, -1.3, -5, 5}) {
                    var settings = TerminalPreferences.defaults().withTextAppearance(new TextAppearance(spacing, spacing));
                    var widget = new JediTermFxWidget(80, 24, new FastRemoteTerminal.FastRemoteTerminalSettings(settings));
                    try {
                        assertEquals(Math.round(80 * spacing), widget.getTerminalPanel().getPixelWidth() - originalWidth);
                        assertEquals(Math.round(24 * spacing), widget.getTerminalPanel().getPixelHeight() - originalHeight);
                        StackPane root = new StackPane(widget.getPane());
                        new Scene(root, 913, 511); root.resize(913, 511); root.applyCss(); root.layout();
                        var panel = widget.getTerminalPanel();
                        var size = panel.getTerminalSizeFromComponent();
                        assertNotNull(size);
                        assertEquals((int) Math.floor((panel.getCanvas().getWidth() - inset) / (naturalWidth + spacing)), size.getColumns());
                        assertEquals((int) Math.floor(panel.getCanvas().getHeight() / (naturalHeight + spacing)), size.getRows());
                    } finally { widget.close(); }
                }
            } catch (Throwable error) { failure.set(error); }
            finally { baseline.close(); done.countDown(); }
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test void spacingChangesCellGeometryAndVisibleTerminalSize() throws Exception {
        var done = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        Platform.runLater(() -> {
            var defaults = TerminalPreferences.defaults();
            var changed = defaults.withTextAppearance(new TextAppearance(15, 5));
            var baseline = new JediTermFxWidget(80, 24, new FastRemoteTerminal.FastRemoteTerminalSettings(defaults));
            var spaced = new JediTermFxWidget(80, 24, new FastRemoteTerminal.FastRemoteTerminalSettings(changed));
            try {
                assertEquals(80 * 5, spaced.getTerminalPanel().getPixelWidth() - baseline.getTerminalPanel().getPixelWidth());
                assertEquals(24 * 15, spaced.getTerminalPanel().getPixelHeight() - baseline.getTerminalPanel().getPixelHeight());
                var root = new StackPane(baseline.getPane());
                new Scene(root, 900, 500); root.resize(900, 500); root.applyCss(); root.layout();
                var originalSize = baseline.getTerminalPanel().getTerminalSizeFromComponent();
                root.getChildren().setAll(spaced.getPane()); root.applyCss(); root.layout();
                var spacedSize = spaced.getTerminalPanel().getTerminalSizeFromComponent();
                assertNotNull(originalSize); assertNotNull(spacedSize);
                assertTrue(spacedSize.getColumns() < originalSize.getColumns());
                assertTrue(spacedSize.getRows() < originalSize.getRows());
                var configured = new FastRemoteTerminal.FastRemoteTerminalSettings(defaults.withTextAppearance(new TextAppearance(15, 5)));
                assertEquals(15, configured.getExtraLineSpacing());
                assertEquals(5, configured.getExtraLetterSpacing());
            } catch (Throwable error) { failure.set(error); }
            finally { baseline.close(); spaced.close(); done.countDown(); }
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
