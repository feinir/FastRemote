package com.techsenger.jeditermfx.ui.settings;

import com.techsenger.jeditermfx.ui.TerminalActionPresentation;
import com.techsenger.jeditermfx.ui.TerminalContextMenuEntry;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public interface SystemSettingsProvider {

    /** Runs before native popup mapping; the host may prepare CSS, sizing and screen placement. */
    default void prepareContextMenu(javafx.scene.control.ContextMenu menu, javafx.scene.Node owner) {
    }

    /** Host CSS classes applied to the terminal's context menu popup. */
    default @NotNull List<String> getContextMenuStyleClasses() {
        return List.of();
    }

    /** Inline CSS applied before the popup creates its separate scene. */
    default @NotNull String getContextMenuStyle() {
        return "";
    }

    /** Whether the native popup implementation owns dismissal after outside clicks. */
    default boolean isContextMenuAutoHide() {
        return true;
    }

    /** Host application actions inserted before the terminal's built-in context menu actions. */
    default @NotNull List<TerminalContextMenuEntry> getContextMenuEntries() {
        return List.of();
    }

    @NotNull
    TerminalActionPresentation getOpenUrlActionPresentation();

    @NotNull
    TerminalActionPresentation getCopyActionPresentation();

    @NotNull
    TerminalActionPresentation getPasteActionPresentation();

    @NotNull
    TerminalActionPresentation getClearBufferActionPresentation();

    @NotNull
    TerminalActionPresentation getPageUpActionPresentation();

    @NotNull
    TerminalActionPresentation getPageDownActionPresentation();

    @NotNull
    TerminalActionPresentation getLineUpActionPresentation();

    @NotNull
    TerminalActionPresentation getLineDownActionPresentation();

    @NotNull
    TerminalActionPresentation getFindActionPresentation();

    @NotNull
    String getFindFieldPromptText();

    @NotNull
    String getFindMatchCaseText();

    @NotNull
    String getFindRegexText();

    @NotNull
    String getFindPreviousText();

    @NotNull
    String getFindNextText();

    @NotNull
    String getFindInvalidRegexText();

    @NotNull
    String getFindResultText(int selectedIndex, int resultCount);

    default @NotNull String getTextInputUndoText() { return "Undo"; }

    default @NotNull String getTextInputRedoText() { return "Redo"; }

    default @NotNull String getTextInputCutText() { return "Cut"; }

    default @NotNull String getTextInputCopyText() { return "Copy"; }

    default @NotNull String getTextInputPasteText() { return "Paste"; }

    default @NotNull String getTextInputDeleteText() { return "Delete"; }

    default @NotNull String getTextInputSelectAllText() { return "Select All"; }

    @NotNull
    TerminalActionPresentation getSelectAllActionPresentation();
}
