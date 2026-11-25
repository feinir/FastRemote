// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.terminal;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import com.ynpe.fastremote.domain.settings.TerminalColorScheme;
import com.ynpe.fastremote.domain.settings.TerminalCursorShape;
import com.ynpe.fastremote.domain.settings.TerminalPreferences;
import com.ynpe.fastremote.domain.settings.TerminalColorPalette;
import com.ynpe.fastremote.domain.settings.InterfaceLanguage;
import com.ynpe.fastremote.i18n.Localizer;
import com.ynpe.fastremote.i18n.Texts;
import com.techsenger.jeditermfx.core.TerminalColor;
import com.techsenger.jeditermfx.core.CursorShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.ynpe.fastremote.domain.terminal.TerminalConnection;
import com.ynpe.fastremote.domain.terminal.TerminalTermination;

class FastRemoteTerminalTest {
    @BeforeAll
    static void startJavaFxToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    @Test
    void observesRealTtyReaderAndParsedScreenWithoutStealingOutput() throws Exception {
        var input = new java.io.PipedInputStream(8192);
        var remote = new java.io.PipedOutputStream(input);
        var termination = new CompletableFuture<TerminalTermination>();
        TerminalConnection connection = new TerminalConnection() {
            @Override public InputStream input() { return input; }
            @Override public OutputStream output() { return new ByteArrayOutputStream(); }
            @Override public boolean isOpen() { return !termination.isDone(); }
            @Override public void resize(int columns, int rows) { }
            @Override public CompletionStage<TerminalTermination> termination() { return termination; }
            @Override public void close() {
                termination.complete(TerminalTermination.disconnected());
                try { input.close(); remote.close(); } catch (java.io.IOException ignored) { }
            }
        };
        var create = new java.util.concurrent.FutureTask<FastRemoteTerminal>(() -> {
            var terminal = new FastRemoteTerminal(); terminal.connect(connection); return terminal;
        });
        Platform.runLater(create);
        var terminal = create.get(5, java.util.concurrent.TimeUnit.SECONDS);
        try {
            remote.write("旧内容\r最终中文🙂\n\u001b[31mred\u001b[0m\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)); remote.flush();
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            com.ynpe.fastremote.domain.terminal.TerminalOutput output = null;
            do {
                var read = new java.util.concurrent.FutureTask<>(() -> terminal.observation().read(0, 4096, true));
                Platform.runLater(read); output = read.get(2, java.util.concurrent.TimeUnit.SECONDS);
                if (output.screen().contains("最终中文")) break;
                Thread.sleep(20);
            } while (System.nanoTime() < deadline);
            assertNotNull(output);
            assertTrue(output.text().contains("最终中文🙂"));
            assertTrue(output.screen().contains("最终中文"), output.screen());
            assertFalse(output.text().contains("\u001b"));
        } finally {
            var close = new java.util.concurrent.FutureTask<Void>(() -> { terminal.close(); return null; });
            Platform.runLater(close); close.get(5, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    @Test
    void usesTheKeyboardReceivingCanvasWithoutAnAdditionalBitmapCache() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try (FastRemoteTerminal terminal = new FastRemoteTerminal()) {
                Canvas canvas = assertInstanceOf(Canvas.class, terminal.focusTarget());
                assertFalse(canvas.isCache(), "Canvas drawing must not create a second Prism texture cache");
                assertTrue(terminal.node().getStyleClass().contains("fastremote-terminal-surface"));
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void drawsTypeAheadPredictionsInTheDefaultStyleInsteadOfDimGray() {
        var typeAhead = new FastRemoteTerminal.FastRemoteTerminalSettings(TerminalPreferences.defaults())
                .getTypeAheadSettings();
        assertTrue(typeAhead.isEnabled());
        assertEquals(com.techsenger.jeditermfx.core.model.TerminalTypeAheadSettings.DEFAULT.getLatencyThreshold(),
                typeAhead.getLatencyThreshold());
        assertEquals(com.techsenger.jeditermfx.core.TextStyle.EMPTY, typeAhead.getTypeAheadStyle());
    }

    @Test
    void contributesApplicationAndPaletteContextMenuStylesToTheControlledTerminalFork() {
        FastRemoteTerminal.FastRemoteTerminalSettings dark =
                new FastRemoteTerminal.FastRemoteTerminalSettings(TerminalPreferences.defaults());
        assertEquals(java.util.List.of("application-context-menu", "terminal-context-dark"),
                dark.getContextMenuStyleClasses());

        TerminalPreferences defaults = TerminalPreferences.defaults();
        FastRemoteTerminal.FastRemoteTerminalSettings light =
                new FastRemoteTerminal.FastRemoteTerminalSettings(new TerminalPreferences(
                        TerminalColorScheme.FAST_LIGHT, defaults.fontFamily(), defaults.fontSize(),
                        defaults.cursorShape(), defaults.cursorBlink()));
        assertEquals(java.util.List.of("application-context-menu", "terminal-context-light"),
                light.getContextMenuStyleClasses());

        TerminalColorPalette customLight = new TerminalColorPalette(
                "Custom light", 0x202020, 0xF0EDE8, 0x202020, 0xCCDDEE,
                java.util.stream.IntStream.range(0, 16).boxed().toList());
        FastRemoteTerminal.FastRemoteTerminalSettings custom =
                new FastRemoteTerminal.FastRemoteTerminalSettings(new TerminalPreferences(
                        TerminalColorScheme.CUSTOM, defaults.fontFamily(), defaults.fontSize(),
                        defaults.cursorShape(), defaults.cursorBlink(), java.util.Optional.of(customLight)));
        assertEquals(java.util.List.of("application-context-menu", "terminal-context-light"),
                custom.getContextMenuStyleClasses());
    }

    @Test
    void linkHighlightingDoesNotChangeTheOpeningPreference() {
        var defaults = TerminalPreferences.defaults();
        for (boolean directClick : new boolean[] {false, true}) {
            for (boolean highlight : new boolean[] {false, true}) {
                var preferences = new TerminalPreferences(defaults.colorScheme(), defaults.fontFamily(),
                        defaults.fontSize(), defaults.cursorShape(), defaults.cursorBlink(),
                        defaults.customColorPalette(), defaults.importedColorPalettes(), defaults.terminalType(),
                        defaults.initialColumns(), defaults.initialRows(), directClick,
                        defaults.remoteProbeSettings(), defaults.textAppearance(), highlight);
                var settings = new FastRemoteTerminal.FastRemoteTerminalSettings(preferences);
                assertEquals(directClick, settings.openHyperlinksOnClick());
                assertEquals(highlight
                                ? com.techsenger.jeditermfx.core.HyperlinkStyle.HighlightMode.HOVER_WITH_BOTH_COLORS
                                : com.techsenger.jeditermfx.core.HyperlinkStyle.HighlightMode.NEVER_WITH_ORIGINAL_COLOR,
                        settings.getHyperlinkHighlightingMode());
            }
        }
    }

    @Test
    void initializesTheTextBufferWithTheConfiguredDarkStyleAndAppliesChanges() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try (FastRemoteTerminal terminal = new FastRemoteTerminal()) {
                assertEquals(TerminalColor.rgb(221, 227, 234), terminal.defaultStyle().getForeground());
                assertEquals(TerminalColor.rgb(18, 22, 28), terminal.defaultStyle().getBackground());
                assertEquals("JetBrains Mono NL",
                        new FastRemoteTerminal.FastRemoteTerminalSettings(TerminalPreferences.defaults())
                                .getTerminalFont().getFamily());
                assertEquals("HarmonyOS Sans SC",
                        new FastRemoteTerminal.FastRemoteTerminalSettings(TerminalPreferences.defaults())
                                .getTerminalFallbackFont().getFamily());
                assertEquals(505, terminal.caretBlinkingMs());
                assertEquals(CursorShape.BLINK_VERTICAL_BAR, terminal.configuredCursorShape());
                assertTrue(terminal.scrollBarStyleClasses().contains("terminal-scroll-dark"));
                TerminalPreferences changed = new TerminalPreferences(
                        TerminalColorScheme.FAST_LIGHT, "Monospaced", 18,
                        TerminalCursorShape.VERTICAL_BAR, false).withHighlightHyperlinksOnHover(false);
                terminal.applyPreferences(changed);
                assertEquals(changed, terminal.currentPreferences());
                assertEquals(TerminalColor.rgb(250, 250, 250), terminal.defaultStyle().getBackground());
                assertEquals(18.0, terminal.terminalFont().getSize());
                assertEquals(0, terminal.caretBlinkingMs());
                assertEquals(CursorShape.STEADY_VERTICAL_BAR, terminal.configuredCursorShape());
                assertTrue(terminal.scrollBarStyleClasses().contains("terminal-scroll-light"));
                assertFalse(terminal.scrollBarStyleClasses().contains("terminal-scroll-dark"));
                TerminalColorPalette imported = new TerminalColorPalette(
                        "Imported", 0xABCDEF, 0x102030, 0xFFFFFF, 0x223344,
                        java.util.stream.IntStream.range(0, 16).boxed().toList());
                TerminalPreferences custom = new TerminalPreferences(
                        TerminalColorScheme.CUSTOM, "Monospaced", 15,
                        TerminalCursorShape.BLOCK, true, java.util.Optional.of(imported));
                terminal.applyPreferences(custom);
                assertEquals(TerminalColor.rgb(0xAB, 0xCD, 0xEF), terminal.defaultStyle().getForeground());
                assertEquals(TerminalColor.rgb(0x10, 0x20, 0x30), terminal.defaultStyle().getBackground());
                assertTrue(terminal.scrollBarStyleClasses().contains("terminal-scroll-dark"));
                assertFalse(terminal.scrollBarStyleClasses().contains("terminal-scroll-light"));
                TerminalColorPalette importedLight = new TerminalColorPalette(
                        "Imported light", 0x202020, 0xF0EDE8, 0x202020, 0xCCDDEE,
                        java.util.stream.IntStream.range(0, 16).boxed().toList());
                terminal.applyPreferences(new TerminalPreferences(
                        TerminalColorScheme.CUSTOM, "Monospaced", 15,
                        TerminalCursorShape.BLOCK, true, java.util.Optional.of(importedLight)));
                assertTrue(terminal.scrollBarStyleClasses().contains("terminal-scroll-light"));
                assertFalse(terminal.scrollBarStyleClasses().contains("terminal-scroll-dark"));
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void bundlesInterfaceAndMonospaceFontsAndUsesMonospaceWhenTheConfiguredPrimaryIsUnavailable() throws Exception {
        assertNotNull(BundledFontRegistry.class.getResourceAsStream(
                "/com/ynpe/fastremote/terminal/fonts/HarmonyOS_Sans_SC.ttf"));
        assertNotNull(BundledFontRegistry.class.getResourceAsStream(
                "/com/ynpe/fastremote/terminal/fonts/NotoSansCJKjp-Regular.otf"));
        assertNotNull(BundledFontRegistry.class.getResourceAsStream(
                "/com/ynpe/fastremote/terminal/fonts/NotoSansCJKkr-Regular.otf"));
        assertNotNull(BundledFontRegistry.class.getResourceAsStream(
                "/com/ynpe/fastremote/terminal/fonts/NotoSansThai-Regular.ttf"));
        assertNotNull(BundledFontRegistry.class.getResourceAsStream(
                "/com/ynpe/fastremote/terminal/fonts/NOTO_OFL.txt"));
        assertNotNull(BundledFontRegistry.class.getResourceAsStream(
                "/com/ynpe/fastremote/terminal/fonts/LICENSE-update.txt"));
        assertNotNull(BundledFontRegistry.class.getResourceAsStream(
                "/com/ynpe/fastremote/terminal/fonts/JetBrainsMonoNL-Regular.ttf"));
        assertNotNull(BundledFontRegistry.class.getResourceAsStream(
                "/com/ynpe/fastremote/terminal/fonts/JETBRAINS_MONO_OFL.txt"));
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                assertEquals("HarmonyOS Sans SC", BundledFontRegistry.harmonyFont(14).getFamily());
                assertEquals("JetBrains Mono NL", BundledFontRegistry.bundledMonospaceFont(14).getFamily());
                assertEquals(BundledFontRegistry.BUNDLED_MONOSPACE_FAMILY,
                        BundledFontRegistry.terminalPrimaryFont("FastRemote Missing Font", 14).getFamily());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, java.util.concurrent.TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void selectsDeterministicBundledInterfaceFontsForEveryScript() {
        Localizer previous = Texts.localizer();
        try {
            assertInterfaceFont(InterfaceLanguage.ENGLISH, "HarmonyOS Sans SC");
            assertInterfaceFont(InterfaceLanguage.SIMPLIFIED_CHINESE, "HarmonyOS Sans SC");
            assertInterfaceFont(InterfaceLanguage.TRADITIONAL_CHINESE, "HarmonyOS Sans TC");
            assertInterfaceFont(InterfaceLanguage.JAPANESE, "Noto Sans CJK JP");
            assertInterfaceFont(InterfaceLanguage.KOREAN, "Noto Sans CJK KR");
            assertInterfaceFont(InterfaceLanguage.THAI, "Noto Sans Thai");
            assertInterfaceFont(InterfaceLanguage.SPANISH, "HarmonyOS Sans SC");
            assertInterfaceFont(InterfaceLanguage.BRAZILIAN_PORTUGUESE, "HarmonyOS Sans SC");
            assertInterfaceFont(InterfaceLanguage.FRENCH, "HarmonyOS Sans SC");
            assertInterfaceFont(InterfaceLanguage.ITALIAN, "HarmonyOS Sans SC");
            assertInterfaceFont(InterfaceLanguage.GERMAN, "HarmonyOS Sans SC");
            assertInterfaceFont(InterfaceLanguage.RUSSIAN, "HarmonyOS Sans SC");
        } finally {
            Texts.configure(previous);
        }
    }

    private static void assertInterfaceFont(InterfaceLanguage language, String expectedFamily) {
        Texts.configure(Localizer.forLanguage(language));
        assertEquals(expectedFamily, BundledFontRegistry.harmonyFamily());
    }

    @Test
    void routesSupportedInputScriptsToBundledGlyphFamiliesIndependentlyOfInterfaceLanguage() {
        Localizer previous = Texts.localizer();
        try {
            Texts.configure(Localizer.forLanguage(InterfaceLanguage.ENGLISH));
            assertEquals("HarmonyOS Sans SC", BundledFontRegistry.terminalFallbackFamily('中'));
            assertEquals("Noto Sans CJK JP", BundledFontRegistry.terminalFallbackFamily('に'));
            assertEquals("Noto Sans CJK KR", BundledFontRegistry.terminalFallbackFamily('한'));
            assertEquals("Noto Sans Thai", BundledFontRegistry.terminalFallbackFamily('ภ'));
            assertEquals("HarmonyOS Sans SC", BundledFontRegistry.terminalFallbackFamily('Ф'));

            Texts.configure(Localizer.forLanguage(InterfaceLanguage.TRADITIONAL_CHINESE));
            assertEquals("HarmonyOS Sans TC", BundledFontRegistry.terminalFallbackFamily('繁'));
        } finally {
            Texts.configure(previous);
        }
    }

    @Test
    void usesTheConfiguredInitialTerminalMatrix() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            TerminalPreferences defaults = TerminalPreferences.defaults();
            TerminalPreferences configured = new TerminalPreferences(
                    defaults.colorScheme(), defaults.fontFamily(), defaults.fontSize(), defaults.cursorShape(),
                    defaults.cursorBlink(), defaults.customColorPalette(), defaults.importedColorPalettes(),
                    com.ynpe.fastremote.domain.settings.TerminalType.VT100, 132, 41);
            try (FastRemoteTerminal terminal = new FastRemoteTerminal(configured)) {
                assertEquals(132, terminal.initialColumns());
                assertEquals(41, terminal.initialRows());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, java.util.concurrent.TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void localizesTerminalMenuActionsWithoutChangingTheirShortcuts() {
        var settings = new FastRemoteTerminal.FastRemoteTerminalSettings(TerminalPreferences.defaults());

        assertEquals("作为链接打开", settings.getOpenUrlActionPresentation().getName());
        assertEquals("复制", settings.getCopyActionPresentation().getName());
        assertEquals("粘贴", settings.getPasteActionPresentation().getName());
        assertEquals("全选", settings.getSelectAllActionPresentation().getName());
        assertEquals("清空缓冲区", settings.getClearBufferActionPresentation().getName());
        assertEquals("向上翻页", settings.getPageUpActionPresentation().getName());
        assertEquals("向下翻页", settings.getPageDownActionPresentation().getName());
        assertEquals("向上滚动", settings.getLineUpActionPresentation().getName());
        assertEquals("向下滚动", settings.getLineDownActionPresentation().getName());
        assertEquals("查找", settings.getFindActionPresentation().getName());
        assertEquals("查找", settings.getFindFieldPromptText());
        assertEquals("区分大小写", settings.getFindMatchCaseText());
        assertEquals("正则表达式", settings.getFindRegexText());
        assertEquals("上一个", settings.getFindPreviousText());
        assertEquals("下一个", settings.getFindNextText());
        assertEquals("正则表达式无效", settings.getFindInvalidRegexText());
        assertEquals("无结果", settings.getFindResultText(0, 0));
        assertEquals("2 / 7", settings.getFindResultText(2, 7));
        assertEquals(new com.techsenger.jeditermfx.ui.settings.DefaultSettingsProvider()
                        .getFindActionPresentation().getKeyCombinations(),
                settings.getFindActionPresentation().getKeyCombinations());
    }

    @Test
    void appliesCustomizedTerminalActionShortcutsWithoutChangingActionNames() {
        var settings = new FastRemoteTerminal.FastRemoteTerminalSettings(TerminalPreferences.defaults());
        settings.updateShortcuts(new com.ynpe.fastremote.domain.settings.TerminalShortcutSettings(
                "Ctrl+Alt+C", "Ctrl+Alt+V", "Ctrl+Alt+F"));

        assertEquals(javafx.scene.input.KeyCombination.valueOf("Ctrl+Alt+C"),
                settings.getCopyActionPresentation().getKeyCombinations().getFirst());
        assertEquals(javafx.scene.input.KeyCombination.valueOf("Ctrl+Alt+V"),
                settings.getPasteActionPresentation().getKeyCombinations().getFirst());
        assertEquals(javafx.scene.input.KeyCombination.valueOf("Ctrl+Alt+F"),
                settings.getFindActionPresentation().getKeyCombinations().getFirst());
        assertEquals("复制", settings.getCopyActionPresentation().getName());
    }

    @Test
    void usesTheLocalizedBuiltInJediTermFxFindComponent() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try (FastRemoteTerminal terminal = new FastRemoteTerminal()) {
                Scene scene = new Scene((Parent) terminal.node(), 900, 600);
                terminal.showFind();
                scene.getRoot().applyCss();
                Region findPane = (Region) terminal.node().lookup("#jeditermfx-find-component");
                assertNotNull(findPane);
                @SuppressWarnings("unchecked")
                ComboBox<String> search = (ComboBox<String>) terminal.node()
                        .lookup("#jeditermfx-find-field");
                CheckBox matchCase = (CheckBox) terminal.node()
                        .lookup("#jeditermfx-find-match-case");
                CheckBox regex = (CheckBox) terminal.node().lookup("#jeditermfx-find-regex");
                Label result = (Label) terminal.node().lookup("#jeditermfx-find-result");
                Button next = (Button) terminal.node().lookup("#jeditermfx-find-next");
                Button previous = (Button) terminal.node().lookup("#jeditermfx-find-previous");
                assertTrue(search.isEditable());
                assertEquals("查找", search.getPromptText());
                assertEquals("区分大小写", matchCase.getText());
                assertFalse(matchCase.isSelected());
                assertEquals("正则表达式", regex.getText());
                assertFalse(regex.isSelected());
                assertEquals("下一个", next.getText());
                assertEquals("上一个", previous.getText());
                assertEquals(0, search.getPadding().getTop());
                assertEquals(0, search.getPadding().getBottom());
                assertEquals(0, search.getEditor().getPadding().getTop());
                assertEquals(0, search.getEditor().getPadding().getBottom());
                assertEquals(8, search.getEditor().getPadding().getLeft());
                assertEquals(8, search.getEditor().getPadding().getRight());
                ContextMenu inputMenu = search.getEditor().getContextMenu();
                assertNotNull(inputMenu);
                assertTrue(inputMenu.getStyleClass().contains("application-context-menu"));
                assertTrue(inputMenu.getStyleClass().contains("terminal-context-dark"));
                assertFalse(inputMenu.getStyleClass().contains("terminal-context-light"));
                search.pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), true);
                scene.getRoot().applyCss();
                assertEquals(Color.web("#424e5f"),
                        search.getBorder().getStrokes().getFirst().getTopStroke());
                assertNull(search.getEffect());
                search.pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), false);
                assertEquals(397, findPane.getPrefWidth());
                assertEquals(463, findPane.getMaxWidth());
                VBox findLayout = (VBox) findPane;
                assertEquals(72, ((HBox) findLayout.getChildren().get(2)).getPadding().getLeft());
                assertEquals(72, ((HBox) findLayout.getChildren().get(3)).getPadding().getLeft());
                assertTrue(findPane.getStyle().contains("-jeditermfx-find-panel: #1b2028"));
                assertTrue(findPane.getStyle().contains("-jeditermfx-find-control: #202630"));
                assertNull(terminal.node().lookup("#jeditermfx-replace-field"));
                assertNull(terminal.node().lookup("#jeditermfx-replace-one"));
                assertNull(terminal.node().lookup("#jeditermfx-replace-all"));

                search.getEditor().setText("历史查找");
                next.fire();
                assertEquals("历史查找", search.getItems().getFirst());
                assertEquals("历史查找", search.getEditor().getText());
                search.getItems().add("另一个历史");
                search.applyCss();
                ListView<?> history = (ListView<?>)
                        ((ComboBoxListViewSkin<?>) search.getSkin()).getPopupContent();
                Scene popupScene = history.getScene();
                if (popupScene == null) {
                    Parent popupRoot = history;
                    while (popupRoot.getParent() != null) popupRoot = popupRoot.getParent();
                    popupScene = new Scene(popupRoot, 300, 120);
                }
                popupScene.getStylesheets().add(FastRemoteTerminalTest.class
                        .getResource("terminal-find-popup-override.css").toExternalForm());
                search.getOnShown().handle(new javafx.event.Event(search, search,
                        javafx.scene.control.ComboBoxBase.ON_SHOWN));
                assertNotNull(history);
                history.applyCss();
                assertTrue(history.getStyleClass().contains("jeditermfx-find-history-dark"));
                assertEquals(Color.web("#1b2028"),
                        history.getBackground().getFills().getFirst().getFill());

                search.getEditor().setText("不存在的文本");
                assertEquals("无结果", result.getText());
                regex.setSelected(true);
                search.getEditor().setText("[");
                assertEquals("正则表达式无效", result.getText());

                TerminalPreferences defaults = TerminalPreferences.defaults();
                terminal.applyPreferences(new TerminalPreferences(
                        com.ynpe.fastremote.domain.settings.TerminalColorScheme.FAST_LIGHT,
                        defaults.fontFamily(), defaults.fontSize(), defaults.cursorShape(),
                        defaults.cursorBlink(), defaults.customColorPalette(),
                        defaults.importedColorPalettes(), defaults.terminalType(),
                        defaults.initialColumns(), defaults.initialRows(),
                        defaults.openHyperlinksOnClick(), defaults.remoteProbeSettings()));
                assertTrue(findPane.getStyle().contains("-jeditermfx-find-panel: #eceff1"));
                assertTrue(findPane.getStyle().contains("-jeditermfx-find-control: #ffffff"));
                assertTrue(inputMenu.getStyleClass().contains("terminal-context-light"));
                assertFalse(inputMenu.getStyleClass().contains("terminal-context-dark"));
                history.applyCss();
                assertTrue(history.getStyleClass().contains("jeditermfx-find-history-light"));
                assertEquals(Color.WHITE,
                        history.getBackground().getFills().getFirst().getFill());

                terminal.hideFind();
                assertNull(terminal.node().lookup("#jeditermfx-find-component"));
                terminal.showFind();
                @SuppressWarnings("unchecked")
                ComboBox<String> reopened = (ComboBox<String>) terminal.node()
                        .lookup("#jeditermfx-find-field");
                assertEquals("历史查找", reopened.getItems().getFirst());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(Duration.ofSeconds(15).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void observesTransportTerminationWithoutDependingOnTtyWaitFor() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try (FastRemoteTerminal terminal = new FastRemoteTerminal()) {
                FakeTerminalConnection connection = new FakeTerminalConnection();
                terminal.connect(connection);
                connection.termination.complete(TerminalTermination.eof());
                assertEquals(TerminalTermination.Kind.NORMAL,
                        terminal.termination().toCompletableFuture().join().kind());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void sendsProgrammaticInputAsUtf8ThroughTheConnectedTerminal() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try (FastRemoteTerminal terminal = new FastRemoteTerminal()) {
                FakeTerminalConnection connection = new FakeTerminalConnection();
                terminal.connect(connection);
                terminal.sendInput("echo 中文\n").toCompletableFuture().join();
                assertEquals("echo 中文\n", connection.output.toString(java.nio.charset.StandardCharsets.UTF_8));
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    private static final class FakeTerminalConnection implements TerminalConnection {
        private final CompletableFuture<TerminalTermination> termination = new CompletableFuture<>();
        private final ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]);
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        @Override public InputStream input() { return input; }
        @Override public OutputStream output() { return output; }
        @Override public boolean isOpen() { return !termination.isDone(); }
        @Override public void resize(int columns, int rows) {}
        @Override public CompletionStage<TerminalTermination> termination() { return termination; }
        @Override public void close() { termination.complete(TerminalTermination.disconnected()); }
    }
}
