package com.techsenger.jeditermfx.core;

import com.techsenger.jeditermfx.core.Color;
import com.techsenger.jeditermfx.core.emulator.ColorPalette;
import com.techsenger.jeditermfx.core.model.TerminalTextBuffer;
import com.techsenger.jeditermfx.core.util.TestSession;
import com.techsenger.jeditermfx.core.util.TermSize;
import org.jetbrains.annotations.NotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

/**
 * @author traff
 */
public class EmulatorTest extends EmulatorTestAbstract {

    @Test
    public void testSetCursorPosition(TestInfo testInfo) throws IOException {
        doTest(testInfo, 3, 4, "X00\n" +  //X wins
                "0X \n" +
                "X X\n" +
                "   \n");
    }

    //public void testTooLargeScrollRegion() throws IOException { TODO: this test fails on Travis somehow
    //  doTest(80, 24);
    //}

    @Test
    public void testMidnightCommanderOnVT100(TestInfo testInfo) throws IOException {
        doTest(testInfo);
    }

    @Test
    public void testMidnightCommanderOnXTerm(TestInfo testInfo) throws IOException {
        TerminalTextBuffer terminalTextBuffer = doTest(testInfo);
        assertColor(terminalTextBuffer.getStyleAt(8, 2), ColorPalette.getIndexedTerminalColor(3),
                ColorPalette.getIndexedTerminalColor(4));
        assertColor(terminalTextBuffer.getStyleAt(23, 4), ColorPalette.getIndexedTerminalColor(7),
                ColorPalette.getIndexedTerminalColor(4));
        assertColor(terminalTextBuffer.getStyleAt(2, 0), ColorPalette.getIndexedTerminalColor(0),
                ColorPalette.getIndexedTerminalColor(6));
    }

    @Test
    public void testEraseBeyondTerminalWidth(TestInfo testInfo) throws IOException {
        doTest(testInfo);
    }

    @Test
    public void testSystemCommands(TestInfo testInfo) throws IOException {
        doTest(testInfo, 30, 3);
    }

    @Test
    public void testOscSetTitle() throws IOException {
        TestSession session = new TestSession(30, 3);
        session.process("\u001B]0;Title A\u001B\\Done1 ");
        assertEquals("Title A", session.getDisplay().getWindowTitle());
        session.process("\u001B]1;Title B\u001B\\Done2 ");
        assertEquals("Title B", session.getDisplay().getWindowTitle());
        session.process("\u001B]2;Title C\u001B\\Done3");
        assertEquals("Title C", session.getDisplay().getWindowTitle());
        assertEquals("Done1 Done2 Done3", session.getTerminal().getTextBuffer().getScreenLines().trim());
    }

    @Test
    public void testOsc7ReportsWorkingDirectoryUri() throws IOException {
        TestSession session = new TestSession(30, 3);
        AtomicReference<String> workingDirectory = new AtomicReference<>();
        session.getTerminal().addWorkingDirectoryListener(workingDirectory::set);

        session.process("\u001B]7;file://server.example/home/user/My%20Files\u001B\\");

        assertEquals("file://server.example/home/user/My%20Files", workingDirectory.get());
    }

    @Test
    public void testOsc10Query() throws IOException {
        TestSession session = new TestSession(10, 10);
        session.getDisplay().setWindowForeground(new Color(16, 15, 14));
        session.process("\u001B]10;?\7");
        assertEquals("\033]10;rgb:1010/0f0f/0e0e\7", session.getTerminal().getOutputAndClear());
        session.process("\u001B]10;?\u001B\\");
        assertEquals("\033]10;rgb:1010/0f0f/0e0e\u001B\\", session.getTerminal().getOutputAndClear());
    }

    @Test
    public void testOsc11Query() throws IOException {
        TestSession session = new TestSession(10, 10);
        session.getDisplay().setWindowBackground(new Color(16, 15, 14));
        session.process("\u001B]11;?\7");
        assertEquals("\033]11;rgb:1010/0f0f/0e0e\7", session.getTerminal().getOutputAndClear());
        session.process("\u001B]11;?\u001B\\");
        assertEquals("\033]11;rgb:1010/0f0f/0e0e\u001B\\", session.getTerminal().getOutputAndClear());
    }

