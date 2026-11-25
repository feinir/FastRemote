// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.connection.ConnectionProfile;
import com.ynpe.fastremote.domain.connection.Credentials;
import com.ynpe.fastremote.domain.connection.ConnectionHop;
import com.ynpe.fastremote.domain.security.HostKeyPrompt;
import java.util.concurrent.CompletionStage;
import java.util.Optional;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import com.ynpe.fastremote.domain.connection.ConnectionProgress;
import com.ynpe.fastremote.domain.settings.TerminalPreferences;
import com.ynpe.fastremote.domain.settings.ConnectionSettings;
import com.ynpe.fastremote.domain.settings.SftpSettings;

public interface ConnectionWorkflow extends LocalFileWorkflow, AutoCloseable {
    CompletionStage<Optional<ConnectionProfile>> loadLastProfile();

    ConnectionAttempt connect(
            ConnectionProfile profile,
            Credentials credentials,
            HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress);

    ConnectionAttempt connect(
            List<ConnectionHop> route,
            HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress);

    ConnectionTestAttempt testConnection(
            ConnectionProfile profile,
            Credentials credentials,
            HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress);

    ConnectionTestAttempt testConnection(
            List<ConnectionHop> route,
            HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress);

    SftpConnectionAttempt connectSftp(
            ConnectionProfile profile,
            Credentials credentials,
            HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress);

    SftpConnectionAttempt connectSftp(
            List<ConnectionHop> route,
            HostKeyPrompt hostKeyPrompt,
            Consumer<ConnectionProgress> progress);

    void updateTerminalPreferences(TerminalPreferences preferences);

    void updateConnectionSettings(ConnectionSettings settings);

    default void updateSftpSettings(SftpSettings settings) {
        // Workflows without SFTP deletion support have no runtime setting to update.
    }

    @Override
    void close();
}
