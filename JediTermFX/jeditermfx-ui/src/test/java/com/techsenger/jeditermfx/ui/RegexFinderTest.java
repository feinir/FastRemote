package com.techsenger.jeditermfx.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.techsenger.jeditermfx.core.model.CharBuffer;
import java.util.regex.PatternSyntaxException;
import org.junit.jupiter.api.Test;

class RegexFinderTest {

    @Test
    void classifiesFindHistoryPopupFromTerminalBackgroundBrightness() {
        assertEquals("jeditermfx-find-history-dark",
                FindComponent.historyPopupStyleClass(new com.techsenger.jeditermfx.core.Color(18, 22, 28)));
        assertEquals("jeditermfx-find-history-light",
                FindComponent.historyPopupStyleClass(new com.techsenger.jeditermfx.core.Color(250, 250, 250)));
    }

    @Test
    void findsRegularExpressionsAndMapsMatchesBackToTerminalCells() {
        RegexFinder finder = new RegexFinder("error-[0-9]+", true);
        feed(finder, 0, "ERROR-12 ok error-7");

        FindResult result = finder.result();

        assertEquals(2, result.getItems().size());
        assertEquals("ERROR-12", result.getItems().get(0).getText());
        assertEquals(0, result.getItems().get(0).getStart().x);
        assertEquals("error-7", result.getItems().get(1).getText());
    }

    @Test
    void preservesLineBoundariesAndHonorsMatchCase() {
        RegexFinder finder = new RegexFinder("alpha\\nBeta", false);
        feed(finder, 0, "alpha");
        feed(finder, 1, "Beta");
        assertEquals(1, finder.result().getItems().size());

        RegexFinder caseSensitive = new RegexFinder("beta", false);
        feed(caseSensitive, 0, "Beta");
        assertEquals(0, caseSensitive.result().getItems().size());
        assertThrows(PatternSyntaxException.class, () -> new RegexFinder("[", false));
    }

    private static void feed(RegexFinder finder, int row, String text) {
        CharBuffer buffer = new CharBuffer(text);
        for (int index = 0; index < buffer.length(); index++) {
            finder.nextChar(0, row, buffer, index);
        }
    }
}
