package com.techsenger.jeditermfx.core;

import com.techsenger.jeditermfx.core.util.TermSize;
import com.techsenger.jeditermfx.core.util.TestSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FullScreenEditorReplayTest {
    @Test
    void nanoPagingAndExitPreserveShellOutput() throws IOException {
        replay("nano");
    }

    @Test
    void vimPagingAndExitPreserveShellOutput() throws IOException {
        replay("vim");
    }

    private void replay(String editor) throws IOException {
        Properties recording = new Properties();
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream(
                "/replays/" + editor + "-paging.properties"))) {
            recording.load(stream);
        }
        for (TermSize size : List.of(new TermSize(80, 24), new TermSize(80, 28),
                new TermSize(64, 12))) {
            TestSession session = new TestSession(80, 24);
            List<String> expected = new ArrayList<>();
            for (int row = 1; row <= 22; row++) {
                expected.add("previous output " + row);
                session.process(expected.getLast() + "\r\n");
            }
            expected.add("$ " + editor + " sample.txt");
            session.process(expected.getLast() + "\r\n");
            for (String stage : List.of("open", "end", "pagedown", "pageup")) {
                session.process(recording.getProperty(stage));
            }
            session.getTerminal().resize(size, RequestOrigin.User);
            session.process(recording.getProperty("exit") + "$ ");
            expected.add("$");

            assertFalse(session.getTerminalTextBuffer().isUsingAlternateBuffer());
            session.assertCursorPosition(3, size.getRows());
            List<String> actual = new ArrayList<>(
                    session.getTerminalTextBuffer().getHistoryBuffer().getLineTexts());
            actual.addAll(session.getTerminalTextBuffer().getScreenBuffer().getLineTexts());
            assertEquals(expected, actual.stream().map(String::stripTrailing)
                    .filter(line -> !line.isEmpty()).toList(), editor + " at " + size);
        }
    }
}
