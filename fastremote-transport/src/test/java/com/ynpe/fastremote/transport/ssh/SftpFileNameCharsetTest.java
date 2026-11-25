// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.transport.ssh;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class SftpFileNameCharsetTest {
    private static final Charset CHARSET = SftpFileNameCharset.INSTANCE;

    @Test
    void validUtf8NamesDecodeUnchanged() {
        String name = "表情 emoji 😀 ファイル.png";
        byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
        assertEquals(name, new String(bytes, CHARSET));
        assertArrayEquals(bytes, name.getBytes(CHARSET));
    }

    @Test
    void gbkNameFromUnzipRoundTripsExactly() {
        // Raw bytes listed on a UTF-8 server after unzipping an archive with GBK names.
        byte[] raw = HexFormat.of().parseHex("5bb5e8a6b5efa65d2e706e67");
        String decoded = new String(raw, CHARSET);
        assertFalse(decoded.contains("�"));
        assertTrue(decoded.startsWith("[") && decoded.endsWith("].png"));
        assertArrayEquals(raw, decoded.getBytes(CHARSET));
        // Joined into a path the escaped bytes stay intact.
        String path = "/root/apk/x/assets/emoji/" + decoded;
        byte[] expected = ("/root/apk/x/assets/emoji/").getBytes(StandardCharsets.UTF_8);
        byte[] encoded = path.getBytes(CHARSET);
        assertArrayEquals(raw, java.util.Arrays.copyOfRange(encoded, expected.length, encoded.length));
    }

    @Test
    void shiftJisAndMixedInvalidSequencesRoundTrip() {
        byte[] shiftJis = "テスト中文.txt".getBytes(Charset.forName("Shift_JIS"));
        assertArrayEquals(shiftJis, new String(shiftJis, CHARSET).getBytes(CHARSET));
        byte[][] samples = {
                {(byte) 0xC0, (byte) 0xAF},                         // overlong '/'
                {(byte) 0xED, (byte) 0xA0, (byte) 0x80},            // encoded surrogate
                {(byte) 0xF4, (byte) 0x90, (byte) 0x80, (byte) 0x80}, // above U+10FFFF
                {'a', (byte) 0xE6, (byte) 0x96},                    // truncated at end
                {(byte) 0xE6, (byte) 0x96, 'b', (byte) 0xFF, (byte) 0x80},
        };
        for (byte[] sample : samples) {
            String decoded = new String(sample, CHARSET);
            assertFalse(decoded.contains("�"));
            assertArrayEquals(sample, decoded.getBytes(CHARSET));
        }
    }

    @Test
    void validCharacterInsideEscapeRangeIsEscapedSoBytesStayDistinct() {
        byte[] privateUse = "".getBytes(StandardCharsets.UTF_8);
        byte[] rawByte = {(byte) 0xA5};
        String fromPrivateUse = new String(privateUse, CHARSET);
        String fromRaw = new String(rawByte, CHARSET);
        assertEquals(3, fromPrivateUse.length());
        assertFalse(fromPrivateUse.equals(fromRaw));
        assertArrayEquals(privateUse, fromPrivateUse.getBytes(CHARSET));
        assertArrayEquals(rawByte, fromRaw.getBytes(CHARSET));
    }

    @Test
    void unpairedSurrogateIsReplacedInsteadOfFailing() {
        assertArrayEquals("a?b".getBytes(StandardCharsets.US_ASCII), "a\uD800b".getBytes(CHARSET));
    }
}