    @Test
    public void alternateScreenCursorSaveDoesNotOverwriteShellCursor() throws IOException {
        TestSession session = new TestSession(40, 8);
        session.process("output 1\r\noutput 2\r\noutput 3\r\n$ nano sample.txt\r\n");
        session.assertCursorPosition(1, 5);

        session.process(csi("?1049h") + csi("2J") + csi("3;12H") + esc("7"));
        session.process(csi("1;6r") + csi("1;1H") + esc("M") + esc("8"));
        session.assertCursorPosition(12, 3);
        session.process(csi("r") + csi("?1049l") + "$ ");

        session.assertCursorPosition(3, 5);
        assertEquals("$ nano sample.txt", session.getTerminalTextBuffer().getLine(3).getText());
        assertEquals("$ ", session.getTerminalTextBuffer().getLine(4).getText());
    }

    @Test
    public void cursorSaveSlotsAreLocalToEachScreen() throws IOException {
        TestSession session = new TestSession(40, 8);
        session.process(csi("5;3H") + csi("32m") + esc("7") + csi("?47h"));
        // A fresh alternate screen has no saved cursor, even if the primary does.
        session.process(esc("8"));
        session.assertCursorPosition(1, 1);
        session.process(csi("2;9H") + csi("31m") + csi("?1048h"));
        session.process(csi("6;20H") + csi("?1048l"));
        session.assertCursorPosition(9, 2);
        assertEquals(TerminalColor.index(1), session.getCurrentStyle().getForeground());

        session.process(csi("?47l") + esc("8"));
        session.assertCursorPosition(3, 5);
        assertEquals(TerminalColor.index(2), session.getCurrentStyle().getForeground());
    }

    @Test
    public void repeatedAlternateScreenModesDoNotReplaceTheSavedShellCursor() throws IOException {
        TestSession session = new TestSession(40, 8);
        session.process(csi("5;1H") + csi("?1049h") + csi("2;10H"));
        session.process(csi("?1049h") + esc("7") + csi("?1049l") + "$ ");
        session.process(csi("?1049l"));
        session.assertCursorPosition(3, 5);
        assertEquals("$ ", session.getTerminalTextBuffer().getLine(4).getText());
    }

    @Test
    public void alternateCursorSaveAndResizeKeepPrimaryOutputInOrder() throws IOException {
        for (TermSize size : List.of(new TermSize(40, 12), new TermSize(40, 4),
                new TermSize(24, 12), new TermSize(24, 4))) {
            TestSession session = new TestSession(40, 8);
            for (int row = 1; row < 7; row++) {
                session.process("output " + row + "\r\n");
            }
            session.process("$ nano sample.txt\r\n" + csi("?1049h")
                    + csi("2J") + csi("1;1H") + "nano sample.txt");
            session.getTerminal().resize(size, RequestOrigin.User);
            session.process(csi("2;9H") + esc("7") + csi("1;1H") + esc("8"));
            session.process(csi("?1049l") + "$ ");

            List<String> restored = new java.util.ArrayList<>(
                    session.getTerminalTextBuffer().getHistoryBuffer().getLineTexts());
            restored.addAll(session.getTerminalTextBuffer().getScreenBuffer().getLineTexts());
            assertEquals(List.of("output 1", "output 2", "output 3", "output 4",
                            "output 5", "output 6", "$ nano sample.txt", "$"),
                    restored.stream().map(String::stripTrailing).filter(line -> !line.isEmpty()).toList());
            session.assertCursorPosition(3, size.getRows());
        }
    }

    @Test
    public void alternateScreenResizeKeepsRestoredPrimaryOutputInOrder() throws IOException {
        TestSession session = new TestSession(12, 6);
        for (int line = 1; line <= 10; line++) {
            if (line > 1) session.process("\r\n");
            session.process("line" + line);
        }

        session.process(csi("?1049h"));
        session.getTerminal().resize(new TermSize(20, 12), RequestOrigin.User);
        session.process(csi("?1049l"));
        session.process("\r\nprompt");

        List<String> restored = new java.util.ArrayList<>(
                session.getTerminalTextBuffer().getHistoryBuffer().getLineTexts());
        restored.addAll(session.getTerminalTextBuffer().getScreenBuffer().getLineTexts());
        assertEquals(List.of(
                "line1", "line2", "line3", "line4", "line5",
                "line6", "line7", "line8", "line9", "line10", "prompt"),
                restored.stream().filter(line -> !line.isEmpty()).toList());
        session.assertCursorPosition(7, 12);
    }

