// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/** Fixed, non-editable encoding choices offered by the embedded editor. */
enum TextEncoding {
    UTF8("UTF-8", StandardCharsets.UTF_8, new byte[0]),
    UTF8_BOM("UTF-8 BOM", StandardCharsets.UTF_8, new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf}),
    UTF16_LE("UTF-16 LE", StandardCharsets.UTF_16LE, new byte[] {(byte) 0xff, (byte) 0xfe}),
    UTF16_BE("UTF-16 BE", StandardCharsets.UTF_16BE, new byte[] {(byte) 0xfe, (byte) 0xff}),
    GBK("GBK", Charset.forName("GBK"), new byte[0]),
    GB18030("GB18030", Charset.forName("GB18030"), new byte[0]),
    BIG5("Big5", Charset.forName("Big5"), new byte[0]),
    SHIFT_JIS("Shift_JIS", Charset.forName("Shift_JIS"), new byte[0]),
    EUC_JP("EUC-JP", Charset.forName("EUC-JP"), new byte[0]),
    EUC_KR("EUC-KR", Charset.forName("EUC-KR"), new byte[0]),
    WINDOWS_1252("Windows-1252", Charset.forName("windows-1252"), new byte[0]),
    ISO_8859_1("ISO-8859-1", StandardCharsets.ISO_8859_1, new byte[0]);

    private final String label;
    private final Charset charset;
    private final byte[] bom;

    TextEncoding(String label, Charset charset, byte[] bom) {
        this.label = label;
        this.charset = charset;
        this.bom = bom;
    }

    static TextEncoding detect(byte[] bytes) {
        if (startsWith(bytes, UTF8_BOM.bom)) return UTF8_BOM;
        if (startsWith(bytes, UTF16_LE.bom)) return UTF16_LE;
        if (startsWith(bytes, UTF16_BE.bom)) return UTF16_BE;
        return UTF8;
    }

    String decode(byte[] bytes) {
        int offset = startsWith(bytes, bom) ? bom.length : 0;
        return new String(bytes, offset, bytes.length - offset, charset);
    }

    Optional<String> decodeChecked(byte[] bytes, String referenceText) {
        if (this == UTF16_LE && !startsWith(bytes, bom) && !looksLikeBomlessUtf16(bytes, true)) {
            return Optional.empty();
        }
        if (this == UTF16_BE && !startsWith(bytes, bom) && !looksLikeBomlessUtf16(bytes, false)) {
            return Optional.empty();
        }
        int offset = startsWith(bytes, bom) ? bom.length : 0;
        try {
            String decoded = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString();
            return looksLikeText(decoded, referenceText) ? Optional.of(decoded) : Optional.empty();
        } catch (java.nio.charset.CharacterCodingException invalid) {
            return Optional.empty();
        }
    }

    private static boolean looksLikeText(String decoded, String referenceText) {
        if (decoded.indexOf('\u0000') >= 0) return false;
        int controls = 0;
        int lineBreaks = 0;
        int longestLine = 0;
        int lineLength = 0;
        for (int offset = 0; offset < decoded.length(); ) {
            int codePoint = decoded.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '\n' || codePoint == '\r') {
                lineBreaks++;
                longestLine = Math.max(longestLine, lineLength);
                lineLength = 0;
            } else {
                lineLength++;
                if (Character.isISOControl(codePoint) && codePoint != '\t' && codePoint != '\f') controls++;
            }
        }
        longestLine = Math.max(longestLine, lineLength);
        if (controls > Math.max(2, decoded.codePointCount(0, decoded.length()) / 200)) return false;

        String reference = referenceText == null ? "" : referenceText;
        int referenceBreaks = countLineBreaks(reference);
        if (referenceBreaks >= 4 && lineBreaks * 8 < referenceBreaks) return false;
        int referenceLongest = longestLine(reference);
        return longestLine <= Math.max(16_384L, referenceLongest * 8L);
    }

    private static int countLineBreaks(String value) {
        int count = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\n' || character == '\r') count++;
        }
        return count;
    }

    private static int longestLine(String value) {
        int longest = 0;
        int current = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\n' || character == '\r') {
                longest = Math.max(longest, current);
                current = 0;
            } else current++;
        }
        return Math.max(longest, current);
    }

    private static boolean looksLikeBomlessUtf16(byte[] bytes, boolean littleEndian) {
        if (bytes.length < 4 || (bytes.length & 1) != 0) return false;
        int expectedZeroLane = littleEndian ? 1 : 0;
        int expectedZeros = 0;
        int otherZeros = 0;
        for (int index = 0; index < bytes.length; index++) {
            if (bytes[index] != 0) continue;
            if ((index & 1) == expectedZeroLane) expectedZeros++;
            else otherZeros++;
        }
        int pairs = bytes.length / 2;
        return expectedZeros >= Math.max(2, pairs / 10) && expectedZeros > otherZeros * 3;
    }

    byte[] encode(String text) {
        byte[] body = text.getBytes(charset);
        if (bom.length == 0) return body;
        ByteArrayOutputStream output = new ByteArrayOutputStream(bom.length + body.length);
        output.writeBytes(bom);
        output.writeBytes(body);
        return output.toByteArray();
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return prefix.length > 0 && bytes.length >= prefix.length
                && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
    }

    @Override public String toString() { return label; }
}
