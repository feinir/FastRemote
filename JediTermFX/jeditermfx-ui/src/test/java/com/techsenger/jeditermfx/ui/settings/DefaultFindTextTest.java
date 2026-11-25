package com.techsenger.jeditermfx.ui.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultFindTextTest {

    @Test
    void exposesReplaceableFindComponentText() {
        DefaultSettingsProvider settings = new DefaultSettingsProvider();

        assertEquals("Find", settings.getFindFieldPromptText());
        assertEquals("Match case", settings.getFindMatchCaseText());
        assertEquals("Regular expression", settings.getFindRegexText());
        assertEquals("Previous", settings.getFindPreviousText());
        assertEquals("Next", settings.getFindNextText());
        assertEquals("Invalid regular expression", settings.getFindInvalidRegexText());
        assertEquals("No results", settings.getFindResultText(0, 0));
        assertEquals("2 of 7", settings.getFindResultText(2, 7));
    }
}
