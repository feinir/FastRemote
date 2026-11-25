package com.techsenger.jeditermfx.ui;

import com.techsenger.jeditermfx.core.model.CharBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Collects bounded terminal text with source coordinates and maps regex matches back to cells. */
final class RegexFinder {
    private final Pattern pattern;
    private final StringBuilder text = new StringBuilder();
    private final List<Position> positions = new ArrayList<>();
    private int currentRow = Integer.MIN_VALUE;

    RegexFinder(String expression, boolean ignoreCase) {
        int flags = ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
        pattern = Pattern.compile(expression, flags);
    }

    void beginLine(int row) {
        if (currentRow == Integer.MIN_VALUE) {
            currentRow = row;
            return;
        }
        while (currentRow < row) {
            text.append('\n');
            positions.add(null);
            currentRow++;
        }
    }

    void nextChar(int x, int y, CharBuffer characters, int index) {
        beginLine(y);
        text.append(characters.charAt(index));
        positions.add(new Position(x, y, characters, index));
    }

    FindResult result() {
        SubstringFinder.FindResultImpl result = new SubstringFinder.FindResultImpl();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) addMatch(result, matcher.start(), matcher.end());
        return result;
    }

    private void addMatch(SubstringFinder.FindResultImpl result, int start, int end) {
        int first = start;
        while (first < end && positions.get(first) == null) first++;
        int last = end - 1;
        while (last >= first && positions.get(last) == null) last--;
        if (first > last) return;

        Position firstPosition = positions.get(first);
        Position lastPosition = positions.get(last);
        ArrayList<SubstringFinder.TextToken> tokens = new ArrayList<>();
        Position previous = null;
        for (int index = first; index <= last; index++) {
            Position position = positions.get(index);
            if (position == null) continue;
            if (previous == null || previous.characters != position.characters
                    || previous.y != position.y || position.index != previous.index + 1) {
                tokens.add(new SubstringFinder.TextToken(position.x, position.y, position.characters));
            }
            previous = position;
        }
        result.patternMatched(tokens, firstPosition.index, lastPosition.index);
    }

    private record Position(int x, int y, CharBuffer characters, int index) {}
}
