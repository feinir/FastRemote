// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TextEncodingTest {
    @Test
    void detectsAndPreservesUtf8Bom() {
        byte[] encoded = TextEncoding.UTF8_BOM.encode("中文 日本語 한국어");

        assertEquals(TextEncoding.UTF8_BOM, TextEncoding.detect(encoded));
        assertEquals("中文 日本語 한국어", TextEncoding.UTF8_BOM.decode(encoded));
        assertTrue(encoded[0] == (byte) 0xef && encoded[1] == (byte) 0xbb && encoded[2] == (byte) 0xbf);
    }

    @Test
    void supportsCommonChineseJapaneseAndKoreanEncodings() {
        assertRoundTrip(TextEncoding.GB18030, "中文扩展");
        assertRoundTrip(TextEncoding.BIG5, "繁體中文");
        assertRoundTrip(TextEncoding.SHIFT_JIS, "日本語");
        assertRoundTrip(TextEncoding.EUC_KR, "한국어");
    }

    @Test
    void plainUtf8DoesNotAcquireABom() {
        assertArrayEquals("text".getBytes(StandardCharsets.UTF_8), TextEncoding.UTF8.encode("text"));
    }

    @Test
    void rejectsUtf8BytesMisreadAsUtf16BeforeTheyBecomeOneHugeLine() {
        byte[] utf8 = "echo first\necho second\n".repeat(20_000).getBytes(StandardCharsets.UTF_8);

        assertFalse(TextEncoding.UTF16_LE.decodeChecked(utf8, new String(utf8, StandardCharsets.UTF_8)).isPresent());
        assertFalse(TextEncoding.UTF16_BE.decodeChecked(utf8, new String(utf8, StandardCharsets.UTF_8)).isPresent());
    }

    @Test
    void acceptsStrictTextAndBomMarkedUtf16() {
        String text = "中文\n日本語\n한국어";

        assertEquals(text, TextEncoding.UTF8.decodeChecked(TextEncoding.UTF8.encode(text), text).orElseThrow());
        assertEquals(text, TextEncoding.UTF16_LE.decodeChecked(TextEncoding.UTF16_LE.encode(text), text).orElseThrow());
        assertEquals(text, TextEncoding.UTF16_BE.decodeChecked(TextEncoding.UTF16_BE.encode(text), text).orElseThrow());
    }

    @Test
    void acceptsBomlessUtf16WhenItsByteLanesAreUnambiguous() {
        String text = "server.name=test\nserver.port=22\n";
        byte[] littleEndian = text.getBytes(StandardCharsets.UTF_16LE);
        byte[] bigEndian = text.getBytes(StandardCharsets.UTF_16BE);

        assertEquals(text, TextEncoding.UTF16_LE.decodeChecked(littleEndian, text).orElseThrow());
        assertEquals(text, TextEncoding.UTF16_BE.decodeChecked(bigEndian, text).orElseThrow());
    }

    private static void assertRoundTrip(TextEncoding encoding, String value) {
        assertEquals(value, encoding.decode(encoding.encode(value)));
    }
}
