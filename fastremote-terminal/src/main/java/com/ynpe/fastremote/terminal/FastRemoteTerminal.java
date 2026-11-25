// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.terminal;

import com.ynpe.fastremote.domain.terminal.TerminalConnection;
import com.ynpe.fastremote.domain.settings.TerminalColorScheme;
import com.ynpe.fastremote.domain.settings.TerminalCursorShape;
import com.ynpe.fastremote.domain.settings.TerminalPreferences;
import com.ynpe.fastremote.domain.settings.TerminalShortcutSettings;
import com.ynpe.fastremote.domain.settings.TerminalColorPalette;
import com.techsenger.jeditermfx.core.CursorShape;
import com.techsenger.jeditermfx.core.TerminalColor;
import com.techsenger.jeditermfx.core.TextStyle;
import com.techsenger.jeditermfx.core.Color;
import com.techsenger.jeditermfx.core.TerminalExecutorServiceManager;
import com.techsenger.jeditermfx.core.emulator.ColorPalette;
import com.techsenger.jeditermfx.core.model.StyleState;
import com.techsenger.jeditermfx.core.model.TerminalTextBuffer;
import com.techsenger.jeditermfx.core.model.TerminalTypeAheadSettings;
import com.techsenger.jeditermfx.ui.DefaultHyperlinkFilter;
import com.techsenger.jeditermfx.ui.JediTermFxWidget;
import com.techsenger.jeditermfx.ui.TerminalActionPresentation;
import com.techsenger.jeditermfx.ui.TerminalContextMenuEntry;
import com.techsenger.jeditermfx.ui.TerminalPanel;
import com.techsenger.jeditermfx.ui.settings.DefaultSettingsProvider;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import com.ynpe.fastremote.domain.terminal.TerminalTermination;
import javafx.scene.Node;
import javafx.scene.input.KeyCombination;
import javafx.scene.text.Font;
import org.jetbrains.annotations.NotNull;

/** JavaFX terminal surface that keeps all JediTermFX types inside this module. */
public final class FastRemoteTerminal implements AutoCloseable {
    private static final String LIGHT_SCROLL_BAR_STYLE_CLASS = "terminal-scroll-light";
    private static final String DARK_SCROLL_BAR_STYLE_CLASS = "terminal-scroll-dark";

    public enum DetectedShellFamily { UNKNOWN, POSIX, POWERSHELL, COMMAND_PROMPT }

    public sealed interface ContextMenuAction
            permits ContextMenuAction.Item, ContextMenuAction.Submenu {
        String label();
        java.util.function.BooleanSupplier enabled();

        record Item(String label, Runnable callback, java.util.function.BooleanSupplier enabled)
                implements ContextMenuAction {
            public Item {
                label = requireContextMenuLabel(label);
                Objects.requireNonNull(callback, "callback");
                Objects.requireNonNull(enabled, "enabled");
            }
        }

        record Submenu(String label, java.util.List<ContextMenuAction> children,
                java.util.function.BooleanSupplier enabled) implements ContextMenuAction {
            public Submenu {
                label = requireContextMenuLabel(label);
                children = java.util.List.copyOf(Objects.requireNonNull(children, "children"));
                Objects.requireNonNull(enabled, "enabled");
            }
        }

        private static String requireContextMenuLabel(String label) {
            String value = Objects.requireNonNull(label, "label").strip();
            if (value.isEmpty()) throw new IllegalArgumentException("label must not be blank");
            return value;
        }
    }

    private final BoundedJediTermFxWidget widget;
    private final FastRemoteTerminalSettings settings;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final CompletableFuture<TerminalTermination> termination = new CompletableFuture<>();
    private TerminalConnection connection;
    private TerminalInputGateway inputGateway;
    private TerminalConnectionTtyConnector ttyConnector;
    private final TerminalCapture capture = new TerminalCapture(text -> sendInput(text, true), this::screenSnapshot);

    public com.ynpe.fastremote.domain.terminal.TerminalObservation observation() { return capture; }

    /** Must be called before connecting the PTY whose startup files install these hooks. */
    public void expectShellIntegration(String nonce) {
        if (connection != null) throw new IllegalStateException("Terminal already connected");
        capture.expectStartupIntegration(nonce);
    }

