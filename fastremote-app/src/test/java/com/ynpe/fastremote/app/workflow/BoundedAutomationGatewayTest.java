// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.ai.AiSettings;
import com.ynpe.fastremote.domain.ai.AiSettingsRepository;
import com.ynpe.fastremote.domain.automation.AutomationCapability;
import com.ynpe.fastremote.domain.automation.AutomationAuditEvent;
import com.ynpe.fastremote.domain.automation.AutomationAuditPhase;
import com.ynpe.fastremote.domain.automation.AutomationPermission;
import com.ynpe.fastremote.domain.automation.AutomationPermissions;
import com.ynpe.fastremote.domain.automation.AutomationSource;
import com.ynpe.fastremote.domain.automation.AutomationUiOperationHandler;
import com.ynpe.fastremote.domain.catalog.GroupId;
import com.ynpe.fastremote.domain.catalog.GroupColor;
import com.ynpe.fastremote.domain.catalog.RecentConnection;
import com.ynpe.fastremote.domain.catalog.SavedHost;
import com.ynpe.fastremote.domain.catalog.HostCatalogRepository;
import com.ynpe.fastremote.domain.catalog.HostGroup;
import com.ynpe.fastremote.domain.catalog.HostId;
import com.ynpe.fastremote.domain.connection.AuthenticationType;
import com.ynpe.fastremote.domain.connection.ConnectionException;
import com.ynpe.fastremote.domain.connection.ConnectionProfile;
import com.ynpe.fastremote.domain.connection.Credentials;
import com.ynpe.fastremote.domain.connection.Host;
import com.ynpe.fastremote.domain.connection.Port;
import com.ynpe.fastremote.domain.connection.SessionConnector;
import com.ynpe.fastremote.domain.connection.Username;
import com.ynpe.fastremote.domain.security.HostKeyPrompt;
import com.ynpe.fastremote.domain.snippet.*;
import com.ynpe.fastremote.domain.settings.GlobalSettings;
import com.ynpe.fastremote.domain.settings.GlobalSettingsRepository;
import com.ynpe.fastremote.domain.terminal.TerminalConnection;
import com.ynpe.fastremote.domain.transfer.TransferConflictChoice;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ynpe.fastremote.domain.automation.RemoteTextWrite;
import com.ynpe.fastremote.domain.security.HostKeyDecision;
import com.ynpe.fastremote.domain.security.HostKeyIdentity;
import com.ynpe.fastremote.domain.security.KnownHostRepository;
import com.ynpe.fastremote.mcp.FastRemoteMcpServer;
import com.ynpe.fastremote.transport.ssh.MinaSshSessionConnector;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.sftp.SftpModuleProperties;
import org.apache.sshd.sftp.server.SftpEventListener;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BoundedAutomationGatewayTest {
    @Test
    void conversationAssociationDoesNotRestrictOtherSavedOrOpenQuickHosts() throws Exception {
        var saved = host();
        var associated = new com.ynpe.fastremote.domain.ai.AiHostAssociation(Optional.empty(), "", "ops", "task.example", 2222);
        var quick = new com.ynpe.fastremote.domain.ai.AiHostAssociation(Optional.empty(), "", "ops", "other.example", 2222);
        var defaults = AiSettings.defaults();
        var settings = new MutableAiSettings(new AiSettings(defaults.providers(), defaults.selectedProviderId(),
                defaults.builtInPermissions().with(AutomationCapability.READ_WORKSPACE, AutomationPermission.ALLOW)
                        .with(AutomationCapability.RUN_COMMAND, AutomationPermission.ALLOW),
                defaults.mcpPermissions(), defaults.allowedLocalRoots(), defaults.allowedRemoteRoots(), defaults.mcpServer()));
        var commands = new java.util.ArrayList<HostId>();
        try (var gateway = gateway(saved, settings)) {
            gateway.setUiOperationHandler(new AutomationUiOperationHandler() {
                @Override public java.util.concurrent.CompletionStage<com.ynpe.fastremote.domain.automation.AutomationConversationContext> conversationContext() {
                    return CompletableFuture.completedFuture(new com.ynpe.fastremote.domain.automation.AutomationConversationContext(
                            Optional.of(java.util.UUID.randomUUID()), Optional.of(associated)));
                }
                @Override public java.util.concurrent.CompletionStage<List<com.ynpe.fastremote.domain.automation.AutomationActiveHost>> listActiveHosts() {
                    return CompletableFuture.completedFuture(List.of(new com.ynpe.fastremote.domain.automation.AutomationActiveHost(quick, List.of("quick-tab"))));
                }
                @Override public java.util.concurrent.CompletionStage<Optional<SavedHost>> resolveActiveHost(HostId id) {
                    var profile = new ConnectionProfile(new Host(quick.host()), new Port(quick.port()), new Username(quick.username()), AuthenticationType.PASSWORD, Optional.empty());
                    return CompletableFuture.completedFuture(id.equals(quick.targetId()) ? Optional.of(new SavedHost(id, Optional.empty(), quick.displayName(), profile, Optional.empty(), false, 0, Instant.EPOCH, Instant.EPOCH)) : Optional.empty());
                }
                @Override public java.util.concurrent.CompletionStage<Boolean> runTerminalCommand(HostId id, String command) {
                    commands.add(id);
                    return CompletableFuture.completedFuture(true);
                }
                @Override public java.util.concurrent.CompletionStage<Boolean> uploadInSftp(HostId id, List<Path> sources, String target) {
                    return CompletableFuture.failedFuture(new AssertionError("Unexpected upload"));
                }
                @Override public java.util.concurrent.CompletionStage<Boolean> downloadInSftp(HostId id, String source, Path target) {
                    return CompletableFuture.failedFuture(new AssertionError("Unexpected download"));
                }
            });
            assertEquals(Optional.of(associated), gateway.conversationContext(AutomationSource.BUILT_IN_AI).toCompletableFuture().join().associatedHost());
            assertEquals(quick.targetId(), gateway.listActiveHosts(AutomationSource.BUILT_IN_AI).toCompletableFuture().join().getFirst().id());
            assertTrue(gateway.runTerminalCommand(AutomationSource.BUILT_IN_AI, saved.id(), "pwd").toCompletableFuture().join());
            assertTrue(gateway.runTerminalCommand(AutomationSource.BUILT_IN_AI, quick.targetId(), "pwd").toCompletableFuture().join());
            assertEquals(List.of(saved.id(), quick.targetId()), commands);
            assertEquals(Optional.of(associated), gateway.conversationContext(AutomationSource.BUILT_IN_AI).toCompletableFuture().join().associatedHost());
        }
    }

    @Test
    void backgroundMcpTransfersContributeTheirPayloadBytesToTraffic(@TempDir Path temporary)
            throws Exception {
        Path allowed = Files.createDirectory(temporary.resolve("allowed"));
        Path first = Files.writeString(allowed.resolve("first.bin"), "first");
        Path second = Files.writeString(allowed.resolve("second.bin"), "second");
        Path downloadTarget = allowed.resolve("download.bin");
        SavedHost saved = host().withCredential(Optional.of(
                com.ynpe.fastremote.domain.vault.SecretId.create()));
        CountDownLatch completedSegments = new CountDownLatch(3);
        com.ynpe.fastremote.domain.transfer.RemoteFileService files =
                (com.ynpe.fastremote.domain.transfer.RemoteFileService) java.lang.reflect.Proxy.newProxyInstance(
                        com.ynpe.fastremote.domain.transfer.RemoteFileService.class.getClassLoader(),
                        new Class<?>[] {com.ynpe.fastremote.domain.transfer.RemoteFileService.class},
                        (proxy, method, arguments) -> {
                            if (method.getName().equals("uploadTree")) {
                                var progress = (com.ynpe.fastremote.domain.transfer.TransferProgressListener) arguments[3];
                                progress.updated(10L, 4L);
                                progress.updated(10L, 10L);
                                completedSegments.countDown();
                                return null;
                            }
                            if (method.getName().equals("downloadTree")) {
                                var progress = (com.ynpe.fastremote.domain.transfer.TransferProgressListener) arguments[3];
                                progress.updated(12L, 5L);
                                progress.updated(12L, 12L);
                                completedSegments.countDown();
                                return null;
                            }
                            throw new AssertionError("Unexpected remote file call: " + method.getName());
                        });
        com.ynpe.fastremote.domain.transfer.SftpConnection connection =
                new com.ynpe.fastremote.domain.transfer.SftpConnection() {
                    @Override public com.ynpe.fastremote.domain.transfer.RemoteFileService remoteFiles() {
                        return files;
                    }
                    @Override public boolean isOpen() { return true; }
                    @Override public java.util.concurrent.CompletionStage<Void> termination() {
                        return CompletableFuture.completedFuture(null);
                    }
                    @Override public void close() { }
                };
        SessionConnector connector = new SessionConnector() {
            @Override public TerminalConnection connect(
                    ConnectionProfile profile, Credentials credentials, HostKeyPrompt prompt) {
                throw new AssertionError("terminal connector must not run");
            }
            @Override public com.ynpe.fastremote.domain.transfer.SftpConnection connectSftp(
                    List<com.ynpe.fastremote.domain.connection.ConnectionHop> route,
                    HostKeyPrompt prompt,
                    java.util.function.Consumer<com.ynpe.fastremote.domain.connection.ConnectionProgress> progress,
                    com.ynpe.fastremote.domain.settings.ConnectionSettings connectionSettings) {
                return connection;
            }
            @Override public void close() { }
        };
        AiSettings defaults = AiSettings.defaults();
        AutomationPermissions mcpPermissions = defaults.mcpPermissions()
                .with(AutomationCapability.UPLOAD, AutomationPermission.ALLOW)
                .with(AutomationCapability.DOWNLOAD, AutomationPermission.ALLOW);
        MutableAiSettings settings = new MutableAiSettings(new AiSettings(
                defaults.providers(), defaults.selectedProviderId(), defaults.builtInPermissions(),
                mcpPermissions, List.of(temporary.resolve("missing-before"), allowed,
                        temporary.resolve("missing-after")), List.of("/data"), defaults.maxTransferBytes(),
                defaults.maxToolRounds(), defaults.mcpServer()));
        try (var gateway = new BoundedAutomationGateway(connector, new OneHostRepository(saved),
                ignored -> CompletableFuture.completedFuture(new com.ynpe.fastremote.ui.ResolvedCredential(
                        AuthenticationType.PASSWORD, new char[] {'x'}, null)),
                new GlobalSettingsRepository() {
                    @Override public GlobalSettings load() { return GlobalSettings.defaults(); }
                    @Override public void save(GlobalSettings value) { }
                }, settings, new EmptySnippetRepository())) {
            gateway.uploadIntoDirectory(AutomationSource.MCP, saved.id(), List.of(first, second), "/data")
                    .toCompletableFuture().get(2, TimeUnit.SECONDS);
            gateway.download(AutomationSource.MCP, saved.id(), "/data/download.bin", downloadTarget)
                    .toCompletableFuture().get(2, TimeUnit.SECONDS);

            assertTrue(completedSegments.await(2, TimeUnit.SECONDS));
            assertEquals(20L, gateway.sentBytes());
            assertEquals(12L, gateway.receivedBytes());
        }
    }

    @Test
    void runsEightAutomationOperationsInParallelAndQueuesTheNinth() throws Exception {
        CountDownLatch entered = new CountDownLatch(BoundedAutomationGateway.MAX_CONCURRENT_OPERATIONS);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        HostCatalogRepository blockingHosts = new OneHostRepository(host()) {
            @Override public List<SavedHost> listHosts() {
                int running = active.incrementAndGet();
                maximum.accumulateAndGet(running, Math::max);
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("operations did not release");
                    return List.of(host());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                } finally {
                    active.decrementAndGet();
                }
            }
        };
        AiSettings defaults = AiSettings.defaults();
        AiSettings settings = new AiSettings(defaults.providers(), defaults.selectedProviderId(),
                defaults.builtInPermissions(), defaults.mcpPermissions().with(
                        AutomationCapability.READ_HOST_METADATA, AutomationPermission.ALLOW),
                defaults.allowedLocalRoots(), defaults.allowedRemoteRoots(), defaults.maxTransferBytes(),
                defaults.maxToolRounds(), defaults.mcpServer());
        try (var gateway = new BoundedAutomationGateway(new RejectingConnector(), blockingHosts,
                value -> CompletableFuture.failedFuture(new AssertionError("credential resolver must not run")),
                new GlobalSettingsRepository() {
                    @Override public GlobalSettings load() { return GlobalSettings.defaults(); }
                    @Override public void save(GlobalSettings value) { }
                }, new MutableAiSettings(settings), new EmptySnippetRepository())) {
            List<CompletableFuture<List<com.ynpe.fastremote.domain.automation.AutomationHost>>> operations =
                    java.util.stream.IntStream.range(0, BoundedAutomationGateway.MAX_CONCURRENT_OPERATIONS + 1)
                            .mapToObj(index -> gateway.listHosts(AutomationSource.MCP).toCompletableFuture())
                            .toList();

            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals(BoundedAutomationGateway.MAX_CONCURRENT_OPERATIONS, active.get());
            assertEquals(BoundedAutomationGateway.MAX_CONCURRENT_OPERATIONS, maximum.get());
            release.countDown();
            CompletableFuture.allOf(operations.toArray(CompletableFuture[]::new)).join();
            assertEquals(BoundedAutomationGateway.MAX_CONCURRENT_OPERATIONS, maximum.get());
        } finally {
            release.countDown();
        }
    }

    @Test
    void rejectsEveryAutomationEntryBeforeBusinessLogicWhenLicenseLeaseIsMissing() {
        AiSettings defaults = AiSettings.defaults();
        try (var gateway = new BoundedAutomationGateway(new RejectingConnector(),
                new OneHostRepository(host()),
                value -> CompletableFuture.failedFuture(new AssertionError("credential resolver must not run")),
                new GlobalSettingsRepository() {
                    @Override public GlobalSettings load() { return GlobalSettings.defaults(); }
                    @Override public void save(GlobalSettings value) { }
                }, new MutableAiSettings(defaults), new EmptySnippetRepository(),
                ignored -> CompletableFuture.completedFuture(false))) {
            CompletionException failure = org.junit.jupiter.api.Assertions.assertThrows(
                    CompletionException.class, () -> gateway.listHosts(AutomationSource.BUILT_IN_AI)
                            .toCompletableFuture().join());
            assertInstanceOf(IllegalStateException.class, failure.getCause());
            assertTrue(failure.getCause().getMessage().contains("license"));
        }
    }

    @Test
    void existingGatewayReadsNewLocalRootsAndRejectsAnEmptyConfiguration(@TempDir Path temporary) throws Exception {
        AiSettings defaults = AiSettings.defaults();
        AutomationPermissions permissions = defaults.builtInPermissions()
                .with(AutomationCapability.READ_LOCAL, AutomationPermission.ALLOW);
        MutableAiSettings settings = new MutableAiSettings(new AiSettings(defaults.providers(),
                defaults.selectedProviderId(), permissions, defaults.mcpPermissions(), List.of(),
                defaults.allowedRemoteRoots(), defaults.mcpServer()));
        try (var gateway = gateway(host(), settings)) {
            CompletionException missingRoots = org.junit.jupiter.api.Assertions.assertThrows(
                    CompletionException.class, () -> gateway.listLocal(
                            AutomationSource.BUILT_IN_AI, Optional.empty()).toCompletableFuture().join());
            assertInstanceOf(SecurityException.class, missingRoots.getCause());
            assertEquals("No configured local root exists", missingRoots.getCause().getMessage());

            Path allowed = Files.createDirectory(temporary.resolve("release"));
            settings.save(new AiSettings(defaults.providers(), defaults.selectedProviderId(), permissions,
                    defaults.mcpPermissions(), List.of(allowed), defaults.allowedRemoteRoots(),
                    defaults.mcpServer()));

            var roots = gateway.listLocal(AutomationSource.BUILT_IN_AI, Optional.empty())
                    .toCompletableFuture().join();
            assertEquals(List.of(allowed.toString()),
                    roots.entries().stream().map(value -> value.path()).toList());
        }
    }

    @Test
    void skipsUnavailableRootsForListingAndLocalMutations(@TempDir Path temporary) throws Exception {
        Path allowed = Files.createDirectory(temporary.resolve("allowed"));
        Path notDirectory = Files.writeString(temporary.resolve("not-directory"), "file");
        var settings = localSettings(List.of(temporary.resolve("missing-before"), notDirectory,
                allowed, temporary.resolve("missing-after")));
        try (var gateway = gateway(host(), settings)) {
            for (AutomationSource source : AutomationSource.values()) {
                var roots = gateway.listLocal(source, Optional.empty()).toCompletableFuture().join();
                assertEquals(List.of(allowed.toString()), roots.entries().stream().map(value -> value.path()).toList());
                assertTrue(roots.entries().getFirst().directory());
                Path file = allowed.resolve("notes.txt");
                Path renamed = allowed.resolve("renamed.txt");
                assertTrue(gateway.writeLocalText(source, file, "hello", false).toCompletableFuture().join());
                assertEquals("hello", gateway.readLocalText(source, file).toCompletableFuture().join());
                var listing = gateway.listLocal(source, Optional.of(allowed)).toCompletableFuture().join();
                assertEquals(List.of("notes.txt"), listing.entries().stream().map(value -> value.name()).toList());
                assertTrue(gateway.renameLocal(source, file, renamed).toCompletableFuture().join());
                assertTrue(gateway.deleteLocal(source, renamed, false).toCompletableFuture().join());
                assertLocalFailure(SecurityException.class, "Configured local root itself cannot be renamed or deleted",
                        () -> gateway.deleteLocal(source, allowed, true).toCompletableFuture().join());
                assertLocalFailure(SecurityException.class, "Configured local root itself cannot be renamed or deleted",
                        () -> gateway.renameLocal(source, allowed, temporary.resolve("moved")).toCompletableFuture().join());
            }
        }
    }

    @Test
    void rootsAreRecheckedAfterDirectoriesDisappearAndReturn(@TempDir Path temporary) throws Exception {
        Path first = Files.createDirectory(temporary.resolve("first"));
        Path second = Files.createDirectory(temporary.resolve("second"));
        try (var gateway = gateway(host(), localSettings(List.of(first, second)))) {
            assertEquals(2, gateway.listLocal(AutomationSource.MCP, Optional.empty()).toCompletableFuture().join().entries().size());
            Files.delete(first);
            assertEquals(List.of(second.toString()), gateway.listLocal(AutomationSource.MCP, Optional.empty())
                    .toCompletableFuture().join().entries().stream().map(value -> value.path()).toList());
            assertTrue(gateway.writeLocalText(AutomationSource.MCP, second.resolve("ok.txt"), "ok", false)
                    .toCompletableFuture().join());
            Files.createDirectory(first);
            assertEquals(2, gateway.listLocal(AutomationSource.MCP, Optional.empty()).toCompletableFuture().join().entries().size());
        }
    }

    @Test
    void rejectsUnavailableRootsAndOutsidePathsWithDistinctErrors(@TempDir Path temporary) throws Exception {
        Path missing = temporary.resolve("missing");
        try (var gateway = gateway(host(), localSettings(List.of(missing)))) {
            assertLocalFailure(SecurityException.class, "No configured local root exists",
                    () -> gateway.listLocal(AutomationSource.MCP, Optional.empty()).toCompletableFuture().join());
            assertLocalFailure(SecurityException.class, "No configured local root exists",
                    () -> gateway.writeLocalText(AutomationSource.MCP, temporary.resolve("no.txt"), "no", false)
                            .toCompletableFuture().join());
        }
        Path allowed = Files.createDirectory(temporary.resolve("allowed"));
        Path outside = Files.writeString(temporary.resolve("outside.txt"), "outside");
        try (var gateway = gateway(host(), localSettings(List.of(missing, allowed)))) {
            assertLocalFailure(SecurityException.class, "Path outside allowed local roots: " + outside,
                    () -> gateway.readLocalText(AutomationSource.MCP, outside).toCompletableFuture().join());
            assertLocalFailure(SecurityException.class, "Path outside allowed local roots: " + outside,
                    () -> gateway.writeLocalText(AutomationSource.MCP, outside, "no", true).toCompletableFuture().join());
        }
    }

    @Test
    void rejectsRelativeLocalPathsExplicitly(@TempDir Path temporary) throws Exception {
        try (var gateway = gateway(host(), localSettings(List.of(temporary)))) {
            Path relative = Path.of("data", "cs_dump", "test.txt");
            assertLocalFailure(IllegalArgumentException.class, "Relative path not allowed: " + relative,
                    () -> gateway.readLocalText(AutomationSource.MCP, relative).toCompletableFuture().join());
            assertLocalFailure(IllegalArgumentException.class, "Relative path not allowed: " + relative,
                    () -> gateway.writeLocalText(AutomationSource.MCP, relative, "no", false).toCompletableFuture().join());
            assertLocalFailure(IllegalArgumentException.class, "Relative path not allowed: .",
                    () -> gateway.listLocal(AutomationSource.MCP, Optional.of(Path.of("."))).toCompletableFuture().join());
            var failure = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> gateway.download(AutomationSource.MCP, host().id(), "/data/test.txt", relative));
            assertEquals("Relative path not allowed: " + relative, failure.getMessage());
        }
    }

    @Test
    void unavailableRootsDoNotPermitSymlinkEscapes(@TempDir Path temporary) throws Exception {
        Path allowed = Files.createDirectory(temporary.resolve("allowed"));
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        Path secret = Files.writeString(outside.resolve("secret.txt"), "unchanged");
        Path link;
        try {
            link = Files.createSymbolicLink(allowed.resolve("escape"), outside);
        } catch (UnsupportedOperationException | IOException failure) {
            org.junit.jupiter.api.Assumptions.abort("Symbolic links unavailable: " + failure.getMessage());
            return;
        }
        try (var gateway = gateway(host(), localSettings(List.of(temporary.resolve("missing"), allowed)))) {
            Path escapedFile = link.resolve("secret.txt");
            assertLocalFailure(SecurityException.class, "Path outside allowed local roots: " + escapedFile,
                    () -> gateway.readLocalText(AutomationSource.MCP, escapedFile).toCompletableFuture().join());
            Path escapedTarget = link.resolve("new.txt");
            assertLocalFailure(SecurityException.class, "Path outside allowed local roots: " + escapedTarget,
                    () -> gateway.writeLocalText(AutomationSource.MCP, escapedTarget, "no", false).toCompletableFuture().join());
            assertEquals("unchanged", Files.readString(secret));
            assertFalse(Files.exists(outside.resolve("new.txt")));
        }
        Path rootLink = Files.createSymbolicLink(temporary.resolve("root-link"), allowed);
        Path brokenLink = Files.createSymbolicLink(temporary.resolve("broken-link"), temporary.resolve("missing"));
        try (var gateway = gateway(host(), localSettings(List.of(brokenLink, rootLink)))) {
            var roots = gateway.listLocal(AutomationSource.MCP, Optional.empty()).toCompletableFuture().join();
            assertEquals(List.of(rootLink.toString()), roots.entries().stream().map(value -> value.path()).toList());
            assertTrue(roots.entries().getFirst().directory());
            assertTrue(gateway.writeLocalText(AutomationSource.MCP, rootLink.resolve("ok.txt"), "ok", false)
                    .toCompletableFuture().join());
            assertEquals("ok", Files.readString(allowed.resolve("ok.txt")));
            for (Path root : List.of(rootLink, allowed)) {
                assertLocalFailure(SecurityException.class, "Configured local root itself cannot be renamed or deleted",
                        () -> gateway.deleteLocal(AutomationSource.MCP, root, true).toCompletableFuture().join());
            }
        }
    }

    private static MutableAiSettings localSettings(List<Path> roots) {
        AiSettings defaults = AiSettings.defaults();
        var permissions = defaults.builtInPermissions()
                .with(AutomationCapability.READ_LOCAL, AutomationPermission.ALLOW)
                .with(AutomationCapability.CREATE_LOCAL, AutomationPermission.ALLOW)
                .with(AutomationCapability.EDIT_LOCAL, AutomationPermission.ALLOW)
                .with(AutomationCapability.DELETE_LOCAL, AutomationPermission.ALLOW)
                .with(AutomationCapability.RENAME_LOCAL, AutomationPermission.ALLOW);
        return new MutableAiSettings(new AiSettings(defaults.providers(), defaults.selectedProviderId(),
                permissions, permissions, roots, defaults.allowedRemoteRoots(), defaults.mcpServer()));
    }

    private static void assertLocalFailure(Class<? extends Throwable> type, String message, Runnable action) {
        CompletionException failure = org.junit.jupiter.api.Assertions.assertThrows(CompletionException.class, action::run);
        assertInstanceOf(type, failure.getCause());
        assertEquals(message, failure.getCause().getMessage());
    }

    @Test
    void listsConfiguredRootsAndBoundedLocalDirectoryForUpload(@TempDir Path temporary) throws Exception {
        Path allowed = Files.createDirectory(temporary.resolve("Desktop"));
        Files.writeString(allowed.resolve("notes.txt"), "hello");
        Files.createDirectory(allowed.resolve("folder"));
        AiSettings defaults = AiSettings.defaults();
        MutableAiSettings settings = new MutableAiSettings(new AiSettings(defaults.providers(),
                defaults.selectedProviderId(), defaults.builtInPermissions().with(
                        AutomationCapability.READ_LOCAL, AutomationPermission.ALLOW),
                defaults.mcpPermissions(), List.of(allowed), defaults.allowedRemoteRoots(), defaults.mcpServer()));
        try (var gateway = gateway(host(), settings)) {
            var roots = gateway.listLocal(AutomationSource.BUILT_IN_AI, Optional.empty())
                    .toCompletableFuture().join();
            assertEquals(1, roots.entries().size());
            assertEquals(allowed.toString(), roots.entries().getFirst().path());

            var listing = gateway.listLocal(AutomationSource.BUILT_IN_AI, Optional.of(allowed))
                    .toCompletableFuture().join();
            assertEquals(List.of("folder", "notes.txt"),
                    listing.entries().stream().map(value -> value.name()).toList());
            assertFalse(listing.truncated());
        }
    }

    @Test
    void writesReadsAndDeletesOnlyBelowConfiguredLocalRoot(@TempDir Path temporary) throws Exception {
        Path allowed = Files.createDirectory(temporary.resolve("allowed"));
        AiSettings defaults = AiSettings.defaults();
        var permissions = defaults.builtInPermissions()
                .with(AutomationCapability.READ_LOCAL, AutomationPermission.ALLOW)
                .with(AutomationCapability.CREATE_LOCAL, AutomationPermission.ALLOW)
                .with(AutomationCapability.EDIT_LOCAL, AutomationPermission.ALLOW)
                .with(AutomationCapability.DELETE_LOCAL, AutomationPermission.ALLOW)
                .with(AutomationCapability.RENAME_LOCAL, AutomationPermission.ALLOW);
        MutableAiSettings settings = new MutableAiSettings(new AiSettings(defaults.providers(),
                defaults.selectedProviderId(), permissions, defaults.mcpPermissions(), List.of(allowed),
                List.of("/data"), defaults.mcpServer()));
        var events = new CopyOnWriteArrayList<com.ynpe.fastremote.domain.automation.AutomationAuditEvent>();
        try (var gateway = gateway(host(), settings)) {
            gateway.setAuditHandler(events::add);
            Path file = allowed.resolve("notes.txt");
            assertTrue(gateway.writeLocalText(AutomationSource.BUILT_IN_AI, file, "hello", false)
                    .toCompletableFuture().join());
            assertEquals("hello", gateway.readLocalText(AutomationSource.BUILT_IN_AI, file)
                    .toCompletableFuture().join());
            assertTrue(gateway.deleteLocal(AutomationSource.BUILT_IN_AI, file, false)
                    .toCompletableFuture().join());
            assertFalse(Files.exists(file));
            assertTrue(events.stream().anyMatch(event -> event.phase() == AutomationAuditPhase.SUCCEEDED
                    && event.detail().contains("notes.txt")));

            CompletionException rootDelete = org.junit.jupiter.api.Assertions.assertThrows(CompletionException.class,
                    () -> gateway.deleteLocal(AutomationSource.BUILT_IN_AI, allowed, true)
                            .toCompletableFuture().join());
            assertInstanceOf(SecurityException.class, rootDelete.getCause());
        }
    }

    @Test
    void localCreateEditRenameAndDeleteUseIndependentPermissions(@TempDir Path temporary) throws Exception {
        Path allowed = Files.createDirectory(temporary.resolve("allowed"));
        AiSettings defaults = AiSettings.defaults();
        AutomationPermissions permissions = new AutomationPermissions(Map.of())
                .with(AutomationCapability.CREATE_LOCAL, AutomationPermission.ALLOW);
        MutableAiSettings settings = new MutableAiSettings(new AiSettings(defaults.providers(),
                defaults.selectedProviderId(), permissions, defaults.mcpPermissions(), List.of(allowed),
                defaults.allowedRemoteRoots(), defaults.mcpServer()));
        var events = new CopyOnWriteArrayList<com.ynpe.fastremote.domain.automation.AutomationAuditEvent>();
        try (var gateway = gateway(host(), settings)) {
            gateway.setAuditHandler(events::add);
            Path original = allowed.resolve("notes.txt");
            Path renamed = allowed.resolve("renamed.txt");
            assertTrue(gateway.writeLocalText(AutomationSource.BUILT_IN_AI, original, "one", false)
                    .toCompletableFuture().join());
            assertPermissionDenied(() -> gateway.writeLocalText(
                    AutomationSource.BUILT_IN_AI, original, "two", true).toCompletableFuture().join());
            assertPermissionDenied(() -> gateway.renameLocal(
                    AutomationSource.BUILT_IN_AI, original, renamed).toCompletableFuture().join());
            assertPermissionDenied(() -> gateway.deleteLocal(
                    AutomationSource.BUILT_IN_AI, original, false).toCompletableFuture().join());

            permissions = permissions.with(AutomationCapability.EDIT_LOCAL, AutomationPermission.ALLOW);
            settings.save(withBuiltInPermissions(settings.load(), permissions));
            assertTrue(gateway.writeLocalText(AutomationSource.BUILT_IN_AI, original, "two", true)
                    .toCompletableFuture().join());
            permissions = settings.load().builtInPermissions()
                    .with(AutomationCapability.RENAME_LOCAL, AutomationPermission.ALLOW);
            settings.save(withBuiltInPermissions(settings.load(), permissions));
            assertTrue(gateway.renameLocal(AutomationSource.BUILT_IN_AI, original, renamed)
                    .toCompletableFuture().join());
            permissions = permissions.with(AutomationCapability.DELETE_LOCAL, AutomationPermission.ALLOW);
            settings.save(withBuiltInPermissions(settings.load(), permissions));
            assertTrue(gateway.deleteLocal(AutomationSource.BUILT_IN_AI, renamed, false)
                    .toCompletableFuture().join());

            assertTrue(events.stream().anyMatch(event -> event.capability() == AutomationCapability.CREATE_LOCAL));
            assertTrue(events.stream().anyMatch(event -> event.capability() == AutomationCapability.EDIT_LOCAL));
            assertTrue(events.stream().anyMatch(event -> event.capability() == AutomationCapability.RENAME_LOCAL));
            assertTrue(events.stream().anyMatch(event -> event.capability() == AutomationCapability.DELETE_LOCAL));
        }
    }

    @Test
    void separatesBuiltInAndMcpMetadataPermissions() throws Exception {
        SavedHost host = host();
        MutableAiSettings settings = new MutableAiSettings(AiSettings.defaults());
        AtomicInteger approvals = new AtomicInteger();
        try (var gateway = gateway(host, settings)) {
            gateway.setApprovalHandler(request -> {
                approvals.incrementAndGet();
                assertTrue(request.hostId().isEmpty());
                return CompletableFuture.completedFuture(true);
            });
            assertEquals(1, gateway.listHosts(AutomationSource.BUILT_IN_AI).toCompletableFuture().join().size());
            assertEquals(0, approvals.get());
            assertEquals(1, gateway.listHosts(AutomationSource.MCP).toCompletableFuture().join().size());
            assertEquals(1, approvals.get());
        }
    }

    @Test
    void hostGroupsResolvePathsAndFilterBeforePagination() throws Exception {
        HostGroup parent = HostGroup.root("vps");
        HostGroup child = new HostGroup(GroupId.create(), Optional.of(parent.id()), "asia", 0);
        HostGroup other = HostGroup.root("asia");
        HostGroup empty = HostGroup.root("empty");
        SavedHost first = SavedHost.create("first", Optional.of(parent.id()), host().profile(), 0);
        SavedHost second = SavedHost.create("second", Optional.of(child.id()), host().profile(), 1);
        SavedHost outside = SavedHost.create("outside", Optional.of(other.id()), host().profile(), 2);
        var repository = new OneHostRepository(first) {
            @Override public List<HostGroup> listGroups() { return List.of(parent, child, other, empty); }
            @Override public List<SavedHost> listHosts() { return List.of(outside, first, second, host()); }
        };
        var defaults = AiSettings.defaults();
        var settings = new MutableAiSettings(withBuiltInPermissions(defaults,
                defaults.builtInPermissions().with(AutomationCapability.READ_WORKSPACE, AutomationPermission.ALLOW)));
        try (var gateway = gateway(repository, settings)) {
            var groups = gateway.listHostGroups(AutomationSource.BUILT_IN_AI).toCompletableFuture().join();
            assertEquals(4, groups.size());
            assertEquals("vps / asia", groups.get(1).path());
            var found = gateway.listHosts(AutomationSource.BUILT_IN_AI, Optional.of("VPS"), 0, 10).toCompletableFuture().join();
            assertEquals(List.of(first.id(), second.id()), found.stream().map(com.ynpe.fastremote.domain.automation.AutomationHost::id).toList());
            assertEquals("asia", found.get(1).groupName());
            assertEquals("vps / asia", found.get(1).groupPath());
            assertEquals(List.of(parent.id(), child.id()), found.get(1).groupIds());
            var page = gateway.listHosts(AutomationSource.BUILT_IN_AI, Optional.empty(), Optional.of(parent.id()), 1, 1).toCompletableFuture().join();
            assertEquals(List.of(second.id()), page.stream().map(com.ynpe.fastremote.domain.automation.AutomationHost::id).toList());
            assertTrue(gateway.listHosts(AutomationSource.BUILT_IN_AI, Optional.empty(), Optional.of(empty.id()), 0, 10).toCompletableFuture().join().isEmpty());
            var ungrouped = gateway.listHosts(AutomationSource.BUILT_IN_AI).toCompletableFuture().join().getLast();
            assertTrue(ungrouped.groupId().isEmpty());
            assertEquals("", ungrouped.groupPath());
            var association = new com.ynpe.fastremote.domain.ai.AiHostAssociation(Optional.of(first.id()), first.name(), "dev", "example.internal");
            var context = new com.ynpe.fastremote.domain.automation.AutomationConversationContext(Optional.of(java.util.UUID.randomUUID()), Optional.of(association));
            var described = gateway.describeConversationContext(AutomationSource.BUILT_IN_AI, context).toCompletableFuture().join();
            assertEquals(context.conversationId(), described.conversationId());
            assertEquals("vps", described.savedHost().orElseThrow().groupPath());
            settings.save(withBuiltInPermissions(settings.load(), settings.load().builtInPermissions()
                    .with(AutomationCapability.READ_HOST_METADATA, AutomationPermission.DENY)));
            org.junit.jupiter.api.Assertions.assertThrows(CompletionException.class,
                    () -> gateway.listHostGroups(AutomationSource.BUILT_IN_AI).toCompletableFuture().join());
        }
    }

    @Test
    void managesCommandAndVariableLibrariesThroughThePermissionBoundary() throws Exception {
        AiSettings defaults = AiSettings.defaults();
        var permissions = defaults.builtInPermissions()
                .with(AutomationCapability.READ_LIBRARY, AutomationPermission.ALLOW)
                .with(AutomationCapability.MANAGE_LIBRARY, AutomationPermission.ALLOW);
        var mcpPermissions = defaults.mcpPermissions()
                .with(AutomationCapability.READ_LIBRARY, AutomationPermission.ALLOW)
                .with(AutomationCapability.MANAGE_LIBRARY, AutomationPermission.ALLOW);
        MutableAiSettings settings = new MutableAiSettings(new AiSettings(defaults.providers(),
                defaults.selectedProviderId(), permissions, mcpPermissions, defaults.allowedLocalRoots(),
                defaults.allowedRemoteRoots(), defaults.mcpServer()));
        AtomicInteger refreshes = new AtomicInteger();
        try (var gateway = gateway(host(), settings)) {
            gateway.setLibraryChangedHandler(refreshes::incrementAndGet);
            SnippetGroup commandGroup = gateway.addCommandGroup(AutomationSource.MCP, "系统", GroupColor.GREEN)
                    .toCompletableFuture().join();
            SnippetVariableGroup variableGroup = gateway.addVariableGroup(
                    AutomationSource.BUILT_IN_AI, "环境", GroupColor.PURPLE).toCompletableFuture().join();
            assertEquals(List.of(commandGroup), gateway.listCommandGroups(AutomationSource.BUILT_IN_AI)
                    .toCompletableFuture().join());
            assertEquals(List.of(variableGroup), gateway.listVariableGroups(AutomationSource.MCP)
                    .toCompletableFuture().join());
            Snippet command = gateway.addCommand(AutomationSource.BUILT_IN_AI,
                    "磁盘", "df -h", "查看磁盘", Optional.of(commandGroup.id())).toCompletableFuture().join();
            SnippetVariable variable = gateway.addVariable(AutomationSource.MCP,
                    "环境", "ENV", SnippetVariableType.SINGLE, "", "prod", "目标环境",
                    Optional.of(variableGroup.id()))
                    .toCompletableFuture().join();
            assertEquals(Optional.of(commandGroup.id()), command.groupId());
            assertEquals(Optional.of(variableGroup.id()), variable.groupId());
            assertEquals(List.of(command), gateway.listCommands(AutomationSource.BUILT_IN_AI)
                    .toCompletableFuture().join());
            assertEquals(List.of(variable), gateway.listVariables(AutomationSource.BUILT_IN_AI)
                    .toCompletableFuture().join());
            assertTrue(gateway.deleteCommand(AutomationSource.BUILT_IN_AI, command.id().toString())
                    .toCompletableFuture().join());
            assertTrue(gateway.deleteVariable(AutomationSource.BUILT_IN_AI, variable.id().toString())
                    .toCompletableFuture().join());
            assertTrue(gateway.listCommands(AutomationSource.BUILT_IN_AI).toCompletableFuture().join().isEmpty());
            assertTrue(gateway.listVariables(AutomationSource.BUILT_IN_AI).toCompletableFuture().join().isEmpty());
            assertEquals(6, refreshes.get());
            var describedCommand = gateway.addCommandEntry(AutomationSource.BUILT_IN_AI, "test", "pwd", "", Optional.of(commandGroup.id())).toCompletableFuture().join();
            var describedVariable = gateway.addVariableEntry(AutomationSource.MCP, "test", "GROUP_TEST", SnippetVariableType.SINGLE, "", "test", "", Optional.of(variableGroup.id())).toCompletableFuture().join();
            assertEquals(commandGroup.name(), describedCommand.groupName());
            assertEquals(variableGroup.name(), describedVariable.groupPath());
            assertEquals(List.of(describedCommand), gateway.listCommandEntries(AutomationSource.BUILT_IN_AI).toCompletableFuture().join());
            assertEquals(List.of(describedVariable), gateway.listVariableEntries(AutomationSource.MCP).toCompletableFuture().join());
            CompletionException unknownGroup = org.junit.jupiter.api.Assertions.assertThrows(
                    CompletionException.class, () -> gateway.addCommand(AutomationSource.MCP,
                            "未知分组", "true", "", Optional.of(SnippetGroupId.create()))
                            .toCompletableFuture().join());
            assertInstanceOf(IllegalArgumentException.class, unknownGroup.getCause());
            assertEquals(8, refreshes.get());
            settings.save(withBuiltInPermissions(settings.load(), permissions.with(AutomationCapability.READ_LIBRARY, AutomationPermission.DENY)));
            assertEquals(commandGroup.name(), gateway.addCommandEntry(AutomationSource.BUILT_IN_AI, "manage-only", "true", "",
                    Optional.of(commandGroup.id())).toCompletableFuture().join().groupName());
        }
    }

    @Test
    void activatesASavedHostThroughTheDesktopHookAfterPermission() throws Exception {
        SavedHost host = host();
        AiSettings defaults = AiSettings.defaults();
        MutableAiSettings settings = new MutableAiSettings(new AiSettings(defaults.providers(),
                defaults.selectedProviderId(), defaults.builtInPermissions().with(
                        AutomationCapability.OPEN_HOST, AutomationPermission.ALLOW),
                defaults.mcpPermissions(), defaults.allowedLocalRoots(), defaults.allowedRemoteRoots(), defaults.mcpServer()));
        AtomicInteger activations = new AtomicInteger();
        try (var gateway = gateway(host, settings)) {
            gateway.setHostActivationHandler(hostId -> {
                assertEquals(host.id(), hostId);
                activations.incrementAndGet();
                return CompletableFuture.completedFuture(true);
            });

            assertTrue(gateway.activateHost(AutomationSource.BUILT_IN_AI, host.id())
                    .toCompletableFuture().join());
            assertEquals(1, activations.get());
        }
    }

    @Test
    void defaultAutomationHostOpenRequestsANewBackgroundTab() throws Exception {
        SavedHost host = host();
        AiSettings defaults = AiSettings.defaults();
        MutableAiSettings settings = new MutableAiSettings(new AiSettings(defaults.providers(),
                defaults.selectedProviderId(), defaults.builtInPermissions().with(
                        AutomationCapability.OPEN_HOST, AutomationPermission.ALLOW),
                defaults.mcpPermissions(), defaults.allowedLocalRoots(), defaults.allowedRemoteRoots(),
                defaults.mcpServer()));
        AtomicInteger backgroundOpens = new AtomicInteger();
        try (var gateway = gateway(host, settings)) {
            gateway.setUiOperationHandler(new AutomationUiOperationHandler() {
                @Override public java.util.concurrent.CompletionStage<Boolean> runTerminalCommand(
                        HostId hostId, String command) {
                    return CompletableFuture.failedFuture(new AssertionError("command must not run"));
                }
                @Override public java.util.concurrent.CompletionStage<Boolean> openTerminal(
                        HostId hostId, boolean reuseExistingTab) {
                    assertEquals(host.id(), hostId);
                    assertFalse(reuseExistingTab);
                    backgroundOpens.incrementAndGet();
                    return CompletableFuture.completedFuture(true);
                }
                @Override public java.util.concurrent.CompletionStage<Boolean> uploadInSftp(
                        HostId hostId, List<Path> sources, String target) {
                    return CompletableFuture.failedFuture(new AssertionError("upload must not run"));
                }
                @Override public java.util.concurrent.CompletionStage<Boolean> downloadInSftp(
                        HostId hostId, String source, Path target) {
                    return CompletableFuture.failedFuture(new AssertionError("download must not run"));
                }
            });

            assertTrue(gateway.activateHost(AutomationSource.BUILT_IN_AI, host.id(), false)
                    .toCompletableFuture().join());
            assertEquals(1, backgroundOpens.get());
        }
    }

    @Test
    void deniedCommandNeverReachesConnector() {
        SavedHost host = host();
        AiSettings defaults = AiSettings.defaults();
        MutableAiSettings settings = new MutableAiSettings(new AiSettings(defaults.providers(),
                defaults.selectedProviderId(), defaults.builtInPermissions().with(
                        AutomationCapability.RUN_COMMAND, AutomationPermission.DENY),
                defaults.mcpPermissions(), defaults.allowedLocalRoots(), defaults.allowedRemoteRoots(), defaults.mcpServer()));
        try (var gateway = gateway(host, settings)) {
            CompletionException error = org.junit.jupiter.api.Assertions.assertThrows(CompletionException.class,
                    () -> gateway.runCommand(AutomationSource.BUILT_IN_AI, host.id(), "id")
                            .toCompletableFuture().join());
            assertInstanceOf(SecurityException.class, error.getCause());
        }
    }

    @Test
    void approvedVisibleCommandUsesDesktopHookInsteadOfBackgroundConnector() throws Exception {
        SavedHost host = host();
        AiSettings defaults = AiSettings.defaults();
        MutableAiSettings settings = new MutableAiSettings(new AiSettings(defaults.providers(),
                defaults.selectedProviderId(), defaults.builtInPermissions().with(
                        AutomationCapability.RUN_COMMAND, AutomationPermission.ALLOW),
                defaults.mcpPermissions(), defaults.allowedLocalRoots(), defaults.allowedRemoteRoots(), defaults.mcpServer()));
        AtomicInteger visibleCommands = new AtomicInteger();
        try (var gateway = gateway(host, settings)) {
            gateway.setUiOperationHandler(new AutomationUiOperationHandler() {
                @Override public java.util.concurrent.CompletionStage<Boolean> runTerminalCommand(
                        HostId hostId, String command) {
                    assertEquals(host.id(), hostId);
                    assertEquals("df -h", command);
                    visibleCommands.incrementAndGet();
                    return CompletableFuture.completedFuture(true);
                }
                @Override public java.util.concurrent.CompletionStage<Boolean> uploadInSftp(
                        HostId hostId, List<Path> sources, String target) {
                    return CompletableFuture.failedFuture(new AssertionError("upload must not run"));
                }
                @Override public java.util.concurrent.CompletionStage<Boolean> downloadInSftp(
                        HostId hostId, String source, Path target) {
                    return CompletableFuture.failedFuture(new AssertionError("download must not run"));
                }
            });
            assertTrue(gateway.runTerminalCommand(AutomationSource.BUILT_IN_AI, host.id(), "df -h")
                    .toCompletableFuture().join());
            assertEquals(1, visibleCommands.get());
        }
    }

    @Test
    void opensVisibleSftpAndChangesItsRemoteAndLocalDirectories(@TempDir Path temporary) throws Exception {
        SavedHost host = host();
        Path allowed = Files.createDirectory(temporary.resolve("Desktop"));
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        AiSettings defaults = AiSettings.defaults();
        var permissions = defaults.builtInPermissions()
                .with(AutomationCapability.OPEN_HOST, AutomationPermission.ALLOW)
                .with(AutomationCapability.READ_REMOTE, AutomationPermission.ALLOW)
                .with(AutomationCapability.READ_LOCAL, AutomationPermission.ALLOW);
        MutableAiSettings settings = new MutableAiSettings(new AiSettings(defaults.providers(),
                defaults.selectedProviderId(), permissions, defaults.mcpPermissions(), List.of(allowed),
                defaults.allowedRemoteRoots(), defaults.mcpServer()));
        AtomicInteger opened = new AtomicInteger();
        AtomicInteger remoteChanges = new AtomicInteger();
        AtomicInteger localChanges = new AtomicInteger();
        var events = new CopyOnWriteArrayList<com.ynpe.fastremote.domain.automation.AutomationAuditEvent>();
        try (var gateway = gateway(host, settings)) {
            gateway.setAuditHandler(events::add);
            gateway.setUiOperationHandler(new AutomationUiOperationHandler() {
                @Override public CompletableFuture<Boolean> runTerminalCommand(HostId hostId, String command) {
                    return CompletableFuture.failedFuture(new AssertionError("terminal command must not run"));
                }
                @Override public CompletableFuture<Boolean> uploadInSftp(
                        HostId hostId, List<Path> sources, String target) {
                    return CompletableFuture.failedFuture(new AssertionError("upload must not run"));
                }
                @Override public CompletableFuture<Boolean> downloadInSftp(
                        HostId hostId, String source, Path target) {
                    return CompletableFuture.failedFuture(new AssertionError("download must not run"));
                }
                @Override public CompletableFuture<Boolean> openSftp(HostId hostId) {
                    assertEquals(host.id(), hostId);
                    opened.incrementAndGet();
                    return CompletableFuture.completedFuture(true);
                }
                @Override public CompletableFuture<Boolean> changeSftpRemoteDirectory(
                        HostId hostId, String directory) {
                    assertEquals(host.id(), hostId);
                    assertEquals("Data", directory);
                    remoteChanges.incrementAndGet();
                    return CompletableFuture.completedFuture(true);
                }
                @Override public CompletableFuture<Boolean> changeSftpLocalDirectory(
                        HostId hostId, Path directory) {
                    assertEquals(host.id(), hostId);
                    assertEquals(allowed, directory);
                    localChanges.incrementAndGet();
                    return CompletableFuture.completedFuture(true);
                }
            });

            assertTrue(gateway.openSftp(AutomationSource.BUILT_IN_AI, host.id())
                    .toCompletableFuture().join());
            assertTrue(gateway.changeSftpRemoteDirectory(AutomationSource.BUILT_IN_AI, host.id(), "Data")
                    .toCompletableFuture().join());
            assertTrue(gateway.changeSftpLocalDirectory(AutomationSource.BUILT_IN_AI, host.id(), allowed)
                    .toCompletableFuture().join());
            assertEquals(1, opened.get());
            assertEquals(1, remoteChanges.get());
            assertEquals(1, localChanges.get());
            assertTrue(events.stream().anyMatch(event -> event.phase() == AutomationAuditPhase.SUCCEEDED
                    && event.action().equals("打开 SFTP 工作区")));
            assertTrue(events.stream().anyMatch(event -> event.phase() == AutomationAuditPhase.SUCCEEDED
                    && event.action().equals("切换 SFTP 远程目录")));
            assertTrue(events.stream().anyMatch(event -> event.phase() == AutomationAuditPhase.SUCCEEDED
                    && event.action().equals("切换 SFTP 本地目录")));
            assertTrue(events.stream().allMatch(event -> event.target().equals("server")));
            assertTrue(events.stream().noneMatch(event -> event.detail().contains("example.internal")
                    || event.detail().contains("dev@")));

            org.junit.jupiter.api.Assertions.assertThrows(SecurityException.class,
                    () -> gateway.changeSftpLocalDirectory(
                            AutomationSource.BUILT_IN_AI, host.id(), outside));
            assertEquals(1, localChanges.get());
        }
    }

    @Test
    void visibleOverwriteUploadRequiresRemoteMutationPermissionAndPassesThePolicy(@TempDir Path temporary)
            throws Exception {
        SavedHost host = host();
        Path allowed = Files.createDirectory(temporary.resolve("allowed"));
        Path source = Files.writeString(allowed.resolve("release.bin"), "new");
        AiSettings defaults = AiSettings.defaults();
        var deniedMutation = defaults.builtInPermissions()
                .with(AutomationCapability.UPLOAD, AutomationPermission.ALLOW)
                .with(AutomationCapability.EDIT_REMOTE, AutomationPermission.DENY);
        MutableAiSettings settings = new MutableAiSettings(new AiSettings(defaults.providers(),
                defaults.selectedProviderId(), deniedMutation, defaults.mcpPermissions(), List.of(allowed),
                List.of("/data"), defaults.mcpServer()));
        AtomicInteger submitted = new AtomicInteger();
        var events = new CopyOnWriteArrayList<com.ynpe.fastremote.domain.automation.AutomationAuditEvent>();
        try (var gateway = gateway(host, settings)) {
            gateway.setAuditHandler(events::add);
            gateway.setUiOperationHandler(new AutomationUiOperationHandler() {
                @Override public CompletableFuture<Boolean> runTerminalCommand(HostId hostId, String command) {
                    return CompletableFuture.failedFuture(new AssertionError("terminal command must not run"));
                }
                @Override public CompletableFuture<Boolean> uploadInSftp(
                        HostId hostId, List<Path> sources, String target) {
                    return CompletableFuture.failedFuture(new AssertionError("prompting upload must not run"));
                }
                @Override public CompletableFuture<Boolean> uploadInSftp(
                        HostId hostId, List<Path> sources, String target,
                        TransferConflictChoice conflictChoice) {
                    assertEquals(host.id(), hostId);
                    assertEquals(List.of(source), sources);
                    assertEquals("/data", target);
                    assertEquals(TransferConflictChoice.OVERWRITE, conflictChoice);
                    submitted.incrementAndGet();
                    return CompletableFuture.completedFuture(true);
                }
                @Override public CompletableFuture<Boolean> downloadInSftp(
                        HostId hostId, String source, Path target) {
                    return CompletableFuture.failedFuture(new AssertionError("download must not run"));
                }
            });

            CompletionException denied = org.junit.jupiter.api.Assertions.assertThrows(CompletionException.class,
                    () -> gateway.uploadInSftp(AutomationSource.BUILT_IN_AI, host.id(), List.of(source),
                            "/data", TransferConflictChoice.OVERWRITE).toCompletableFuture().join());
            assertInstanceOf(SecurityException.class, denied.getCause());
            assertEquals(0, submitted.get());

            settings.save(new AiSettings(defaults.providers(), defaults.selectedProviderId(),
                    deniedMutation.with(AutomationCapability.EDIT_REMOTE, AutomationPermission.ALLOW),
                    defaults.mcpPermissions(), List.of(allowed), List.of("/data"),
                    defaults.mcpServer()));
            assertTrue(gateway.uploadInSftp(AutomationSource.BUILT_IN_AI, host.id(), List.of(source),
                    "/data", TransferConflictChoice.OVERWRITE).toCompletableFuture().join());
            assertEquals(1, submitted.get());
            assertTrue(events.stream().anyMatch(event -> event.phase() == AutomationAuditPhase.SUCCEEDED
                    && event.capability() == AutomationCapability.EDIT_REMOTE));
            assertTrue(events.stream().anyMatch(event -> event.phase() == AutomationAuditPhase.SUCCEEDED
                    && event.capability() == AutomationCapability.UPLOAD));
        }
    }

    @Test
    void visibleAiAndMcpUploadsHonorTheConfiguredTransferLimit(@TempDir Path temporary)
            throws Exception {
        SavedHost host = host();
        Path allowed = Files.createDirectory(temporary.resolve("allowed"));
        Path source = allowed.resolve("oversized.bin");
        try (var channel = java.nio.channels.FileChannel.open(source,
                java.nio.file.StandardOpenOption.CREATE_NEW,
                java.nio.file.StandardOpenOption.WRITE)) {
            channel.position(AiSettings.MIN_MAX_TRANSFER_BYTES);
            channel.write(java.nio.ByteBuffer.wrap(new byte[] {1}));
        }
        AiSettings defaults = AiSettings.defaults();
        AutomationPermissions permissions = defaults.builtInPermissions()
                .with(AutomationCapability.UPLOAD, AutomationPermission.ALLOW);
        MutableAiSettings settings = new MutableAiSettings(new AiSettings(defaults.providers(),
                defaults.selectedProviderId(), permissions, defaults.mcpPermissions(), List.of(allowed),
                List.of("/data"), AiSettings.MIN_MAX_TRANSFER_BYTES, defaults.maxToolRounds(),
                defaults.mcpServer()));
        AtomicInteger submitted = new AtomicInteger();
        try (var gateway = gateway(host, settings)) {
            gateway.setUiOperationHandler(new AutomationUiOperationHandler() {
                @Override public CompletableFuture<Boolean> runTerminalCommand(HostId id, String command) {
                    return CompletableFuture.failedFuture(new AssertionError("terminal command must not run"));
                }
                @Override public CompletableFuture<Boolean> uploadInSftp(
                        HostId id, List<Path> sources, String target) {
                    submitted.incrementAndGet();
                    return CompletableFuture.completedFuture(true);
                }
                @Override public CompletableFuture<Boolean> downloadInSftp(
                        HostId id, String remote, Path target) {
                    return CompletableFuture.failedFuture(new AssertionError("download must not run"));
                }
            });

            CompletionException failure = org.junit.jupiter.api.Assertions.assertThrows(
                    CompletionException.class, () -> gateway.uploadInSftp(
                            AutomationSource.BUILT_IN_AI, host.id(), List.of(source), "/data")
                            .toCompletableFuture().join());

            assertInstanceOf(IllegalArgumentException.class, failure.getCause());
            assertTrue(failure.getCause().getMessage().contains("MiB"));
            assertEquals(0, submitted.get());
        }
    }

    @Test
    void backgroundOverwriteUploadRequestsRemoteEditPermissionAndNamesARejection(@TempDir Path temporary)
            throws Exception {
        SavedHost host = host();
        Path allowed = Files.createDirectory(temporary.resolve("allowed"));
        Path source = Files.writeString(allowed.resolve("release.bin"), "new");
        AiSettings defaults = AiSettings.defaults();
        AutomationPermissions permissions = defaults.builtInPermissions()
                .with(AutomationCapability.UPLOAD, AutomationPermission.ALLOW)
                .with(AutomationCapability.EDIT_REMOTE, AutomationPermission.ASK);
        MutableAiSettings settings = new MutableAiSettings(new AiSettings(defaults.providers(),
                defaults.selectedProviderId(), permissions, defaults.mcpPermissions(), List.of(allowed),
                List.of("/data"), defaults.mcpServer()));
        AtomicReference<AutomationCapability> requested = new AtomicReference<>();
        try (var gateway = gateway(host, settings)) {
            gateway.setApprovalHandler(request -> {
                requested.set(request.capability());
                return CompletableFuture.completedFuture(false);
            });

            CompletionException denied = org.junit.jupiter.api.Assertions.assertThrows(
                    CompletionException.class, () -> gateway.uploadIntoDirectory(
                            AutomationSource.BUILT_IN_AI, host.id(), List.of(source), "/data",
                            TransferConflictChoice.OVERWRITE).toCompletableFuture().join());

            assertEquals(AutomationCapability.EDIT_REMOTE, requested.get());
            assertInstanceOf(SecurityException.class, denied.getCause());
            assertTrue(denied.getCause().getMessage().contains("EDIT_REMOTE"));
        }
    }

    @Test
    void remoteCreateEditRenameAndDeleteUseIndependentPermissions() throws Exception {
        SavedHost host = host();
        AiSettings defaults = AiSettings.defaults();
        AutomationPermissions permissions = new AutomationPermissions(Map.of())
                .with(AutomationCapability.CREATE_REMOTE, AutomationPermission.ALLOW);
        MutableAiSettings settings = new MutableAiSettings(new AiSettings(defaults.providers(),
                defaults.selectedProviderId(), permissions, defaults.mcpPermissions(),
                defaults.allowedLocalRoots(), List.of("/data"), defaults.mcpServer()));
        var events = new CopyOnWriteArrayList<com.ynpe.fastremote.domain.automation.AutomationAuditEvent>();
        try (var gateway = gateway(host, settings)) {
            gateway.setAuditHandler(events::add);
            gateway.setUiOperationHandler(new AutomationUiOperationHandler() {
                @Override public CompletableFuture<Boolean> runTerminalCommand(HostId id, String command) {
                    return CompletableFuture.failedFuture(new AssertionError("terminal command must not run"));
                }
                @Override public CompletableFuture<Boolean> uploadInSftp(
                        HostId id, List<Path> sources, String target) {
                    return CompletableFuture.failedFuture(new AssertionError("upload must not run"));
                }
                @Override public CompletableFuture<Boolean> downloadInSftp(
                        HostId id, String source, Path target) {
                    return CompletableFuture.failedFuture(new AssertionError("download must not run"));
                }
                @Override public CompletableFuture<Boolean> createRemoteFile(HostId id, String path) {
                    assertEquals(host.id(), id);
                    return CompletableFuture.completedFuture(true);
                }
                @Override public CompletableFuture<Boolean> setRemotePermissions(
                        HostId id, String path, int value) {
                    return CompletableFuture.completedFuture(true);
                }
                @Override public CompletableFuture<Boolean> renameRemote(
                        HostId id, String source, String target) {
                    return CompletableFuture.completedFuture(true);
                }
                @Override public CompletableFuture<Boolean> deleteRemote(
                        HostId id, String path, boolean recursive) {
                    return CompletableFuture.completedFuture(true);
                }
            });

            assertTrue(gateway.createRemoteFile(AutomationSource.BUILT_IN_AI, host.id(), "/data/a.txt")
                    .toCompletableFuture().join());
            assertPermissionDenied(() -> gateway.setRemotePermissions(
                    AutomationSource.BUILT_IN_AI, host.id(), "/data/a.txt", 0644)
                    .toCompletableFuture().join());
            assertPermissionDenied(() -> gateway.renameRemote(
                    AutomationSource.BUILT_IN_AI, host.id(), "/data/a.txt", "/data/b.txt")
                    .toCompletableFuture().join());
            assertPermissionDenied(() -> gateway.deleteRemote(
                    AutomationSource.BUILT_IN_AI, host.id(), "/data/a.txt", false)
                    .toCompletableFuture().join());

            permissions = permissions.with(AutomationCapability.EDIT_REMOTE, AutomationPermission.ALLOW);
            settings.save(withBuiltInPermissions(settings.load(), permissions));
            assertTrue(gateway.setRemotePermissions(
                    AutomationSource.BUILT_IN_AI, host.id(), "/data/a.txt", 0644)
                    .toCompletableFuture().join());
            permissions = permissions.with(AutomationCapability.RENAME_REMOTE, AutomationPermission.ALLOW);
            settings.save(withBuiltInPermissions(settings.load(), permissions));
            assertTrue(gateway.renameRemote(
                    AutomationSource.BUILT_IN_AI, host.id(), "/data/a.txt", "/data/b.txt")
                    .toCompletableFuture().join());
            permissions = permissions.with(AutomationCapability.DELETE_REMOTE, AutomationPermission.ALLOW);
            settings.save(withBuiltInPermissions(settings.load(), permissions));
            assertTrue(gateway.deleteRemote(
                    AutomationSource.BUILT_IN_AI, host.id(), "/data/b.txt", false)
                    .toCompletableFuture().join());

            assertTrue(events.stream().anyMatch(event -> event.capability() == AutomationCapability.CREATE_REMOTE));
            assertTrue(events.stream().anyMatch(event -> event.capability() == AutomationCapability.EDIT_REMOTE));
            assertTrue(events.stream().anyMatch(event -> event.capability() == AutomationCapability.RENAME_REMOTE));
            assertTrue(events.stream().anyMatch(event -> event.capability() == AutomationCapability.DELETE_REMOTE));
        }
    }

    @Test
    void approvedLocalVisibleCommandNeedsNoSavedHostAndAuditsTheLocalTarget() throws Exception {
        AiSettings defaults = AiSettings.defaults();
        MutableAiSettings settings = new MutableAiSettings(new AiSettings(defaults.providers(),
                defaults.selectedProviderId(), defaults.builtInPermissions().with(
                        AutomationCapability.RUN_COMMAND, AutomationPermission.ALLOW),
                defaults.mcpPermissions(), defaults.allowedLocalRoots(), defaults.allowedRemoteRoots(),
                defaults.mcpServer()));
        AtomicInteger localCommands = new AtomicInteger();
        CopyOnWriteArrayList<com.ynpe.fastremote.domain.automation.AutomationAuditEvent> events =
                new CopyOnWriteArrayList<>();
        try (var gateway = gateway(host(), settings)) {
            gateway.setAuditHandler(events::add);
            gateway.setUiOperationHandler(new AutomationUiOperationHandler() {
                @Override public java.util.concurrent.CompletionStage<Boolean> runTerminalCommand(
                        HostId hostId, String command) {
                    return CompletableFuture.failedFuture(new AssertionError("remote terminal must not run"));
                }
                @Override public java.util.concurrent.CompletionStage<Boolean> runLocalTerminalCommand(String command) {
                    assertEquals("pwd", command);
                    localCommands.incrementAndGet();
                    return CompletableFuture.completedFuture(true);
                }
                @Override public java.util.concurrent.CompletionStage<Boolean> uploadInSftp(
                        HostId hostId, List<Path> sources, String target) {
                    return CompletableFuture.failedFuture(new AssertionError("upload must not run"));
                }
                @Override public java.util.concurrent.CompletionStage<Boolean> downloadInSftp(
                        HostId hostId, String source, Path target) {
                    return CompletableFuture.failedFuture(new AssertionError("download must not run"));
                }
            });
            assertTrue(gateway.runLocalTerminalCommand(AutomationSource.BUILT_IN_AI, "pwd")
                    .toCompletableFuture().join());
            assertEquals(1, localCommands.get());
            assertTrue(events.stream().anyMatch(event -> event.phase() == AutomationAuditPhase.SUCCEEDED
                    && event.target().equals("本地终端")
                    && event.action().equals("在可见本地终端中执行命令")));
        }
    }

    @Test
    void rejectsRemoteMutationOutsideConfiguredRootBeforeConnecting() throws Exception {
        AiSettings defaults = AiSettings.defaults();
        MutableAiSettings settings = new MutableAiSettings(new AiSettings(defaults.providers(),
                defaults.selectedProviderId(), defaults.builtInPermissions().with(
                        AutomationCapability.DELETE_REMOTE, AutomationPermission.ALLOW),
                defaults.mcpPermissions(), defaults.allowedLocalRoots(), List.of("/data"),
                defaults.mcpServer()));
        try (var gateway = gateway(host(), settings)) {
            SecurityException failure = org.junit.jupiter.api.Assertions.assertThrows(SecurityException.class,
                    () -> gateway.deleteRemote(AutomationSource.BUILT_IN_AI, host().id(), "/etc/passwd", false));
            assertTrue(failure.getMessage().contains("outside"));
        }
    }

    @Test
    void windowsRemoteRootAllowsEitherSeparatorStyleButStillRejectsSiblingPaths() throws Exception {
        SavedHost host = host();
        AiSettings defaults = AiSettings.defaults();
        MutableAiSettings settings = new MutableAiSettings(new AiSettings(defaults.providers(),
                defaults.selectedProviderId(), defaults.builtInPermissions().with(
                        AutomationCapability.CREATE_REMOTE, AutomationPermission.ALLOW),
                defaults.mcpPermissions(), defaults.allowedLocalRoots(), List.of("D:\\FastRemote\\"),
                defaults.mcpServer()));
        AtomicReference<String> created = new AtomicReference<>();
        try (var gateway = gateway(host, settings)) {
            gateway.setUiOperationHandler(new AutomationUiOperationHandler() {
                @Override public java.util.concurrent.CompletionStage<Boolean> runTerminalCommand(
                        HostId hostId, String command) {
                    return CompletableFuture.completedFuture(false);
                }
                @Override public java.util.concurrent.CompletionStage<Boolean> uploadInSftp(
                        HostId hostId, List<Path> sources, String target) {
                    return CompletableFuture.completedFuture(false);
                }
                @Override public java.util.concurrent.CompletionStage<Boolean> downloadInSftp(
                        HostId hostId, String source, Path target) {
                    return CompletableFuture.completedFuture(false);
                }
                @Override public java.util.concurrent.CompletionStage<Boolean> createRemoteDirectory(
                        HostId hostId, String path) {
                    created.set(path);
                    return CompletableFuture.completedFuture(true);
                }
            });

            assertTrue(gateway.createRemoteDirectory(
                    AutomationSource.BUILT_IN_AI, host.id(), "d:\\FastRemote\\logs\\..\\uploads\\")
                    .toCompletableFuture().join());
            assertEquals("D:/FastRemote/uploads", created.get());

            SecurityException outside = org.junit.jupiter.api.Assertions.assertThrows(
                    SecurityException.class, () -> gateway.createRemoteDirectory(
                            AutomationSource.BUILT_IN_AI, host.id(), "D:/FastRemote-old/uploads"));
            assertTrue(outside.getMessage().contains("outside"));
        }
    }

    @Test
    void terminalObservationHasIndependentReadPermissionAndBoundedArguments() throws Exception {
        var defaults = AiSettings.defaults();
        var builtIn = defaults.builtInPermissions().with(AutomationCapability.READ_TERMINAL_OUTPUT, AutomationPermission.ALLOW);
        var mcp = defaults.mcpPermissions().with(AutomationCapability.READ_TERMINAL_OUTPUT, AutomationPermission.DENY);
        var settings = new MutableAiSettings(new AiSettings(defaults.providers(), defaults.selectedProviderId(),
                builtIn, mcp, defaults.allowedLocalRoots(), defaults.allowedRemoteRoots(), defaults.maxTransferBytes(),
                defaults.maxToolRounds(), defaults.mcpServer()));
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        try (var gateway = gateway(host(), settings)) {
            gateway.setUiOperationHandler(new AutomationUiOperationHandler() {
                @Override public java.util.concurrent.CompletionStage<com.ynpe.fastremote.domain.terminal.TerminalOutput> readTerminal(
                        String workspace, String generation, long after, int maximum, boolean screen) {
                    reads.incrementAndGet();
                    assertEquals("pane-1", workspace); assertEquals("generation-1", generation);
                    return CompletableFuture.completedFuture(new com.ynpe.fastremote.domain.terminal.TerminalOutput(
                            generation, after, after + 2, after + 2, false, false, "ok", "", false, "READY", true));
                }
                @Override public java.util.concurrent.CompletionStage<Boolean> runTerminalCommand(HostId id, String command) { return CompletableFuture.completedFuture(false); }
                @Override public java.util.concurrent.CompletionStage<Boolean> uploadInSftp(HostId id, List<Path> paths, String target) { return CompletableFuture.completedFuture(false); }
                @Override public java.util.concurrent.CompletionStage<Boolean> downloadInSftp(HostId id, String source, Path target) { return CompletableFuture.completedFuture(false); }
            });
            var rejected = org.junit.jupiter.api.Assertions.assertThrows(CompletionException.class, () ->
                    gateway.readTerminal(AutomationSource.MCP, "pane-1", "generation-1", 0, 100, false).toCompletableFuture().join());
            assertInstanceOf(SecurityException.class, rejected.getCause()); assertEquals(0, reads.get());
            assertEquals("ok", gateway.readTerminal(AutomationSource.BUILT_IN_AI, "pane-1", "generation-1", 0, 100, false)
                    .toCompletableFuture().join().text());
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                    gateway.readTerminal(AutomationSource.BUILT_IN_AI, "pane-1", "generation-1", -1, 100, false));
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                    gateway.readTerminal(AutomationSource.BUILT_IN_AI, "pane-1", "generation-1", 0, 65537, false));
            assertEquals(1, reads.get());
        }
    }

    @Test
    void localBackgroundExecutionHonorsPermissionsWithoutOpeningAWorkspace(@TempDir Path directory) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(System.getProperty("os.name").toLowerCase().contains("win"));
        var defaults = AiSettings.defaults();
        var settings = new MutableAiSettings(new AiSettings(defaults.providers(), defaults.selectedProviderId(),
                defaults.builtInPermissions().with(AutomationCapability.RUN_COMMAND, AutomationPermission.ALLOW),
                defaults.mcpPermissions().with(AutomationCapability.RUN_COMMAND, AutomationPermission.DENY),
                defaults.allowedLocalRoots(), defaults.allowedRemoteRoots(), defaults.maxTransferBytes(),
                defaults.maxToolRounds(), defaults.mcpServer()));
        try (var gateway = gateway(host(), settings)) {
            gateway.setUiOperationHandler(new AutomationUiOperationHandler() {
                @Override public java.util.concurrent.CompletionStage<Boolean> runTerminalCommand(HostId id, String command) { throw new AssertionError("No terminal UI allowed"); }
                @Override public java.util.concurrent.CompletionStage<Boolean> runLocalTerminalCommand(String command) { throw new AssertionError("No local terminal UI allowed"); }
                @Override public java.util.concurrent.CompletionStage<Boolean> uploadInSftp(HostId id, List<Path> paths, String target) { throw new AssertionError(); }
                @Override public java.util.concurrent.CompletionStage<Boolean> downloadInSftp(HostId id, String source, Path target) { throw new AssertionError(); }
            });
            var rejected = org.junit.jupiter.api.Assertions.assertThrows(CompletionException.class, () ->
                    gateway.runLocalCommand(AutomationSource.MCP, "touch should-not-exist", Optional.of(directory)).toCompletableFuture().join());
            assertInstanceOf(SecurityException.class, rejected.getCause()); assertFalse(Files.exists(directory.resolve("should-not-exist")));
            var result = gateway.runLocalCommand(AutomationSource.BUILT_IN_AI,
                    "printf output; printf problem >&2; exit 3", Optional.of(directory)).toCompletableFuture().join();
            assertEquals("output", result.stdout()); assertEquals("problem", result.stderr());
            assertEquals(3, result.exitCode().orElseThrow()); assertTrue(result.notice().contains("background"));
        }
    }

    @Test
    void mcpWritesExactUtf8ThroughSftpAndReportsValidationAndCleanupOutcomes(@TempDir Path temporary)
            throws Exception {
        Path target = temporary.resolve("space ' 中文 🙂 % &.txt");
        var server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(temporary.resolve("host-key")));
        server.setPasswordAuthenticator((user, password, session) -> "test".equals(user) && "fixture".equals(password));
        SftpModuleProperties.SFTP_VERSION.set(server, 3);
        SftpModuleProperties.OPENSSH_EXTENSIONS.set(server, "statvfs@openssh.com=2");
        var subsystem = new SftpSubsystemFactory.Builder().build();
        subsystem.addSftpEventListener(new SftpEventListener() {
            @Override public void removing(ServerSession session, Path path, boolean directory) throws IOException {
                if (path.getFileName().toString().endsWith(".bak")) throw new IOException("fixture denies backup removal");
            }
        });
        server.setSubsystemFactories(List.of(subsystem));
        server.start();
        try {
            var profile = new ConnectionProfile(new Host("127.0.0.1"), new Port(server.getPort()),
                    new Username("test"), AuthenticationType.PASSWORD, Optional.empty());
            var saved = SavedHost.create("loopback", Optional.empty(), profile, 0)
                    .withCredential(Optional.of(com.ynpe.fastremote.domain.vault.SecretId.create()));
            var defaults = AiSettings.defaults();
            var permissions = defaults.mcpPermissions()
                    .with(AutomationCapability.CREATE_REMOTE, AutomationPermission.ALLOW)
                    .with(AutomationCapability.EDIT_REMOTE, AutomationPermission.ALLOW);
            var settings = new MutableAiSettings(new AiSettings(defaults.providers(), defaults.selectedProviderId(),
                    defaults.builtInPermissions(), permissions, List.of(), List.of(temporary.toString()), defaults.mcpServer()));
            var knownHosts = new KnownHostRepository() {
                @Override public Optional<HostKeyIdentity> find(Host host, Port port) { return Optional.empty(); }
                @Override public void save(HostKeyIdentity identity) { throw new AssertionError("Fixture keys are not persisted"); }
            };
            try (var connector = new MinaSshSessionConnector(knownHosts);
                 var gateway = new BoundedAutomationGateway(connector, new OneHostRepository(saved),
                         ignored -> CompletableFuture.completedFuture(new com.ynpe.fastremote.ui.ResolvedCredential(
                                 AuthenticationType.PASSWORD, "fixture".toCharArray(), null)),
                         new GlobalSettingsRepository() {
                             @Override public GlobalSettings load() { return GlobalSettings.defaults(); }
                             @Override public void save(GlobalSettings value) { }
                         }, settings, new EmptySnippetRepository());
                 var client = HttpClient.newHttpClient()) {
                gateway.setHostKeyPrompt(ignored -> HostKeyDecision.ACCEPT_ONCE);
                var token = FastRemoteMcpServer.generateToken();
                try (var mcp = new FastRemoteMcpServer(gateway, 0, token.storedHash())) {
                    mcp.start();
                    var endpoint = URI.create("http://127.0.0.1:" + mcp.port() + "/mcp");
                    var json = new ObjectMapper();
                    String original = "  \"quotes\" 'single' \\ \t\r\n中文🙂 ${literal} & | % !\n"
                            + "literal backslash-n: \\n\n" + (char) 0 + "end  ";
                    String request = writeRemoteTextRequest(json, saved.id(), target, original, false);
                    var malformed = postMcp(client, endpoint, token.plaintext(), request.replace("\\r\\n", "\r\n"));
                    assertEquals(400, malformed.statusCode());
                    assertEquals(-32700, json.readTree(malformed.body()).path("error").path("code").asInt());
                    assertFalse(Files.exists(target));

                    assertFalse(toolResult(json, postMcp(client, endpoint, token.plaintext(), request)).path("isError").asBoolean(true));
                    assertArrayEquals(original.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(target));

                    Path invalidUnicodeTarget = temporary.resolve("invalid-unicode.txt");
                    String invalidUnicode = writeRemoteTextRequest(json, saved.id(), invalidUnicodeTarget,
                            "invalid-unicode-fixture", false).replace("invalid-unicode-fixture", "\\uD800");
                    var invalidResult = toolResult(json, postMcp(client, endpoint, token.plaintext(), invalidUnicode));
                    assertTrue(invalidResult.path("isError").asBoolean());
                    assertTrue(invalidResult.path("content").get(0).path("text").asText().contains("invalid Unicode"));
                    assertFalse(Files.exists(invalidUnicodeTarget));

                    Path maximumTarget = temporary.resolve("maximum.txt");
                    String maximum = String.valueOf((char) 0).repeat(RemoteTextWrite.MAX_BYTES);
                    String maximumRequest = writeRemoteTextRequest(json, saved.id(), maximumTarget, maximum, false);
                    assertTrue(maximumRequest.length() > 6 * RemoteTextWrite.MAX_BYTES);
                    assertFalse(toolResult(json, postMcp(client, endpoint, token.plaintext(), maximumRequest))
                            .path("isError").asBoolean(true));
                    assertArrayEquals(new byte[RemoteTextWrite.MAX_BYTES], Files.readAllBytes(maximumTarget));

                    Path oversizedTarget = temporary.resolve("too-big.txt");
                    String oversized = "中".repeat(RemoteTextWrite.MAX_BYTES / 3 + 1);
                    var oversizedResult = toolResult(json, postMcp(client, endpoint, token.plaintext(),
                            writeRemoteTextRequest(json, saved.id(), oversizedTarget, oversized, false)));
                    assertTrue(oversizedResult.path("isError").asBoolean());
                    assertTrue(oversizedResult.path("content").get(0).path("text").asText().contains("1 MiB"));
                    assertFalse(Files.exists(oversizedTarget));

                    var overwrite = toolResult(json, postMcp(client, endpoint, token.plaintext(),
                            writeRemoteTextRequest(json, saved.id(), target, "replacement", true)));
                    assertTrue(overwrite.path("isError").asBoolean());
                    assertTrue(overwrite.path("content").get(0).path("text").asText()
                            .contains("Remote file was updated, but its compatibility backup could not be removed"));
                    assertEquals("replacement", Files.readString(target));
                    try (var children = Files.list(temporary)) {
                        Path backup = children.filter(path -> path.getFileName().toString().endsWith(".bak"))
                                .findFirst().orElseThrow();
                        assertArrayEquals(original.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(backup));
                    }
                }
            }
        } finally {
            server.stop(true);
        }
    }

    private static String writeRemoteTextRequest(ObjectMapper json, HostId host, Path path, String content,
            boolean overwrite) throws IOException {
        return json.writeValueAsString(Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/call",
                "params", Map.of("name", "write_remote_text", "arguments",
                        Map.of("host_id", host.toString(), "path", path.toString(), "content", content,
                                "overwrite", overwrite))));
    }

    @Test
    void catalogLifecycleOverMcpAndIndependentAiPermissions(@TempDir Path directory) throws Exception {
        var configuration = new com.ynpe.fastremote.storage.YamlGlobalConfigStore(directory.resolve("config.yaml"));
        var credentialRepository = new com.ynpe.fastremote.storage.YamlGlobalCredentialRepository(configuration);
        var vault = new com.ynpe.fastremote.storage.YamlSecretVault(configuration);
        vault.create(java.util.UUID.randomUUID().toString().toCharArray());
        var database = new com.ynpe.fastremote.storage.FastRemoteDatabase(directory.resolve("catalog.db"));
        var hosts = new com.ynpe.fastremote.storage.SqliteHostCatalogRepository(database);
        var commands = new com.ynpe.fastremote.storage.SqliteSnippetRepository(database);
        var defaults = AiSettings.defaults();
        var permissions = defaults.mcpPermissions()
                .with(AutomationCapability.READ_LOCAL, AutomationPermission.DENY)
                .with(AutomationCapability.READ_LIBRARY, AutomationPermission.ALLOW)
                .with(AutomationCapability.MANAGE_LIBRARY, AutomationPermission.ALLOW)
                .with(AutomationCapability.READ_HOST_METADATA, AutomationPermission.ALLOW);
        var settings = new MutableAiSettings(new AiSettings(defaults.providers(), defaults.selectedProviderId(),
                defaults.builtInPermissions(), permissions, List.of(), List.of(), defaults.mcpServer()));
        var token = FastRemoteMcpServer.generateToken();
        var notifications = new AtomicInteger();
        var audit = new java.util.concurrent.ConcurrentLinkedQueue<AutomationAuditEvent>();
        try (var workflow = new SqliteHostCatalogWorkflow(database, hosts, vault, credentialRepository);
             var gateway = new BoundedAutomationGateway(new RejectingConnector(), hosts, workflow::loadCredential,
                     new GlobalSettingsRepository() {
                         @Override public GlobalSettings load() { return GlobalSettings.defaults(); }
                         @Override public void save(GlobalSettings value) { }
                     }, settings, commands, ignored -> CompletableFuture.completedFuture(true),
                     credentialRepository, workflow);
             var mcp = new FastRemoteMcpServer(gateway, 0, token.storedHash());
             var client = HttpClient.newHttpClient()) {
            gateway.setLibraryChangedHandler(notifications::incrementAndGet);
            gateway.setAuditHandler(audit::add);
            mcp.start();
            var endpoint = URI.create("http://127.0.0.1:" + mcp.port() + "/mcp");
            var protocol = new CatalogProtocol(client, endpoint, token.plaintext());
            assertTrue(protocol.call("add_host_group", Map.of("name", "fixture")).path("isError").asBoolean());
            assertTrue(hosts.listGroups().isEmpty());
            settings.save(new AiSettings(defaults.providers(), defaults.selectedProviderId(), defaults.builtInPermissions(),
                    permissions.with(AutomationCapability.CREATE_HOSTS, AutomationPermission.ALLOW),
                    List.of(), List.of(), defaults.mcpServer()));
            assertPermissionDenied(() -> gateway.catalogTool(AutomationSource.BUILT_IN_AI,
                    com.ynpe.fastremote.domain.automation.AutomationCatalogTool.ADD_HOST_GROUP,
                    Map.of("name", "AI must be denied")).toCompletableFuture().join());

            String group = protocol.id("add_host_group", Map.of("name", "测试组"));
            var groupPatch = Map.<String, Object>of("group_id", group, "name", "新名称", "color", "green");
            assertTrue(protocol.call("update_host_group", groupPatch).toString().contains("EDIT_HOSTS"));
            assertTrue(protocol.call("delete_host_group", Map.of("group_id", group)).toString().contains("DELETE_HOSTS"));
            var editing = settings.load().mcpPermissions().with(AutomationCapability.EDIT_HOSTS, AutomationPermission.ALLOW);
            settings.save(new AiSettings(defaults.providers(), defaults.selectedProviderId(), defaults.builtInPermissions(),
                    editing, List.of(), List.of(), defaults.mcpServer()));
            protocol.ok("update_host_group", groupPatch);
            assertEquals("新名称", protocol.ok("list_host_groups", Map.of()).path("result").get(0).path("name").asText());

            String fixture = " \"" + java.util.UUID.randomUUID() + "\\密码\r\n ";
            String passphrase = " " + java.util.UUID.randomUUID() + "口令 ";
            String privateKey = generatePrivateKeyText();
            String credentialGroup = protocol.id("add_credential_group", Map.of("name", "Credentials"));
            protocol.ok("update_credential_group", Map.of("group_id", credentialGroup, "name", "Renamed credentials"));
            assertTrue(protocol.call("add_credential", Map.of("label", "Invalid", "username", "test"))
                    .path("isError").asBoolean());
            String credential = protocol.id("add_credential", Map.of("label", "Fixture", "username", "test",
                    "type", "PASSWORD", "password", fixture, "group_id", credentialGroup));
            assertFalse(protocol.ok("list_credentials", Map.of()).toString().contains(fixture));
            String jump = protocol.id("add_host", Map.of("name", "Jump", "host", "jump.invalid", "username", "test"));
            String target = protocol.id("add_host", Map.of("name", "Target", "host", "target.invalid", "port", 2222,
                    "username", "test", "credential_id", credential, "group_id", group, "jump_host_id", jump));
            var changed = protocol.ok("update_host", Map.of("host_id", target, "name", "Renamed host", "favorite", true));
            assertEquals(2222, changed.path("port").asInt());
            assertEquals(credential, changed.path("credentialId").asText());
            assertTrue(changed.path("favorite").asBoolean());
            assertStoredAuthentication(workflow, hosts, target, fixture, null);
            String separateHost = protocol.id("add_host", Map.of("name", "Own credential", "host", "own.invalid",
                    "username", "test", "password", fixture));
            var before = protocol.ok("get_host", Map.of("host_id", separateHost));
            var previousSecret = com.ynpe.fastremote.domain.vault.SecretId.parse(before.path("credentialId").asText());
            assertEquals(com.ynpe.fastremote.domain.credential.CredentialScope.HOST_BOUND,
                    credentialRepository.find(previousSecret).orElseThrow().scope());
            assertStoredAuthentication(workflow, hosts, separateHost, fixture, null);
            assertTrue(protocol.call("update_host", Map.of("host_id", separateHost, "credential_id", credential,
                    "password", fixture)).path("isError").asBoolean());
            assertTrue(protocol.call("update_host", Map.of("host_id", separateHost, "passphrase", passphrase))
                    .path("isError").asBoolean());
            assertTrue(protocol.call("update_host", Map.of("host_id", separateHost, "password", fixture,
                    "private_key", privateKey)).path("isError").asBoolean());
            assertTrue(protocol.call("update_host", Map.of("host_id", separateHost, "password", "密".repeat(21_846)))
                    .path("isError").asBoolean());
            assertTrue(protocol.call("update_host", Map.of("host_id", separateHost, "jump_host_id", separateHost,
                    "private_key", privateKey)).path("isError").asBoolean());
            assertEquals(2, credentialRepository.list().size());
            assertStoredAuthentication(workflow, hosts, separateHost, fixture, null);
            var replaced = protocol.ok("update_host", Map.of("host_id", separateHost, "private_key", privateKey,
                    "passphrase", passphrase));
            assertEquals("PRIVATE_KEY", replaced.path("authenticationType").asText());
            assertFalse(replaced.has("private_key"));
            assertFalse(replaced.has("passphrase"));
            assertEquals(previousSecret.toString(), replaced.path("credentialId").asText());
            assertStoredAuthentication(workflow, hosts, separateHost, passphrase, privateKey);
            assertStoredAuthentication(workflow, hosts, target, fixture, null);
            assertTrue(protocol.call("delete_host", Map.of("host_id", jump)).toString().contains("DELETE_HOSTS"));
            settings.save(new AiSettings(defaults.providers(), defaults.selectedProviderId(), defaults.builtInPermissions(),
                    editing.with(AutomationCapability.DELETE_HOSTS, AutomationPermission.ALLOW),
                    List.of(), List.of(), defaults.mcpServer()));
            assertTrue(protocol.call("delete_credential", Map.of("credential_id", credential)).path("isError").asBoolean());
            assertTrue(protocol.call("delete_host_group", Map.of("group_id", group)).path("isError").asBoolean());
            protocol.ok("update_credential", Map.of("credential_id", credential, "username", "replacement"));
            assertEquals("replacement", protocol.ok("get_host", Map.of("host_id", target)).path("username").asText());
            assertStoredAuthentication(workflow, hosts, target, fixture, null);
            assertTrue(protocol.call("update_credential", Map.of("credential_id", credential, "type", "PASSWORD",
                    "private_key", privateKey)).path("isError").asBoolean());
            assertEquals(credential, protocol.id("update_credential", Map.of("credential_id", credential,
                    "private_key", privateKey, "passphrase", passphrase)));
            assertStoredAuthentication(workflow, hosts, target, passphrase, privateKey);
            assertEquals("PRIVATE_KEY", protocol.ok("get_host", Map.of("host_id", target)).path("authenticationType").asText());
            assertEquals("", protocol.ok("list_credentials", Map.of()).path("result").get(0).path("password").asText());
            String persisted = Files.readString(directory.resolve("config.yaml"));
            assertFalse(persisted.contains(fixture));
            assertFalse(persisted.contains(passphrase));
            assertFalse(persisted.contains("BEGIN PRIVATE KEY"));
            assertTrue(audit.stream().noneMatch(event -> event.detail().contains(fixture)
                    || event.detail().contains(passphrase) || event.detail().contains("BEGIN PRIVATE KEY")));
            assertEquals("Renamed host", protocol.ok("delete_host", Map.of("host_id", jump))
                    .path("changedToDirect").get(0).asText());
            assertEquals("", protocol.ok("get_host", Map.of("host_id", target)).path("jumpHostId").asText());
            protocol.ok("delete_host", Map.of("host_id", target));
            protocol.ok("delete_host", Map.of("host_id", separateHost));
            protocol.ok("delete_host_group", Map.of("group_id", group));
            protocol.ok("delete_credential", Map.of("credential_id", credential));
            protocol.ok("delete_credential_group", Map.of("group_id", credentialGroup));
            assertTrue(credentialRepository.list().isEmpty());

            String commandGroup = protocol.id("add_command_group", Map.of("name", "Commands"));
            String command = protocol.id("add_command", Map.of("name", "Example", "command", "printf test",
                    "description", "", "group_id", commandGroup));
            protocol.ok("update_command_group", Map.of("group_id", commandGroup, "name", "Renamed commands"));
            protocol.ok("update_command", Map.of("command_id", command, "command", "printf updated"));
            assertEquals("printf updated", commands.listSnippets().getFirst().content());
            protocol.ok("delete_command", Map.of("command_id", command));
            protocol.ok("delete_command_group", Map.of("group_id", commandGroup));

            String variableGroup = protocol.id("add_variable_group", Map.of("name", "Variables"));
            String variable = protocol.id("add_variable", Map.of("name", "Example", "variable_name", "example",
                    "description", "", "delimiter", "", "type", "SINGLE", "value", "before", "group_id", variableGroup));
            protocol.ok("update_variable_group", Map.of("group_id", variableGroup, "name", "Renamed variables"));
            protocol.ok("update_variable", Map.of("variable_id", variable, "value", "changed", "group_id", ""));
            assertEquals("changed", commands.listVariables().getFirst().value());
            assertTrue(commands.listVariables().getFirst().groupId().isEmpty());
            protocol.ok("delete_variable", Map.of("variable_id", variable));
            protocol.ok("delete_variable_group", Map.of("group_id", variableGroup));
            assertTrue(notifications.get() >= 20);
            assertTrue(hosts.listHosts().isEmpty());

            vault.lock();
            assertTrue(protocol.call("add_credential", Map.of("label", "Locked vault", "username", "test",
                    "password", fixture)).path("isError").asBoolean());
            assertTrue(protocol.call("add_host", Map.of("name", "Locked vault", "host", "locked.invalid",
                    "username", "test", "private_key", privateKey)).path("isError").asBoolean());
            assertTrue(credentialRepository.list().isEmpty());
            assertTrue(hosts.listHosts().isEmpty());
        }
    }

    private static String generatePrivateKeyText() throws Exception {
        var generator = java.security.KeyPairGenerator.getInstance("Ed25519");
        byte[] encoded = generator.generateKeyPair().getPrivate().getEncoded();
        try {
            return "-----BEGIN PRIVATE KEY-----\r\n"
                    + java.util.Base64.getMimeEncoder(64, new byte[] {'\r', '\n'}).encodeToString(encoded)
                    + "\r\n-----END PRIVATE KEY-----\r\n";
        } finally {
            java.util.Arrays.fill(encoded, (byte) 0);
        }
    }

    private static void assertStoredAuthentication(SqliteHostCatalogWorkflow workflow,
            HostCatalogRepository hosts, String hostId,
            String expectedSecret, String expectedKey) throws Exception {
        var host = hosts.findHost(HostId.parse(hostId)).orElseThrow();
        assertTrue(host.profile().privateKeyPath().isEmpty());
        try (var resolved = workflow.loadCredential(host).toCompletableFuture().get(5, TimeUnit.SECONDS)) {
            char[] secret = resolved.copySecret();
            byte[] key = resolved.copyPrivateKey();
            try {
                assertTrue(java.nio.CharBuffer.wrap(secret).equals(java.nio.CharBuffer.wrap(expectedSecret)));
                if (expectedKey == null) assertNull(key);
                else assertTrue(java.nio.ByteBuffer.wrap(key).equals(StandardCharsets.UTF_8.encode(expectedKey)));
            } finally {
                java.util.Arrays.fill(secret, '\0');
                if (key != null) java.util.Arrays.fill(key, (byte) 0);
            }
        }
    }

    private record CatalogProtocol(HttpClient client, URI endpoint, String token) {
        JsonNode call(String name, Map<String, Object> arguments) throws Exception {
            var json = new ObjectMapper();
            String request = json.writeValueAsString(Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/call",
                    "params", Map.of("name", name, "arguments", arguments)));
            return toolResult(json, postMcp(client, endpoint, token, request));
        }

        JsonNode ok(String name, Map<String, Object> arguments) throws Exception {
            JsonNode response = call(name, arguments);
            assertFalse(response.path("isError").asBoolean(true), () -> name + " " + response);
            return response.path("structuredContent");
        }

        String id(String name, Map<String, Object> arguments) throws Exception {
            var id = ok(name, arguments).path("id");
            return id.isObject() ? id.path("value").asText() : id.asText();
        }
    }

    private static HttpResponse<String> postMcp(HttpClient client, URI endpoint, String token, String request)
            throws Exception {
        return client.send(HttpRequest.newBuilder(endpoint).timeout(java.time.Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(request)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonNode toolResult(ObjectMapper json, HttpResponse<String> response) throws IOException {
        assertEquals(200, response.statusCode());
        return json.readTree(response.body()).path("result");
    }

    private static BoundedAutomationGateway gateway(SavedHost host, AiSettingsRepository settings) {
        return gateway(new OneHostRepository(host), settings);
    }

    private static BoundedAutomationGateway gateway(HostCatalogRepository repository, AiSettingsRepository settings) {
        return new BoundedAutomationGateway(new RejectingConnector(), repository,
                value -> CompletableFuture.failedFuture(new AssertionError("credential resolver must not run")),
                new GlobalSettingsRepository() {
                    @Override public GlobalSettings load() { return GlobalSettings.defaults(); }
                    @Override public void save(GlobalSettings value) { }
                }, settings, new EmptySnippetRepository());
    }

    private static AiSettings withBuiltInPermissions(AiSettings settings, AutomationPermissions permissions) {
        return new AiSettings(settings.providers(), settings.selectedProviderId(), permissions,
                settings.mcpPermissions(), settings.allowedLocalRoots(), settings.allowedRemoteRoots(),
                settings.maxTransferBytes(), settings.maxToolRounds(), settings.mcpServer());
    }

    private static void assertPermissionDenied(Runnable action) {
        CompletionException failure = org.junit.jupiter.api.Assertions.assertThrows(
                CompletionException.class, action::run);
        assertInstanceOf(SecurityException.class, failure.getCause());
    }

    private static final class EmptySnippetRepository implements SnippetRepository {
        private final List<SnippetGroup> groups = new java.util.ArrayList<>();
        private final List<Snippet> snippets = new java.util.ArrayList<>();
        private final List<SnippetVariableGroup> variableGroups = new java.util.ArrayList<>();
        private final List<SnippetVariable> variables = new java.util.ArrayList<>();
        @Override public List<SnippetGroup> listGroups() { return List.copyOf(groups); }
        @Override public List<Snippet> listSnippets() { return List.copyOf(snippets); }
        @Override public void saveGroup(SnippetGroup value) { groups.add(value); }
        @Override public void deleteGroup(SnippetGroupId id) { groups.removeIf(item -> item.id().equals(id)); }
        @Override public void saveSnippet(Snippet value) { snippets.removeIf(item -> item.id().equals(value.id())); snippets.add(value); }
        @Override public void saveSnippets(List<Snippet> values) { values.forEach(this::saveSnippet); }
        @Override public void deleteSnippet(SnippetId id) { snippets.removeIf(item -> item.id().equals(id)); }
        @Override public List<SnippetVariableGroup> listVariableGroups() { return List.copyOf(variableGroups); }
        @Override public List<SnippetVariable> listVariables() { return List.copyOf(variables); }
        @Override public void saveVariableGroup(SnippetVariableGroup value) { variableGroups.add(value); }
        @Override public void deleteVariableGroup(SnippetVariableGroupId id) { variableGroups.removeIf(item -> item.id().equals(id)); }
        @Override public void saveVariable(SnippetVariable value) { variables.removeIf(item -> item.id().equals(value.id())); variables.add(value); }
        @Override public void saveVariables(List<SnippetVariable> values) { values.forEach(this::saveVariable); }
        @Override public void saveLibraryImport(List<SnippetGroup> groups, List<Snippet> snippets,
                List<SnippetVariableGroup> variableGroups, List<SnippetVariable> variables) { }
        @Override public void deleteVariable(SnippetVariableId id) { variables.removeIf(item -> item.id().equals(id)); }
    }

    private static SavedHost host() {
        var profile = new ConnectionProfile(new Host("example.internal"), new Port(22), new Username("dev"),
                AuthenticationType.PASSWORD, Optional.empty());
        return SavedHost.create("server", Optional.empty(), profile, 0);
    }

    private static final class MutableAiSettings implements AiSettingsRepository {
        private AiSettings value;
        private MutableAiSettings(AiSettings value) { this.value = value; }
        @Override public AiSettings load() { return value; }
        @Override public void save(AiSettings settings) { value = settings; }
    }

    private static final class RejectingConnector implements SessionConnector {
        @Override public TerminalConnection connect(ConnectionProfile profile, Credentials credentials,
                HostKeyPrompt prompt) throws ConnectionException { throw new AssertionError("connector must not run"); }
        @Override public void close() { }
    }

    private static class OneHostRepository implements HostCatalogRepository {
        private final SavedHost host;
        private OneHostRepository(SavedHost host) { this.host = host; }
        @Override public List<HostGroup> listGroups() { return List.of(); }
        @Override public List<SavedHost> searchHosts(String query, int limit) { return List.of(host); }
        @Override public List<SavedHost> listHosts() { return List.of(host); }
        @Override public Optional<SavedHost> findHost(HostId id) { return host.id().equals(id) ? Optional.of(host) : Optional.empty(); }
        @Override public List<RecentConnection> listRecentConnections(int limit) { return List.of(); }
        @Override public void recordSuccessfulConnection(Optional<HostId> id, ConnectionProfile profile, Instant at) { }
        @Override public void saveGroup(HostGroup group) { throw new UnsupportedOperationException(); }
        @Override public void moveGroup(GroupId id, Optional<GroupId> parentId, int targetIndex) { throw new UnsupportedOperationException(); }
        @Override public void deleteGroup(GroupId id) { throw new UnsupportedOperationException(); }
        @Override public void saveHost(SavedHost value) { throw new UnsupportedOperationException(); }
        @Override public void moveHost(HostId id, Optional<GroupId> groupId, int targetIndex) { throw new UnsupportedOperationException(); }
        @Override public List<String> deleteHost(HostId id) { throw new UnsupportedOperationException(); }
    }
}
