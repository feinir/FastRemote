package com.techsenger.jeditermfx.core;

import com.techsenger.jeditermfx.core.util.TestSession;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AptFancyProgressTest {

    private static final String ESC = "\u001B";

    @Test
    void keepsFancyProgressOnReservedBottomRow() throws IOException {
        TestSession session = new TestSession(20, 5);
        session.process("one\r\ntwo\r\nthree\r\nfour");

        // Dpkg::Progress-Fancy reserves the last terminal row and restores the cursor
        // to the normal output area after every progress update.
        session.process(ESC + "7" + ESC + "[0;4r" + ESC + "8");
        updateProgress(session, 10);
        session.process("\r\nfive");
        updateProgress(session, 50);
        session.process("\r\nsix");
        updateProgress(session, 90);

        assertEquals(List.of("three", "four", "five", "six", "Progress: 90%#######"),
                session.getTerminalTextBuffer().getScreenBuffer().getLineTexts());
        assertEquals(List.of("one", "two"),
                session.getTerminalTextBuffer().getHistoryBuffer().getLineTexts());
        assertEquals(TerminalColor.index(2),
                session.getTerminalTextBuffer().getStyleAt(0, 4).getBackground());
        assertNull(session.getTerminalTextBuffer().getStyleAt(0, 3).getBackground());
    }

    @Test
    void aptSetupDrawAndStopNeverMovesProgressIntoNormalOutput() throws IOException {
        TestSession session = new TestSession(30, 5);
        session.process("one\r\ntwo\r\nthree\r\nfour");

        setupAptScrollArea(session, 5);
        drawAptProgress(session, 11, "[##......]");
        session.process("\r\nfive");
        drawAptProgress(session, 22, "[####....]");

        assertTrue(session.getTerminalTextBuffer().getScreenBuffer().getLineText(4)
                .startsWith("Progress: [ 22%]"));
        assertFalse(normalOutputContainsProgress(session));

        // apt restores the full scrolling area and clears the status line before returning.
        setupAptScrollArea(session, 6);
        session.process(ESC + "[J");

        assertFalse(session.getTerminalTextBuffer().getScreenBuffer().getLineTexts().stream()
                .anyMatch(line -> line.contains("Progress:")));
        assertFalse(session.getTerminalTextBuffer().getHistoryBuffer().getLineTexts().stream()
                .anyMatch(line -> line.contains("Progress:")));
    }

    @Test
    void unicodeProgressBlocksRemainSingleColumnInsideReservedRow() throws IOException {
        TestSession session = new TestSession(135, 5);
        session.process("one\r\ntwo\r\nthree\r\nfour");
        setupAptScrollArea(session, 5);

        String unicodeBar = "[" + "█".repeat(12) + "▏" + " ".repeat(100) + "]";
        drawAptProgress(session, 11, unicodeBar);
        session.process("\r\nfive");
        drawAptProgress(session, 22, unicodeBar);

        assertTrue(session.getTerminalTextBuffer().getScreenBuffer().getLineText(4)
                .startsWith("Progress: [ 22%]"));
        assertFalse(normalOutputContainsProgress(session));
    }

    private static void updateProgress(TestSession session, int percent) throws IOException {
        session.process(ESC + "7" + ESC + "[5;1H" + ESC + "[42mProgress: " + percent + "%#######"
                + ESC + "[0K" + ESC + "[0m" + ESC + "8");
    }

    private static void setupAptScrollArea(TestSession session, int rowCount) throws IOException {
        session.process("\n" + ESC + "7" + ESC + "[0;" + (rowCount - 1) + "r" + ESC + "8" + ESC + "[1A");
    }

    private static void drawAptProgress(TestSession session, int percent, String bar) throws IOException {
        session.process(ESC + "7" + ESC + "[5;0f" + ESC + "[42m" + ESC + "[30m"
                + "Progress: [ " + percent + "%]" + ESC + "[49m" + ESC + "[39m " + bar + " " + ESC + "8");
    }

    private static boolean normalOutputContainsProgress(TestSession session) {
        return session.getTerminalTextBuffer().getScreenBuffer().getLineTexts().subList(0, 4).stream()
                .anyMatch(line -> line.contains("Progress:"));
    }
}