    private String screenSnapshot() {
        var buffer = widget.getTerminalPanel().getTerminalTextBuffer();
        buffer.lock();
        try {
            java.util.ArrayDeque<String> lines = new java.util.ArrayDeque<>();
            int remaining = 65536;
            for (int row = buffer.getHeight() - 1; row >= -Math.min(200, buffer.getHistoryLinesCount()); row--) {
                if (remaining <= 1) break;
                String line = buffer.getLine(row).getText().replace(
                        String.valueOf(com.techsenger.jeditermfx.core.util.CharUtils.DWC), "");
                if (line.length() >= remaining) line = line.substring(line.length() - remaining + 1);
                lines.addFirst(line);
                remaining -= line.length() + 1;
            }
            return String.join("\n", lines);
        } finally { buffer.unlock(); }
    }

    private final AtomicLong receivedBytes = new AtomicLong();
    private final AtomicLong sentBytes = new AtomicLong();
    private final AtomicReference<String> latestWorkingDirectory = new AtomicReference<>();
    private final AtomicReference<DetectedShellFamily> latestShellFamily =
            new AtomicReference<>(DetectedShellFamily.UNKNOWN);
    private volatile java.util.function.Consumer<String> workingDirectoryListener = ignored -> {};
    private static final java.util.concurrent.ThreadPoolExecutor INPUT_EXECUTOR = new java.util.concurrent.ThreadPoolExecutor(
            1, 1, 30L, java.util.concurrent.TimeUnit.SECONDS,
            new java.util.concurrent.ArrayBlockingQueue<>(64),
            Thread.ofPlatform().name("fastremote-terminal-input-", 0).daemon(true).factory(),
            new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());

    static {
        INPUT_EXECUTOR.allowCoreThreadTimeOut(true);
    }

    public FastRemoteTerminal() {
        this(TerminalPreferences.defaults());
    }

    public FastRemoteTerminal(TerminalPreferences preferences) {
        this(preferences, (menu, owner) -> {});
    }

    /** JavaFX preparation supplied by the host UI, without exposing JediTermFX types. */
    public FastRemoteTerminal(TerminalPreferences preferences,
            java.util.function.BiConsumer<javafx.scene.control.ContextMenu, Node> menuPreparation) {
        BundledFontRegistry.load();
        settings = new FastRemoteTerminalSettings(preferences);
        settings.menuPreparation = Objects.requireNonNull(menuPreparation, "menuPreparation");
        widget = new BoundedJediTermFxWidget(settings);
        // Canvas already has its own render surface. A second Prism bitmap of its
        // rapidly changing content can exhaust the texture pool and trigger full GCs.
        widget.getTerminalPanel().getCanvas().setCache(false);
        widget.getPane().getStyleClass().add("fastremote-terminal-surface");
        applyScrollBarTheme(preferences);
        widget.addHyperlinkFilter(new DefaultHyperlinkFilter());
        widget.getTerminalPanel().setCursorShape(cursorShape(preferences));
    }

    public Node node() {
        return widget.getPane();
    }

    public Node focusTarget() {
        // JediTermFX receives key events on its Canvas. Its public preferred-focus node is the
        // surrounding Pane, which is not focus traversable, so focusing it leaves a connected
        // terminal unable to accept keyboard input after connection or tab selection.
        return widget.getTerminalPanel().getCanvas();
    }

    public void connect(TerminalConnection terminalConnection) {
        Objects.requireNonNull(terminalConnection, "terminalConnection");
        if (closed.get()) {
            terminalConnection.close();
            throw new IllegalStateException("Terminal has already been closed");
        }
        if (connection != null) {
            terminalConnection.close();
            throw new IllegalStateException("Terminal already has a connection");
        }
        connection = terminalConnection;
        inputGateway = new TerminalInputGateway(terminalConnection.output(), sentBytes::addAndGet);
        inputGateway.observeOutsideInput(capture::outsideInput);
        // JediTermFX reads the TTY stream but never invokes TtyConnector.waitFor(). Observe the
        // transport lifecycle independently so channel/session closure always reaches the UI.
        terminalConnection.termination().whenComplete((result, failure) -> termination.complete(
                failure == null && result != null ? result : TerminalTermination.disconnected()));
        var directoryParser = new RemoteWorkingDirectoryParser(this::handleWorkingDirectory,
                shellFamily -> latestShellFamily.set(DetectedShellFamily.valueOf(shellFamily.name())));
        ttyConnector = new TerminalConnectionTtyConnector(
                terminalConnection, inputGateway, ignored -> {}, receivedBytes::addAndGet,
                output -> { capture.accept(output); directoryParser.accept(output); });
        termination.whenComplete((result, failure) -> capture.disconnect());
        widget.setTtyConnector(ttyConnector);
        widget.start();
        focusTarget().requestFocus();
    }

