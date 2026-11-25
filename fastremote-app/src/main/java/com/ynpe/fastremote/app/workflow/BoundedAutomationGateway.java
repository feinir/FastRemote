// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;
import static com.ynpe.fastremote.app.workflow.AutomationDiagnostics.*;

import static com.ynpe.fastremote.app.workflow.AutomationLocalFiles.MAX_LOCAL_ENTRIES;
import static com.ynpe.fastremote.app.workflow.AutomationLibraryOperations.summary;

import com.ynpe.fastremote.app.workflow.AutomationExecution.CheckedSupplier;

import com.ynpe.fastremote.domain.ai.AiSettings;
import com.ynpe.fastremote.domain.ai.AiSettingsRepository;
import com.ynpe.fastremote.domain.automation.AutomationApprovalHandler;
import com.ynpe.fastremote.domain.automation.AutomationApprovalRequest;
import com.ynpe.fastremote.domain.automation.AutomationAuditEvent;
import com.ynpe.fastremote.domain.automation.AutomationAuditPhase;
import com.ynpe.fastremote.domain.automation.AutomationCapability;
import com.ynpe.fastremote.domain.automation.AutomationCatalogTool;
import com.ynpe.fastremote.domain.credential.GlobalCredentialRepository;
import com.ynpe.fastremote.domain.automation.AutomationLibrary;
import com.ynpe.fastremote.domain.automation.AutomationLocalListing;
import com.ynpe.fastremote.domain.automation.AutomationOperationId;
import com.ynpe.fastremote.domain.automation.AutomationOperationStatus;
import com.ynpe.fastremote.domain.automation.AutomationPermission;
import com.ynpe.fastremote.domain.automation.AutomationPermissions;
import com.ynpe.fastremote.domain.automation.AutomationHost;
import com.ynpe.fastremote.domain.automation.AutomationHostGroup;
import com.ynpe.fastremote.domain.automation.AutomationCommand;
import com.ynpe.fastremote.domain.automation.AutomationVariable;
import com.ynpe.fastremote.domain.automation.AutomationHostActivationHandler;
import com.ynpe.fastremote.domain.automation.AutomationSource;
import com.ynpe.fastremote.domain.automation.RemoteHostSystem;
import com.ynpe.fastremote.domain.automation.AutomationToolWindow;
import com.ynpe.fastremote.domain.automation.AutomationWorkspaceScope;
import com.ynpe.fastremote.domain.automation.AutomationWorkspaceItem;
import com.ynpe.fastremote.domain.automation.InteractiveAutomationGateway;
import com.ynpe.fastremote.domain.catalog.SavedHost;
import com.ynpe.fastremote.domain.catalog.HostCatalogRepository;
import com.ynpe.fastremote.domain.catalog.HostId;
import com.ynpe.fastremote.domain.catalog.GroupColor;
import com.ynpe.fastremote.domain.connection.AuthenticationType;
import com.ynpe.fastremote.domain.connection.ConnectionHop;
import com.ynpe.fastremote.domain.connection.Credentials;
import com.ynpe.fastremote.domain.connection.PasswordCredentials;
import com.ynpe.fastremote.domain.connection.PrivateKeyCredentials;
import com.ynpe.fastremote.domain.connection.SessionConnector;
import com.ynpe.fastremote.domain.security.HostKeyDecision;
import com.ynpe.fastremote.domain.security.HostKeyPrompt;
import com.ynpe.fastremote.domain.settings.GlobalSettingsRepository;
import com.ynpe.fastremote.domain.snippet.Snippet;
import com.ynpe.fastremote.domain.snippet.SnippetGroup;
import com.ynpe.fastremote.domain.snippet.SnippetGroupId;
import com.ynpe.fastremote.domain.snippet.SnippetRepository;
import com.ynpe.fastremote.domain.snippet.SnippetVariable;
import com.ynpe.fastremote.domain.snippet.SnippetVariableGroup;
import com.ynpe.fastremote.domain.snippet.SnippetVariableGroupId;
import com.ynpe.fastremote.domain.snippet.SnippetVariableType;
import com.ynpe.fastremote.domain.terminal.RemoteCommandResult;
import com.ynpe.fastremote.domain.transfer.RemoteFileEntry;
import com.ynpe.fastremote.domain.transfer.TransferConflictChoice;
import com.ynpe.fastremote.ui.ResolvedCredential;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Shared, bounded execution boundary used by both the built-in assistant and MCP. */
public final class BoundedAutomationGateway implements InteractiveAutomationGateway {
    private static final AutomationApprovalHandler REJECT_APPROVAL =
            request -> CompletableFuture.completedFuture(false);
    private static final HostKeyPrompt REJECT_HOST_KEY = check -> HostKeyDecision.REJECT;
    private static final AutomationHostActivationHandler REJECT_ACTIVATION =
            hostId -> CompletableFuture.completedFuture(false);
    private static final com.ynpe.fastremote.domain.automation.AutomationUiOperationHandler NO_UI =
            new com.ynpe.fastremote.domain.automation.AutomationUiOperationHandler() {
                @Override public CompletionStage<Boolean> runTerminalCommand(HostId id, String command) {
                    return CompletableFuture.completedFuture(false);
                }
                @Override public CompletionStage<Boolean> uploadInSftp(HostId id, List<Path> sources, String target) {
                    return CompletableFuture.completedFuture(false);
                }
                @Override public CompletionStage<Boolean> downloadInSftp(HostId id, String source, Path target) {
                    return CompletableFuture.completedFuture(false);
                }
            };
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(60);
    private static final int COMMAND_OUTPUT_LIMIT = 256 * 1024;
    private static final Duration APPROVAL_TIMEOUT = Duration.ofMinutes(2);

    private static final int MAX_TEXT_BYTES = 256 * 1024;
    static final int MAX_CONCURRENT_OPERATIONS = 8;

