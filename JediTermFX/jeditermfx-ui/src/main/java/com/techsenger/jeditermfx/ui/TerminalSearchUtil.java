package com.techsenger.jeditermfx.ui;

import com.techsenger.jeditermfx.core.StyledTextConsumer;
import com.techsenger.jeditermfx.core.TextStyle;
import com.techsenger.jeditermfx.core.model.CharBuffer;
import com.techsenger.jeditermfx.core.model.SubCharBuffer;
import com.techsenger.jeditermfx.core.model.TerminalTextBuffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class TerminalSearchUtil {

    static @Nullable FindResult searchInTerminalTextBuffer(@NotNull TerminalTextBuffer textBuffer,
            @NotNull String pattern, boolean ignoreCase, boolean regex) {
        if (pattern.isEmpty()) {
            return null;
        }
        final SubstringFinder finder = regex ? null : new SubstringFinder(pattern, ignoreCase);
        final RegexFinder regexFinder = regex ? new RegexFinder(pattern, ignoreCase) : null;
        textBuffer.processHistoryAndScreenLines(-textBuffer.getHistoryLinesCount(), -1, new StyledTextConsumer() {

            @Override
            public void consume(int x, int y, @NotNull TextStyle style, @NotNull CharBuffer characters, int startRow) {
                int offset = 0;
                int length = characters.length();
                if (characters instanceof SubCharBuffer) {
                    SubCharBuffer subCharBuffer = (SubCharBuffer) characters;
                    characters = subCharBuffer.getParent();
                    offset = subCharBuffer.getOffset();
                }
                for (int i = offset; i < offset + length; i++) {
                    if (regexFinder == null) finder.nextChar(x, y - startRow, characters, i);
                    else regexFinder.nextChar(x, y - startRow, characters, i);
                }
            }

            @Override
            public void consumeNul(int x, int y, int nulIndex, @NotNull TextStyle style, @NotNull CharBuffer characters,
                                   int startRow) {
                if (regexFinder != null) regexFinder.beginLine(y - startRow);
            }

            @Override
            public void consumeQueue(int x, int y, int nulIndex, int startRow) {

            }
        });
        return regexFinder == null ? finder.getResult() : regexFinder.result();
    }
}
