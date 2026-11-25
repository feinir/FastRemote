// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.transport.ssh;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Conservative best-effort decoder for SSH exec output whose protocol carries no charset. */
final class RemoteCommandOutputDecoder {
    private static final Charset GB18030 = Charset.forName("GB18030");
    private static final Charset BIG5 = Charset.forName("Big5");
    private static final Charset SHIFT_JIS = Charset.forName("Shift_JIS");
    private static final Charset WINDOWS_949 = Charset.forName("x-windows-949");
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private RemoteCommandOutputDecoder() {
    }

    static boolean isStrictUtf8(byte[] bytes, int length, boolean truncated) {
        if (decodeStrict(bytes, 0, length, StandardCharsets.UTF_8) != null) return true;
        if (!truncated) return false;
        for (int removed = 1; removed <= Math.min(3, length); removed++) {
            if (decodeStrict(bytes, 0, length - removed, StandardCharsets.UTF_8) != null) return true;
        }
        return false;
    }

    static String decode(byte[] bytes, int length, boolean truncated) {
        return decode(bytes, length, truncated, null);
    }

    static String decode(byte[] bytes, int length, boolean truncated, Charset preferred) {
        if (length == 0) return "";
        if (startsWith(bytes, length, 0xEF, 0xBB, 0xBF)) {
            String value = decodeStrict(bytes, 3, length - 3, StandardCharsets.UTF_8);
            if (value != null) return value;
        }
        if (startsWith(bytes, length, 0xFF, 0xFE)) {
            String value = decodeStrict(bytes, 2, length - 2, StandardCharsets.UTF_16LE);
            if (value != null) return value;
        }
        if (startsWith(bytes, length, 0xFE, 0xFF)) {
            String value = decodeStrict(bytes, 2, length - 2, StandardCharsets.UTF_16BE);
            if (value != null) return value;
        }

        Charset bomlessUtf16 = probableUtf16(bytes, length);
        if (bomlessUtf16 != null) {
            String value = decodeStrict(bytes, 0, length, bomlessUtf16);
            if (value != null) return value;
        }

        String utf8 = decodeStrict(bytes, 0, length, StandardCharsets.UTF_8);
        if (utf8 != null) return utf8;
        if (truncated) {
            for (int removed = 1; removed <= Math.min(3, length); removed++) {
                utf8 = decodeStrict(bytes, 0, length - removed, StandardCharsets.UTF_8);
                if (utf8 != null) return utf8;
            }
        }

        if (preferred != null) {
            String value = decodeStrict(bytes, 0, length, preferred);
            if (value != null) return value;
        }

        List<Candidate> candidates = new ArrayList<>();
        addCandidate(candidates, bytes, length, SHIFT_JIS, Script.JAPANESE);
        Candidate japanese = candidates.stream().max(Comparator.comparingInt(Candidate::score)).orElse(null);
        if (japanese != null && japanese.score() > 0) return japanese.text();
        candidates.clear();
        addCandidate(candidates, bytes, length, GB18030, Script.CHINESE);
        Candidate simplifiedChinese = candidates.stream().findFirst().orElse(null);
        if (simplifiedChinese != null && simplifiedChinese.score() > 0) return simplifiedChinese.text();
        candidates.clear();
        addCandidate(candidates, bytes, length, BIG5, Script.CHINESE);
        addCandidate(candidates, bytes, length, WINDOWS_949, Script.KOREAN);
        Candidate best = candidates.stream().max(Comparator.comparingInt(Candidate::score)).orElse(null);
        if (best != null && best.score() > 0) return best.text();

        String western = decodeStrict(bytes, 0, length, WINDOWS_1252);
        return western != null ? western : new String(bytes, 0, length, StandardCharsets.ISO_8859_1);
    }

    private static void addCandidate(
            List<Candidate> candidates, byte[] bytes, int length, Charset charset, Script script) {
        String value = decodeStrict(bytes, 0, length, charset);
        if (value == null) return;
        int expected = 0;
        int unexpected = 0;
        int controls = 0;
        for (int index = 0; index < value.length();) {
            int codePoint = value.codePointAt(index);
            index += Character.charCount(codePoint);
            if (isExpected(codePoint, script)) expected++;
            else if (codePoint > 0x7F && Character.isLetter(codePoint)) unexpected++;
            if (Character.isISOControl(codePoint) && codePoint != '\r' && codePoint != '\n' && codePoint != '\t') {
                controls++;
            }
        }
        int minimumEvidence = script == Script.CHINESE ? 1 : 1;
        if (expected < minimumEvidence) return;
        int evidenceWeight = script == Script.CHINESE ? 12 : 20;
        candidates.add(new Candidate(value, expected * evidenceWeight - unexpected * 3 - controls * 100));
    }

    private static boolean isExpected(int codePoint, Script script) {
        Character.UnicodeScript actual = Character.UnicodeScript.of(codePoint);
        return switch (script) {
            case CHINESE -> actual == Character.UnicodeScript.HAN;
            case JAPANESE -> actual == Character.UnicodeScript.HIRAGANA
                    || (actual == Character.UnicodeScript.KATAKANA
                            && (codePoint < 0xFF61 || codePoint > 0xFF9F));
            case KOREAN -> actual == Character.UnicodeScript.HANGUL;
        };
    }

    private static Charset probableUtf16(byte[] bytes, int length) {
        if (length < 4 || (length & 1) != 0) return null;
        int evenZeroes = 0;
        int oddZeroes = 0;
        for (int index = 0; index < length; index += 2) {
            if (bytes[index] == 0) evenZeroes++;
            if (bytes[index + 1] == 0) oddZeroes++;
        }
        int pairs = length / 2;
        if (oddZeroes * 5 >= pairs * 3 && evenZeroes * 5 <= pairs) return StandardCharsets.UTF_16LE;
        if (evenZeroes * 5 >= pairs * 3 && oddZeroes * 5 <= pairs) return StandardCharsets.UTF_16BE;
        return null;
    }

    private static String decodeStrict(byte[] bytes, int offset, int length, Charset charset) {
        try {
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, length))
                    .toString();
        } catch (CharacterCodingException invalid) {
            return null;
        }
    }

    private static boolean startsWith(byte[] bytes, int length, int... prefix) {
        if (length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) {
            if (Byte.toUnsignedInt(bytes[index]) != prefix[index]) return false;
        }
        return true;
    }

    private enum Script { CHINESE, JAPANESE, KOREAN }

    private record Candidate(String text, int score) { }
}
