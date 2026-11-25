// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.terminal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TerminalInputGatewayTest {
    @Test
    void disconnectedTerminalBlocksWritesUntilInputIsEnabledAgain() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        TerminalInputGateway gateway = new TerminalInputGateway(output);

        gateway.write("before");
        gateway.setInputEnabled(false);
        gateway.write("blocked");
        gateway.setInputEnabled(true);
        gateway.write("after");

        assertArrayEquals("beforeafter".getBytes(StandardCharsets.UTF_8), output.toByteArray());
    }
}
