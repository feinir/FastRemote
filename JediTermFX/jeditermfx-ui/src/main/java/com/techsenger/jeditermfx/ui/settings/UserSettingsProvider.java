package com.techsenger.jeditermfx.ui.settings;

import org.jetbrains.annotations.NotNull;
import javafx.scene.text.Font;
import java.util.Objects;
import com.techsenger.jeditermfx.core.HyperlinkStyle;
import com.techsenger.jeditermfx.core.TerminalColor;
import com.techsenger.jeditermfx.core.TextStyle;
import com.techsenger.jeditermfx.core.emulator.ColorPalette;
import com.techsenger.jeditermfx.core.model.TerminalTypeAheadSettings;

public interface UserSettingsProvider {

    ColorPalette getTerminalColorPalette();

    Font getTerminalFont();

    /** Font used for Unicode graphemes outside the terminal's primary ASCII font. */
    default Font getTerminalFallbackFont() {
        return getTerminalFont();
    }

    /** Resolves the fallback family for one Unicode code point. */
    default String getTerminalFallbackFontFamily(int codePoint) {
        return getTerminalFallbackFont().getFamily();
    }

    float getTerminalFontSize();

    /** Additional cell spacing in logical pixels. */
    default double getExtraLineSpacing() { return 0; }
    default double getExtraLetterSpacing() { return 0; }

    /** @return vertical scaling factor */
    default float getLineSpacing() {
        return 1.0f;
    }

    default boolean shouldDisableLineSpacingForAlternateScreenBuffer() {
        return false;
    }

    default boolean shouldFillCharacterBackgroundIncludingLineSpacing() {
        return true;
    }

    default @NotNull TerminalColor getDefaultForeground() {
        return Objects.requireNonNull(getDefaultStyle().getForeground());
    }

    default @NotNull TerminalColor getDefaultBackground() {
        return Objects.requireNonNull(getDefaultStyle().getBackground());
    }

    /**
     * @deprecated override {@link UserSettingsProvider#getDefaultForeground()} and
     * {@link UserSettingsProvider#getDefaultBackground()} instead
     */
    @Deprecated
    default @NotNull TextStyle getDefaultStyle() {
        return new TextStyle(TerminalColor.BLACK, TerminalColor.WHITE);
    }

    @NotNull
    TextStyle getSelectionColor();

    @NotNull
    TextStyle getFoundPatternColor();

    TextStyle getHyperlinkColor();

    HyperlinkStyle.HighlightMode getHyperlinkHighlightingMode();

    /** Opens detected links without requiring the platform shortcut modifier. */
    default boolean openHyperlinksOnClick() {
        return true;
    }

    default boolean enableTextBlinking() {
        return false;
    }

    default int slowTextBlinkMs() {
        return 1000;
    }

    default int rapidTextBlinkMs() {
        return 500;
    }

    boolean useInverseSelectionColor();

    boolean copyOnSelect();

    boolean pasteOnMiddleMouseClick();

    boolean emulateX11CopyPaste();

    boolean useAntialiasing();

    int maxRefreshRate();

    boolean audibleBell();

    boolean enableMouseReporting();

    int caretBlinkingMs();

    boolean scrollToBottomOnTyping();

    boolean DECCompatibilityMode();

    boolean forceActionOnMouseReporting();

    int getBufferMaxLinesCount();

    boolean altSendsEscape();

    boolean ambiguousCharsAreDoubleWidth();

    @NotNull
    TerminalTypeAheadSettings getTypeAheadSettings();

    boolean sendArrowKeysInAlternativeMode();
}
