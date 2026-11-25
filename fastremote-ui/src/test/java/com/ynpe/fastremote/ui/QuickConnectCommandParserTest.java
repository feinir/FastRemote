// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class QuickConnectCommandParserTest {
    @Test
    void parsesSshpassPasswordSshPortUserHostAndQuotedIdentity() {
        var parsed = QuickConnectCommandParser.parse(
                "sshpass -p 'temporary password' ssh -p 22022 -i \"/Keys/work key\" deploy@example.com");

        assertEquals("ssh", parsed.protocol());
        assertEquals("example.com", parsed.host());
        assertEquals("deploy", parsed.username().orElseThrow());
        assertEquals(22022, parsed.port().orElseThrow());
        assertEquals("temporary password", parsed.password().orElseThrow());
        assertEquals("/Keys/work key", parsed.privateKey().orElseThrow());
    }

    @Test
    void parsesSftpCapitalPortAndUri() {
        var parsed = QuickConnectCommandParser.parse("sftp -P 2222 sftp://backup@files.example.com/inbox");

        assertEquals("files.example.com", parsed.host());
        assertEquals("backup", parsed.username().orElseThrow());
        assertEquals(2222, parsed.port().orElseThrow());
    }

    @Test
    void tokenizesQuotedAndEscapedValuesWithoutRunningThem() {
        assertEquals(List.of("ssh", "-i", "/tmp/my key", "root@host"),
                QuickConnectCommandParser.tokenize("ssh -i /tmp/my\\ key root@host"));
        assertThrows(IllegalArgumentException.class,
                () -> QuickConnectCommandParser.parse("echo unsafe"));
    }

    @Test
    void rejectsInlineProxyJumpInsteadOfSilentlyConnectingDirectly() {
        assertThrows(IllegalArgumentException.class,
                () -> QuickConnectCommandParser.parse("ssh -J bastion app@internal"));
        assertThrows(IllegalArgumentException.class,
                () -> QuickConnectCommandParser.parse("ssh -o ProxyJump=bastion app@internal"));
    }

    @Test
    void rejectsUnknownOptionsInsteadOfSilentlyChangingTheirMeaning() {
        assertThrows(IllegalArgumentException.class,
                () -> QuickConnectCommandParser.parse("ssh -Z value user@gateway"));
        assertThrows(IllegalArgumentException.class,
                () -> QuickConnectCommandParser.parse("ssh -o Unsupported=value user@gateway"));
    }
}
