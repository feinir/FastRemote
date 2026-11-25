// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.automation;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class RemoteTextWrite {
    public static final int MAX_BYTES = 1024 * 1024;

    private RemoteTextWrite() { }

    public static byte[] encode(String content) {
        Objects.requireNonNull(content, "content");
        if (content.length() > MAX_BYTES) throw new IllegalArgumentException("Remote text exceeds 1 MiB of UTF-8");
        if (!StandardCharsets.UTF_8.newEncoder().canEncode(content)) {
            throw new IllegalArgumentException("Remote text contains invalid Unicode");
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) {
            java.util.Arrays.fill(bytes, (byte) 0);
            throw new IllegalArgumentException("Remote text exceeds 1 MiB of UTF-8");
        }
        return bytes;
    }
}
