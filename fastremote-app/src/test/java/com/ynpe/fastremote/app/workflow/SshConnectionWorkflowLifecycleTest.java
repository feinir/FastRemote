// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.connection.AuthenticationType;
import com.ynpe.fastremote.domain.connection.ConnectionException;
import com.ynpe.fastremote.domain.connection.ConnectionFailureType;
import com.ynpe.fastremote.domain.connection.ConnectionProfile;
import com.ynpe.fastremote.domain.connection.Credentials;
import com.ynpe.fastremote.domain.connection.Host;
import com.ynpe.fastremote.domain.connection.PasswordCredentials;
import com.ynpe.fastremote.domain.connection.Port;
import com.ynpe.fastremote.domain.connection.SessionConnector;
import com.ynpe.fastremote.domain.connection.Username;
import com.ynpe.fastremote.domain.profile.ConnectionProfileRepository;
import com.ynpe.fastremote.domain.security.HostKeyPrompt;
import com.ynpe.fastremote.domain.terminal.TerminalConnection;
import com.ynpe.fastremote.domain.transfer.RemoteFileService;
import com.ynpe.fastremote.domain.transfer.SftpConnection;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.ynpe.fastremote.domain.settings.ConnectionSettings;
import com.ynpe.fastremote.domain.settings.TerminalPreferences;
import com.ynpe.fastremote.domain.settings.TerminalType;
import org.junit.jupiter.api.Test;