    @Test
    public void alternateScreenGrowthKeepsBottomPromptAtBottom() throws IOException {
        TestSession session = new TestSession(12, 6);
        for (int row = 1; row < 6; row++) {
            session.process("line" + row + "\r\n");
        }
        session.process("$ ");
        session.assertCursorPosition(3, 6);

        session.process(csi("?1049h"));
        session.getTerminal().resize(new TermSize(12, 12), RequestOrigin.User);
        session.process(csi("?1049l"));
        session.process("\r\n$ ");

        session.assertCursorPosition(3, 12);
        List<String> restored = new java.util.ArrayList<>(
                session.getTerminalTextBuffer().getHistoryBuffer().getLineTexts());
        restored.addAll(session.getTerminalTextBuffer().getScreenBuffer().getLineTexts());
        assertEquals(List.of("line1", "line2", "line3", "line4", "line5", "$", "$"),
                restored.stream().map(String::stripTrailing).filter(line -> !line.isEmpty()).toList());
    }

    @Test
    public void testResetToInitialState() throws IOException {
        TestSession session = new TestSession(20, 4);
        for (int i = 1; i <= 9; i++) {
            if (i > 1) {
                session.process("\r\n");
            }
            session.process("foo " + i);
        }
        assertScreenLines(session, List.of("foo 6", "foo 7", "foo 8", "foo 9"));
        assertHistoryLines(session, List.of("foo 1", "foo 2", "foo 3", "foo 4", "foo 5"));
        session.process(esc("c"));
        assertScreenLines(session, List.of(""));
        assertHistoryLines(session, List.of());
    }

    @Test
    public void testSoftReset() throws IOException {
        TestSession session = new TestSession(20, 4);
        for (int i = 1; i <= 9; i++) {
            if (i > 1) {
                session.process("\r\n");
            }
            session.process("foo " + i);
        }
        assertScreenLines(session, List.of("foo 6", "foo 7", "foo 8", "foo 9"));
        assertHistoryLines(session, List.of("foo 1", "foo 2", "foo 3", "foo 4", "foo 5"));
        session.process(csi("!p"));
        assertScreenLines(session, List.of(""));
        assertHistoryLines(session, List.of("foo 1", "foo 2", "foo 3", "foo 4", "foo 5"));
    }

    @Test
    public void testEraseInDisplay3() throws IOException {
        TestSession session = new TestSession(20, 2);
        for (int i = 1; i <= 5; i++) {
            if (i > 1) {
                session.process("\r\n");
            }
            session.process("foo " + i);
        }
        assertScreenLines(session, List.of("foo 4", "foo 5"));
        assertHistoryLines(session, List.of("foo 1", "foo 2", "foo 3"));
        session.process(csi("3J"));
        assertScreenLines(session, List.of("", ""));
        assertHistoryLines(session, List.of());
    }

    private void assertScreenLines(@NotNull TestSession session, @NotNull List<String> expectedScreenLines) {
        assertEquals(expectedScreenLines, session.getTerminalTextBuffer().getScreenBuffer().getLineTexts());
    }

    private void assertHistoryLines(@NotNull TestSession session, @NotNull List<String> expectedHistoryLines) {
        assertEquals(expectedHistoryLines, session.getTerminalTextBuffer().getHistoryBuffer().getLineTexts());
    }

    @SuppressWarnings("SameParameterValue")
    private static @NotNull String esc(@NotNull String string) {
        return "\u001B" + string;
    }

    @SuppressWarnings("SameParameterValue")
    private static @NotNull String csi(@NotNull String string) {
        return "\u001B[" + string;
    }

    @Override
    protected @NotNull Path getPathToTest(TestInfo testInfo) {
        return TestPathsManager.getTestDataPath().resolve(testInfo.getTestMethod().get().getName());
    }
}
