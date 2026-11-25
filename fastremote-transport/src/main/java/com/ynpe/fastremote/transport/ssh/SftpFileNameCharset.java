// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.transport.ssh;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;

/**
 * UTF-8 for SFTP names that keeps every byte of names which are not valid UTF-8.
 *
 * <p>POSIX servers return raw file name bytes. Archives created with GBK or Shift_JIS names
 * unpack to bytes that UTF-8 decoding replaces with U+FFFD, so the decoded name can never be
 * sent back to delete, rename or open the file. Each byte of an invalid sequence is decoded to
 * U+F780..U+F7FF (private use, 0xF700 + byte) and encoded back to that byte. Valid UTF-8 names
 * decode unchanged; a valid character that falls inside the escape range is itself escaped per
 * byte, so decoding followed by encoding reproduces the original bytes exactly.
 *
 * <p>MINA decodes and encodes each name as a whole buffer, so a sequence truncated at the end
 * of the input is treated as invalid rather than awaiting more bytes.
 */
final class SftpFileNameCharset extends Charset {
    static final SftpFileNameCharset INSTANCE = new SftpFileNameCharset();
    private static final int ESCAPE_BASE = 0xF700;
    private static final int ESCAPE_FIRST = ESCAPE_BASE + 0x80;
    private static final int ESCAPE_LAST = ESCAPE_BASE + 0xFF;

    private SftpFileNameCharset() { super("X-FastRemote-UTF-8-Raw-Names", new String[0]); }

    @Override public boolean contains(Charset charset) {
        return charset instanceof SftpFileNameCharset || StandardCharsets.UTF_8.contains(charset);
    }

    @Override public CharsetDecoder newDecoder() { return new Decoder(this); }

    @Override public CharsetEncoder newEncoder() { return new Encoder(this); }

    static boolean escaped(char value) { return value >= ESCAPE_FIRST && value <= ESCAPE_LAST; }

    private static final class Decoder extends CharsetDecoder {
        private Decoder(Charset charset) { super(charset, 1.0f, 1.0f); }

        @Override protected CoderResult decodeLoop(ByteBuffer in, CharBuffer out) {
            while (in.hasRemaining()) {
                int start = in.position();
                int length = validSequenceLength(in, start);
                int codePoint = length == 0 ? -1 : codePoint(in, start, length);
                if (codePoint < 0 || (codePoint >= ESCAPE_FIRST && codePoint <= ESCAPE_LAST)) {
                    int escapedBytes = codePoint < 0 ? 1 : length;
                    if (out.remaining() < escapedBytes) return CoderResult.OVERFLOW;
                    for (int index = 0; index < escapedBytes; index++) {
                        out.put((char) (ESCAPE_BASE + (in.get() & 0xFF)));
                    }
                    continue;
                }
                int chars = Character.charCount(codePoint);
                if (out.remaining() < chars) return CoderResult.OVERFLOW;
                in.position(start + length);
                if (chars == 1) out.put((char) codePoint);
                else out.put(Character.highSurrogate(codePoint)).put(Character.lowSurrogate(codePoint));
            }
            return CoderResult.UNDERFLOW;
        }

        /** Returns the length of a well-formed UTF-8 sequence at {@code start}, or 0. */
        private static int validSequenceLength(ByteBuffer in, int start) {
            int lead = in.get(start) & 0xFF;
            if (lead < 0x80) return 1;
            int length;
            int minimumSecond = 0x80;
            int maximumSecond = 0xBF;
            if (lead >= 0xC2 && lead <= 0xDF) length = 2;
            else if (lead >= 0xE0 && lead <= 0xEF) {
                length = 3;
                if (lead == 0xE0) minimumSecond = 0xA0; // overlong
                if (lead == 0xED) maximumSecond = 0x9F; // UTF-16 surrogates
            } else if (lead >= 0xF0 && lead <= 0xF4) {
                length = 4;
                if (lead == 0xF0) minimumSecond = 0x90; // overlong
                if (lead == 0xF4) maximumSecond = 0x8F; // above U+10FFFF
            } else return 0;
            if (in.limit() - start < length) return 0;
            int second = in.get(start + 1) & 0xFF;
            if (second < minimumSecond || second > maximumSecond) return 0;
            for (int index = 2; index < length; index++) {
                int next = in.get(start + index) & 0xFF;
                if (next < 0x80 || next > 0xBF) return 0;
            }
            return length;
        }

        private static int codePoint(ByteBuffer in, int start, int length) {
            int lead = in.get(start) & 0xFF;
            if (length == 1) return lead;
            int value = lead & (0xFF >> (length + 1));
            for (int index = 1; index < length; index++) value = (value << 6) | (in.get(start + index) & 0x3F);
            return value;
        }
    }

    private static final class Encoder extends CharsetEncoder {
        private Encoder(Charset charset) { super(charset, 1.1f, 3.0f); }

        @Override protected CoderResult encodeLoop(CharBuffer in, ByteBuffer out) {
            while (in.hasRemaining()) {
                int start = in.position();
                char value = in.get(start);
                int codePoint;
                int chars = 1;
                if (escaped(value)) {
                    if (!out.hasRemaining()) return CoderResult.OVERFLOW;
                    out.put((byte) (value - ESCAPE_BASE));
                    in.position(start + 1);
                    continue;
                }
                if (Character.isHighSurrogate(value)) {
                    if (in.limit() - start < 2 || !Character.isLowSurrogate(in.get(start + 1))) {
                        return CoderResult.malformedForLength(1);
                    }
                    codePoint = Character.toCodePoint(value, in.get(start + 1));
                    chars = 2;
                } else if (Character.isLowSurrogate(value)) {
                    return CoderResult.malformedForLength(1);
                } else codePoint = value;
                int bytes = codePoint < 0x80 ? 1 : codePoint < 0x800 ? 2 : codePoint < 0x10000 ? 3 : 4;
                if (out.remaining() < bytes) return CoderResult.OVERFLOW;
                switch (bytes) {
                    case 1 -> out.put((byte) codePoint);
                    case 2 -> out.put((byte) (0xC0 | (codePoint >> 6)))
                            .put((byte) (0x80 | (codePoint & 0x3F)));
                    case 3 -> out.put((byte) (0xE0 | (codePoint >> 12)))
                            .put((byte) (0x80 | ((codePoint >> 6) & 0x3F)))
                            .put((byte) (0x80 | (codePoint & 0x3F)));
                    default -> out.put((byte) (0xF0 | (codePoint >> 18)))
                            .put((byte) (0x80 | ((codePoint >> 12) & 0x3F)))
                            .put((byte) (0x80 | ((codePoint >> 6) & 0x3F)))
                            .put((byte) (0x80 | (codePoint & 0x3F)));
                }
                in.position(start + chars);
            }
            return CoderResult.UNDERFLOW;
        }
    }
}