    public java.util.concurrent.CompletionStage<Void> sendInput(String text) { return sendInput(text, false); }

    private java.util.concurrent.CompletionStage<Void> sendInput(String text, boolean observedAutomation) {
        Objects.requireNonNull(text, "text");
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > 256 * 1024) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                    new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.02d50a580b6eb290")));
        }
        if (closed.get() || connection == null || !connection.isOpen()) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                    new IllegalStateException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a1fbb9276380785f")));
        }
        var result = new java.util.concurrent.CompletableFuture<Void>();
        try {
            INPUT_EXECUTOR.execute(() -> {
                try {
                    if (observedAutomation) inputGateway.writeAutomation(bytes);
                    else inputGateway.write(bytes);
                    result.complete(null);
                } catch (java.io.IOException failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException failure) {
            result.completeExceptionally(new IllegalStateException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.97613400767db075"), failure));
        }
        return result;
    }

    public CompletionStage<TerminalTermination> termination() {
        return termination;
    }

    public void setInputEnabled(boolean enabled) {
        if (!closed.get() && inputGateway != null) inputGateway.setInputEnabled(enabled);
    }

    public long receivedBytes() { return receivedBytes.get(); }

    public long sentBytes() { return sentBytes.get(); }

    /** Installs a listener and immediately replays the latest path inferred from remote prompt output. */
    public void setWorkingDirectoryListener(java.util.function.Consumer<String> listener) {
        workingDirectoryListener = Objects.requireNonNull(listener, "listener");
        String current = latestWorkingDirectory.get();
        if (current != null) listener.accept(current);
    }

    /** Returns the shell family inferred from the latest recognizable prompt. */
    public DetectedShellFamily detectedShellFamily() {
        return latestShellFamily.get();
    }

    private void handleWorkingDirectory(String path) {
        latestWorkingDirectory.set(path);
        workingDirectoryListener.accept(path);
    }

    /** Applies presentation-only changes without reconnecting the SSH session. */
    public void applyPreferences(TerminalPreferences preferences) {
        Objects.requireNonNull(preferences, "preferences");
        if (closed.get()) return;
        TerminalPreferences previous = settings.preferences();
        settings.update(preferences);
        widget.applyPreferences(previous, preferences);
        applyScrollBarTheme(preferences);
    }

    private void applyScrollBarTheme(TerminalPreferences preferences) {
        var styles = widget.getTerminalPanel().getScrollBar().getStyleClass();
        styles.removeAll(LIGHT_SCROLL_BAR_STYLE_CLASS, DARK_SCROLL_BAR_STYLE_CLASS);
        styles.add(FastRemoteTerminalSettings.usesLightChrome(preferences)
                ? LIGHT_SCROLL_BAR_STYLE_CLASS : DARK_SCROLL_BAR_STYLE_CLASS);
    }

    /** Replaces the terminal component's copy, paste and find key combinations in-place. */
    public void applyKeyboardShortcuts(TerminalShortcutSettings shortcuts) {
        Objects.requireNonNull(shortcuts, "shortcuts");
        if (!closed.get()) settings.updateShortcuts(shortcuts);
    }

    /** Replaces the host actions shown before the terminal's built-in context menu actions. */
    public void setContextMenuItems(
            java.util.function.Supplier<java.util.List<ContextMenuAction>> items) {
        settings.setContextMenuItems(items);
    }

    TerminalPreferences currentPreferences() {
        return settings.preferences();
    }

    int initialColumns() {
        return widget.initialColumns();
    }

    int initialRows() {
        return widget.initialRows();
    }

    TextStyle defaultStyle() {
        return settings.getDefaultStyle();
    }

    Font terminalFont() {
        return settings.getTerminalFont();
    }

    int caretBlinkingMs() {
        return settings.caretBlinkingMs();
    }

    CursorShape configuredCursorShape() {
        return cursorShape(settings.preferences());
    }

    java.util.List<String> scrollBarStyleClasses() {
        return java.util.List.copyOf(widget.getTerminalPanel().getScrollBar().getStyleClass());
    }

    void showFind() {
        widget.showBuiltInFindComponent();
    }

    void hideFind() {
        widget.hideBuiltInFindComponent();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        workingDirectoryListener = ignored -> {};
        capture.disconnect();
        widget.close();
        connection = null;
    }

    private static final class BoundedJediTermFxWidget extends JediTermFxWidget {
        private BoundedJediTermFxWidget(FastRemoteTerminalSettings settings) {
            super(settings.preferences().initialColumns(), settings.preferences().initialRows(), settings);
        }

        private void showBuiltInFindComponent() {
            super.showFindComponent();
        }

        private void hideBuiltInFindComponent() {
            super.hideFindComponent();
        }

        private int initialColumns() {
            return myTerminal.getTerminalWidth();
        }

        private int initialRows() {
            return myTerminal.getTerminalHeight();
        }

        @Override
        protected TerminalPanel createTerminalPanel(
                @NotNull com.techsenger.jeditermfx.ui.settings.SettingsProvider settingsProvider,
                @NotNull StyleState styleState,
                @NotNull TerminalTextBuffer terminalTextBuffer) {
            return new RefreshableTerminalPanel(settingsProvider, styleState, terminalTextBuffer);
        }

        private void applyPreferences(TerminalPreferences previous, TerminalPreferences current) {
            StyleState styleState = myTerminal.getStyleState();
            TextStyle previousDefault = FastRemoteTerminalSettings.defaultStyle(previous);
            TextStyle currentDefault = FastRemoteTerminalSettings.defaultStyle(current);
            TextStyle active = styleState.getCurrent();
            styleState.setDefaultStyle(currentDefault);
            styleState.setCurrent(remapDefaultColors(active, previousDefault, currentDefault));
            remapBufferDefaults(myTerminal.getTerminalTextBuffer(), previousDefault, currentDefault);
            RefreshableTerminalPanel panel = (RefreshableTerminalPanel) getTerminalPanel();
            panel.refreshFontAndSize();
            panel.setCursorShape(cursorShape(current));
            refreshFindComponentTheme();
            panel.repaint();
        }

        private static void remapBufferDefaults(
                TerminalTextBuffer buffer, TextStyle previousDefault, TextStyle currentDefault) {
            buffer.lock();
            try {
                for (int row = -buffer.getHistoryLinesCount(); row < buffer.getHeight(); row++) {
                    var line = buffer.getLine(row);
                    String text = line.getText();
                    int start = 0;
                    while (start < text.length()) {
                        TextStyle oldStyle = line.getStyleAt(start);
                        int end = start + 1;
                        while (end < text.length() && Objects.equals(oldStyle, line.getStyleAt(end))) end++;
                        TextStyle newStyle = remapDefaultColors(oldStyle, previousDefault, currentDefault);
                        if (!Objects.equals(oldStyle, newStyle)) {
                            line.writeString(start,
                                    new com.techsenger.jeditermfx.core.model.CharBuffer(
                                            text.substring(start, end).toCharArray(), 0, end - start),
                                    newStyle);
                        }
                        start = end;
                    }
                }
            } finally {
                buffer.unlock();
            }
        }

        private static TextStyle remapDefaultColors(
                TextStyle style, TextStyle previousDefault, TextStyle currentDefault) {
            if (style == null) return null;
            TextStyle.Builder builder = style.toBuilder();
            boolean changed = false;
            if (Objects.equals(style.getForeground(), previousDefault.getForeground())) {
                builder.setForeground(currentDefault.getForeground());
                changed = true;
            }
            if (Objects.equals(style.getBackground(), previousDefault.getBackground())) {
                builder.setBackground(currentDefault.getBackground());
                changed = true;
            }
            return changed ? builder.build() : style;
        }

        @Override
        protected @NotNull TerminalExecutorServiceManager createExecutorServiceManager() {
            return new BoundedTerminalExecutorServiceManager();
        }

    }

    private static final class RefreshableTerminalPanel extends TerminalPanel {
        private final FastRemoteTerminalSettings settings;

        private RefreshableTerminalPanel(
                com.techsenger.jeditermfx.ui.settings.SettingsProvider settingsProvider,
                StyleState styleState,
                TerminalTextBuffer terminalTextBuffer) {
            super(settingsProvider, terminalTextBuffer, styleState);
            settings = (FastRemoteTerminalSettings) settingsProvider;
        }

        private void refreshFontAndSize() {
            reinitFontAndResize();
        }

        @Override
        public void setCursorShape(CursorShape shape) {
            super.setCursorShape(shape == null ? cursorShape(settings.preferences()) : shape);
        }
    }

    static final class FastRemoteTerminalSettings extends DefaultSettingsProvider {
        private java.util.function.BiConsumer<javafx.scene.control.ContextMenu, Node> menuPreparation =
                (menu, owner) -> {};

        @Override
        public void prepareContextMenu(javafx.scene.control.ContextMenu menu, Node owner) {
            menuPreparation.accept(menu, owner);
        }
        private static final int MAX_SCROLLBACK_LINES = 10_000;
        private static final String APPLICATION_CONTEXT_MENU_STYLE_CLASS = "application-context-menu";
        private static final String DARK_CONTEXT_MENU_STYLE_CLASS = "terminal-context-dark";
        private static final String LIGHT_CONTEXT_MENU_STYLE_CLASS = "terminal-context-light";
        private volatile TerminalPreferences preferences;
        private volatile TerminalShortcutSettings shortcuts;
        private volatile java.util.function.Supplier<java.util.List<ContextMenuAction>> contextMenuItems =
                java.util.List::of;

        FastRemoteTerminalSettings(TerminalPreferences preferences) {
            this.preferences = Objects.requireNonNull(preferences, "preferences");
        }

        private TerminalPreferences preferences() {
            return preferences;
        }

        private void update(TerminalPreferences preferences) {
            this.preferences = Objects.requireNonNull(preferences, "preferences");
        }

        void updateShortcuts(TerminalShortcutSettings shortcuts) {
            // Values have already passed the settings editor, but retain adapter-boundary validation.
            KeyCombination.valueOf(shortcuts.copy());
            KeyCombination.valueOf(shortcuts.paste());
            KeyCombination.valueOf(shortcuts.find());
            this.shortcuts = shortcuts;
        }

        void setContextMenuItems(
                java.util.function.Supplier<java.util.List<ContextMenuAction>> items) {
            contextMenuItems = Objects.requireNonNull(items, "items");
        }

        @Override
        public @NotNull java.util.List<TerminalContextMenuEntry> getContextMenuEntries() {
            return contextMenuItems.get().stream()
                    .map(FastRemoteTerminalSettings::toJediEntry)
                    .toList();
        }

        private static TerminalContextMenuEntry toJediEntry(ContextMenuAction entry) {
            if (entry instanceof ContextMenuAction.Item item) {
                return new TerminalContextMenuEntry.Action(
                        item.label(), item.callback(), item.enabled());
            }
            ContextMenuAction.Submenu submenu = (ContextMenuAction.Submenu) entry;
            return new TerminalContextMenuEntry.Submenu(
                    submenu.label(), submenu.children().stream()
                            .map(FastRemoteTerminalSettings::toJediEntry).toList(),
                    submenu.enabled());
        }

        @Override
        public int getBufferMaxLinesCount() {
            return MAX_SCROLLBACK_LINES;
        }

        // JediTermFX draws keystroke predictions on high-latency links in dim palette color 8 until the
        // server echo arrives, so typing over a slow SSH link appeared to fade in. Keep the prediction
        // engine (it still withholds predictions at password prompts and drops them on mismatch) but
        // draw predictions in the terminal's default style.
        private static final TerminalTypeAheadSettings TYPE_AHEAD_SETTINGS = new TerminalTypeAheadSettings(
                true, TerminalTypeAheadSettings.DEFAULT.getLatencyThreshold(), TextStyle.EMPTY);

        @Override
        public @NotNull TerminalTypeAheadSettings getTypeAheadSettings() {
            return TYPE_AHEAD_SETTINGS;
        }

        @Override
        public @NotNull java.util.List<String> getContextMenuStyleClasses() {
            // CSS is owned by fastremote-ui; the fork only exposes host styling hooks. The
            // terminal palette is independent from the application palette, so the popup must
            // carry its own light/dark state into its separate JavaFX popup scene.
            return java.util.List.of(APPLICATION_CONTEXT_MENU_STYLE_CLASS,
                    usesLightChrome(preferences())
                            ? LIGHT_CONTEXT_MENU_STYLE_CLASS : DARK_CONTEXT_MENU_STYLE_CLASS);
        }

        @Override
        public @NotNull String getContextMenuStyle() {
            String family = BundledFontRegistry.harmonyFamily().replace("'", "\\'");
            return "-fx-font-family: '" + family + "';";
        }

        @Override
        public boolean isContextMenuAutoHide() {
            return !System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT)
                    .contains("linux");
        }

        private static boolean usesLightChrome(TerminalPreferences preferences) {
            return switch (preferences.colorScheme()) {
                case FAST_LIGHT -> true;
                case FAST_DARK, SOLARIZED_DARK -> false;
                case CUSTOM -> isLight(preferences.customColorPalette().orElseThrow().background());
            };
        }

        private static boolean isLight(int rgb) {
            double red = linearRgb((rgb >> 16) & 0xff);
            double green = linearRgb((rgb >> 8) & 0xff);
            double blue = linearRgb(rgb & 0xff);
            return 0.2126 * red + 0.7152 * green + 0.0722 * blue > 0.45;
        }

        private static double linearRgb(int component) {
            double value = component / 255.0;
            return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
        }

        @Override
        public boolean openHyperlinksOnClick() {
            return preferences.openHyperlinksOnClick();
        }

        @Override
        public com.techsenger.jeditermfx.core.HyperlinkStyle.HighlightMode getHyperlinkHighlightingMode() {
            return preferences.highlightHyperlinksOnHover()
                    ? com.techsenger.jeditermfx.core.HyperlinkStyle.HighlightMode.HOVER_WITH_BOTH_COLORS
                    : com.techsenger.jeditermfx.core.HyperlinkStyle.HighlightMode.NEVER_WITH_ORIGINAL_COLOR;
        }

        @Override
        public @NotNull TerminalColor getDefaultForeground() {
            return defaultStyle(preferences).getForeground();
        }

        @Override
        public @NotNull TerminalColor getDefaultBackground() {
            return defaultStyle(preferences).getBackground();
        }

        @Override
        @SuppressWarnings("deprecation")
        public @NotNull TextStyle getDefaultStyle() {
            // JediTermFX initializes StyleState from this method, not from the two color getters.
            return defaultStyle(preferences);
        }

        @Override
        public Font getTerminalFont() {
            TerminalPreferences current = preferences;
            return BundledFontRegistry.terminalPrimaryFont(current.fontFamily(), current.fontSize());
        }

        @Override
        public Font getTerminalFallbackFont() {
            TerminalPreferences current = preferences;
            return BundledFontRegistry.harmonyFont(current.fontSize());
        }

        @Override
        public String getTerminalFallbackFontFamily(int codePoint) {
            return BundledFontRegistry.terminalFallbackFamily(codePoint);
        }

        @Override
        public double getExtraLineSpacing() { return preferences.textAppearance().lineSpacing(); }

        @Override
        public double getExtraLetterSpacing() { return preferences.textAppearance().letterSpacing(); }

        @Override
        public float getTerminalFontSize() {
            return preferences.fontSize();
        }

        @Override
        public ColorPalette getTerminalColorPalette() {
            return TerminalPalette.forPreferences(preferences);
        }

        @Override
        public @NotNull TextStyle getSelectionColor() {
            if (preferences.colorScheme() != TerminalColorScheme.CUSTOM) return super.getSelectionColor();
            TerminalColorPalette palette = preferences.customColorPalette().orElseThrow();
            return new TextStyle(rgb(palette.foreground()), rgb(palette.selectionBackground()));
        }

        @Override
        public int caretBlinkingMs() {
            return preferences.cursorBlink() ? 505 : 0;
        }

        @Override
        public boolean audibleBell() {
            return false;
        }

        @Override
        public @NotNull TerminalActionPresentation getOpenUrlActionPresentation() {
            return localized(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5b4cdf68625c2c51"), super.getOpenUrlActionPresentation());
        }

        @Override
        public @NotNull TerminalActionPresentation getCopyActionPresentation() {
            TerminalShortcutSettings current = shortcuts;
            return current == null ? localized(com.ynpe.fastremote.i18n.Texts.text("legacy.java.63d90d977348ab1f"), super.getCopyActionPresentation())
                    : new TerminalActionPresentation(com.ynpe.fastremote.i18n.Texts.text("legacy.java.63d90d977348ab1f"), KeyCombination.valueOf(current.copy()));
        }

        @Override
        public @NotNull TerminalActionPresentation getPasteActionPresentation() {
            TerminalShortcutSettings current = shortcuts;
            return current == null ? localized(com.ynpe.fastremote.i18n.Texts.text("legacy.java.33517926747180e6"), super.getPasteActionPresentation())
                    : new TerminalActionPresentation(com.ynpe.fastremote.i18n.Texts.text("legacy.java.33517926747180e6"), KeyCombination.valueOf(current.paste()));
        }

        @Override
        public @NotNull TerminalActionPresentation getSelectAllActionPresentation() {
            return localized(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3a5040b68abf75f9"), super.getSelectAllActionPresentation());
        }

        @Override
        public @NotNull TerminalActionPresentation getClearBufferActionPresentation() {
            return localized(com.ynpe.fastremote.i18n.Texts.text("legacy.java.bc3a72c717425935"), super.getClearBufferActionPresentation());
        }

        @Override
        public @NotNull TerminalActionPresentation getPageUpActionPresentation() {
            return localized(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d918c1a9a342dd02"), super.getPageUpActionPresentation());
        }

        @Override
        public @NotNull TerminalActionPresentation getPageDownActionPresentation() {
            return localized(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f031682b9f295e32"), super.getPageDownActionPresentation());
        }

        @Override
        public @NotNull TerminalActionPresentation getLineUpActionPresentation() {
            return localized(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a1c2931850241896"), super.getLineUpActionPresentation());
        }

        @Override
        public @NotNull TerminalActionPresentation getLineDownActionPresentation() {
            return localized(com.ynpe.fastremote.i18n.Texts.text("legacy.java.dbe66822e12d3b28"), super.getLineDownActionPresentation());
        }

        @Override
        public @NotNull TerminalActionPresentation getFindActionPresentation() {
            TerminalShortcutSettings current = shortcuts;
            return current == null ? localized(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3003318e729a860b"), super.getFindActionPresentation())
                    : new TerminalActionPresentation(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3003318e729a860b"), KeyCombination.valueOf(current.find()));
        }

        @Override
        public @NotNull String getFindFieldPromptText() {
            return com.ynpe.fastremote.i18n.Texts.text("legacy.java.3003318e729a860b");
        }

        @Override
        public @NotNull String getFindMatchCaseText() {
            return com.ynpe.fastremote.i18n.Texts.text("editor.find.matchCase");
        }

        @Override
        public @NotNull String getFindRegexText() {
            return com.ynpe.fastremote.i18n.Texts.text("editor.find.regex");
        }

        @Override
        public @NotNull String getFindPreviousText() {
            return com.ynpe.fastremote.i18n.Texts.text("editor.find.previous");
        }

        @Override
        public @NotNull String getFindNextText() {
            return com.ynpe.fastremote.i18n.Texts.text("editor.find.next");
        }

        @Override
        public @NotNull String getFindInvalidRegexText() {
            return com.ynpe.fastremote.i18n.Texts.text("editor.find.invalidRegex");
        }

        @Override
        public @NotNull String getFindResultText(int selectedIndex, int resultCount) {
            return resultCount == 0 ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.f1c9cc430f6e6725") : selectedIndex + " / " + resultCount;
        }

        @Override public @NotNull String getTextInputUndoText() {
            return com.ynpe.fastremote.i18n.Texts.text("textInput.context.undo");
        }

        @Override public @NotNull String getTextInputRedoText() {
            return com.ynpe.fastremote.i18n.Texts.text("textInput.context.redo");
        }

        @Override public @NotNull String getTextInputCutText() {
            return com.ynpe.fastremote.i18n.Texts.text("textInput.context.cut");
        }

        @Override public @NotNull String getTextInputCopyText() {
            return com.ynpe.fastremote.i18n.Texts.text("textInput.context.copy");
        }

        @Override public @NotNull String getTextInputPasteText() {
            return com.ynpe.fastremote.i18n.Texts.text("textInput.context.paste");
        }

        @Override public @NotNull String getTextInputDeleteText() {
            return com.ynpe.fastremote.i18n.Texts.text("textInput.context.delete");
        }

        @Override public @NotNull String getTextInputSelectAllText() {
            return com.ynpe.fastremote.i18n.Texts.text("textInput.context.selectAll");
        }

        private static TerminalActionPresentation localized(
                String name, TerminalActionPresentation original) {
            return new TerminalActionPresentation(name, original.getKeyCombinations());
        }

        private static TextStyle defaultStyle(TerminalPreferences preferences) {
            return switch (preferences.colorScheme()) {
                case FAST_DARK -> new TextStyle(
                        TerminalColor.rgb(221, 227, 234), TerminalColor.rgb(18, 22, 28));
                case SOLARIZED_DARK -> new TextStyle(
                        TerminalColor.rgb(131, 148, 150), TerminalColor.rgb(0, 43, 54));
                case FAST_LIGHT -> new TextStyle(
                        TerminalColor.rgb(38, 50, 56), TerminalColor.rgb(250, 250, 250));
                case CUSTOM -> {
                    TerminalColorPalette palette = preferences.customColorPalette().orElseThrow();
                    yield new TextStyle(rgb(palette.foreground()), rgb(palette.background()));
                }
            };
        }

        private static TerminalColor rgb(int value) {
            return TerminalColor.rgb((value >> 16) & 0xff, (value >> 8) & 0xff, value & 0xff);
        }
    }

    private static CursorShape cursorShape(TerminalPreferences preferences) {
        boolean blink = preferences.cursorBlink();
        return switch (preferences.cursorShape()) {
            case BLOCK -> blink ? CursorShape.BLINK_BLOCK : CursorShape.STEADY_BLOCK;
            case VERTICAL_BAR -> blink ? CursorShape.BLINK_VERTICAL_BAR : CursorShape.STEADY_VERTICAL_BAR;
            case UNDERLINE -> blink ? CursorShape.BLINK_UNDERLINE : CursorShape.STEADY_UNDERLINE;
        };
    }

    private static final class TerminalPalette extends ColorPalette {
        private static final TerminalPalette FAST_DARK = new TerminalPalette(new int[]{
                0x1b2028, 0xe06c75, 0x98c379, 0xe5c07b, 0x61afef, 0xc678dd, 0x56b6c2, 0xabb2bf,
                0x5c6370, 0xff7a85, 0xb3db8c, 0xffd68a, 0x79bdff, 0xdb8cf2, 0x70d1dd, 0xf2f4f8
        });
        private static final TerminalPalette SOLARIZED_DARK = new TerminalPalette(new int[]{
                0x073642, 0xdc322f, 0x859900, 0xb58900, 0x268bd2, 0xd33682, 0x2aa198, 0xeee8d5,
                0x586e75, 0xcb4b16, 0x93a1a1, 0x839496, 0x657b83, 0x6c71c4, 0x2aa198, 0xfdf6e3
        });
        private static final TerminalPalette FAST_LIGHT = new TerminalPalette(new int[]{
                0x263238, 0xc62828, 0x2e7d32, 0x9a6700, 0x1565c0, 0x7b1fa2, 0x00838f, 0xeceff1,
                0x607d8b, 0xe53935, 0x43a047, 0xb77900, 0x1e88e5, 0x8e24aa, 0x00acc1, 0xffffff
        });

        private final Color[] colors;

        private TerminalPalette(int[] rgb) {
            colors = java.util.Arrays.stream(rgb).mapToObj(Color::new).toArray(Color[]::new);
        }

        private static TerminalPalette forPreferences(TerminalPreferences preferences) {
            return switch (preferences.colorScheme()) {
                case FAST_DARK -> FAST_DARK;
                case SOLARIZED_DARK -> SOLARIZED_DARK;
                case FAST_LIGHT -> FAST_LIGHT;
                case CUSTOM -> new TerminalPalette(preferences.customColorPalette().orElseThrow().ansiColors()
                        .stream().mapToInt(Integer::intValue).toArray());
            };
        }

        @Override
        protected @NotNull Color getForegroundByColorIndex(int colorIndex) {
            return colors[colorIndex];
        }

        @Override
        protected @NotNull Color getBackgroundByColorIndex(int colorIndex) {
            return colors[colorIndex];
        }
    }

    private static final class BoundedTerminalExecutorServiceManager implements TerminalExecutorServiceManager {
        private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                new TerminalThreadFactory("fastremote-terminal-scheduled-"));
        private final ExecutorService workers = Executors.newFixedThreadPool(
                4,
                new TerminalThreadFactory("fastremote-terminal-worker-"));

        @Override
        public @NotNull ScheduledExecutorService getSingleThreadScheduledExecutor() {
            return scheduler;
        }

        @Override
        public @NotNull ExecutorService getUnboundedExecutorService() {
            return workers;
        }

        @Override
        public void shutdownWhenAllExecuted() {
            if (!scheduler.isShutdown()) {
                scheduler.execute(scheduler::shutdown);
            }
            workers.shutdown();
        }
    }

    private static final class TerminalThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger sequence = new AtomicInteger();

        private TerminalThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = Thread.ofPlatform()
                    .name(prefix + sequence.incrementAndGet())
                    .daemon(true)
                    .unstarted(task);
            thread.setUncaughtExceptionHandler((ignored, error) ->
                    System.getLogger(FastRemoteTerminal.class.getName())
                            .log(System.Logger.Level.ERROR, "Terminal worker failed", error));
            return thread;
        }
    }
}