    private final AutomationLibraryOperations library;
    private final Optional<AutomationCatalogOperations> catalog;
    private final AutomationLocalFiles localFiles;
    private final AutomationCommands commands;
    private final AutomationRemoteFiles remoteFiles;
    private final SessionConnector connector;
    private final HostCatalogRepository hosts;
    private final java.util.function.Function<SavedHost, CompletionStage<ResolvedCredential>> credentialResolver;
    private final GlobalSettingsRepository globalSettings;
    private final AiSettingsRepository aiSettings;
    private final SnippetRepository snippets;
    private final com.ynpe.fastremote.domain.license.LicenseAccess licenseAccess;
    private final WorkflowThreadPool executor;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<AutomationApprovalHandler> approval = new AtomicReference<>(REJECT_APPROVAL);
    private final AtomicReference<HostKeyPrompt> hostKeyPrompt = new AtomicReference<>(REJECT_HOST_KEY);
    private final AtomicReference<AutomationHostActivationHandler> hostActivation = new AtomicReference<>(REJECT_ACTIVATION);
    private final AtomicReference<java.util.function.Consumer<AutomationAuditEvent>> audit =
            new AtomicReference<>(ignored -> { });
    private final AtomicReference<Runnable> libraryChanged = new AtomicReference<>(() -> { });
    private final AtomicReference<com.ynpe.fastremote.domain.automation.AutomationUiOperationHandler> uiOperations =
            new AtomicReference<>(NO_UI);

    public BoundedAutomationGateway(
            SessionConnector connector,
            HostCatalogRepository hosts,
            java.util.function.Function<SavedHost, CompletionStage<ResolvedCredential>> credentialResolver,
            GlobalSettingsRepository globalSettings,
            AiSettingsRepository aiSettings,
            SnippetRepository snippets) {
        this(connector, hosts, credentialResolver, globalSettings, aiSettings, snippets,
                ignored -> CompletableFuture.completedFuture(true));
    }

    public BoundedAutomationGateway(
            SessionConnector connector,
            HostCatalogRepository hosts,
            java.util.function.Function<SavedHost, CompletionStage<ResolvedCredential>> credentialResolver,
            GlobalSettingsRepository globalSettings,
            AiSettingsRepository aiSettings,
            SnippetRepository snippets,
            com.ynpe.fastremote.domain.license.LicenseAccess licenseAccess) {
        this(connector, hosts, credentialResolver, globalSettings, aiSettings, snippets, licenseAccess, null, null);
    }

    public BoundedAutomationGateway(
            SessionConnector connector,
            HostCatalogRepository hosts,
            java.util.function.Function<SavedHost, CompletionStage<ResolvedCredential>> credentialResolver,
            GlobalSettingsRepository globalSettings,
            AiSettingsRepository aiSettings,
            SnippetRepository snippets,
            com.ynpe.fastremote.domain.license.LicenseAccess licenseAccess,
            GlobalCredentialRepository credentials,
            SqliteHostCatalogWorkflow catalogWorkflow) {
        this.connector = Objects.requireNonNull(connector, "connector");
        this.hosts = Objects.requireNonNull(hosts, "hosts");
        this.credentialResolver = Objects.requireNonNull(credentialResolver, "credentialResolver");
        this.globalSettings = Objects.requireNonNull(globalSettings, "globalSettings");
        this.aiSettings = Objects.requireNonNull(aiSettings, "aiSettings");
        this.snippets = Objects.requireNonNull(snippets, "snippets");
        this.licenseAccess = Objects.requireNonNull(licenseAccess, "licenseAccess");
        executor = new WorkflowThreadPool(MAX_CONCURRENT_OPERATIONS, MAX_CONCURRENT_OPERATIONS,
                30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32),
                Thread.ofPlatform().name("fastremote-automation-", 0).daemon(false).factory(),
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        AutomationExecution execution = new AutomationExecution() {
            @Override public <T> CompletionStage<T> submit(CheckedSupplier<T> action) {
                return BoundedAutomationGateway.this.submit(action);
            }
            @Override public <T> T audited(AutomationSource source, AutomationCapability capability,
                    SavedHost target, String summary, String detail, CheckedSupplier<T> action) throws Exception {
                return BoundedAutomationGateway.this.audited(source, capability, target, summary, detail, action);
            }
            @Override public <T> T auditedGlobal(AutomationSource source, AutomationCapability capability,
                    String target, String summary, String detail, CheckedSupplier<T> action) throws Exception {
                return BoundedAutomationGateway.this.auditedGlobal(source, capability, target, summary, detail, action);
            }
            @Override public void requirePermission(AutomationSource source, AutomationCapability capability,
                    SavedHost target, String summary, String detail) throws Exception {
                BoundedAutomationGateway.this.requirePermission(source, capability, target, summary, detail);
            }
        };
        library = new AutomationLibraryOperations(execution, hosts, snippets, this::notifyLibraryChanged);
        localFiles = new AutomationLocalFiles(execution, aiSettings);
        catalog = Optional.ofNullable(catalogWorkflow).map(workflow ->
                new AutomationCatalogOperations(hosts, Objects.requireNonNull(credentials), snippets, workflow));
        commands = new AutomationCommands(execution, new AutomationCommands.Commands() {
            @Override public SavedHost requireHost(HostId id) throws Exception {
                return BoundedAutomationGateway.this.requireHost(id);
            }
            @Override public RemoteCommandResult execute(SavedHost target, String command) throws Exception {
                try (AutomationRoute route = resolveRoute(target);
                     var connection = connector.connect(route.hops(), hostKeyPrompt.get(), ignored -> {},
                             globalSettings.load().connectionSettings())) {
                    return connection.executeCommand(command, COMMAND_TIMEOUT, COMMAND_OUTPUT_LIMIT);
                }
            }
        }, uiOperations::get);
        remoteFiles = new AutomationRemoteFiles(execution,new AutomationRemoteFiles.Access() {
            public SavedHost requireHost(HostId id) throws Exception { return BoundedAutomationGateway.this.requireHost(id); }
            public AutomationRoute resolveRoute(SavedHost host) throws Exception { return BoundedAutomationGateway.this.resolveRoute(host); }
            public void emit(AutomationSource source, AutomationAuditPhase phase, AutomationCapability capability,
                    String action,String target,String detail,Duration duration) {
                BoundedAutomationGateway.this.emit(source,phase,capability,action,target,detail,duration);
            }
        },connector,globalSettings,aiSettings,hostKeyPrompt::get,uiOperations::get,localFiles,executor,closed);


    }

