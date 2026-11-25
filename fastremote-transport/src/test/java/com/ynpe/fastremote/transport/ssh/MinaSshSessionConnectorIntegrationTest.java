// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.transport.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.connection.AuthenticationType;
import com.ynpe.fastremote.domain.connection.ConnectionProfile;
import com.ynpe.fastremote.domain.connection.ConnectionProgress;
import com.ynpe.fastremote.domain.connection.ConnectionHop;
import com.ynpe.fastremote.domain.connection.Host;
import com.ynpe.fastremote.domain.connection.PasswordCredentials;
import com.ynpe.fastremote.domain.connection.Port;
import com.ynpe.fastremote.domain.connection.Username;
import com.ynpe.fastremote.domain.security.HostKeyDecision;
import com.ynpe.fastremote.domain.security.HostKeyIdentity;
import com.ynpe.fastremote.domain.security.KnownHostRepository;
import com.ynpe.fastremote.domain.terminal.TerminalConnection;
import com.ynpe.fastremote.domain.terminal.TerminalTermination;
import com.ynpe.fastremote.domain.terminal.RemoteCommandResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Duration;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.ServerBuilder;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.apache.sshd.sftp.SftpModuleProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.ynpe.fastremote.domain.settings.ConnectionSettings;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.auth.pubkey.UserAuthPublicKeyFactory;
import org.apache.sshd.client.config.hosts.HostConfigEntryResolver;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.common.cipher.BuiltinCiphers;
import org.apache.sshd.common.kex.BuiltinDHFactories;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.keyprovider.KeyIdentityProvider;
import org.apache.sshd.common.mac.BuiltinMacs;
import org.apache.sshd.common.signature.BuiltinSignatures;
import com.ynpe.fastremote.domain.transfer.SftpConnection;

class MinaSshSessionConnectorIntegrationTest {
    @TempDir
    Path directory;

    private SshServer server;

    @Test
    void overwritesRemoteTextThroughAnSftpVersionThreeServer() throws Exception {
        Path target = directory.resolve("sftp-v3-overwrite.txt");
        Files.writeString(target, "old", StandardCharsets.UTF_8);
        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(directory.resolve("sftp-v3-host-key")));
        server.setPasswordAuthenticator((username, password, session) -> true);
        server.setSubsystemFactories(List.of(new SftpSubsystemFactory.Builder().build()));
        SftpModuleProperties.SFTP_VERSION.set(server, 3);
        server.start();

