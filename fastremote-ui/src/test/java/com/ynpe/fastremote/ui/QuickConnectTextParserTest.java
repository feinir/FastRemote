// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QuickConnectTextParserTest {
    private static final String PASSWORD = "kdilslldf**XCF";

    @Test
    void infersSlashSeparatedUserAndPasswordWithDefaults() {
        var parsed = QuickConnectTextParser.parse("""
                192.168.11.20
                root / kdilslldf**XCF
                """);

        assertConnection(parsed, "192.168.11.20", 22, "root", PASSWORD);
    }

    @Test
    void infersPortAndSpaceSeparatedCredentials() {
        var parsed = QuickConnectTextParser.parse("""
                192.168.11.33 22222
                root kdilslldf**XCF
                """);

        assertConnection(parsed, "192.168.11.33", 22222, "root", PASSWORD);
    }

    @Test
    void infersFieldsSeparatedByNewlines() {
        var parsed = QuickConnectTextParser.parse("""
                192.168.11.40
                2222
                root
                kdilslldf**XCF
                """);

        assertConnection(parsed, "192.168.11.40", 2222, "root", PASSWORD);
    }

    @Test
    void defaultsMissingUsernameAndPortButStillInfersPassword() {
        var parsed = QuickConnectTextParser.parse("""
                192.168.11.40
                kdilslldf**XCF
                """);

        assertConnection(parsed, "192.168.11.40", 22, "root", PASSWORD);
    }

    @Test
    void understandsCredentialLabelsAndRepeatedPassLabel() {
        assertConnection(QuickConnectTextParser.parse(
                "server.example.com 用户名: adm 密码: secret"),
                "server.example.com", 22, "adm", "secret");
        assertConnection(QuickConnectTextParser.parse(
                "server.example.com 用户名 adm 密码 secret"),
                "server.example.com", 22, "adm", "secret");
        assertConnection(QuickConnectTextParser.parse(
                "server.example.com user adm pass pass"),
                "server.example.com", 22, "adm", "pass");
        assertConnection(QuickConnectTextParser.parse(
                "server.example.com adm/secret"),
                "server.example.com", 22, "adm", "secret");
    }

    @Test
    void understandsCompactAndSpacedUsernamePasswordColons() {
        assertConnection(QuickConnectTextParser.parse("""
                192.168.11.40:2222
                admin:password123
                """), "192.168.11.40", 2222, "admin", "password123");
        assertConnection(QuickConnectTextParser.parse("""
                192.168.11.40:2222
                admin : password123
                """), "192.168.11.40", 2222, "admin", "password123");
    }

    @Test
    void understandsExpandedUsernameAndPasswordLabels() {
        for (String label : java.util.List.of(
                "用户", "用户名", "user", "USER", "username", "USERNAME", "u", "U",
                "login", "LOGIN", "sshuser", "SSHUSER", "ssh user", "SSH USER", "ssh_user", "SSH_USER")) {
            assertConnection(QuickConnectTextParser.parse(
                    "server.example.com\n" + label + " admin\npass password123"),
                    "server.example.com", 22, "admin", "password123");
        }
        for (String label : java.util.List.of(
                "密码", "pass", "PASS", "password", "Password", "passwd", "pwd", "p", "P",
                "sshpass", "SSHPASS", "ssh pass", "SSH PASS", "ssh_pass", "SSH_PASS")) {
            assertConnection(QuickConnectTextParser.parse(
                    "server.example.com\nuser admin\n" + label + " password123"),
                    "server.example.com", 22, "admin", "password123");
        }
    }

    @Test
    void parsesLoginAndPasswordWithoutAHostAsLabeledCredentials() {
        var login = QuickConnectTextParser.parseLabeledCredentialsOnly(
                "login: smileserv   password: Example!Clipboard.SM##").orElseThrow();
        assertEquals("smileserv", login.username());
        assertEquals("Example!Clipboard.SM##", login.password());

        var user = QuickConnectTextParser.parseLabeledCredentialsOnly(
                "user: root pass: XXXXxxxxx").orElseThrow();
        assertEquals("root", user.username());
        assertEquals("XXXXxxxxx", user.password());
    }

    @Test
    void doesNotInferAnOrdinaryTextPasswordThatStartsWithAColon() {
        var compact = QuickConnectTextParser.parse("server.example.com admin::password123");
        var labeled = QuickConnectTextParser.parse("server.example.com user admin pass ::password123");

        assertTrue(compact.password().isEmpty());
        assertTrue(labeled.password().isEmpty());
    }

    @Test
    void acceptsIpv6AndColonPrefixedPortsWhenUnambiguous() {
        assertConnection(QuickConnectTextParser.parse(
                "2001:db8::20 22022 admin:secret"),
                "2001:db8::20", 22022, "admin", "secret");
        assertConnection(QuickConnectTextParser.parse(
                "files.example.com:22022 root secret"),
                "files.example.com", 22022, "root", "secret");
        assertConnection(QuickConnectTextParser.parse(
                "[2001:db8::20]:2222 admin secret"),
                "2001:db8::20", 2222, "admin", "secret");
    }

    @Test
    void parsesColonPortAfterIpv4AndAFiveSegmentDomain() {
        assertConnection(QuickConnectTextParser.parse("""
                192.168.11.40:2222
                admin kdilslldf**XCF
                """), "192.168.11.40", 2222, "admin", PASSWORD);
        assertConnection(QuickConnectTextParser.parse("""
                web-17.sub.xxx.fastremote.com:2222
                admin kdilslldf**XCF
                """), "web-17.sub.xxx.fastremote.com", 2222, "admin", PASSWORD);
    }

    @Test
    void rejectsDomainsWithMoreThanFiveSegmentsWithoutMatchingTheirSuffix() {
        assertThrows(IllegalArgumentException.class, () -> QuickConnectTextParser.parse(
                "one.two.three.four.five.fastremote.com:2222 admin secret"));
    }

    @Test
    void onlyExtractsAQualifiedDomainAtWhitespaceOrHostPortBoundaries() {
        assertThrows(IllegalArgumentException.class, () -> QuickConnectTextParser.parse(
                "login: smileserv password: Example!Clipboard.SM##"));
        assertThrows(IllegalArgumentException.class, () -> QuickConnectTextParser.parse(
                "prefix!server.example.com suffix"));
        assertConnection(QuickConnectTextParser.parse(
                "server.example.com:22022 login: smileserv password: synthetic-secret"),
                "server.example.com", 22022, "smileserv", "synthetic-secret");
        assertConnection(QuickConnectTextParser.parse(
                "server.example.com 22022 login: smileserv password: synthetic-secret"),
                "server.example.com", 22022, "smileserv", "synthetic-secret");
    }

    @Test
    void onlyUsesPrivateKeyFromAnExplicitCommand() {
        var command = QuickConnectTextParser.parse(
                "ssh -p 2222 -i /tmp/id_ed25519 deploy@example.com");
        assertEquals("/tmp/id_ed25519", command.privateKey().orElseThrow());
        assertEquals("deploy", command.username().orElseThrow());

        var inferred = QuickConnectTextParser.parse("example.com root /tmp/id_ed25519");
        assertTrue(inferred.privateKey().isEmpty());
    }

    @Test
    void rejectsTextAtOrAbove256CharactersAndTextWithoutAQualifiedHost() {
        String accepted = "example.com root secret "
                + "X".repeat(255 - "example.com root secret ".length());
        assertEquals("example.com", QuickConnectTextParser.parse(accepted).host());
        assertThrows(IllegalArgumentException.class,
                () -> QuickConnectTextParser.parse("x".repeat(256)));
        assertThrows(IllegalArgumentException.class,
                () -> QuickConnectTextParser.parse(" ".repeat(256)));
        assertThrows(IllegalArgumentException.class,
                () -> QuickConnectTextParser.parse("root password internal-host"));
    }

    private static void assertConnection(
            QuickConnectCommandParser.ParsedCommand parsed,
            String host,
            int port,
            String username,
            String password) {
        assertEquals(host, parsed.host());
        assertEquals(port, parsed.port().orElseThrow());
        assertEquals(username, parsed.username().orElseThrow());
        assertEquals(password, parsed.password().orElseThrow());
        assertTrue(parsed.privateKey().isEmpty());
    }
}