    @Override public CompletionStage<Object> catalogTool(
            AutomationSource source, AutomationCatalogTool tool, Map<String, Object> arguments) {
        tool.validate(arguments);
        var changes = new HashMap<>(arguments);
        changes.values().removeIf(Objects::isNull);
        Map<String, Object> inputs = Map.copyOf(changes);
        var capability = switch (tool) {
            case GET_HOST, LIST_CREDENTIALS, LIST_CREDENTIAL_GROUPS -> AutomationCapability.READ_HOST_METADATA;
            case ADD_HOST, ADD_HOST_GROUP, ADD_CREDENTIAL, ADD_CREDENTIAL_GROUP -> AutomationCapability.CREATE_HOSTS;
            case DELETE_HOST, DELETE_HOST_GROUP, DELETE_CREDENTIAL, DELETE_CREDENTIAL_GROUP -> AutomationCapability.DELETE_HOSTS;
            case UPDATE_HOST, UPDATE_HOST_GROUP, UPDATE_CREDENTIAL, UPDATE_CREDENTIAL_GROUP -> AutomationCapability.EDIT_HOSTS;
            case UPDATE_COMMAND, UPDATE_COMMAND_GROUP, DELETE_COMMAND_GROUP,
                    UPDATE_VARIABLE, UPDATE_VARIABLE_GROUP, DELETE_VARIABLE_GROUP -> AutomationCapability.MANAGE_LIBRARY;
        };
        String detail = List.of("name", "label", "host", "host_id", "group_id", "parent_id", "jump_host_id",
                "credential_id", "command_id", "variable_id").stream()
                .filter(inputs::containsKey).map(key -> {
                    String value = String.valueOf(inputs.get(key)).replaceAll("\\p{Cntrl}", " ");
                    return key + "=" + value.substring(0, Math.min(value.length(), 255));
                })
                .collect(java.util.stream.Collectors.joining(", "));
        return submit(() -> auditedGlobal(source, capability, "FastRemote catalogs", tool.toolName(), detail, () -> {
            Object result = catalog.orElseThrow(() -> new IllegalStateException("Catalog management is unavailable"))
                    .execute(tool, inputs).toCompletableFuture().get();
            if (!tool.readOnly()) notifyLibraryChanged();
            if (tool == AutomationCatalogTool.DELETE_HOST && result instanceof Map<?, ?> deleted
                    && deleted.get("changedToDirect") instanceof List<?> names && !names.isEmpty()) {
                emit(source, AutomationAuditPhase.SUCCEEDED, capability, "Jump host deleted",
                        String.valueOf(deleted.get("name")), "Changed to direct connections: "
                                + names.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", ")), null);
            }
            return result;
        }));
    }

    @Override public void setApprovalHandler(AutomationApprovalHandler handler) {
        approval.set(Objects.requireNonNull(handler, "handler"));
    }

    @Override public void setHostKeyPrompt(HostKeyPrompt prompt) {
        hostKeyPrompt.set(Objects.requireNonNull(prompt, "prompt"));
    }

    @Override public void setHostActivationHandler(AutomationHostActivationHandler handler) {
        hostActivation.set(Objects.requireNonNull(handler, "handler"));
    }

    @Override public void setAuditHandler(java.util.function.Consumer<AutomationAuditEvent> handler) {
        audit.set(Objects.requireNonNull(handler, "handler"));
    }

    @Override public void setLibraryChangedHandler(Runnable handler) {
        libraryChanged.set(Objects.requireNonNull(handler, "handler"));
    }

    @Override public void setUiOperationHandler(
            com.ynpe.fastremote.domain.automation.AutomationUiOperationHandler handler) {
        uiOperations.set(Objects.requireNonNull(handler, "handler"));
    }

    @Override public long receivedBytes() { return remoteFiles.receivedBytes(); }

    @Override public long sentBytes() { return remoteFiles.sentBytes(); }

    @Override public void clearWindowHandlers() {
        approval.set(REJECT_APPROVAL);
        hostKeyPrompt.set(REJECT_HOST_KEY);
        hostActivation.set(REJECT_ACTIVATION);
        audit.set(ignored -> { });
        libraryChanged.set(() -> { });
        uiOperations.set(NO_UI);
    }

    @Override public CompletionStage<Boolean> openLocalTerminal(AutomationSource source) {
        return openLocalTerminal(source, false);
    }

    @Override public CompletionStage<Boolean> openLocalTerminal(
            AutomationSource source, boolean reuseExistingTab) {
        return submit(() -> audited(source, AutomationCapability.MANAGE_WORKSPACE, null,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.9a074af0017fe284"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.545477680ea6cdb0"), () -> {
                    boolean opened = uiOperations.get().openLocalTerminal(reuseExistingTab).toCompletableFuture()
                            .get(30, TimeUnit.SECONDS);
                    if (!opened) throw new IllegalStateException("FastRemote could not open a local terminal tab");
                    return true;
                }));
    }

    @Override public CompletionStage<Integer> closeWorkspaceTabs(
            AutomationSource source, AutomationWorkspaceScope scope) {
        AutomationWorkspaceScope safeScope = Objects.requireNonNull(scope, "scope");
        return submit(() -> audited(source, AutomationCapability.MANAGE_WORKSPACE, null,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.686f26fb98b437bd"), safeScope.name(), () ->
                        uiOperations.get().closeWorkspaceTabs(safeScope).toCompletableFuture()
                                .get(30, TimeUnit.SECONDS)));
    }

    @Override public CompletionStage<List<AutomationWorkspaceItem>> listWorkspace(
            AutomationSource source) {
        return submit(() -> auditedGlobal(source, AutomationCapability.READ_WORKSPACE,
                "FastRemote workspace", "Inspect open workspaces", "", () ->
                uiOperations.get().listWorkspace().toCompletableFuture().get(30, TimeUnit.SECONDS)));
    }

    @Override public CompletionStage<List<com.ynpe.fastremote.domain.automation.AutomationActiveHost>> listActiveHosts(
            AutomationSource source) {
        return submit(() -> auditedGlobal(source, AutomationCapability.READ_WORKSPACE,
                "FastRemote active hosts", "List active hosts", "", () ->
                {
                    var groups = AutomationHostGroup.describe(hosts.listGroups());
                    var active = uiOperations.get().listActiveHosts().toCompletableFuture().get(30, TimeUnit.SECONDS);
                    var result = new java.util.ArrayList<com.ynpe.fastremote.domain.automation.AutomationActiveHost>();
                    for (var item : active) result.add(new com.ynpe.fastremote.domain.automation.AutomationActiveHost(
                            item.id(), item.host(), item.workspaceIds(), hosts.findHost(item.id()).map(host -> summary(host, groups))));
                    return List.copyOf(result);
                }));
    }

    @Override public CompletionStage<com.ynpe.fastremote.domain.automation.AutomationConversationContext> conversationContext(
            AutomationSource source) {
        return submit(() -> auditedGlobal(source, AutomationCapability.READ_WORKSPACE,
                "FastRemote AI conversation", "Read associated host", "", () ->
                {
                    var context = uiOperations.get().conversationContext().toCompletableFuture().get(30, TimeUnit.SECONDS);
                    return describeContext(context);
                }));
    }

    @Override public CompletionStage<com.ynpe.fastremote.domain.automation.AutomationConversationContext> describeConversationContext(
            AutomationSource source, com.ynpe.fastremote.domain.automation.AutomationConversationContext context) {
        return submit(() -> auditedGlobal(source, AutomationCapability.READ_WORKSPACE,
                "FastRemote AI conversation", "Read associated host", "", () -> {
                    return describeContext(context);
                }));
    }

    private com.ynpe.fastremote.domain.automation.AutomationConversationContext describeContext(
            com.ynpe.fastremote.domain.automation.AutomationConversationContext context) throws java.io.IOException {
        var groups = AutomationHostGroup.describe(hosts.listGroups());
        Optional<AutomationHost> saved = context.associatedHostId().isEmpty() ? Optional.empty()
                : hosts.findHost(context.associatedHostId().orElseThrow()).map(host -> summary(host, groups));
        return new com.ynpe.fastremote.domain.automation.AutomationConversationContext(
                context.conversationId(), context.associatedHost(), context.associatedHostId(), saved);
    }

    @Override public CompletionStage<String> readWorkspaceText(
            AutomationSource source, String workspaceId) {
        String id = boundedWorkspaceId(workspaceId);
        return submit(() -> auditedGlobal(source, AutomationCapability.READ_WORKSPACE,
                "FastRemote workspace", "Read open editor", id, () ->
                uiOperations.get().readWorkspaceText(id).toCompletableFuture().get(30, TimeUnit.SECONDS)));
    }

    @Override public CompletionStage<Boolean> writeWorkspaceText(
            AutomationSource source, String workspaceId, String content) {
        String id = boundedWorkspaceId(workspaceId);
        String safeContent = Objects.requireNonNull(content, "content");
        if (safeContent.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_TEXT_BYTES) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Workspace editor text exceeds 256 KiB"));
        }
        return submit(() -> auditedGlobal(source, AutomationCapability.EDIT_WORKSPACE,
                "FastRemote workspace", "Edit open editor", id + " (" + safeContent.length() + " characters)", () -> {
                    boolean updated = uiOperations.get().writeWorkspaceText(id, safeContent)
                            .toCompletableFuture().get(30, TimeUnit.SECONDS);
                    if (!updated) throw new IllegalArgumentException("Open workspace editor was not found or is busy");
                    return true;
                }));
    }

    @Override public CompletionStage<com.ynpe.fastremote.domain.terminal.TerminalOutput> readTerminal(
            AutomationSource source, String workspaceId, String generation, long after, int maximum, boolean screen) {
        String id = boundedWorkspaceId(workspaceId);
        validateObservationRange(after, maximum, 0);
        return submit(() -> auditedGlobal(source, AutomationCapability.READ_TERMINAL_OUTPUT,
                "FastRemote terminal", "Read terminal output (may include sensitive remote data)", id, () ->
                uiOperations.get().readTerminal(id, generation, after, maximum, screen).toCompletableFuture().get(30, TimeUnit.SECONDS)));
    }
    @Override public CompletionStage<List<com.ynpe.fastremote.domain.automation.WorkspaceOperationReceipt>> listWorkspaceOperations(
            AutomationSource source, String workspaceId) {
        String id = boundedWorkspaceId(workspaceId);
        return submit(() -> auditedGlobal(source, AutomationCapability.READ_WORKSPACE,
                "FastRemote workspace", "List observed operations", id, () ->
                uiOperations.get().listWorkspaceOperations(id).toCompletableFuture().get(30, TimeUnit.SECONDS)));
    }
    @Override public CompletionStage<com.ynpe.fastremote.domain.automation.WorkspaceOperationSnapshot> readWorkspaceOperation(
            AutomationSource source, String operationId, long after, int maximum, int waitMillis) {
        String id = boundedWorkspaceId(operationId);
        validateObservationRange(after, maximum, waitMillis);
        return submit(() -> {
            var metadata = uiOperations.get().readWorkspaceOperation(id, 0, 1, 0).toCompletableFuture().get(30, TimeUnit.SECONDS);
            var capability = metadata.kind().equals("TERMINAL") ? AutomationCapability.READ_TERMINAL_OUTPUT
                    : AutomationCapability.READ_WORKSPACE_LOG;
            return auditedGlobal(source, capability, "FastRemote workspace", "Read operation result", id, () -> {
                long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMillis);
                while (true) {
                    var snapshot = uiOperations.get().readWorkspaceOperation(id, after, maximum, 0)
                            .toCompletableFuture().get(30, TimeUnit.SECONDS);
                    if (snapshot.terminalState() || System.nanoTime() >= deadline
                            || snapshot.state().equals("PAUSED")
                            || snapshot.detail().startsWith(
                                    com.ynpe.fastremote.domain.automation.AutomationExecutionGuidance.SFTP_CONFLICT_TIMEOUT)
                            || snapshot.output().map(output -> !output.text().isEmpty()).orElse(false)) return snapshot;
                    Thread.sleep(100);
                }
            });
        });
    }
    private static void validateObservationRange(long after, int maximum, int waitMillis) {
        if (after < 0 || maximum < 1 || maximum > 65536 || waitMillis < 0 || waitMillis > 30000)
            throw new IllegalArgumentException("Invalid observation cursor, limit or wait duration");
    }
    @Override public CompletionStage<com.ynpe.fastremote.domain.automation.WorkspaceOperationReceipt> startTerminalCommand(
            AutomationSource source, HostId id, String command, boolean reuse) {
        return commands.startTerminalCommand(source, id, command, reuse);
    }
    @Override public CompletionStage<com.ynpe.fastremote.domain.automation.WorkspaceOperationReceipt> startLocalTerminalCommand(
            AutomationSource source, String command, boolean reuse) {
        return commands.startLocalTerminalCommand(source, command, reuse);
    }
    @Override public CompletionStage<com.ynpe.fastremote.domain.automation.WorkspaceOperationReceipt> startSftpUpload(
            AutomationSource source, HostId id, List<Path> sources, String directory,
            com.ynpe.fastremote.domain.transfer.TransferConflictChoice choice, boolean reuse) {
        return remoteFiles.startSftpUpload(source, id, sources, directory, choice, reuse);
    }
    @Override public CompletionStage<com.ynpe.fastremote.domain.automation.WorkspaceOperationReceipt> startSftpDownload(
            AutomationSource source, HostId id, String remote, Path local, boolean reuse) {
        return remoteFiles.startSftpDownload(source, id, remote, local, reuse);
    }

    @Override public CompletionStage<String> readWorkspaceLog(
            AutomationSource source, String workspaceId) {
        String id = boundedWorkspaceId(workspaceId);
        return submit(() -> auditedGlobal(source, AutomationCapability.READ_WORKSPACE_LOG,
                "FastRemote workspace", "Read workspace log", id, () ->
                uiOperations.get().readWorkspaceLog(id).toCompletableFuture().get(30, TimeUnit.SECONDS)));
    }

    @Override public CompletionStage<Boolean> showToolWindow(
            AutomationSource source, AutomationToolWindow window) {
        AutomationToolWindow safeWindow = Objects.requireNonNull(window, "window");
        return submit(() -> audited(source, AutomationCapability.MANAGE_WORKSPACE, null,
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.ef78e3e24eb66c0f"), safeWindow.name(), () ->
                        uiOperations.get().showToolWindow(safeWindow).toCompletableFuture()
                                .get(30, TimeUnit.SECONDS)));
    }

    @Override public CompletionStage<Boolean> showImportDialog(
            AutomationSource source, AutomationLibrary library) {
        return showLibraryDialog(source, Objects.requireNonNull(library, "library"), true);
    }

    @Override public CompletionStage<Boolean> showExportDialog(
            AutomationSource source, AutomationLibrary library) {
        return showLibraryDialog(source, Objects.requireNonNull(library, "library"), false);
    }

    private CompletionStage<Boolean> showLibraryDialog(
            AutomationSource source, AutomationLibrary library, boolean importing) {
        String action = importing ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.913f76c16636334b") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.f85ca35d3c1bb38d");
        return submit(() -> audited(source, AutomationCapability.IMPORT_EXPORT, null, action, library.name(), () ->
                (importing ? uiOperations.get().showImportDialog(library)
                        : uiOperations.get().showExportDialog(library)).toCompletableFuture()
                        .get(30, TimeUnit.SECONDS)));
    }

    @Override
    public CompletionStage<List<AutomationHost>> listHosts(AutomationSource source) {
        return library.listHosts(source);
    }

    @Override
    public CompletionStage<List<AutomationHost>> listHosts(
            AutomationSource source, Optional<String> query, int offset, int limit) {
        return library.listHosts(source, query, offset, limit);
    }

    @Override public CompletionStage<List<AutomationHostGroup>> listHostGroups(AutomationSource source) {
        return library.listHostGroups(source);
    }

    @Override public CompletionStage<List<AutomationCommand>> listCommandEntries(AutomationSource source) {
        return library.listCommandEntries(source);
    }

    @Override public CompletionStage<List<AutomationVariable>> listVariableEntries(AutomationSource source) {
        return library.listVariableEntries(source);
    }

    @Override public CompletionStage<List<Snippet>> listCommands(AutomationSource source) {
        return library.listCommands(source);
    }

    @Override public CompletionStage<List<SnippetGroup>> listCommandGroups(AutomationSource source) {
        return library.listCommandGroups(source);
    }

    @Override public CompletionStage<SnippetGroup> addCommandGroup(
            AutomationSource source, String name, GroupColor color) {
        return library.addCommandGroup(source, name, color);
    }

    @Override public CompletionStage<Snippet> addCommand(
            AutomationSource source, String name, String command, String description) {
        return library.addCommand(source, name, command, description);
    }

    @Override public CompletionStage<Snippet> addCommand(
            AutomationSource source, String name, String command, String description,
            Optional<SnippetGroupId> groupId) {
        return library.addCommand(source, name, command, description, groupId);
    }

    @Override public CompletionStage<AutomationCommand> addCommandEntry(AutomationSource source, String name,
            String command, String description, Optional<SnippetGroupId> groupId) {
        return library.addCommandEntry(source, name, command, description, groupId);
    }

    @Override public CompletionStage<Boolean> deleteCommand(AutomationSource source, String commandId) {
        return library.deleteCommand(source, commandId);
    }

    @Override public CompletionStage<List<SnippetVariable>> listVariables(AutomationSource source) {
        return library.listVariables(source);
    }

    @Override public CompletionStage<List<SnippetVariableGroup>> listVariableGroups(AutomationSource source) {
        return library.listVariableGroups(source);
    }

    @Override public CompletionStage<SnippetVariableGroup> addVariableGroup(
            AutomationSource source, String name, GroupColor color) {
        return library.addVariableGroup(source, name, color);
    }

    @Override public CompletionStage<SnippetVariable> addVariable(AutomationSource source, String name,
            String variableName, SnippetVariableType type, String delimiter, String value, String description) {
        return library.addVariable(source, name, variableName, type, delimiter, value, description);
    }

    @Override public CompletionStage<SnippetVariable> addVariable(AutomationSource source, String name,
            String variableName, SnippetVariableType type, String delimiter, String value, String description,
            Optional<SnippetVariableGroupId> groupId) {
        return library.addVariable(source, name, variableName, type, delimiter, value, description, groupId);
    }

    @Override public CompletionStage<AutomationVariable> addVariableEntry(AutomationSource source, String name,
            String variableName, SnippetVariableType type, String delimiter, String value, String description,
            Optional<SnippetVariableGroupId> groupId) {
        return library.addVariableEntry(source, name, variableName, type, delimiter, value, description, groupId);
    }

    @Override public CompletionStage<Boolean> deleteVariable(AutomationSource source, String variableId) {
        return library.deleteVariable(source, variableId);
    }

    @Override
    public CompletionStage<Boolean> activateHost(AutomationSource source, HostId hostId) {
        return activateHost(source, hostId, true);
    }

    @Override
    public CompletionStage<Boolean> activateHost(
            AutomationSource source, HostId hostId, boolean reuseExistingTab) {
        return submit(() -> {
            SavedHost target = requireHost(hostId);
            return audited(source, AutomationCapability.OPEN_HOST, target,
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.ba86677090f1e989"), "", () -> {
                        boolean activated = (reuseExistingTab
                                ? hostActivation.get().activate(target.id())
                                : uiOperations.get().openTerminal(target.id(), false)).toCompletableFuture()
                                .get(30, TimeUnit.SECONDS);
                        if (!activated) throw new IllegalStateException("FastRemote could not open the saved host tab");
                        return true;
                    });
        });
    }

    @Override
    public CompletionStage<Boolean> openSftp(AutomationSource source, HostId hostId) {
        return openSftp(source, hostId, false);
    }

    @Override
    public CompletionStage<Boolean> openSftp(
            AutomationSource source, HostId hostId, boolean reuseExistingTab) {
        return submit(() -> {
            SavedHost target = requireHost(hostId);
            return audited(source, AutomationCapability.OPEN_HOST, target,
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.7d85b3475e51f2b2"), "", () -> {
                        boolean opened = uiOperations.get().openSftp(target.id(), reuseExistingTab).toCompletableFuture()
                                .get(35, TimeUnit.SECONDS);
                        if (!opened) throw new IllegalStateException(
                                "FastRemote could not open the saved host SFTP tab");
                        return true;
                    });
        });
    }

    @Override
    public CompletionStage<Boolean> changeSftpRemoteDirectory(
            AutomationSource source, HostId hostId, String directory) {
        return remoteFiles.changeSftpRemoteDirectory(source, hostId, directory);
    }

    @Override
    public CompletionStage<Boolean> changeSftpLocalDirectory(
            AutomationSource source, HostId hostId, Path directory) {
        return remoteFiles.changeSftpLocalDirectory(source, hostId, directory);
    }

    @Override public CompletionStage<com.ynpe.fastremote.domain.terminal.LocalCommandResult> runLocalCommand(
            AutomationSource source, String command, Optional<Path> workingDirectory) {
        return commands.runLocalCommand(source, command, workingDirectory);
    }

    @Override
    public CompletionStage<RemoteCommandResult> runCommand(
            AutomationSource source, HostId hostId, String command) {
        return commands.runCommand(source, hostId, command);
    }
    @Override public CompletionStage<Boolean> runTerminalCommand(
            AutomationSource source, HostId hostId, String command) {
        return commands.runTerminalCommand(source, hostId, command);
    }
    @Override public CompletionStage<Boolean> runTerminalCommand(
            AutomationSource source, HostId hostId, String command, boolean reuseExistingTab) {
        return commands.runTerminalCommand(source, hostId, command, reuseExistingTab);
    }
    @Override public CompletionStage<Boolean> runLocalTerminalCommand(AutomationSource source, String command) {
        return commands.runLocalTerminalCommand(source, command);
    }
    @Override public CompletionStage<Boolean> runLocalTerminalCommand(
            AutomationSource source, String command, boolean reuseExistingTab) {
        return commands.runLocalTerminalCommand(source, command, reuseExistingTab);
    }

    @Override
    public CompletionStage<List<RemoteFileEntry>> listRemote(
            AutomationSource source, HostId hostId, String directory) {
        return remoteFiles.listRemote(source, hostId, directory);
    }

    @Override public CompletionStage<RemoteHostSystem> getRemoteSystem(AutomationSource source, HostId hostId) {
        return submit(() -> {
            SavedHost target = requireHost(hostId);
            return audited(source, AutomationCapability.RUN_COMMAND, target, "Detect remote operating system", "", () -> {
                try (AutomationRoute route = resolveRoute(target);
                     var connection = connector.connect(route.hops(), hostKeyPrompt.get(), ignored -> {},
                             globalSettings.load().connectionSettings())) {
                    return RemoteSystemProbe.detectSystem(connection);
                }
            });
        });
    }

    @Override public CompletionStage<String> readRemoteText(
            AutomationSource source, HostId hostId, String path) {
        return remoteFiles.readRemoteText(source, hostId, path);
    }

    @Override public CompletionStage<Boolean> writeRemoteText(
            AutomationSource source, HostId hostId, String path, String content, boolean overwrite) {
        return remoteFiles.writeRemoteText(source, hostId, path, content, overwrite);
    }

    @Override
    public CompletionStage<AutomationLocalListing> listLocal(
            AutomationSource source, Optional<Path> directory) {
        return localFiles.listLocal(source, directory);
    }

    @Override public CompletionStage<String> readLocalText(AutomationSource source, Path path) {
        return localFiles.readLocalText(source, path);
    }

    @Override public CompletionStage<Boolean> writeLocalText(
            AutomationSource source, Path path, String content, boolean overwrite) {
        return localFiles.writeLocalText(source, path, content, overwrite);
    }

    @Override public CompletionStage<Boolean> createLocalDirectory(AutomationSource source, Path path) {
        return localFiles.createLocalDirectory(source, path);
    }

    @Override public CompletionStage<Boolean> renameLocal(
            AutomationSource source, Path sourcePath, Path targetPath) {
        return localFiles.renameLocal(source, sourcePath, targetPath);
    }

    @Override public CompletionStage<Boolean> deleteLocal(
            AutomationSource source, Path path, boolean recursive) {
        return localFiles.deleteLocal(source, path, recursive);
    }

    @Override
    public CompletionStage<AutomationOperationId> upload(
            AutomationSource source, HostId hostId, Path localSource, String remoteTarget) {
        return remoteFiles.upload(source, hostId, localSource, remoteTarget);
    }

    @Override
    public CompletionStage<AutomationOperationId> uploadIntoDirectory(
            AutomationSource source, HostId hostId, List<Path> localSources, String remoteDirectory) {
        return remoteFiles.uploadIntoDirectory(source, hostId, localSources, remoteDirectory);
    }

    @Override
    public CompletionStage<AutomationOperationId> uploadIntoDirectory(
            AutomationSource source, HostId hostId, List<Path> localSources, String remoteDirectory,
            TransferConflictChoice conflictChoice) {
        return remoteFiles.uploadIntoDirectory(source, hostId, localSources, remoteDirectory, conflictChoice);
    }

    @Override
    public CompletionStage<Boolean> uploadInSftp(
            AutomationSource source, HostId hostId, List<Path> localSources, String remoteDirectory) {
        return remoteFiles.uploadInSftp(source, hostId, localSources, remoteDirectory);
    }

    @Override
    public CompletionStage<Boolean> uploadInSftp(
            AutomationSource source, HostId hostId, List<Path> localSources, String remoteDirectory,
            TransferConflictChoice conflictChoice) {
        return remoteFiles.uploadInSftp(source, hostId, localSources, remoteDirectory, conflictChoice);
    }

    @Override
    public CompletionStage<Boolean> uploadInSftp(
            AutomationSource source, HostId hostId, List<Path> localSources, String remoteDirectory,
            TransferConflictChoice conflictChoice, boolean reuseExistingTab) {
        return remoteFiles.uploadInSftp(source, hostId, localSources, remoteDirectory, conflictChoice, reuseExistingTab);
    }

    @Override
    public CompletionStage<AutomationOperationId> download(
            AutomationSource source, HostId hostId, String remoteSource, Path localTarget) {
        return remoteFiles.download(source, hostId, remoteSource, localTarget);
    }

    @Override
    public CompletionStage<Boolean> downloadInSftp(
            AutomationSource source, HostId hostId, String remoteSource, Path localDirectory) {
        return remoteFiles.downloadInSftp(source, hostId, remoteSource, localDirectory);
    }

    @Override
    public CompletionStage<Boolean> downloadInSftp(
            AutomationSource source, HostId hostId, String remoteSource, Path localDirectory,
            boolean reuseExistingTab) {
        return remoteFiles.downloadInSftp(source, hostId, remoteSource, localDirectory, reuseExistingTab);
    }

    @Override public CompletionStage<Boolean> createRemoteDirectory(
            AutomationSource source, HostId hostId, String path) {
        return remoteFiles.createRemoteDirectory(source, hostId, path);
    }

    @Override public CompletionStage<Boolean> createRemoteFile(
            AutomationSource source, HostId hostId, String path) {
        return remoteFiles.createRemoteFile(source, hostId, path);
    }

    @Override public CompletionStage<Boolean> renameRemote(
            AutomationSource source, HostId hostId, String sourcePath, String targetPath) {
        return remoteFiles.renameRemote(source, hostId, sourcePath, targetPath);
    }

    @Override public CompletionStage<Boolean> deleteRemote(
            AutomationSource source, HostId hostId, String path, boolean recursive) {
        return remoteFiles.deleteRemote(source, hostId, path, recursive);
    }

    @Override public CompletionStage<Boolean> setRemotePermissions(
            AutomationSource source, HostId hostId, String path, int permissions) {
        return remoteFiles.setRemotePermissions(source, hostId, path, permissions);
    }

    @Override
    public CompletionStage<Optional<AutomationOperationStatus>> operation(AutomationOperationId id) {
        return remoteFiles.operation(id);
    }

    @Override public CompletionStage<Optional<AutomationOperationStatus>> operation(
            AutomationSource source, AutomationOperationId id) {
        return remoteFiles.operation(source, id);
    }

    @Override
    public CompletionStage<Boolean> cancel(AutomationOperationId id) {
        return remoteFiles.cancel(id);
    }

    @Override public CompletionStage<Boolean> cancel(AutomationSource source, AutomationOperationId id) {
        return remoteFiles.cancel(source, id);
    }

    private void requirePermission(
            AutomationSource source, AutomationCapability capability, SavedHost target,
            String summary, String detail) throws Exception {
        requirePermission(source, capability, target, target == null ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.278dda9e9d5dce65") : target.name(), summary, detail);
    }

    private void requirePermission(
            AutomationSource source, AutomationCapability capability, SavedHost target, String targetName,
            String summary, String detail) throws Exception {
        String auditTarget = target == null ? Objects.requireNonNull(targetName, "targetName") : auditTarget(target);
        emit(source, AutomationAuditPhase.REQUESTED, capability, summary, auditTarget, detail, null);
        AiSettings settings = aiSettings.load();
        AutomationPermissions permissions = source == AutomationSource.BUILT_IN_AI
                ? settings.builtInPermissions() : settings.mcpPermissions();
        AutomationPermission decision = permissions.permission(capability);
        if (decision == AutomationPermission.DENY) {
            emit(source, AutomationAuditPhase.DENIED, capability, summary, auditTarget,
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.62362cf9377cb106") + detail, null);
            throw new SecurityException("This automation capability is disabled in settings: " + capability.name());
        }
        if (decision == AutomationPermission.ASK) {
            emit(source, AutomationAuditPhase.APPROVAL_REQUIRED, capability, summary, auditTarget, detail, null);
            boolean approved = approval.get().approve(new AutomationApprovalRequest(
                    source, capability, target == null ? Optional.empty() : Optional.of(target.id()),
                    target == null ? targetName : target.name(), summary, detail))
                    .toCompletableFuture().get(APPROVAL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!approved) {
                emit(source, AutomationAuditPhase.DENIED, capability, summary, auditTarget,
                        com.ynpe.fastremote.i18n.Texts.text("legacy.java.4b872117f5970e1d") + detail, null);
                throw new SecurityException("The user denied the automation operation: " + capability.name());
            }
            emit(source, AutomationAuditPhase.APPROVED, capability, summary, auditTarget,
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.f2d0f8e15cf4ce7e") + detail, null);
        } else {
            emit(source, AutomationAuditPhase.APPROVED, capability, summary, auditTarget,
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.7bc5d12edac4fa27") + detail, null);
        }
    }

    private <T> T audited(AutomationSource source, AutomationCapability capability, SavedHost target,
            String summary, String detail, CheckedSupplier<T> action) throws Exception {
        requirePermission(source, capability, target, summary, detail);
        long started = System.nanoTime();
        emit(source, AutomationAuditPhase.STARTED, capability, summary, auditTarget(target), detail, null);
        try {
            T result = action.get();
            emit(source, AutomationAuditPhase.SUCCEEDED, capability, summary, auditTarget(target), detail,
                    Duration.ofNanos(System.nanoTime() - started));
            return result;
        } catch (Exception failure) {
            emit(source, AutomationAuditPhase.FAILED, capability, summary, auditTarget(target),
                    detail + com.ynpe.fastremote.i18n.Texts.text("legacy.java.552a208b8ec13b5c")
                            + com.ynpe.fastremote.i18n.Texts.translateExternalText(sanitizedMessage(failure)),
                    Duration.ofNanos(System.nanoTime() - started));
            throw failure;
        }
    }

    private <T> T auditedGlobal(AutomationSource source, AutomationCapability capability, String target,
            String summary, String detail, CheckedSupplier<T> action) throws Exception {
        requirePermission(source, capability, null, target, summary, detail);
        long started = System.nanoTime();
        emit(source, AutomationAuditPhase.STARTED, capability, summary, target, detail, null);
        try {
            T result = action.get();
            emit(source, AutomationAuditPhase.SUCCEEDED, capability, summary, target, detail,
                    Duration.ofNanos(System.nanoTime() - started));
            return result;
        } catch (Exception failure) {
            emit(source, AutomationAuditPhase.FAILED, capability, summary, target,
                    detail + com.ynpe.fastremote.i18n.Texts.text("legacy.java.552a208b8ec13b5c")
                            + com.ynpe.fastremote.i18n.Texts.translateExternalText(sanitizedMessage(failure)),
                    Duration.ofNanos(System.nanoTime() - started));
            throw failure;
        }
    }

    private void emit(AutomationSource source, AutomationAuditPhase phase, AutomationCapability capability,
            String action, String target, String detail, Duration duration) {
        try {
            audit.get().accept(new AutomationAuditEvent(Instant.now(), source, phase, capability,
                    action, target, redact(detail), Optional.ofNullable(duration)));
        } catch (RuntimeException ignored) { }
    }

    private void notifyLibraryChanged() {
        // A disposed window must not turn a successful database write into a failed tool call.
        try {
            libraryChanged.get().run();
        } catch (RuntimeException ignored) { }
    }

    private static String redact(String value) {
        String safe = Objects.requireNonNullElse(value, "");
        safe = safe.replaceAll("(?i)(password|passwd|token|secret|api[_-]?key)\\s*[:=]\\s*[^\\s,;]+", "$1=<redacted>");
        safe = safe.replaceAll("(?i)(authorization\\s*:\\s*bearer)\\s+[^\\s]+", "$1 <redacted>");
        safe = safe.replaceAll("(?i)(sshpass\\s+-p)\\s+[^\\s]+", "$1 <redacted>");
        safe = safe.replaceAll("(?i)(--password(?:=|\\s+))[^\\s]+", "$1<redacted>");
        return safe.length() <= 4_096 ? safe : safe.substring(0, 4_096) + "…";
    }

    private SavedHost requireHost(HostId id) throws IOException {
        Optional<SavedHost> saved = hosts.findHost(Objects.requireNonNull(id, "hostId"));
        if (saved.isPresent()) return saved.orElseThrow();
        try {
            return uiOperations.get().resolveActiveHost(id).toCompletableFuture().get(30, TimeUnit.SECONDS)
                    .orElseThrow(() -> new IllegalArgumentException("Host is not saved or currently open"));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Active host lookup interrupted", interrupted);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException failure) {
            throw new IOException("Unable to resolve active host", failure);
        }
    }

    private AutomationRoute resolveRoute(SavedHost target) throws Exception {
        if (hosts.findHost(target.id()).isEmpty()) {
            throw new IllegalArgumentException("Quick connections use visible terminal/SFTP tools while open; background connections require a saved host");
        }
        Map<HostId, SavedHost> byId = new HashMap<>();
        for (SavedHost host : hosts.listHosts()) byId.put(host.id(), host);
        List<SavedHost> reverse = new ArrayList<>();
        Set<HostId> visited = new HashSet<>();
        SavedHost current = target;
        while (true) {
            if (!visited.add(current.id())) throw new IllegalArgumentException("Jump-host route contains a cycle");
            reverse.add(current);
            if (reverse.size() > 6) throw new IllegalArgumentException("Jump-host route exceeds five hops");
            if (current.jumpHostId().isEmpty()) break;
            current = Optional.ofNullable(byId.get(current.jumpHostId().orElseThrow()))
                    .orElseThrow(() -> new IllegalArgumentException("Jump-host host no longer exists"));
        }
        java.util.Collections.reverse(reverse);
        List<ConnectionHop> hops = new ArrayList<>();
        try {
            for (SavedHost host : reverse) {
                if (host.credentialSecretId().isEmpty()) {
                    throw new IllegalStateException("Automation requires a saved credential for every route hop");
                }
                ResolvedCredential resolved = credentialResolver.apply(host).toCompletableFuture()
                        .get(30, TimeUnit.SECONDS);
                hops.add(new ConnectionHop(host.name(), host.profile(), credentials(host, resolved)));
            }
            return new AutomationRoute(hops);
        } catch (Exception failure) {
            hops.forEach(hop -> hop.credentials().close());
            throw failure;
        }
    }

    private static Credentials credentials(SavedHost host, ResolvedCredential resolved) {
        try (resolved) {
            if (host.profile().authenticationType() != resolved.authenticationType()) {
                throw new IllegalArgumentException("Stored credential type does not match the host");
            }
            char[] secret = resolved.copySecret();
            byte[] key = resolved.copyPrivateKey();
            try {
                return host.profile().authenticationType() == AuthenticationType.PASSWORD
                        ? new PasswordCredentials(secret) : new PrivateKeyCredentials(secret, key);
            } finally {
                Arrays.fill(secret, '\0');
                if (key != null) Arrays.fill(key, (byte) 0);
            }
        }
    }

    private static String boundedWorkspaceId(String value) {
        String safe = Objects.requireNonNull(value, "workspaceId").strip();
        if (safe.isEmpty() || safe.length() > 200 || safe.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("workspaceId is invalid");
        }
        return safe;
    }

    private <T> CompletionStage<T> submit(CheckedSupplier<T> supplier) {
        if (closed.get()) return CompletableFuture.failedFuture(new IllegalStateException("Automation gateway is closed"));
        return licenseAccess.permits(com.ynpe.fastremote.domain.license.LicenseCapability.AUTOMATION)
                .thenCompose(permitted -> Boolean.TRUE.equals(permitted)
                        ? submitAuthorized(supplier)
                        : CompletableFuture.failedFuture(new IllegalStateException("A valid automation license is required")));
    }

    private <T> CompletionStage<T> submitAuthorized(CheckedSupplier<T> supplier) {
        if (closed.get()) return CompletableFuture.failedFuture(new IllegalStateException("Automation gateway is closed"));
        try {
            return executor.supply(() -> {
                try { return supplier.get(); }
                catch (Exception failure) { throw new CompletionException(failure); }
            }, () -> {});
        } catch (RejectedExecutionException rejected) {
            return CompletableFuture.failedFuture(new IllegalStateException("Automation queue is full", rejected));
        }
    }

    public java.util.concurrent.CompletionStage<Void> stopped() { return executor.stopped(); }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        commands.close();
        remoteFiles.close();
        executor.shutdownNow();
    }

}