        MinaSshSessionConnector connector = new MinaSshSessionConnector(new MemoryKnownHosts());
        ConnectionProfile profile = new ConnectionProfile(
                new Host("127.0.0.1"), new Port(server.getPort()), new Username("alice"),
                AuthenticationType.PASSWORD, Optional.empty());
        try (PasswordCredentials credentials = new PasswordCredentials("password".toCharArray());
                SftpConnection sftp = connector.connectSftp(
                        profile, credentials, ignored -> HostKeyDecision.ACCEPT_ONCE, ignored -> {},
                        ConnectionSettings.defaults())) {
            sftp.remoteFiles().writeFileAtomic(target.toString(),
                    "new-content".getBytes(StandardCharsets.UTF_8), true);

            assertEquals("new-content", Files.readString(target, StandardCharsets.UTF_8));
            try (var children = Files.list(directory)) {
                assertFalse(children.anyMatch(path -> path.getFileName().toString().contains(".fastremote-")));
            }
        } finally {
            connector.close();
        }
    }

    @Test
    void movesFilesAndDirectoriesWithoutOverwriteOnSftpThree() throws Exception {
        assertSftpMoves(3);
    }

    @Test
    void movesFilesAndDirectoriesWithoutOverwriteOnSftpSix() throws Exception {
        assertSftpMoves(6);
    }

    private void assertSftpMoves(int version) throws Exception {
        Path file = Files.writeString(directory.resolve("a.txt"), "source");
        Path folder = Files.createDirectory(directory.resolve("folder"));
        Files.writeString(folder.resolve("nested.txt"), "nested");
        Path destination = Files.createDirectory(directory.resolve("test"));
        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(directory.resolve("move-host-key")));
        server.setPasswordAuthenticator((username, password, session) -> true);
        server.setSubsystemFactories(List.of(new SftpSubsystemFactory.Builder().build()));
        SftpModuleProperties.SFTP_VERSION.set(server, version);
        server.start();
        MinaSshSessionConnector connector = new MinaSshSessionConnector(new MemoryKnownHosts());
        ConnectionProfile profile = new ConnectionProfile(
                new Host("127.0.0.1"), new Port(server.getPort()), new Username("alice"),
                AuthenticationType.PASSWORD, Optional.empty());
        try (PasswordCredentials credentials = new PasswordCredentials("password".toCharArray());
                SftpConnection sftp = connector.connectSftp(
                        profile, credentials, ignored -> HostKeyDecision.ACCEPT_ONCE, ignored -> {},
                        ConnectionSettings.defaults())) {
            var files = sftp.remoteFiles();
            String target = files.canonicalPath(destination.toString());
            files.rename(files.canonicalPath(file.toString()), target + "/a.txt");
            files.rename(files.canonicalPath(folder.toString()), target + "/folder");
            assertFalse(Files.exists(file));
            assertFalse(Files.exists(folder));
            assertEquals("source", Files.readString(destination.resolve("a.txt")));
            assertEquals("nested", Files.readString(destination.resolve("folder/nested.txt")));
            Files.writeString(file, "preserved");
            assertThrows(IOException.class, () -> files.rename(file.toString(), target + "/a.txt"));
            assertEquals("preserved", Files.readString(file));
            assertEquals("source", Files.readString(destination.resolve("a.txt")));
            Files.createDirectory(folder);
            assertThrows(IOException.class, () -> files.rename(folder.toString(), target + "/folder"));
            assertTrue(Files.isDirectory(folder));
            assertEquals("nested", Files.readString(destination.resolve("folder/nested.txt")));
        } finally { connector.close(); }
    }

    @Test
    void testsAuthenticationWithoutOpeningShellOrSftpChannel() throws Exception {
        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(directory.resolve("test-connection-host-key")));
        server.setPasswordAuthenticator((username, password, session) ->
                username.equals("alice") && password.equals("correct-password"));
        server.start();

        MinaSshSessionConnector connector = new MinaSshSessionConnector(new MemoryKnownHosts());
        ConnectionProfile profile = new ConnectionProfile(
                new Host("127.0.0.1"), new Port(server.getPort()), new Username("alice"),
                AuthenticationType.PASSWORD, Optional.empty());
        List<ConnectionProgress> progress = new ArrayList<>();
        try (PasswordCredentials credentials = new PasswordCredentials("correct-password".toCharArray())) {
            connector.testConnection(profile, credentials,
                    ignored -> HostKeyDecision.ACCEPT_ONCE, progress::add, ConnectionSettings.defaults());

            assertEquals(ConnectionProgress.SESSION_ESTABLISHED, progress.getLast());
        } finally {
            connector.close();
        }
    }

    @Test
    void appliesConfiguredPtyAndRepairsCCharacterLocaleWhenUtf8IsAvailable() throws Exception {
        AtomicReference<java.util.Map<String, String>> environment = new AtomicReference<>();
        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(directory.resolve("pty-settings-host-key")));
        server.setPasswordAuthenticator((username, password, session) -> true);
        server.setCommandFactory((channel, command) -> new FixedCommand("LC_CTYPE"));
        server.setShellFactory(channel -> new EchoShell(environment));
        server.start();

        MinaSshSessionConnector connector = new MinaSshSessionConnector(new MemoryKnownHosts());
        ConnectionProfile profile = new ConnectionProfile(
                new Host("127.0.0.1"), new Port(server.getPort()), new Username("alice"),
                AuthenticationType.PASSWORD, Optional.empty());
        var defaults = com.ynpe.fastremote.domain.settings.TerminalPreferences.defaults();
        var terminalPreferences = new com.ynpe.fastremote.domain.settings.TerminalPreferences(
                defaults.colorScheme(), defaults.fontFamily(), defaults.fontSize(), defaults.cursorShape(),
                defaults.cursorBlink(), defaults.customColorPalette(), defaults.importedColorPalettes(),
                com.ynpe.fastremote.domain.settings.TerminalType.LINUX, 132, 41);
        try (PasswordCredentials credentials = new PasswordCredentials("password".toCharArray());
             TerminalConnection terminal = connector.connect(
                     profile, credentials, ignored -> HostKeyDecision.ACCEPT_ONCE, ignored -> {},
                     ConnectionSettings.defaults(), terminalPreferences)) {
            terminal.input().readNBytes("ready\r\n".length());
            assertEquals("linux", environment.get().get(Environment.ENV_TERM));
            assertEquals("132", environment.get().get(Environment.ENV_COLUMNS));
            assertEquals("41", environment.get().get(Environment.ENV_LINES));
            assertEquals("en_US.UTF-8", environment.get().get("LC_CTYPE"));
        } finally {
            connector.close();
        }
    }

    @Test
    void preservesAnExistingRemoteCharacterLocale() throws Exception {
        AtomicReference<java.util.Map<String, String>> environment = new AtomicReference<>();
        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(directory.resolve("locale-host-key")));
        server.setPasswordAuthenticator((username, password, session) -> true);
        server.setCommandFactory((channel, command) -> new FixedCommand(""));
        server.setShellFactory(channel -> new EchoShell(environment));
        server.start();

        MinaSshSessionConnector connector = new MinaSshSessionConnector(new MemoryKnownHosts());
        ConnectionProfile profile = new ConnectionProfile(
                new Host("127.0.0.1"), new Port(server.getPort()), new Username("alice"),
                AuthenticationType.PASSWORD, Optional.empty());
        try (PasswordCredentials credentials = new PasswordCredentials("password".toCharArray());
             TerminalConnection terminal = connector.connect(
                     profile, credentials, ignored -> HostKeyDecision.ACCEPT_ONCE, ignored -> {},
                     ConnectionSettings.defaults(),
                     com.ynpe.fastremote.domain.settings.TerminalPreferences.defaults())) {
            terminal.input().readNBytes("ready\r\n".length());
            assertFalse(environment.get().containsKey("LC_ALL"));
            assertFalse(environment.get().containsKey("LC_CTYPE"));
        } finally {
            connector.close();
        }
    }

    @Test
    void connectsToTerminalThroughAuthenticatedJumpHost() throws Exception {
        SshServer jump = SshServer.setUpDefaultServer();
        SshServer target = SshServer.setUpDefaultServer();
        jump.setHost("127.0.0.1");
        jump.setPort(0);
        jump.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(directory.resolve("jump-host-key")));
        jump.setPasswordAuthenticator((username, password, session) ->
                username.equals("jump-user") && password.equals("jump-password"));
        jump.setForwardingFilter(AcceptAllForwardingFilter.INSTANCE);
        target.setHost("127.0.0.1");
        target.setPort(0);
        target.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(directory.resolve("target-host-key")));
        target.setPasswordAuthenticator((username, password, session) ->
                username.equals("target-user") && password.equals("target-password"));
        target.setShellFactory(channel -> new EchoShell());
        target.setSubsystemFactories(List.of(new SftpSubsystemFactory.Builder().build()));
        jump.start();
        target.start();

        MinaSshSessionConnector connector = new MinaSshSessionConnector(new MemoryKnownHosts());
        ConnectionProfile jumpProfile = new ConnectionProfile(
                new Host("127.0.0.1"), new Port(jump.getPort()), new Username("jump-user"),
                AuthenticationType.PASSWORD, Optional.empty());
        ConnectionProfile targetProfile = new ConnectionProfile(
                new Host("127.0.0.1"), new Port(target.getPort()), new Username("target-user"),
                AuthenticationType.PASSWORD, Optional.empty());
        try (PasswordCredentials jumpCredentials = new PasswordCredentials("jump-password".toCharArray());
             PasswordCredentials targetCredentials = new PasswordCredentials("target-password".toCharArray());
             TerminalConnection terminal = connector.connect(List.of(
                             new ConnectionHop("jump", jumpProfile, jumpCredentials),
                             new ConnectionHop("target", targetProfile, targetCredentials)),
                     ignored -> HostKeyDecision.TRUST_AND_SAVE, ignored -> {}, ConnectionSettings.defaults())) {
            assertEquals("ready\r\n", new String(
                    terminal.input().readNBytes("ready\r\n".length()), StandardCharsets.UTF_8));
            terminal.output().write("routed\n".getBytes(StandardCharsets.UTF_8));
            terminal.output().flush();
            assertEquals("routed\n", new String(
                    terminal.input().readNBytes("routed\n".length()), StandardCharsets.UTF_8));
        }
        try (PasswordCredentials jumpCredentials = new PasswordCredentials("jump-password".toCharArray());
             PasswordCredentials targetCredentials = new PasswordCredentials("target-password".toCharArray());
             SftpConnection sftp = connector.connectSftp(List.of(
                             new ConnectionHop("jump", jumpProfile, jumpCredentials),
                             new ConnectionHop("target", targetProfile, targetCredentials)),
                     ignored -> HostKeyDecision.TRUST_AND_SAVE, ignored -> {}, ConnectionSettings.defaults())) {
            String canonical = sftp.remoteFiles().canonicalPath(directory.toString());
            assertTrue(sftp.remoteFiles().list(canonical).stream().anyMatch(entry -> !entry.name().isBlank()));
        } finally {
            connector.close();
            target.stop(true);
            jump.stop(true);
        }
    }

    @Test
    void appliesConfiguredNetworkTimeoutsAndKeepAliveToTheSshClient() {
        SshClient client = SshClient.setUpDefaultClient();
        ConnectionSettings settings = new ConnectionSettings(29, 43, 17, false, 30);

        MinaSshSessionConnector.configureClient(client, settings);

        assertEquals(Duration.ofSeconds(29), CoreModuleProperties.IO_CONNECT_TIMEOUT.getRequired(client));
        assertEquals(Duration.ofSeconds(43), CoreModuleProperties.AUTH_TIMEOUT.getRequired(client));
        assertEquals(Duration.ofSeconds(17), CoreModuleProperties.HEARTBEAT_INTERVAL.getRequired(client));
        assertEquals(5, CoreModuleProperties.HEARTBEAT_NO_REPLY_MAX.getRequired(client));
        assertEquals(Duration.ofSeconds(120), SftpModuleProperties.SFTP_CHANNEL_OPEN_TIMEOUT.getRequired(client));
        assertEquals(Duration.ofSeconds(120), org.apache.sshd.sftp.client.impl.AbstractSftpClient
                .SFTP_CLIENT_CMD_TIMEOUT.getRequired(client));
        assertEquals(KeyIdentityProvider.EMPTY_KEYS_PROVIDER, client.getKeyIdentityProvider());
        assertEquals(HostConfigEntryResolver.EMPTY, client.getHostConfigEntryResolver());
    }

    @Test
    void keepsCurrentAlgorithmsByDefaultAndAddsOnlyLegacyAlgorithmsWhenEnabled() {
        SshClient modern = SshClient.setUpDefaultClient();
        MinaSshSessionConnector.configureClient(modern, ConnectionSettings.defaults(), false);
        assertFalse(factoryNames(modern.getSignatureFactories()).contains("ssh-dss"));
        assertFalse(factoryNames(modern.getSignatureFactories()).contains("ssh-rsa"));
        assertFalse(factoryNames(modern.getKeyExchangeFactories()).contains("diffie-hellman-group1-sha1"));
        assertFalse(factoryNames(modern.getCipherFactories()).contains("aes128-cbc"));
        assertFalse(factoryNames(modern.getCipherFactories()).contains("3des-cbc"));
        assertTrue(factoryNames(modern.getSignatureFactories()).contains("rsa-sha2-512"));
        assertTrue(factoryNames(modern.getSignatureFactories()).contains("rsa-sha2-256"));
        assertTrue(factoryNames(modern.getKeyExchangeFactories()).contains("diffie-hellman-group14-sha256"));
        assertTrue(factoryNames(modern.getCipherFactories()).contains("aes128-ctr"));
        assertTrue(factoryNames(modern.getMacFactories()).contains("hmac-sha2-256"));
        assertTrue(factoryNames(modern.getMacFactories()).contains("hmac-sha1-etm@openssh.com"));
        assertTrue(factoryNames(modern.getMacFactories()).contains("hmac-sha1"));
        assertFalse(factoryNames(modern.getMacFactories()).contains("hmac-sha1-96"));

        SshClient legacy = SshClient.setUpDefaultClient();
        MinaSshSessionConnector.configureClient(legacy, ConnectionSettings.defaults(), true);
        assertTrue(factoryNames(legacy.getSignatureFactories()).contains("ssh-rsa"));
        assertTrue(factoryNames(legacy.getSignatureFactories()).contains("ssh-dss"));
        assertTrue(factoryNames(legacy.getKeyExchangeFactories()).contains("diffie-hellman-group14-sha1"));
        assertTrue(factoryNames(legacy.getKeyExchangeFactories()).contains("diffie-hellman-group-exchange-sha1"));
        assertTrue(factoryNames(legacy.getKeyExchangeFactories()).contains("diffie-hellman-group1-sha1"));
        assertTrue(factoryNames(legacy.getCipherFactories()).contains("aes128-cbc"));
        assertTrue(factoryNames(legacy.getCipherFactories()).contains("3des-cbc"));
        assertTrue(factoryNames(legacy.getMacFactories()).contains("hmac-sha1"));
        assertTrue(factoryNames(legacy.getMacFactories()).contains("hmac-sha1-96"));

        List<String> legacyTransportSignatures = factoryNames(legacy.getSignatureFactories());
        assertTrue(legacyTransportSignatures.indexOf("rsa-sha2-512")
                < legacyTransportSignatures.indexOf("ssh-rsa"));
        UserAuthPublicKeyFactory publicKeyAuthentication = legacy.getUserAuthFactories().stream()
                .filter(UserAuthPublicKeyFactory.class::isInstance)
                .map(UserAuthPublicKeyFactory.class::cast)
                .findFirst().orElseThrow();
        List<String> authenticationSignatures = factoryNames(publicKeyAuthentication.getSignatureFactories());
        assertEquals("ssh-rsa", authenticationSignatures.getFirst());
        assertTrue(authenticationSignatures.indexOf("ssh-rsa")
                < authenticationSignatures.indexOf("rsa-sha2-512"));
    }

    @Test
    void connectsToAContemporaryRsaServerThatOffersFullLengthHmacSha1() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair userKey = generator.generateKeyPair();
        Path privateKey = writePkcs8PrivateKey("aws-debian-user-key", userKey);

        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        SimpleGeneratorHostKeyProvider hostKey = new SimpleGeneratorHostKeyProvider(
                directory.resolve("aws-debian-rsa-host-key"));
        hostKey.setAlgorithm("RSA");
        hostKey.setKeySize(2048);
        server.setKeyPairProvider(hostKey);
        server.setSignatureFactories(List.of(BuiltinSignatures.rsaSHA512, BuiltinSignatures.rsaSHA256));
        server.setKeyExchangeFactories(List.of(ServerBuilder.DH2KEX.apply(BuiltinDHFactories.dhg14_256)));
        server.setCipherFactories(List.of(BuiltinCiphers.aes128ctr));
        server.setMacFactories(List.of(BuiltinMacs.hmacsha1));
        server.setPublickeyAuthenticator((username, key, session) ->
                username.equals("admin") && key.equals(userKey.getPublic()));
        server.setShellFactory(channel -> new EchoShell());
        server.start();

        MinaSshSessionConnector connector = new MinaSshSessionConnector(new MemoryKnownHosts());
        ConnectionProfile profile = new ConnectionProfile(
                new Host("127.0.0.1"), new Port(server.getPort()), new Username("admin"),
                AuthenticationType.PRIVATE_KEY, Optional.empty());
        try (com.ynpe.fastremote.domain.connection.PrivateKeyCredentials credentials
                     = new com.ynpe.fastremote.domain.connection.PrivateKeyCredentials(
                             new char[0], Files.readAllBytes(privateKey));
                TerminalConnection terminal = connector.connect(
                        profile, credentials, ignored -> HostKeyDecision.ACCEPT_ONCE)) {
            assertTrue(terminal.isOpen());
        } finally {
            connector.close();
        }
    }

    @Test
    void connectsWithAnRsaSha1UserSignatureOnlyWhenLegacyModeIsEnabled() throws Exception {
        KeyPairGenerator rsaGenerator = KeyPairGenerator.getInstance("RSA");
        rsaGenerator.initialize(2048);
        KeyPair userKey = rsaGenerator.generateKeyPair();
        Path privateKey = writePkcs8PrivateKey("legacy-rsa-user-key", userKey);

        KeyPairGenerator ecGenerator = KeyPairGenerator.getInstance("EC");
        ecGenerator.initialize(256);
        KeyPair hostKey = ecGenerator.generateKeyPair();

        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        server.setKeyPairProvider(KeyPairProvider.wrap(hostKey));
        server.setSignatureFactories(List.of(BuiltinSignatures.nistp256, BuiltinSignatures.rsa));
        server.setPublickeyAuthenticator((username, key, session) ->
                username.equals("legacy-rsa") && key.equals(userKey.getPublic()));
        server.setShellFactory(channel -> new EchoShell());
        server.start();

        MinaSshSessionConnector connector = new MinaSshSessionConnector(new MemoryKnownHosts());
        ConnectionProfile modern = new ConnectionProfile(
                new Host("127.0.0.1"), new Port(server.getPort()), new Username("legacy-rsa"),
                AuthenticationType.PRIVATE_KEY, Optional.empty());
        try (com.ynpe.fastremote.domain.connection.PrivateKeyCredentials credentials
                     = new com.ynpe.fastremote.domain.connection.PrivateKeyCredentials(
                             new char[0], Files.readAllBytes(privateKey))) {
            assertThrows(com.ynpe.fastremote.domain.connection.ConnectionException.class,
                    () -> connector.connect(modern, credentials,
                            ignored -> HostKeyDecision.ACCEPT_ONCE, ignored -> {}));
        }

        ConnectionProfile compatible = new ConnectionProfile(
                new Host("127.0.0.1"), new Port(server.getPort()), new Username("legacy-rsa"),
                AuthenticationType.PRIVATE_KEY, Optional.empty(), true);
        try (com.ynpe.fastremote.domain.connection.PrivateKeyCredentials credentials
                     = new com.ynpe.fastremote.domain.connection.PrivateKeyCredentials(
                             new char[0], Files.readAllBytes(privateKey));
                TerminalConnection terminal = connector.connect(
                        compatible, credentials, ignored -> HostKeyDecision.ACCEPT_ONCE, ignored -> {})) {
            assertTrue(terminal.isOpen());
        } finally {
            connector.close();
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    void connectsToALegacyOnlyDsaServerWhenTheProfileExplicitlyAllowsIt() throws Exception {
        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        SimpleGeneratorHostKeyProvider hostKey = new SimpleGeneratorHostKeyProvider(
                directory.resolve("legacy-dsa-host-key"));
        hostKey.setAlgorithm("DSA");
        hostKey.setKeySize(1024);
        server.setKeyPairProvider(hostKey);
        server.setSignatureFactories(List.of(BuiltinSignatures.dsa));
        server.setKeyExchangeFactories(List.of(ServerBuilder.DH2KEX.apply(BuiltinDHFactories.dhg1)));
        server.setCipherFactories(List.of(BuiltinCiphers.aes128cbc));
        server.setMacFactories(List.of(BuiltinMacs.hmacsha1));
        server.setPasswordAuthenticator((username, password, session) -> true);
        server.setShellFactory(channel -> new EchoShell());
        server.setSubsystemFactories(List.of(new SftpSubsystemFactory.Builder().build()));
        server.start();

        MinaSshSessionConnector connector = new MinaSshSessionConnector(new MemoryKnownHosts());
        ConnectionProfile modern = new ConnectionProfile(
                new Host("127.0.0.1"), new Port(server.getPort()), new Username("legacy"),
                AuthenticationType.PASSWORD, Optional.empty());
        try (PasswordCredentials credentials = new PasswordCredentials("password".toCharArray())) {
            assertThrows(com.ynpe.fastremote.domain.connection.ConnectionException.class,
                    () -> connector.connect(modern, credentials,
                            ignored -> HostKeyDecision.ACCEPT_ONCE, ignored -> {}));
        }

        ConnectionProfile compatible = new ConnectionProfile(
                new Host("127.0.0.1"), new Port(server.getPort()), new Username("legacy"),
                AuthenticationType.PASSWORD, Optional.empty(), true);
        try (PasswordCredentials credentials = new PasswordCredentials("password".toCharArray());
                TerminalConnection terminal = connector.connect(compatible, credentials,
                        ignored -> HostKeyDecision.ACCEPT_ONCE, ignored -> {})) {
            assertTrue(terminal.isOpen());
        }
        try (PasswordCredentials credentials = new PasswordCredentials("password".toCharArray());
                SftpConnection sftp = connector.connectSftp(compatible, credentials,
                        ignored -> HostKeyDecision.ACCEPT_ONCE, ignored -> {})) {
            assertTrue(sftp.isOpen());
            assertFalse(sftp.remoteFiles().canonicalPath(".").isBlank());
        } finally {
            connector.close();
        }
    }

    private static List<String> factoryNames(List<? extends org.apache.sshd.common.NamedResource> factories) {
        return factories.stream().map(org.apache.sshd.common.NamedResource::getName).toList();
    }

    @Test
    void reportsTerminalIdleTimeoutSeparatelyFromNetworkFailure() throws Exception {
        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(directory.resolve("idle-host-key")));
        server.setPasswordAuthenticator((username, password, session) -> true);
        server.setShellFactory(channel -> new EchoShell());
        server.start();

        MinaSshSessionConnector connector = new MinaSshSessionConnector(new MemoryKnownHosts());
        ConnectionProfile profile = new ConnectionProfile(
                new Host("127.0.0.1"), new Port(server.getPort()), new Username("alice"),
                AuthenticationType.PASSWORD, Optional.empty());
        try (PasswordCredentials credentials = new PasswordCredentials("password".toCharArray());
                TerminalConnection terminal = connector.connect(
                        profile, credentials, ignored -> HostKeyDecision.ACCEPT_ONCE, ignored -> {},
                        new ConnectionSettings(15, 20, 30, true, 1))) {
            terminal.input().readNBytes("ready\r\n".length());
            ((MinaTerminalConnection) terminal).closeIfIdle(Long.MAX_VALUE);

            assertEquals(TerminalTermination.Kind.IDLE_TIMEOUT, terminal.waitForTermination().kind());
        } finally {
            connector.close();
        }
    }

    @Test
    void notifiesAnIdleSftpWorkspaceWhenItsUnderlyingSessionCloses() throws Exception {
        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(directory.resolve("sftp-monitor-host-key")));
        server.setPasswordAuthenticator((username, password, session) -> true);
        server.setSubsystemFactories(List.of(new SftpSubsystemFactory.Builder().build()));
        server.start();

        MinaSshSessionConnector connector = new MinaSshSessionConnector(new MemoryKnownHosts());
        ConnectionProfile profile = new ConnectionProfile(
                new Host("127.0.0.1"), new Port(server.getPort()), new Username("alice"),
                AuthenticationType.PASSWORD, Optional.empty());
        try (PasswordCredentials credentials = new PasswordCredentials("password".toCharArray());
                SftpConnection sftp = connector.connectSftp(
                        profile, credentials, ignored -> HostKeyDecision.ACCEPT_ONCE, ignored -> {},
                        ConnectionSettings.defaults())) {
            server.stop(true);

            sftp.termination().toCompletableFuture().handle((ignored, failure) -> {
                if (failure != null) org.junit.jupiter.api.Assertions.assertInstanceOf(java.io.IOException.class, failure);
                return null;
            }).get(2, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            connector.close();
        }
    }

    @Test
    void executesABoundedAuxiliaryCommandOnTheAuthenticatedTerminalSession() throws Exception {
        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(directory.resolve("command-host-key")));
        server.setPasswordAuthenticator((username, password, session) -> true);
        server.setShellFactory(channel -> new EchoShell());
        server.setCommandFactory((channel, command) -> new FixedCommand("facts\nextra-output"));
        server.start();

        MinaSshSessionConnector connector = new MinaSshSessionConnector(new MemoryKnownHosts());
        ConnectionProfile profile = new ConnectionProfile(
                new Host("127.0.0.1"), new Port(server.getPort()), new Username("alice"),
                AuthenticationType.PASSWORD, Optional.empty());
        try (PasswordCredentials credentials = new PasswordCredentials("password".toCharArray());
                TerminalConnection terminal = connector.connect(
                        profile, credentials, ignored -> HostKeyDecision.ACCEPT_ONCE)) {
            terminal.input().readNBytes("ready\r\n".length());

            RemoteCommandResult result = terminal.executeCommand("overview", Duration.ofSeconds(2), 5);

            assertEquals("facts", result.standardOutput());
            assertEquals(0, result.exitCode());
            assertTrue(result.truncated());
            assertTrue(terminal.isOpen());
        } finally {
            connector.close();
        }
    }

    @Test
    void detectsTheWindowsCodePageBeforeDecodingNonUtf8ExecOutput() throws Exception {
        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(directory.resolve("encoded-command-host-key")));
        server.setPasswordAuthenticator((username, password, session) -> true);
        server.setShellFactory(channel -> new EchoShell());
        java.util.concurrent.atomic.AtomicInteger commands = new java.util.concurrent.atomic.AtomicInteger();
        Charset gb18030 = Charset.forName("GB18030");
        server.setCommandFactory((channel, command) -> {
            if (command.contains("fastremote_locale_var")) return new FixedCommand("");
            commands.incrementAndGet();
            return command.contains(" chcp")
                    ? new FixedCommand("Active code page: 936\r\n".getBytes(StandardCharsets.US_ASCII))
                    : new FixedCommand("Microsoft Windows [版本 10.0]\r\n".getBytes(gb18030));
        });
        server.start();

        MinaSshSessionConnector connector = new MinaSshSessionConnector(new MemoryKnownHosts());
        ConnectionProfile profile = new ConnectionProfile(
                new Host("127.0.0.1"), new Port(server.getPort()), new Username("alice"),
                AuthenticationType.PASSWORD, Optional.empty());
        try (PasswordCredentials credentials = new PasswordCredentials("password".toCharArray());
                TerminalConnection terminal = connector.connect(
                        profile, credentials, ignored -> HostKeyDecision.ACCEPT_ONCE)) {
            terminal.input().readNBytes("ready\r\n".length());

            RemoteCommandResult result = terminal.executeCommand("cmd.exe /d /c ver", Duration.ofSeconds(2), 4096);

            assertEquals("Microsoft Windows [版本 10.0]\r\n", result.standardOutput());
            assertEquals(2, commands.get());
        } finally {
            connector.close();
        }
    }

    @Test
    void detectsAndCachesANonUtf8PosixLocaleCharset() throws Exception {
        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(directory.resolve("locale-command-host-key")));
        server.setPasswordAuthenticator((username, password, session) -> true);
        server.setShellFactory(channel -> new EchoShell());
        java.util.concurrent.atomic.AtomicInteger commands = new java.util.concurrent.atomic.AtomicInteger();
        Charset latin9 = Charset.forName("ISO-8859-15");
        server.setCommandFactory((channel, command) -> {
            if (command.contains("fastremote_locale_var")) return new FixedCommand("");
            commands.incrementAndGet();
            if (command.contains("cmd.exe")) return new FixedCommand(new byte[0], 127);
            if (command.equals("locale charmap")) {
                return new FixedCommand("ISO-8859-15\n".getBytes(StandardCharsets.US_ASCII));
            }
            return new FixedCommand("Résumé coût €\n".getBytes(latin9));
        });
        server.start();

        MinaSshSessionConnector connector = new MinaSshSessionConnector(new MemoryKnownHosts());
        ConnectionProfile profile = new ConnectionProfile(
                new Host("127.0.0.1"), new Port(server.getPort()), new Username("alice"),
                AuthenticationType.PASSWORD, Optional.empty());
        try (PasswordCredentials credentials = new PasswordCredentials("password".toCharArray());
                TerminalConnection terminal = connector.connect(
                        profile, credentials, ignored -> HostKeyDecision.ACCEPT_ONCE)) {
            terminal.input().readNBytes("ready\r\n".length());

            assertEquals("Résumé coût €\n",
                    terminal.executeCommand("status", Duration.ofSeconds(2), 4096).standardOutput());
            assertEquals("Résumé coût €\n",
                    terminal.executeCommand("status-again", Duration.ofSeconds(2), 4096).standardOutput());
            assertEquals(4, commands.get());
        } finally {
            connector.close();
        }
    }

    @Test
    void classifiesARejectedPasswordAsAuthenticationFailure() throws Exception {
        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(directory.resolve("rejected-host-key")));
        server.setPasswordAuthenticator((username, password, session) -> false);
        server.start();

        MinaSshSessionConnector connector = new MinaSshSessionConnector(new MemoryKnownHosts());
        ConnectionProfile profile = new ConnectionProfile(
                new Host("127.0.0.1"), new Port(server.getPort()), new Username("root"),
                AuthenticationType.PASSWORD, Optional.empty());
        try (PasswordCredentials credentials = new PasswordCredentials("correct-but-disabled".toCharArray())) {
            com.ynpe.fastremote.domain.connection.ConnectionException failure = assertThrows(
                    com.ynpe.fastremote.domain.connection.ConnectionException.class,
                    () -> connector.connect(profile, credentials, ignored -> HostKeyDecision.ACCEPT_ONCE));
            assertEquals(com.ynpe.fastremote.domain.connection.ConnectionFailureType.AUTHENTICATION_FAILED,
                    failure.failureType());
        } finally {
            connector.close();
        }
    }

    @Test
    void preservesHostKeyPromptFailureInsteadOfReportingNetworkFailure() throws Exception {
        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(directory.resolve("prompt-failure-host-key")));
        server.setPasswordAuthenticator((username, password, session) -> true);
        server.start();

        MinaSshSessionConnector connector = new MinaSshSessionConnector(new MemoryKnownHosts());
        ConnectionProfile profile = new ConnectionProfile(
                new Host("127.0.0.1"), new Port(server.getPort()), new Username("root"),
                AuthenticationType.PASSWORD, Optional.empty());
        try (PasswordCredentials credentials = new PasswordCredentials("password".toCharArray())) {
            com.ynpe.fastremote.domain.connection.ConnectionException failure = assertThrows(
                    com.ynpe.fastremote.domain.connection.ConnectionException.class,
                    () -> connector.connect(profile, credentials, ignored -> {
                        throw new IllegalStateException("dialog unavailable");
                    }));
            assertEquals(com.ynpe.fastremote.domain.connection.ConnectionFailureType.HOST_KEY_REJECTED,
                    failure.failureType());
        } finally {
            connector.close();
        }
    }

    @AfterEach
    void stopServer() throws IOException {
        if (server != null) {
            server.stop(true);
        }
    }

    @Test
    void connectsAuthenticatesTrustsHostAndTransfersTerminalBytes() throws Exception {
        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(directory.resolve("host-key")));
        server.setPasswordAuthenticator((username, password, session) ->
                username.equals("alice") && password.equals("correct-password"));
        server.setShellFactory(channel -> new EchoShell());
        server.setSubsystemFactories(List.of(new SftpSubsystemFactory.Builder().build()));
        server.start();

        MemoryKnownHosts knownHosts = new MemoryKnownHosts();
        MinaSshSessionConnector connector = new MinaSshSessionConnector(knownHosts);
        ConnectionProfile profile = new ConnectionProfile(
                new Host("127.0.0.1"),
                new Port(server.getPort()),
                new Username("alice"),
                AuthenticationType.PASSWORD,
                Optional.empty());

        List<ConnectionProgress> progress = new ArrayList<>();
        try (PasswordCredentials credentials = new PasswordCredentials("correct-password".toCharArray());
             TerminalConnection terminal = connector.connect(
                     profile,
                     credentials,
                     check -> HostKeyDecision.TRUST_AND_SAVE,
                     progress::add)) {
            byte[] greeting = terminal.input().readNBytes("ready\r\n".length());
            assertEquals("ready\r\n", new String(greeting, StandardCharsets.UTF_8));

            terminal.output().write("hello\n".getBytes(StandardCharsets.UTF_8));
            terminal.output().flush();
            byte[] echoed = terminal.input().readNBytes("hello\n".length());
            assertEquals("hello\n", new String(echoed, StandardCharsets.UTF_8));
            assertTrue(knownHosts.identity.isPresent());
            assertTrue(terminal.isOpen());
            assertEquals(List.of(
                    ConnectionProgress.NETWORK_CONNECTING,
                    ConnectionProgress.HOST_KEY_VALIDATING,
                    ConnectionProgress.PASSWORD_AUTHENTICATING,
                    ConnectionProgress.SESSION_ESTABLISHED), progress);

            Path localUpload = directory.resolve("local-upload.txt");
            Files.writeString(localUpload, "shared-session-sftp", StandardCharsets.UTF_8);
            String remoteDirectory = terminal.remoteFiles().canonicalPath(directory.toString());
            String remoteFile = remoteDirectory + "/remote-upload.txt";
            terminal.remoteFiles().upload(localUpload, remoteFile);
            assertTrue(terminal.remoteFiles().list(remoteDirectory).stream()
                    .anyMatch(entry -> entry.name().equals("remote-upload.txt")));
            var originalAttributes = terminal.remoteFiles().readAttributes(remoteFile);
            assertEquals("remote-upload.txt", originalAttributes.name());
            assertFalse(originalAttributes.directory());
            terminal.remoteFiles().setPermissions(remoteFile, 0640);
            if (Files.getFileAttributeView(directory, PosixFileAttributeView.class) != null) {
                assertEquals(0640, terminal.remoteFiles().readAttributes(remoteFile).permissions());
            }
            Path downloaded = directory.resolve("downloaded.txt");
            terminal.remoteFiles().download(remoteFile, downloaded);
            assertEquals("shared-session-sftp", Files.readString(downloaded, StandardCharsets.UTF_8));

            Path localTree = directory.resolve("local-tree");
            Files.createDirectories(localTree.resolve("nested"));
            Files.writeString(localTree.resolve("root.txt"), "root", StandardCharsets.UTF_8);
            Files.writeString(localTree.resolve("nested/child.txt"), "child", StandardCharsets.UTF_8);
            String remoteTreeName = "uploaded tree.[build]-#1";
            String remoteTree = remoteDirectory + "/" + remoteTreeName;
            java.util.concurrent.atomic.AtomicLong discovered = new java.util.concurrent.atomic.AtomicLong();
            java.util.concurrent.atomic.AtomicLong transferred = new java.util.concurrent.atomic.AtomicLong();
            List<com.ynpe.fastremote.domain.transfer.TransferActivity> uploadActivities = new ArrayList<>();
            terminal.remoteFiles().uploadTree(localTree, remoteTree, () -> {},
                    (total, copied) -> { discovered.set(total); transferred.set(copied); },
                    conflict -> com.ynpe.fastremote.domain.transfer.TransferConflictChoice.OVERWRITE,
                    uploadActivities::add);
            assertEquals(9L, discovered.get());
            assertEquals(9L, transferred.get());
            assertTrue(uploadActivities.stream().anyMatch(activity -> activity.source().endsWith("root.txt")));
            assertTrue(uploadActivities.stream().anyMatch(activity ->
                    activity.objectType() == com.ynpe.fastremote.domain.transfer.TransferActivity.ObjectType.DIRECTORY));
            Path downloadedTree = directory.resolve("downloaded-tree");
            List<com.ynpe.fastremote.domain.transfer.TransferActivity> downloadActivities = new ArrayList<>();
            terminal.remoteFiles().downloadTree(remoteTree, downloadedTree, () -> {},
                    (total, copied) -> {},
                    conflict -> com.ynpe.fastremote.domain.transfer.TransferConflictChoice.OVERWRITE,
                    downloadActivities::add);
            assertEquals("root", Files.readString(downloadedTree.resolve("root.txt"), StandardCharsets.UTF_8));
            assertEquals("child", Files.readString(
                    downloadedTree.resolve("nested/child.txt"), StandardCharsets.UTF_8));
            assertTrue(downloadActivities.stream().anyMatch(activity -> activity.target().endsWith("child.txt")));

            boolean createdSymbolicLink =
                    Files.getFileAttributeView(directory, PosixFileAttributeView.class) != null;
            if (createdSymbolicLink) {
                Files.createSymbolicLink(directory.resolve(remoteTreeName + "/nested/source-link"),
                        Path.of("../root.txt"));
            }

            List<com.ynpe.fastremote.domain.transfer.TransferActivity> deleteActivities = new ArrayList<>();
            terminal.remoteFiles().delete(remoteTree, deleteActivities::add);
            assertFalse(Files.exists(directory.resolve(remoteTreeName)));
            assertTrue(terminal.remoteFiles().list(remoteDirectory).stream()
                    .noneMatch(entry -> entry.name().equals(remoteTreeName)));
            if (createdSymbolicLink) {
                assertTrue(deleteActivities.stream().anyMatch(activity -> activity.objectType()
                        == com.ynpe.fastremote.domain.transfer.TransferActivity.ObjectType.SYMBOLIC_LINK));
            }
            terminal.remoteFiles().delete(remoteTree); // A concurrent/repeated delete is idempotent.

            Path cancelledSource = directory.resolve("cancelled-upload.bin");
            Files.write(cancelledSource, new byte[128 * 1024]);
            String cancelledTarget = remoteDirectory + "/must-not-exist.bin";
            java.util.concurrent.atomic.AtomicInteger checkpoints = new java.util.concurrent.atomic.AtomicInteger();
            assertThrows(IOException.class, () -> terminal.remoteFiles().uploadTree(
                    cancelledSource, cancelledTarget,
                    () -> {
                        if (checkpoints.incrementAndGet() >= 2) {
                            throw new java.io.InterruptedIOException("cancel fixture");
                        }
                    },
                    (total, copied) -> {},
                    conflict -> com.ynpe.fastremote.domain.transfer.TransferConflictChoice.OVERWRITE));
            assertTrue(terminal.remoteFiles().list(remoteDirectory).stream()
                    .noneMatch(entry -> entry.name().equals("must-not-exist.bin")
                            || entry.name().startsWith("must-not-exist.bin.fastremote-")));

            terminal.output().close();
            assertEquals(TerminalTermination.Kind.NORMAL, terminal.waitForTermination().kind());
        } finally {
            connector.close();
        }
    }

    @Test
    void authenticatesWithAnEmbeddedUnencryptedPkcs8PrivateKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair userKey = generator.generateKeyPair();
        Path privateKey = writePkcs8PrivateKey("id_rsa", userKey);

        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(directory.resolve("server-host-key")));
        server.setPublickeyAuthenticator((username, key, session) ->
                username.equals("alice") && key.equals(userKey.getPublic()));
        server.setShellFactory(channel -> new EchoShell());
        server.start();

        MinaSshSessionConnector connector = new MinaSshSessionConnector(new MemoryKnownHosts());
        ConnectionProfile profile = new ConnectionProfile(
                new Host("127.0.0.1"),
                new Port(server.getPort()),
                new Username("alice"),
                AuthenticationType.PRIVATE_KEY,
                Optional.empty());
        try (com.ynpe.fastremote.domain.connection.PrivateKeyCredentials credentials
                     = new com.ynpe.fastremote.domain.connection.PrivateKeyCredentials(
                             new char[0], Files.readAllBytes(privateKey));
             TerminalConnection terminal = connector.connect(
                     profile,
                     credentials,
                     check -> HostKeyDecision.ACCEPT_ONCE)) {
            assertEquals(
                    "ready\r\n",
                    new String(terminal.input().readNBytes("ready\r\n".length()), StandardCharsets.UTF_8));
            assertTrue(terminal.isOpen());
        } finally {
            connector.close();
        }
    }

    private Path writePkcs8PrivateKey(String fileName, KeyPair keyPair) throws IOException {
        Path privateKey = directory.resolve(fileName);
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(keyPair.getPrivate().getEncoded());
        Files.writeString(
                privateKey,
                "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n",
                StandardCharsets.US_ASCII);
        return privateKey;
    }

    @Test
    void distinguishesServerDisconnectFromNormalShellEof() throws Exception {
        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(directory.resolve("disconnect-host-key")));
        server.setPasswordAuthenticator((username, password, session) -> true);
        server.setShellFactory(channel -> new EchoShell());
        server.start();

        MinaSshSessionConnector connector = new MinaSshSessionConnector(new MemoryKnownHosts());
        ConnectionProfile profile = new ConnectionProfile(
                new Host("127.0.0.1"), new Port(server.getPort()), new Username("alice"),
                AuthenticationType.PASSWORD, Optional.empty());
        try (PasswordCredentials credentials = new PasswordCredentials("password".toCharArray());
                TerminalConnection terminal = connector.connect(
                        profile, credentials, ignored -> HostKeyDecision.ACCEPT_ONCE)) {
            terminal.input().readNBytes("ready\r\n".length());
            server.stop(true);

            assertEquals(TerminalTermination.Kind.NETWORK_FAILURE,
                    terminal.waitForTermination().kind());
        } finally {
            connector.close();
        }
    }

    private static final class MemoryKnownHosts implements KnownHostRepository {
        private Optional<HostKeyIdentity> identity = Optional.empty();

        @Override
        public Optional<HostKeyIdentity> find(Host host, Port port) {
            return identity;
        }

        @Override
        public void save(HostKeyIdentity saved) {
            identity = Optional.of(saved);
        }
    }

    private static final class EchoShell implements Command, Runnable {
        private final AtomicBoolean running = new AtomicBoolean();
        private final AtomicReference<java.util.Map<String, String>> observedEnvironment;
        private InputStream input;
        private OutputStream output;
        private ExitCallback exitCallback;
        private Thread thread;

        private EchoShell() {
            this(null);
        }

        private EchoShell(AtomicReference<java.util.Map<String, String>> observedEnvironment) {
            this.observedEnvironment = observedEnvironment;
        }

        @Override
        public void setInputStream(InputStream input) {
            this.input = input;
        }

        @Override
        public void setOutputStream(OutputStream output) {
            this.output = output;
        }

        @Override
        public void setErrorStream(OutputStream error) {
            // PTY clients redirect the extended-data stream to standard output.
        }

        @Override
        public void setExitCallback(ExitCallback callback) {
            exitCallback = callback;
        }

        @Override
        public void start(ChannelSession channel, Environment environment) {
            if (observedEnvironment != null) {
                observedEnvironment.set(java.util.Map.copyOf(environment.getEnv()));
            }
            running.set(true);
            thread = Thread.ofPlatform().daemon(true).name("fastremote-test-echo-shell").start(this);
        }

        @Override
        public void run() {
            try {
                output.write("ready\r\n".getBytes(StandardCharsets.UTF_8));
                output.flush();
                byte[] buffer = new byte[256];
                while (running.get()) {
                    int count = input.read(buffer);
                    if (count < 0) {
                        break;
                    }
                    output.write(buffer, 0, count);
                    output.flush();
                }
                exitCallback.onExit(0);
            } catch (IOException failure) {
                exitCallback.onExit(1, "I/O failure");
            }
        }

        @Override
        public void destroy(ChannelSession channel) throws InterruptedException {
            running.set(false);
            if (thread != null) {
                thread.interrupt();
                thread.join(1_000L);
            }
        }
    }

    private static final class FixedCommand implements Command {
        private final byte[] response;
        private final int exitStatus;
        private OutputStream output;
        private ExitCallback exitCallback;

        private FixedCommand(String response) {
            this(response.getBytes(StandardCharsets.UTF_8), 0);
        }

        private FixedCommand(byte[] response) {
            this(response, 0);
        }

        private FixedCommand(byte[] response, int exitStatus) {
            this.response = response.clone();
            this.exitStatus = exitStatus;
        }

        @Override public void setInputStream(InputStream input) { }
        @Override public void setOutputStream(OutputStream output) { this.output = output; }
        @Override public void setErrorStream(OutputStream error) { }
        @Override public void setExitCallback(ExitCallback callback) { exitCallback = callback; }

        @Override
        public void start(ChannelSession channel, Environment environment) throws IOException {
            output.write(response);
            output.flush();
            exitCallback.onExit(exitStatus);
        }

        @Override public void destroy(ChannelSession channel) { }
    }
}
