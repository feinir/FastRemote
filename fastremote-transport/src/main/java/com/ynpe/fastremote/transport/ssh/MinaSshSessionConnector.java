// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.transport.ssh;

import com.ynpe.fastremote.domain.connection.AuthenticationType;
import com.ynpe.fastremote.domain.connection.ConnectionException;
import com.ynpe.fastremote.domain.connection.ConnectionFailureType;
import com.ynpe.fastremote.domain.connection.ConnectionHop;
import com.ynpe.fastremote.domain.connection.ConnectionProfile;
import com.ynpe.fastremote.domain.connection.Credentials;
import com.ynpe.fastremote.domain.connection.PrivateKeyCredentials;
import com.ynpe.fastremote.domain.connection.SessionConnector;
import com.ynpe.fastremote.domain.security.HostKeyPrompt;
import com.ynpe.fastremote.domain.security.KnownHostRepository;
import com.ynpe.fastremote.domain.terminal.TerminalConnection;
import com.ynpe.fastremote.domain.transfer.SftpConnection;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import com.ynpe.fastremote.domain.connection.ConnectionProgress;
import com.ynpe.fastremote.domain.settings.ConnectionSettings;
import com.ynpe.fastremote.domain.settings.TerminalPreferences;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.client.ClientBuilder;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.auth.UserAuthFactory;
import org.apache.sshd.client.auth.pubkey.UserAuthPublicKeyFactory;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.config.hosts.HostConfigEntryResolver;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.cipher.BuiltinCiphers;
import org.apache.sshd.common.kex.BuiltinDHFactories;
import org.apache.sshd.common.mac.BuiltinMacs;
import org.apache.sshd.common.signature.BuiltinSignatures;
import org.apache.sshd.common.signature.Signature;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.keyprovider.KeyIdentityProvider;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.client.session.forward.ExplicitPortForwardingTracker;

/** Apache MINA SSHD adapter for one interactive shell per connection. */
public final class MinaSshSessionConnector implements SessionConnector {
    private static final Duration CHANNEL_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration LOCALE_PROBE_TIMEOUT = Duration.ofSeconds(2);
    private static final String UTF8_LOCALE = "en_US.UTF-8";
    private static final String REMOTE_LOCALE_PROBE = """
            fastremote_locale_var=''
            if [ -n "${LC_ALL:-}" ]; then
              case "$LC_ALL" in C|POSIX) fastremote_locale_var='LC_ALL';; esac
            elif [ -n "${LC_CTYPE:-}" ]; then
              case "$LC_CTYPE" in C|POSIX) fastremote_locale_var='LC_CTYPE';; esac
            else
              case "${LANG:-C}" in C|POSIX) fastremote_locale_var='LC_CTYPE';; esac
            fi
            if [ -n "$fastremote_locale_var" ]; then
              fastremote_charmap=$(LC_ALL=en_US.UTF-8 locale charmap 2>/dev/null) || {
                printf 'UNAVAILABLE'
                exit 0
              }
              case "$fastremote_charmap" in
                UTF-8|UTF8|utf-8|utf8) printf '%s' "$fastremote_locale_var";;
                *) printf 'UNAVAILABLE';;
              esac
            fi
            """;
    private static final System.Logger LOGGER = System.getLogger(MinaSshSessionConnector.class.getName());

