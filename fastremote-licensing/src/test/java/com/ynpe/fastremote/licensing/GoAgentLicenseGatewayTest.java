// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.licensing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GoAgentLicenseGatewayTest {
    private final com.fasterxml.jackson.databind.ObjectMapper mapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

    @Test
    void rejectsPackagedAgentWhoseVersionDoesNotMatchTheApplication() throws Exception {
        var matching = mapper.readTree("""
                {"ok":true,"result":{"protocol_version":1,"agent_version":"1.1.2"}}
                """);

        assertTrue(GoAgentLicenseGateway.validInitializationResponse(matching, "1.1.2"));
        assertFalse(GoAgentLicenseGateway.validInitializationResponse(matching, "1.1.1"));
        assertFalse(GoAgentLicenseGateway.validInitializationResponse(
                mapper.readTree("{\"ok\":true,\"result\":{\"protocol_version\":1}}"), "1.1.2"));
    }

    @Test
    void packagedAgentDoesNotInheritAConsoleHandleFromGuiLauncher() {
        var builder = GoAgentLicenseGateway.agentProcessBuilder(java.util.List.of("agent", "--state-dir", "state"));

        assertEquals(ProcessBuilder.Redirect.DISCARD, builder.redirectError());
    }

    @Test
    void productAgentLaunchDoesNotReceiveTheLicenseServerAsAnArgument() {
        var command = GoAgentLicenseGateway.agentCommand(Path.of("agent"), Path.of("state"), null);

        assertEquals(java.util.List.of("agent", "--state-dir", "state"), command);
        assertFalse(command.contains("--server-url"));

        var development = GoAgentLicenseGateway.agentCommand(
                Path.of("agent"), Path.of("state"), URI.create("http://127.0.0.1:8090"));
        assertEquals(java.util.List.of("agent", "--state-dir", "state", "--server-url", "http://127.0.0.1:8090"),
                development);
    }

    @Test
    void readsCompleteFrame() throws Exception {
        byte[] payload = "hello".getBytes();
        byte[] framed = ByteBuffer.allocate(Integer.BYTES + payload.length)
                .putInt(payload.length).put(payload).array();

        assertArrayEquals(payload, GoAgentLicenseGateway.readFrame(
                new DataInputStream(new ByteArrayInputStream(framed))));
    }

    @Test
    void rejectsTruncatedFrame() {
        byte[] framed = ByteBuffer.allocate(Integer.BYTES + 2).putInt(5).put(new byte[] {1, 2}).array();

        assertThrows(IOException.class, () -> GoAgentLicenseGateway.readFrame(
                new DataInputStream(new ByteArrayInputStream(framed))));
    }

    @Test
    void rejectsOversizedFrameBeforeAllocation() {
        byte[] framed = ByteBuffer.allocate(Integer.BYTES).putInt(64 * 1024 + 1).array();

        assertThrows(IOException.class, () -> GoAgentLicenseGateway.readFrame(
                new DataInputStream(new ByteArrayInputStream(framed))));
    }
}
