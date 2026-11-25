// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.connection;

import com.ynpe.fastremote.domain.security.HostKeyPrompt;
import com.ynpe.fastremote.domain.terminal.TerminalConnection;
import com.ynpe.fastremote.domain.transfer.SftpConnection;
import java.util.Objects;
import java.util.List;
import java.util.function.Consumer;
import com.ynpe.fastremote.domain.settings.ConnectionSettings;
import com.ynpe.fastremote.domain.settings.TerminalPreferences;

public interface SessionConnector extends AutoCloseable {
    TerminalConnection connect(
            ConnectionProfile profile,
            Credentials credentials,
            HostKeyPrompt hostKeyPrompt) throws ConnectionException;

    default TerminalConnection connect(
            ConnectionProfile profile,
            Credentials credentials,
            HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress) throws ConnectionException {
        Objects.requireNonNull(progress, "progress").accept(ConnectionProgress.NETWORK_CONNECTING);
        return connect(profile, credentials, hostKeyPrompt);
    }

    default TerminalConnection connect(
            ConnectionProfile profile,
            Credentials credentials,
            HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress,
            ConnectionSettings settings) throws ConnectionException {
        Objects.requireNonNull(settings, "settings");
        return connect(profile, credentials, hostKeyPrompt, progress);
    }

    default TerminalConnection connect(
            ConnectionProfile profile,
            Credentials credentials,
            HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress,
            ConnectionSettings settings,
            TerminalPreferences terminalPreferences) throws ConnectionException {
        Objects.requireNonNull(terminalPreferences, "terminalPreferences");
        return connect(profile, credentials, hostKeyPrompt, progress, settings);
    }

    default TerminalConnection connect(
            List<ConnectionHop> route,
            HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress,
            ConnectionSettings settings) throws ConnectionException {
        Objects.requireNonNull(route, "route");
        if (route.size() != 1) throw new UnsupportedOperationException("This connector does not support jump hosts");
        ConnectionHop target = route.getFirst();
        return connect(target.profile(), target.credentials(), hostKeyPrompt, progress, settings);
    }

    default TerminalConnection connect(
            List<ConnectionHop> route,
            HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress,
            ConnectionSettings settings,
            TerminalPreferences terminalPreferences) throws ConnectionException {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(terminalPreferences, "terminalPreferences");
        return connect(route, hostKeyPrompt, progress, settings);
    }

    /**
     * Verifies network reachability, host identity and authentication without opening a shell or
     * SFTP subsystem. Implementations should override this when they can authenticate without
     * creating an interactive channel.
     */
    default void testConnection(
            ConnectionProfile profile,
            Credentials credentials,
            HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress,
            ConnectionSettings settings) throws ConnectionException {
        try (TerminalConnection ignored = connect(profile, credentials, hostKeyPrompt, progress, settings)) {
            // The compatibility fallback closes the connection immediately after it is established.
        }
    }

    default void testConnection(
            List<ConnectionHop> route,
            HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress,
            ConnectionSettings settings) throws ConnectionException {
        Objects.requireNonNull(route, "route");
        if (route.size() != 1) throw new UnsupportedOperationException("This connector does not support jump hosts");
        ConnectionHop target = route.getFirst();
        testConnection(target.profile(), target.credentials(), hostKeyPrompt, progress, settings);
    }

    default SftpConnection connectSftp(
            ConnectionProfile profile,
            Credentials credentials,
            HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress) throws ConnectionException {
        throw new UnsupportedOperationException("This connector does not support SFTP-only connections");
    }

    default SftpConnection connectSftp(
            ConnectionProfile profile,
            Credentials credentials,
            HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress,
            ConnectionSettings settings) throws ConnectionException {
        Objects.requireNonNull(settings, "settings");
        return connectSftp(profile, credentials, hostKeyPrompt, progress);
    }

    default SftpConnection connectSftp(
            List<ConnectionHop> route,
            HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress,
            ConnectionSettings settings) throws ConnectionException {
        Objects.requireNonNull(route, "route");
        if (route.size() != 1) throw new UnsupportedOperationException("This connector does not support jump hosts");
        ConnectionHop target = route.getFirst();
        return connectSftp(target.profile(), target.credentials(), hostKeyPrompt, progress, settings);
    }

    @Override
    void close();
}