    private final KnownHostRepository knownHosts;
    private final Set<TerminalConnection> activeConnections = ConcurrentHashMap.newKeySet();
    private final Set<SftpConnection> activeSftpConnections = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ScheduledExecutorService idleMonitor = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("fastremote-terminal-idle-monitor").daemon(false).factory());

    public MinaSshSessionConnector(KnownHostRepository knownHosts) {
        this.knownHosts = Objects.requireNonNull(knownHosts, "knownHosts");
        idleMonitor.scheduleAtFixedRate(this::closeIdleTerminals, 1L, 1L, TimeUnit.SECONDS);
    }

    @Override
    public TerminalConnection connect(
            ConnectionProfile profile,
            Credentials credentials,
            HostKeyPrompt hostKeyPrompt) throws ConnectionException {
        return connect(profile, credentials, hostKeyPrompt, ignored -> {});
    }

    @Override
    public TerminalConnection connect(
            List<ConnectionHop> route, HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress, ConnectionSettings settings) throws ConnectionException {
        return connect(route, hostKeyPrompt, progress, settings, TerminalPreferences.defaults());
    }

    @Override
    public TerminalConnection connect(
            List<ConnectionHop> route, HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress, ConnectionSettings settings,
            TerminalPreferences terminalPreferences) throws ConnectionException {
        validateRoute(route, hostKeyPrompt, progress, settings);
        Objects.requireNonNull(terminalPreferences, "terminalPreferences");
        MinaRoutedSshResources resources = authenticateRoute(route, hostKeyPrompt, progress, settings);
        ChannelShell channel = null;
        try {
            ClientSession target = resources.lastSession();
            channel = target.createShellChannel();
            configurePty(target, channel, terminalPreferences);
            channel.setRedirectErrorStream(true);
            channel.open().verify(CHANNEL_TIMEOUT);
            MinaTerminalConnection connection = new MinaTerminalConnection(
                    resources, target, channel,
                    settings.disconnectIdleTerminal() ? settings.terminalIdleTimeout() : null,
                    () -> activeConnections.removeIf(existing -> existing == null || !existing.isOpen()));
            activeConnections.add(connection);
            progress.accept(ConnectionProgress.SESSION_ESTABLISHED);
            return connection;
        } catch (IOException | RuntimeException failure) {
            if (channel != null) channel.close(true);
            resources.close();
            throw new ConnectionException(ConnectionFailureType.CONNECTION_FAILED,
                    "The terminal channel could not be established through the jump host chain.", failure);
        }
    }

    @Override
    public SftpConnection connectSftp(
            List<ConnectionHop> route, HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress, ConnectionSettings settings) throws ConnectionException {
        validateRoute(route, hostKeyPrompt, progress, settings);
        MinaRoutedSshResources resources = authenticateRoute(route, hostKeyPrompt, progress, settings);
        MinaSftpConnection connection = new MinaSftpConnection(
                resources, resources.lastSession(),
                () -> activeSftpConnections.removeIf(existing -> existing == null || !existing.isOpen()));
        activeSftpConnections.add(connection);
        progress.accept(ConnectionProgress.SESSION_ESTABLISHED);
        return connection;
    }

    private void validateRoute(
            List<ConnectionHop> route, HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress, ConnectionSettings settings) throws ConnectionException {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(hostKeyPrompt, "hostKeyPrompt");
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(settings, "settings");
        if (closed.get()) throw new ConnectionException(ConnectionFailureType.SESSION_CLOSED, "SSH connector is closed");
        if (route.isEmpty() || route.size() > 6) {
            throw new IllegalArgumentException("A routed connection requires one target and at most five jump hosts");
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new ConnectionException(ConnectionFailureType.CANCELLED, "Connection was cancelled");
        }
    }

    private MinaRoutedSshResources authenticateRoute(
            List<ConnectionHop> route, HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress, ConnectionSettings settings) throws ConnectionException {
        MinaRoutedSshResources resources = new MinaRoutedSshResources();
        try {
            ClientSession previous = null;
            for (int index = 0; index < route.size(); index++) {
                ConnectionHop hop = route.get(index);
                ConnectionProfile profile = hop.profile();
                java.net.SocketAddress connectAddress;
                if (previous == null) {
                    connectAddress = new java.net.InetSocketAddress(profile.host().value(), profile.port().value());
                } else {
                    try {
                        ExplicitPortForwardingTracker tunnel = previous.createLocalPortForwardingTracker(
                                SshdSocketAddress.LOCALHOST_ADDRESS,
                                new SshdSocketAddress(profile.host().value(), profile.port().value()));
                        resources.addTunnel(tunnel);
                        connectAddress = tunnel.getBoundAddress().toInetSocketAddress();
                    } catch (IOException failure) {
                        throw new ConnectionException(ConnectionFailureType.CONNECTION_FAILED,
                                "The jump host route tunnel was rejected before connecting to " + hop.name() + ".",
                                failure);
                    }
                }

                SshClient client = SshClient.setUpDefaultClient();
                configureClient(client, settings, profile.legacyAlgorithmsEnabled());
                StrictHostKeyVerifier verifier = new StrictHostKeyVerifier(
                        profile.host(), profile.port(), knownHosts, hostKeyPrompt);
                client.setServerKeyVerifier(verifier);
                ClientSession session = null;
                try {
                    progress.accept(ConnectionProgress.NETWORK_CONNECTING);
                    client.start();
                    session = client.connect(profile.username().value(), connectAddress)
                            .verify(settings.connectTimeout()).getSession();
                    resources.add(client, session);
                    progress.accept(ConnectionProgress.HOST_KEY_VALIDATING);
                    waitForKeyExchange(session, verifier, settings.connectTimeout());
                    progress.accept(profile.authenticationType() == AuthenticationType.PASSWORD
                            ? ConnectionProgress.PASSWORD_AUTHENTICATING
                            : ConnectionProgress.PRIVATE_KEY_AUTHENTICATING);
                    Runnable removeIdentity = configureAuthentication(profile, hop.credentials(), session);
                    try {
                        session.auth().verify(settings.authenticationTimeout());
                    } catch (IOException failure) {
                        if (verifier.failure() != null) throw verifier.failure();
                        throw new ConnectionException(ConnectionFailureType.AUTHENTICATION_FAILED,
                                "Authentication failed for " + hop.name() + ".", failure);
                    } finally {
                        removeIdentity.run();
                    }
                    previous = session;
                } catch (ConnectionException failure) {
                    throw failure;
                } catch (IOException | RuntimeException failure) {
                    if (session == null) client.stop();
                    if (verifier.failure() != null) throw verifier.failure();
                    String role = index == route.size() - 1 ? "target" : "jump host";
                    throw new ConnectionException(ConnectionFailureType.CONNECTION_FAILED,
                            "Unable to connect to " + role + " " + hop.name() + ".", failure);
                }
            }
            return resources;
        } catch (ConnectionException | RuntimeException failure) {
            resources.close();
            throw failure;
        }
    }

    @Override
    public void testConnection(
            ConnectionProfile profile,
            Credentials credentials,
            HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress,
            ConnectionSettings settings) throws ConnectionException {
        testConnection(List.of(new ConnectionHop("target", profile, credentials)), hostKeyPrompt, progress, settings);
    }

    @Override
    public void testConnection(
            List<ConnectionHop> route,
            HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress,
            ConnectionSettings settings) throws ConnectionException {
        validateRoute(route, hostKeyPrompt, progress, settings);
        try (MinaRoutedSshResources ignored = authenticateRoute(route, hostKeyPrompt, progress, settings)) {
            progress.accept(ConnectionProgress.SESSION_ESTABLISHED);
        }
    }

    @Override
    public SftpConnection connectSftp(
            ConnectionProfile profile, Credentials credentials, HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress) throws ConnectionException {
        return connectSftp(profile, credentials, hostKeyPrompt, progress, ConnectionSettings.defaults());
    }

    @Override
    public SftpConnection connectSftp(
            ConnectionProfile profile, Credentials credentials, HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress, ConnectionSettings settings) throws ConnectionException {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(credentials, "credentials");
        Objects.requireNonNull(hostKeyPrompt, "hostKeyPrompt");
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(settings, "settings");
        if (closed.get()) throw new ConnectionException(ConnectionFailureType.SESSION_CLOSED, "SSH connector is closed");
        SshClient client = SshClient.setUpDefaultClient();
        configureClient(client, settings, profile.legacyAlgorithmsEnabled());
        StrictHostKeyVerifier verifier = new StrictHostKeyVerifier(profile.host(), profile.port(), knownHosts, hostKeyPrompt);
        client.setServerKeyVerifier(verifier);
        ClientSession session = null;
        String stage = "network-or-key-exchange";
        try {
            progress.accept(ConnectionProgress.NETWORK_CONNECTING);
            client.start();
            session = client.connect(profile.username().value(), profile.host().value(), profile.port().value())
                    .verify(settings.connectTimeout()).getSession();
            progress.accept(ConnectionProgress.HOST_KEY_VALIDATING);
            waitForKeyExchange(session, verifier, settings.connectTimeout());
            progress.accept(profile.authenticationType() == AuthenticationType.PASSWORD
                    ? ConnectionProgress.PASSWORD_AUTHENTICATING : ConnectionProgress.PRIVATE_KEY_AUTHENTICATING);
            stage = "authentication";
            Runnable removeIdentity = configureAuthentication(profile, credentials, session);
            try {
                session.auth().verify(settings.authenticationTimeout());
            } catch (IOException failure) {
                if (verifier.failure() != null) throw verifier.failure();
                throw new ConnectionException(ConnectionFailureType.AUTHENTICATION_FAILED,
                        "Authentication failed. Check the username and credentials.", failure);
            } finally {
                removeIdentity.run();
            }
            MinaSftpConnection connection = new MinaSftpConnection(client, session,
                    () -> activeSftpConnections.removeIf(existing -> existing == null || !existing.isOpen()));
            activeSftpConnections.add(connection);
            progress.accept(ConnectionProgress.SESSION_ESTABLISHED);
            return connection;
        } catch (ConnectionException expected) {
            closeFailedConnection(null, session, client);
            throw expected;
        } catch (IOException | RuntimeException failure) {
            closeFailedConnection(null, session, client);
            if (verifier.failure() != null) throw verifier.failure();
            logFailure(stage, failure);
            throw new ConnectionException(Thread.currentThread().isInterrupted()
                    ? ConnectionFailureType.CANCELLED : ConnectionFailureType.CONNECTION_FAILED,
                    safeConnectionMessage(ConnectionFailureType.CONNECTION_FAILED), failure);
        }
    }

    @Override
    public TerminalConnection connect(
            ConnectionProfile profile,
            Credentials credentials,
            HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress) throws ConnectionException {
        return connect(profile, credentials, hostKeyPrompt, progress, ConnectionSettings.defaults());
    }

    @Override
    public TerminalConnection connect(
            ConnectionProfile profile,
            Credentials credentials,
            HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress,
            ConnectionSettings settings) throws ConnectionException {
        return connect(profile, credentials, hostKeyPrompt, progress, settings, TerminalPreferences.defaults());
    }

    @Override
    public TerminalConnection connect(
            ConnectionProfile profile,
            Credentials credentials,
            HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress,
            ConnectionSettings settings,
            TerminalPreferences terminalPreferences) throws ConnectionException {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(credentials, "credentials");
        Objects.requireNonNull(hostKeyPrompt, "hostKeyPrompt");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(terminalPreferences, "terminalPreferences");
        if (closed.get()) {
            throw new ConnectionException(ConnectionFailureType.SESSION_CLOSED, "SSH connector is closed");
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new ConnectionException(ConnectionFailureType.CANCELLED, "Connection was cancelled");
        }

        SshClient client = SshClient.setUpDefaultClient();
        configureClient(client, settings, profile.legacyAlgorithmsEnabled());
        Objects.requireNonNull(progress, "progress");
        progress.accept(ConnectionProgress.NETWORK_CONNECTING);
        StrictHostKeyVerifier verifier = new StrictHostKeyVerifier(
                profile.host(), profile.port(), knownHosts, hostKeyPrompt);
        client.setServerKeyVerifier(verifier);
        ClientSession session = null;
        ChannelShell channel = null;
        String stage = "network-or-key-exchange";
        try {
            client.start();
            session = client.connect(
                            profile.username().value(),
                            profile.host().value(),
                            profile.port().value())
                    .verify(settings.connectTimeout())
                    .getSession();
            progress.accept(ConnectionProgress.HOST_KEY_VALIDATING);
            waitForKeyExchange(session, verifier, settings.connectTimeout());

            progress.accept(profile.authenticationType() == AuthenticationType.PASSWORD
                    ? ConnectionProgress.PASSWORD_AUTHENTICATING
                    : ConnectionProgress.PRIVATE_KEY_AUTHENTICATING);
            stage = "authentication";
            Runnable removeAuthenticationIdentity = configureAuthentication(profile, credentials, session);
            try {
                session.auth().verify(settings.authenticationTimeout());
            } catch (IOException authenticationFailure) {
                ConnectionException verifierFailure = verifier.failure();
                if (verifierFailure != null) {
                    throw verifierFailure;
                }
                throw new ConnectionException(
                        ConnectionFailureType.AUTHENTICATION_FAILED,
                        "Authentication failed. Check the username and credentials.",
                        authenticationFailure);
            } finally {
                removeAuthenticationIdentity.run();
            }

            channel = session.createShellChannel();
            stage = "terminal-channel";
            configurePty(session, channel, terminalPreferences);
            channel.setRedirectErrorStream(true);
            channel.open().verify(CHANNEL_TIMEOUT);

            ClientSession connectedSession = session;
            ChannelShell connectedChannel = channel;
            MinaTerminalConnection connection = new MinaTerminalConnection(
                    client,
                    connectedSession,
                    connectedChannel,
                    settings.disconnectIdleTerminal() ? settings.terminalIdleTimeout() : null,
                    () -> activeConnections.removeIf(existing -> existing == null || !existing.isOpen()));
            activeConnections.add(connection);
            progress.accept(ConnectionProgress.SESSION_ESTABLISHED);
            return connection;
        } catch (ConnectionException expected) {
            closeFailedConnection(channel, session, client);
            throw expected;
        } catch (IOException failure) {
            closeFailedConnection(channel, session, client);
            ConnectionException verifierFailure = verifier.failure();
            if (verifierFailure != null) {
                throw verifierFailure;
            }
            logFailure(stage, failure);
            ConnectionFailureType type = Thread.currentThread().isInterrupted()
                    ? ConnectionFailureType.CANCELLED
                    : ConnectionFailureType.CONNECTION_FAILED;
            throw new ConnectionException(type, safeConnectionMessage(type), failure);
        } catch (RuntimeException failure) {
            closeFailedConnection(channel, session, client);
            ConnectionException verifierFailure = verifier.failure();
            if (verifierFailure != null) {
                throw verifierFailure;
            }
            logFailure(stage, failure);
            throw new ConnectionException(
                    ConnectionFailureType.CONNECTION_FAILED,
                    "The SSH connection could not be established.",
                    failure);
        }
    }

    private static void configurePty(
            ClientSession session, ChannelShell channel, TerminalPreferences preferences) {
        channel.setPtyType(preferences.terminalType().ptyName());
        channel.setPtyColumns(preferences.initialColumns());
        channel.setPtyLines(preferences.initialRows());
        String localeVariable = detectRemoteLocaleOverride(session);
        if ("LC_ALL".equals(localeVariable) || "LC_CTYPE".equals(localeVariable)) {
            channel.setEnv(localeVariable, UTF8_LOCALE);
            LOGGER.log(System.Logger.Level.INFO,
                    "Remote character locale is C/POSIX; requesting "
                            + localeVariable + "=" + UTF8_LOCALE + " for the interactive shell");
        } else if ("UNAVAILABLE".equals(localeVariable)) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Remote character locale is C/POSIX, but " + UTF8_LOCALE + " is unavailable; preserving it");
        }
    }

    private static String detectRemoteLocaleOverride(ClientSession session) {
        LocaleProbeOutput output = new LocaleProbeOutput();
        try (ChannelExec probe = session.createExecChannel(REMOTE_LOCALE_PROBE)) {
            probe.setOut(output);
            probe.setErr(OutputStream.nullOutputStream());
            probe.open().verify(LOCALE_PROBE_TIMEOUT);
            probe.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), LOCALE_PROBE_TIMEOUT.toMillis());
            if (probe.isOpen()) {
                probe.close(true);
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Remote locale probe timed out; preserving the server locale");
                return "";
            }
            if (Objects.requireNonNullElse(probe.getExitStatus(), -1) != 0) return "";
            String result = output.text().trim();
            return result.length() <= 16 ? result : "";
        } catch (IOException | RuntimeException failure) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Unable to detect the remote character locale; preserving the server locale", failure);
            return "";
        }
    }

    private static final class LocaleProbeOutput extends OutputStream {
        private final byte[] bytes = new byte[32];
        private int count;

        @Override public void write(int value) {
            if (count < bytes.length) bytes[count++] = (byte) value;
        }

        @Override public void write(byte[] values, int offset, int length) {
            Objects.checkFromIndexSize(offset, length, values.length);
            int accepted = Math.min(length, bytes.length - count);
            if (accepted > 0) {
                System.arraycopy(values, offset, bytes, count, accepted);
                count += accepted;
            }
        }

        String text() {
            return new String(bytes, 0, count, java.nio.charset.StandardCharsets.US_ASCII);
        }
    }

    private static Runnable configureAuthentication(
            ConnectionProfile profile,
            Credentials credentials,
            ClientSession session) throws ConnectionException {
        char[] secret = credentials.copySecret();
        try {
            if (profile.authenticationType() == AuthenticationType.PASSWORD) {
                String password = new String(secret);
                session.addPasswordIdentity(password);
                return () -> session.removePasswordIdentity(password);
            }

            FilePasswordProvider passwordProvider = secret.length == 0
                    ? FilePasswordProvider.EMPTY
                    : FilePasswordProvider.of(new String(secret));
            int loadedKeys = 0;
            List<KeyPair> identities = new ArrayList<>();
            byte[] embeddedKey = credentials instanceof PrivateKeyCredentials privateKey
                    ? privateKey.copyPrivateKey() : null;
            try {
                Iterable<KeyPair> loaded;
                if (embeddedKey != null) {
                    loaded = SecurityUtils.loadKeyPairIdentities(
                            session, NamedResource.ofName("vault-private-key"),
                            new ByteArrayInputStream(embeddedKey), passwordProvider);
                } else {
                    var keyPath = profile.privateKeyPath().orElseThrow(() -> new ConnectionException(
                            ConnectionFailureType.KEY_LOADING_FAILED,
                            "No private key is configured for this connection."));
                    if (!Files.isRegularFile(keyPath) || !Files.isReadable(keyPath)) {
                        throw new ConnectionException(
                                ConnectionFailureType.KEY_LOADING_FAILED,
                                "The selected private key file cannot be read.");
                    }
                    FileKeyPairProvider keyProvider = new FileKeyPairProvider(keyPath);
                    keyProvider.setPasswordFinder(passwordProvider);
                    loaded = keyProvider.loadKeys(session);
                }
                for (KeyPair keyPair : loaded) {
                    session.addPublicKeyIdentity(keyPair);
                    identities.add(keyPair);
                    loadedKeys++;
                }
            } catch (IOException | GeneralSecurityException | RuntimeException failure) {
                identities.forEach(session::removePublicKeyIdentity);
                throw new ConnectionException(
                        ConnectionFailureType.KEY_LOADING_FAILED,
                        "The private key could not be decoded. Check its format and passphrase.",
                        failure);
            } finally {
                if (embeddedKey != null) Arrays.fill(embeddedKey, (byte) 0);
            }
            if (loadedKeys == 0) {
                throw new ConnectionException(
                        ConnectionFailureType.KEY_LOADING_FAILED,
                        "The selected file did not contain a supported private key.");
            }
            return () -> identities.forEach(session::removePublicKeyIdentity);
        } finally {
            Arrays.fill(secret, '\0');
        }
    }

    private static String safeConnectionMessage(ConnectionFailureType type) {
        return type == ConnectionFailureType.CANCELLED
                ? "Connection was cancelled."
                : "Unable to connect to the SSH server. Check the host, port, and network.";
    }

    /**
     * MINA completes the TCP connect future before key exchange has necessarily finished. Do not
     * start authentication until its synchronous server-key verifier has either accepted the key
     * or recorded a failure; otherwise an unknown-host dialog races with password authentication.
     */
    private static void waitForKeyExchange(
            ClientSession session, StrictHostKeyVerifier verifier, Duration connectTimeout)
            throws ConnectionException {
        long deadline = System.nanoTime() + connectTimeout.toNanos();
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw new ConnectionException(ConnectionFailureType.CANCELLED, "Connection was cancelled");
            }
            ConnectionException verifierFailure = verifier.failure();
            if (verifierFailure != null) {
                throw verifierFailure;
            }
            var state = session.getSessionState();
            if (state.contains(ClientSession.ClientSessionEvent.WAIT_AUTH)) {
                return;
            }
            if (state.contains(ClientSession.ClientSessionEvent.CLOSED)) {
                throw new ConnectionException(
                        ConnectionFailureType.CONNECTION_FAILED,
                        "SSH key exchange closed before authentication.");
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) {
                throw new ConnectionException(
                        ConnectionFailureType.CONNECTION_TIMED_OUT,
                        "SSH key exchange timed out.");
            }
            try {
                Thread.sleep(Math.min(25L, Math.max(1L, remaining / 1_000_000L)));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new ConnectionException(ConnectionFailureType.CANCELLED, "Connection was cancelled", interrupted);
            }
        }
    }

    static void configureClient(SshClient client, ConnectionSettings settings) {
        configureClient(client, settings, false);
    }

    static void configureClient(SshClient client, ConnectionSettings settings, boolean legacyAlgorithmsEnabled) {
        // FastRemote credentials are explicit. Do not silently add identities or
        // connection overrides from the user's ~/.ssh directory.
        client.setKeyIdentityProvider(KeyIdentityProvider.EMPTY_KEYS_PROVIDER);
        client.setHostConfigEntryResolver(HostConfigEntryResolver.EMPTY);
        client.addSessionListener(new org.apache.sshd.common.session.SessionListener() {
            @Override
            public void sessionException(org.apache.sshd.common.session.Session session, Throwable failure) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "SSH transport exception; local=" + session.getIoSession().getLocalAddress()
                                + "; remote=" + session.getIoSession().getRemoteAddress(), failure);
            }
        });
        CoreModuleProperties.IO_CONNECT_TIMEOUT.set(client, settings.connectTimeout());
        CoreModuleProperties.AUTH_TIMEOUT.set(client, settings.authenticationTimeout());
        Duration keepAlive = Duration.ofSeconds(settings.keepAliveIntervalSeconds());
        CoreModuleProperties.HEARTBEAT_INTERVAL.set(client, keepAlive);
        if (!keepAlive.isZero()) {
            // Allow transient congestion; inbound channel traffic resets the silence deadline.
            // A silent peer still closes after six ticks (three minutes by default).
            CoreModuleProperties.HEARTBEAT_NO_REPLY_MAX.set(client, 5);
        }
        client.setServiceFactories(List.of(
                org.apache.sshd.client.session.ClientUserAuthServiceFactory.INSTANCE,
                TrafficAwareClientConnectionService.FACTORY));
        org.apache.sshd.sftp.SftpModuleProperties.SFTP_CHANNEL_OPEN_TIMEOUT.set(client, Duration.ofSeconds(120));
        org.apache.sshd.sftp.client.impl.AbstractSftpClient.SFTP_CLIENT_CMD_TIMEOUT.set(client, Duration.ofSeconds(120));
        configureAlgorithmPolicy(client, legacyAlgorithmsEnabled);
    }

    @SuppressWarnings("deprecation")
    private static void configureAlgorithmPolicy(SshClient client, boolean legacyAlgorithmsEnabled) {
        var signatures = withoutNames(client.getSignatureFactories(), Set.of("ssh-rsa"));
        var keyExchanges = withoutNames(client.getKeyExchangeFactories(), Set.of(
                "diffie-hellman-group14-sha1", "diffie-hellman-group-exchange-sha1",
                "diffie-hellman-group1-sha1"));
        var ciphers = withoutNames(client.getCipherFactories(), Set.of(
                "aes128-cbc", "aes192-cbc", "aes256-cbc", "3des-cbc"));
        // Debian 12 / OpenSSH 9.2 still includes the full-length HMAC-SHA1 and
        // encrypt-then-MAC variant in its default server policy. Keep those as
        // mainstream interoperability algorithms; only the truncated 96-bit
        // variant belongs behind the explicit legacy compatibility switch.
        var macs = withoutNames(client.getMacFactories(), Set.of("hmac-sha1-96"));

        if (!legacyAlgorithmsEnabled) {
            client.setSignatureFactories(signatures);
            client.setKeyExchangeFactories(keyExchanges);
            client.setCipherFactories(ciphers);
            client.setMacFactories(macs);
            return;
        }

        if (BuiltinSignatures.rsa.isSupported()) appendDistinct(signatures, BuiltinSignatures.rsa);
        if (BuiltinSignatures.dsa.isSupported()) appendDistinct(signatures, BuiltinSignatures.dsa);
        client.setSignatureFactories(signatures);
        preferLegacyPublicKeySignatures(client, signatures);

        if (BuiltinDHFactories.dhg14.isSupported()) {
            appendDistinct(keyExchanges, ClientBuilder.DH2KEX.apply(BuiltinDHFactories.dhg14));
        }
        if (BuiltinDHFactories.dhgex.isSupported()) {
            appendDistinct(keyExchanges, ClientBuilder.DH2KEX.apply(BuiltinDHFactories.dhgex));
        }
        if (BuiltinDHFactories.dhg1.isSupported()) {
            appendDistinct(keyExchanges, ClientBuilder.DH2KEX.apply(BuiltinDHFactories.dhg1));
        }
        client.setKeyExchangeFactories(keyExchanges);

        if (BuiltinCiphers.aes128cbc.isSupported()) appendDistinct(ciphers, BuiltinCiphers.aes128cbc);
        if (BuiltinCiphers.aes192cbc.isSupported()) appendDistinct(ciphers, BuiltinCiphers.aes192cbc);
        if (BuiltinCiphers.aes256cbc.isSupported()) appendDistinct(ciphers, BuiltinCiphers.aes256cbc);
        if (BuiltinCiphers.tripledescbc.isSupported()) appendDistinct(ciphers, BuiltinCiphers.tripledescbc);
        client.setCipherFactories(ciphers);

        if (BuiltinMacs.hmacsha1.isSupported()) appendDistinct(macs, BuiltinMacs.hmacsha1);
        if (BuiltinMacs.hmacsha1etm.isSupported()) appendDistinct(macs, BuiltinMacs.hmacsha1etm);
        if (BuiltinMacs.hmacsha196.isSupported()) appendDistinct(macs, BuiltinMacs.hmacsha196);
        client.setMacFactories(macs);
    }

    private static void preferLegacyPublicKeySignatures(
            SshClient client, List<NamedFactory<Signature>> signatures) {
        ArrayList<NamedFactory<Signature>> authenticationSignatures = new ArrayList<>(signatures);
        // MINA stops trying the remaining algorithms for an RSA identity when a server advertises
        // RSA-SHA2 but rejects that authentication attempt. Some old per-user/server policies do
        // exactly that while accepting only ssh-rsa, so explicit legacy mode must try ssh-rsa first.
        // Keep the transport/host-key signature order unchanged to avoid an unrelated downgrade.
        moveNamedFactoryFirst(authenticationSignatures, BuiltinSignatures.rsa.getName());

        List<UserAuthFactory> configuredFactories = client.getUserAuthFactories();
        List<UserAuthFactory> sourceFactories = configuredFactories == null || configuredFactories.isEmpty()
                ? SshClient.DEFAULT_USER_AUTH_FACTORIES
                : configuredFactories;
        ArrayList<UserAuthFactory> authenticationFactories = new ArrayList<>(sourceFactories.size());
        for (UserAuthFactory factory : sourceFactories) {
            authenticationFactories.add(UserAuthPublicKeyFactory.NAME.equals(factory.getName())
                    ? new UserAuthPublicKeyFactory(authenticationSignatures)
                    : factory);
        }
        client.setUserAuthFactories(authenticationFactories);
    }

    private static <F extends org.apache.sshd.common.NamedResource> void moveNamedFactoryFirst(
            List<F> factories, String name) {
        for (int index = 0; index < factories.size(); index++) {
            if (factories.get(index).getName().equals(name)) {
                if (index > 0) factories.add(0, factories.remove(index));
                return;
            }
        }
    }

    private static <F extends org.apache.sshd.common.NamedResource> ArrayList<F> withoutNames(
            List<F> factories, Set<String> excludedNames) {
        ArrayList<F> result = new ArrayList<>(factories.size());
        factories.stream().filter(factory -> !excludedNames.contains(factory.getName())).forEach(result::add);
        return result;
    }

    private static <F extends org.apache.sshd.common.NamedResource> void appendDistinct(List<F> factories, F legacy) {
        if (legacy != null && factories.stream().noneMatch(factory -> factory.getName().equals(legacy.getName()))) {
            factories.add(legacy);
        }
    }

    private void closeIdleTerminals() {
        long now = System.nanoTime();
        for (TerminalConnection connection : activeConnections) {
            if (connection instanceof MinaTerminalConnection mina) {
                mina.closeIfIdle(now);
            }
        }
    }

    private static void logFailure(String stage, Throwable failure) {
        Throwable cause = failure.getCause();
        LOGGER.log(System.Logger.Level.WARNING,
                "SSH connection failed during {0}; exception={1}; cause={2}",
                stage,
                failure.getClass().getSimpleName(),
                cause == null ? "none" : cause.getClass().getSimpleName());
    }

    private static void closeFailedConnection(ChannelShell channel, ClientSession session, SshClient client) {
        if (channel != null) {
            channel.close(true);
        }
        if (session != null) {
            session.close(true);
        }
        client.stop();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        activeConnections.forEach(TerminalConnection::close);
        activeConnections.clear();
        activeSftpConnections.forEach(SftpConnection::close);
        activeSftpConnections.clear();
        idleMonitor.shutdownNow();
    }
}
