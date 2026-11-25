package com.techsenger.jeditermfx.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.techsenger.jeditermfx.core.util.Platform;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.Test;

class TerminalPanelShortcutTest {
    @Test
    void unmatchedMacCommandShortcutIsLeftForTheApplication() {
        KeyEvent commandAltC = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.C,
                false, false, true, true);
        assertEquals(Platform.isMacOS(), TerminalPanel.passPlatformShortcutToApplication(commandAltC));
    }

    @Test
    void ordinaryTerminalInputIsNotPassedToTheApplication() {
        KeyEvent c = new KeyEvent(KeyEvent.KEY_PRESSED, "c", "c", KeyCode.C,
                false, false, false, false);
        assertFalse(TerminalPanel.passPlatformShortcutToApplication(c));
    }

    @Test
    void reservesModifiedTerminalKeysFromApplicationAccelerators() {
        assertTrue(TerminalPanel.reserveModifiedKeyForTerminal(
                keyPressed(KeyCode.W, false, true, false, false)));
        assertTrue(TerminalPanel.reserveModifiedKeyForTerminal(
                keyPressed(KeyCode.B, false, true, true, false)));
        assertTrue(TerminalPanel.reserveModifiedKeyForTerminal(
                keyPressed(KeyCode.B, false, false, true, false)));
        assertEquals(!Platform.isMacOS(), TerminalPanel.reserveModifiedKeyForTerminal(
                keyPressed(KeyCode.B, false, false, false, true)));
    }

    @Test
    void mapsControlLettersToAsciiControlCharactersOnKeyPressed() {
        assertEquals(3, TerminalPanel.controlCharacterFor(keyPressed(KeyCode.C,
                false, true, false, false)));
        assertEquals(4, TerminalPanel.controlCharacterFor(keyPressed(KeyCode.D,
                false, true, false, false)));
        assertEquals(26, TerminalPanel.controlCharacterFor(keyPressed(KeyCode.Z,
                false, true, false, false)));
    }

    @Test
    void leavesNonControlAndAltGraphLikeCombinationsUnmapped() {
        assertEquals(-1, TerminalPanel.controlCharacterFor(keyPressed(KeyCode.C,
                false, false, false, false)));
        assertEquals(-1, TerminalPanel.controlCharacterFor(keyPressed(KeyCode.C,
                false, true, true, false)));
        assertEquals(-1, TerminalPanel.controlCharacterFor(keyPressed(KeyCode.C,
                false, true, false, true)));
    }

    @Test
    void mapsPortableTerminalControlPunctuation() {
        assertEquals(27, TerminalPanel.controlCharacterFor(keyPressed(KeyCode.OPEN_BRACKET,
                false, true, false, false)));
        assertEquals(28, TerminalPanel.controlCharacterFor(keyPressed(KeyCode.BACK_SLASH,
                false, true, false, false)));
        assertEquals(29, TerminalPanel.controlCharacterFor(keyPressed(KeyCode.CLOSE_BRACKET,
                false, true, false, false)));
        assertEquals(30, TerminalPanel.controlCharacterFor(keyPressed(KeyCode.DIGIT6,
                true, true, false, false)));
        assertEquals(31, TerminalPanel.controlCharacterFor(keyPressed(KeyCode.MINUS,
                true, true, false, false)));
        assertEquals(127, TerminalPanel.controlCharacterFor(keyPressed(KeyCode.SLASH,
                true, true, false, false)));
        assertEquals(0, TerminalPanel.controlCharacterFor(keyPressed(KeyCode.DIGIT2,
                true, true, false, false)));
    }

    @Test
    void preservesCompleteUnicodeTextFromKeyAndInputMethodEvents() {
        assertEquals("中文.txt", TerminalPanel.printableInputText("中文.txt"));
        assertEquals("繁體.txt", TerminalPanel.printableInputText("繁體.txt"));
        assertEquals("日本語.txt", TerminalPanel.printableInputText("日本語.txt"));
        assertEquals("한글.txt", TerminalPanel.printableInputText("한글.txt"));
        assertEquals("ภาษาไทย.txt", TerminalPanel.printableInputText("ภาษาไทย.txt"));
        assertEquals("Файл.txt", TerminalPanel.printableInputText("Файл.txt"));
        assertEquals("📄.txt", TerminalPanel.printableInputText("📄.txt"));
    }

    @Test
    void filtersTerminalControlCharactersWithoutDroppingPrintableUnicode() {
        assertEquals("中文.txt", TerminalPanel.printableInputText("\u0003中\u007f文.txt\n"));
        assertEquals("", TerminalPanel.printableInputText(null));
        assertEquals("", TerminalPanel.printableInputText(""));
    }

    @Test
    void clearsStaleInputMethodCompositionWhenTerminalLosesFocus() {
        TerminalPanel.InputMethodCompositionState composition =
                new TerminalPanel.InputMethodCompositionState();

        composition.update("拼音");
        assertTrue(composition.hasText());
        composition.focusChanged(true);
        assertEquals("拼音", composition.text());

        composition.focusChanged(false);
        assertFalse(composition.hasText());
        assertEquals("", composition.text());
    }

    private static KeyEvent keyPressed(
            KeyCode code, boolean shift, boolean control, boolean alt, boolean meta) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, code.getChar(), code.getChar(), code,
                shift, control, alt, meta);
    }
}
