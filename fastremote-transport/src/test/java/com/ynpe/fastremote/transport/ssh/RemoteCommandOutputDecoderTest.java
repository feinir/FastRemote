// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.transport.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RemoteCommandOutputDecoderTest {
    @Test
    void keepsValidUtf8OutputUnchanged() {
        assertDecodes("Microsoft Windows 版本 10.0\r\n", StandardCharsets.UTF_8);
    }

    @Test
    void decodesMainWindowsCjkCodePagesWhenUtf8IsInvalid() {
        assertDecodesWithDetectedCodePage("Microsoft Windows 版本 10.0\r\n", Charset.forName("GB18030"));
        assertDecodesWithDetectedCodePage("Microsoft Windows 版本 10.0\r\n", Charset.forName("Big5"));
        assertDecodesWithDetectedCodePage("Microsoft Windows バージョン 10.0\r\n", Charset.forName("Shift_JIS"));
        assertDecodesWithDetectedCodePage("Microsoft Windows 버전 10.0\r\n", Charset.forName("x-windows-949"));
    }

    @Test
    void decodesLegacyPosixLocaleCharsetsWhenTheyAreDetected() {
        assertDecodesWithDetectedCodePage("Résumé coût €\n", Charset.forName("ISO-8859-15"));
        assertDecodesWithDetectedCodePage("Привет, мир\n", Charset.forName("KOI8-R"));
        assertDecodesWithDetectedCodePage("システム情報\n", Charset.forName("EUC-JP"));
    }

    @Test
    void decodesBomlessUtf16WindowsOutput() {
        assertDecodes("FRP1_OS=Windows\r\n", StandardCharsets.UTF_16LE);
        assertDecodes("FRP1_OS=Windows\r\n", StandardCharsets.UTF_16BE);
    }

    @Test
    void keepsAValidUtf8PrefixWhenTheOutputBudgetCutsAMultibyteCharacter() {
        byte[] bytes = "状态".getBytes(StandardCharsets.UTF_8);
        assertEquals("状", RemoteCommandOutputDecoder.decode(bytes, bytes.length - 1, true));
    }

    private static void assertDecodes(String expected, Charset charset) {
        byte[] bytes = expected.getBytes(charset);
        assertEquals(expected, RemoteCommandOutputDecoder.decode(bytes, bytes.length, false));
    }

    private static void assertDecodesWithDetectedCodePage(String expected, Charset charset) {
        byte[] bytes = expected.getBytes(charset);
        assertEquals(expected, RemoteCommandOutputDecoder.decode(bytes, bytes.length, false, charset));
    }
}