class SshConnectionWorkflowLifecycleTest {
    @Test
    void cancellingAsyncFileOperationInterruptsItsSubmittedTask() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        RemoteFileService files = (RemoteFileService) Proxy.newProxyInstance(
                RemoteFileService.class.getClassLoader(), new Class<?>[]{RemoteFileService.class},
                (proxy, method, arguments) -> {
                    if (!method.getName().equals("uploadTree")) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                    started.countDown();
                    try {
                        while (true) Thread.sleep(100L);
                    } catch (InterruptedException cancellation) {
                        interrupted.countDown();
                        Thread.currentThread().interrupt();
                        throw new IOException("cancelled", cancellation);
                    }
                });
        SftpConnection connection = new SftpConnection() {
            @Override public RemoteFileService remoteFiles() { return files; }
            @Override public boolean isOpen() { return true; }
            @Override public java.util.concurrent.CompletionStage<Void> termination() {
                return new CompletableFuture<>();
            }
            @Override public void close() { }
        };
        SessionConnector connector = new SessionConnector() {
            @Override
            public TerminalConnection connect(
                    ConnectionProfile profile, Credentials credentials, HostKeyPrompt prompt) {
                throw new UnsupportedOperationException();
            }

            @Override
            public SftpConnection connectSftp(
                    ConnectionProfile profile, Credentials credentials, HostKeyPrompt prompt,
                    java.util.function.Consumer<com.ynpe.fastremote.domain.connection.ConnectionProgress> progress) {
                return connection;
            }

            @Override public void close() { }
        };
        var workflow = new SshConnectionWorkflow(connector, new EmptyProfiles(), Runnable::run, () -> true);
        try {
            var handle = workflow.connectSftp(profile("files.example"),
                    new PasswordCredentials("password".toCharArray()), ignored -> null, ignored -> {})
                    .result().toCompletableFuture().get(2, TimeUnit.SECONDS);
            CompletableFuture<Void> operation = handle.remoteFiles().uploadTree(
                    Path.of("source"), "/target", () -> { }, (total, transferred) -> { },
                    conflict -> com.ynpe.fastremote.domain.transfer.TransferConflictChoice.CANCEL)
                    .toCompletableFuture();
            assertTrue(started.await(2, TimeUnit.SECONDS));

            operation.cancel(true);

            assertTrue(interrupted.await(2, TimeUnit.SECONDS));
            assertTrue(operation.isCancelled());
        } finally {
            workflow.close();
        }
    }

    @Test
    void deletionHasIndependentWorkersWhenFileExecutorIsSaturatedAndCanBeCancelled() throws Exception {
        CountDownLatch uploadsStarted = new CountDownLatch(32);
        CountDownLatch releaseUploads = new CountDownLatch(1);
        CountDownLatch deleteStarted = new CountDownLatch(1);
        CountDownLatch deleteCancelled = new CountDownLatch(1);
        RemoteFileService files = (RemoteFileService) Proxy.newProxyInstance(
                RemoteFileService.class.getClassLoader(), new Class<?>[]{RemoteFileService.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("uploadTree")) {
                        uploadsStarted.countDown();
                        releaseUploads.await(10, TimeUnit.SECONDS);
                    } else if (method.getName().equals("delete")) {
                        deleteStarted.countDown();
                        try { new CountDownLatch(1).await(10, TimeUnit.SECONDS); }
                        catch (InterruptedException interrupted) {
                            deleteCancelled.countDown();
                            Thread.currentThread().interrupt();
                            throw new IOException("fixture cancelled", interrupted);
                        }
                    } else throw new UnsupportedOperationException(method.getName());
                    return null;
                });
        SftpConnection connection = new SftpConnection() {
            @Override public RemoteFileService remoteFiles() { return files; }
            @Override public boolean isOpen() { return true; }
            @Override public java.util.concurrent.CompletionStage<Void> termination() { return new CompletableFuture<>(); }
            @Override public void close() { }
        };
        SessionConnector connector = new SessionConnector() {
            @Override public TerminalConnection connect(ConnectionProfile profile, Credentials credentials, HostKeyPrompt prompt) {
                throw new UnsupportedOperationException();
            }
            @Override public SftpConnection connectSftp(ConnectionProfile profile, Credentials credentials,
                    HostKeyPrompt prompt,
                    java.util.function.Consumer<com.ynpe.fastremote.domain.connection.ConnectionProgress> progress) {
                return connection;
            }
            @Override public void close() { }
        };
        var workflow = new SshConnectionWorkflow(connector, new EmptyProfiles(), Runnable::run, () -> true);
        try {
            var handle = workflow.connectSftp(profile("delete-fixture.example"),
                    new PasswordCredentials("fixture".toCharArray()), ignored -> null, ignored -> {})
                    .result().toCompletableFuture().get(2, TimeUnit.SECONDS);
            var uploads = new java.util.ArrayList<CompletableFuture<Void>>();
            for (int index = 0; index < 32; index++) uploads.add(handle.remoteFiles().uploadTree(
                    Path.of("fixture"), "/fixture", () -> {}, (total, copied) -> {},
                    ignored -> com.ynpe.fastremote.domain.transfer.TransferConflictChoice.CANCEL).toCompletableFuture());
            assertTrue(uploadsStarted.await(5, TimeUnit.SECONDS));
            CompletableFuture<Void> deletion = handle.remoteFiles().delete("/fixture-delete").toCompletableFuture();
            assertTrue(deleteStarted.await(2, TimeUnit.SECONDS));
            deletion.cancel(true);
            assertTrue(deleteCancelled.await(2, TimeUnit.SECONDS));
            releaseUploads.countDown();
            CompletableFuture.allOf(uploads.toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
        } finally {
            releaseUploads.countDown();
            workflow.close();
        }
    }

    @Test
    void forwardsCurrentGlobalConnectionAndTerminalSettingsToEachNewConnection() throws Exception {
        AtomicReference<ConnectionSettings> received = new AtomicReference<>();
        AtomicReference<TerminalPreferences> receivedTerminal = new AtomicReference<>();
        CountDownLatch called = new CountDownLatch(1);
        SessionConnector connector = new SessionConnector() {
            @Override
            public TerminalConnection connect(
                    ConnectionProfile profile, Credentials credentials, HostKeyPrompt prompt)
                    throws ConnectionException {
                throw new UnsupportedOperationException();
            }

            @Override
            public TerminalConnection connect(
                    ConnectionProfile profile, Credentials credentials, HostKeyPrompt prompt,
                    java.util.function.Consumer<com.ynpe.fastremote.domain.connection.ConnectionProgress> progress,
                    ConnectionSettings settings, TerminalPreferences terminalPreferences) throws ConnectionException {
                received.set(settings);
                receivedTerminal.set(terminalPreferences);
                called.countDown();
                throw new ConnectionException(ConnectionFailureType.CONNECTION_FAILED, "fixture");
            }

            @Override
            public void close() {
            }
        };
        ConnectionSettings configured = new ConnectionSettings(31, 47, 19, true, 55);
        TerminalPreferences defaults = TerminalPreferences.defaults();
        TerminalPreferences configuredTerminal = new TerminalPreferences(
                defaults.colorScheme(), defaults.fontFamily(), defaults.fontSize(), defaults.cursorShape(),
                defaults.cursorBlink(), defaults.customColorPalette(), defaults.importedColorPalettes(),
                TerminalType.ANSI, 140, 46);
        var workflow = new SshConnectionWorkflow(connector, new EmptyProfiles(), Runnable::run, () -> true);
        workflow.updateConnectionSettings(configured);
        workflow.updateTerminalPreferences(configuredTerminal);
        try {
            workflow.connect(profile("configured.example"),
                    new PasswordCredentials("password".toCharArray()), ignored -> null, ignored -> {});

            assertTrue(called.await(2, TimeUnit.SECONDS));
            assertEquals(configured, received.get());
            assertEquals(configuredTerminal, receivedTerminal.get());
        } finally {
            workflow.close();
        }
    }

    @Test
    void routesConnectionProgressThroughTheUiDispatcher() throws Exception {
        CountDownLatch progressReceived = new CountDownLatch(1);
        AtomicBoolean insideDispatcher = new AtomicBoolean();
        AtomicBoolean callbackWasDispatched = new AtomicBoolean();
        SessionConnector connector = new SessionConnector() {
            @Override
            public TerminalConnection connect(
                    ConnectionProfile profile, Credentials credentials, HostKeyPrompt prompt)
                    throws ConnectionException {
                throw new UnsupportedOperationException();
            }

            @Override
            public TerminalConnection connect(
                    ConnectionProfile profile,
                    Credentials credentials,
                    HostKeyPrompt prompt,
                    java.util.function.Consumer<com.ynpe.fastremote.domain.connection.ConnectionProgress> progress)
                    throws ConnectionException {
                progress.accept(com.ynpe.fastremote.domain.connection.ConnectionProgress.NETWORK_CONNECTING);
                throw new ConnectionException(ConnectionFailureType.AUTHENTICATION_FAILED, "rejected");
            }

            @Override
            public void close() {
            }
        };
        var workflow = new SshConnectionWorkflow(connector, new EmptyProfiles(), runnable -> {
            insideDispatcher.set(true);
            try {
                runnable.run();
            } finally {
                insideDispatcher.set(false);
            }
        }, () -> true);
        try {
            var attempt = workflow.connect(profile("host.example"),
                    new PasswordCredentials("password".toCharArray()), ignored -> null, ignored -> {
                        callbackWasDispatched.set(insideDispatcher.get());
                        progressReceived.countDown();
                    });

            assertTrue(progressReceived.await(2, TimeUnit.SECONDS));
            assertTrue(callbackWasDispatched.get());
            assertThrows(java.util.concurrent.CompletionException.class,
                    () -> attempt.result().toCompletableFuture().join());
        } finally {
            workflow.close();
        }
    }

    @Test
    void connectionTestAuthenticatesWithoutCreatingATerminalAndClearsCredentials() {
        AtomicBoolean tested = new AtomicBoolean();
        AtomicBoolean openedTerminal = new AtomicBoolean();
        AtomicBoolean progressDispatched = new AtomicBoolean();
        AtomicBoolean insideDispatcher = new AtomicBoolean();
        SessionConnector connector = new SessionConnector() {
            @Override
            public TerminalConnection connect(
                    ConnectionProfile profile, Credentials credentials, HostKeyPrompt prompt) {
                openedTerminal.set(true);
                throw new UnsupportedOperationException();
            }

            @Override
            public void testConnection(
                    ConnectionProfile profile,
                    Credentials credentials,
                    HostKeyPrompt prompt,
                    java.util.function.Consumer<com.ynpe.fastremote.domain.connection.ConnectionProgress> progress,
                    ConnectionSettings settings) {
                tested.set(true);
                progress.accept(com.ynpe.fastremote.domain.connection.ConnectionProgress.SESSION_ESTABLISHED);
            }

            @Override public void close() { }
        };
        var workflow = new SshConnectionWorkflow(connector, new EmptyProfiles(), runnable -> {
            insideDispatcher.set(true);
            try {
                runnable.run();
            } finally {
                insideDispatcher.set(false);
            }
        }, () -> true);
        PasswordCredentials credentials = new PasswordCredentials("secret".toCharArray());
        try {
            var attempt = workflow.testConnection(profile("test.example"), credentials,
                    ignored -> null, ignored -> progressDispatched.set(insideDispatcher.get()));

            attempt.result().toCompletableFuture().join();

            assertTrue(tested.get());
            assertTrue(progressDispatched.get());
            assertTrue(!openedTerminal.get());
            assertThrows(IllegalStateException.class, credentials::copySecret);
        } finally {
            workflow.close();
        }
    }

    @Test
    void runningConnectionTestCanBeCancelledIdempotentlyAndClearsCredentials() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        SessionConnector connector = new SessionConnector() {
            @Override
            public TerminalConnection connect(
                    ConnectionProfile profile, Credentials credentials, HostKeyPrompt prompt) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void testConnection(
                    ConnectionProfile profile,
                    Credentials credentials,
                    HostKeyPrompt prompt,
                    java.util.function.Consumer<com.ynpe.fastremote.domain.connection.ConnectionProgress> progress,
                    ConnectionSettings settings) throws ConnectionException {
                started.countDown();
                try {
                    while (true) Thread.sleep(100L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new ConnectionException(ConnectionFailureType.CANCELLED, "cancelled", interrupted);
                }
            }

            @Override public void close() { }
        };
        var workflow = new SshConnectionWorkflow(connector, new EmptyProfiles(), Runnable::run, () -> true);
        PasswordCredentials credentials = new PasswordCredentials("secret".toCharArray());
        try {
            var attempt = workflow.testConnection(profile("cancel.example"), credentials,
                    ignored -> null, ignored -> {});
            assertTrue(started.await(2, TimeUnit.SECONDS));

            attempt.cancel();
            attempt.cancel();

            assertThrows(java.util.concurrent.CancellationException.class,
                    () -> attempt.result().toCompletableFuture().join());
            assertThrows(IllegalStateException.class, credentials::copySecret);
        } finally {
            workflow.close();
        }
    }

    @Test
    void pendingConnectionsAreIndependentAndCancellationIsIdempotent() throws Exception {
        BlockingConnector connector = new BlockingConnector();
        var workflow = new SshConnectionWorkflow(connector, new EmptyProfiles(), Runnable::run, () -> true);
        PasswordCredentials firstCredentials = new PasswordCredentials("first".toCharArray());
        PasswordCredentials secondCredentials = new PasswordCredentials("second".toCharArray());
        try {
            var first = workflow.connect(profile("one.example"), firstCredentials, ignored -> null, ignored -> {});
            var second = workflow.connect(profile("two.example"), secondCredentials, ignored -> null, ignored -> {});

            assertTrue(connector.started.await(2, TimeUnit.SECONDS));
            assertEquals(2, connector.connectCount.get());

            first.cancel();
            first.cancel();
            second.cancel();
            assertThrows(java.util.concurrent.CancellationException.class,
                    () -> first.result().toCompletableFuture().join());
            assertThrows(java.util.concurrent.CancellationException.class,
                    () -> second.result().toCompletableFuture().join());
        } finally {
            workflow.close();
        }

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (connector.cancelled.get() < 2 && System.nanoTime() < deadline) Thread.onSpinWait();
        assertEquals(2, connector.cancelled.get());
        assertThrows(IllegalStateException.class, firstCredentials::copySecret);
        assertThrows(IllegalStateException.class, secondCredentials::copySecret);
    }

    @Test
    void workflowCloseCancelsEveryPendingConnection() throws Exception {
        BlockingConnector connector = new BlockingConnector();
        var workflow = new SshConnectionWorkflow(connector, new EmptyProfiles(), Runnable::run, () -> true);
        var first = workflow.connect(profile("one.example"),
                new PasswordCredentials("first".toCharArray()), ignored -> null, ignored -> {});
        var second = workflow.connect(profile("two.example"),
                new PasswordCredentials("second".toCharArray()), ignored -> null, ignored -> {});
        assertTrue(connector.started.await(2, TimeUnit.SECONDS));

        workflow.close();

        assertThrows(java.util.concurrent.CancellationException.class,
                () -> first.result().toCompletableFuture().join());
        assertThrows(java.util.concurrent.CancellationException.class,
                () -> second.result().toCompletableFuture().join());
    }

    private static ConnectionProfile profile(String host) {
        return new ConnectionProfile(new Host(host), new Port(22), new Username("user"),
                AuthenticationType.PASSWORD, Optional.empty());
    }

    private static final class BlockingConnector implements SessionConnector {
        private final AtomicInteger connectCount = new AtomicInteger();
        private final AtomicInteger cancelled = new AtomicInteger();
        private final CountDownLatch started = new CountDownLatch(2);

        @Override
        public TerminalConnection connect(
                ConnectionProfile profile, Credentials credentials, HostKeyPrompt prompt) throws ConnectionException {
            connectCount.incrementAndGet();
            started.countDown();
            try {
                while (true) Thread.sleep(100L);
            } catch (InterruptedException interrupted) {
                cancelled.incrementAndGet();
                Thread.currentThread().interrupt();
                throw new ConnectionException(ConnectionFailureType.CANCELLED, "cancelled", interrupted);
            }
        }

        @Override
        public void close() {
        }
    }

    private static final class EmptyProfiles implements ConnectionProfileRepository {
        @Override
        public Optional<ConnectionProfile> loadLastProfile() {
            return Optional.empty();
        }

        @Override
        public void saveLastProfile(ConnectionProfile profile) throws IOException {
        }
    }
}
