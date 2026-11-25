// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.automation;

import com.ynpe.fastremote.domain.catalog.HostId;
import com.ynpe.fastremote.domain.catalog.GroupColor;
import com.ynpe.fastremote.domain.snippet.Snippet;
import com.ynpe.fastremote.domain.snippet.SnippetGroup;
import com.ynpe.fastremote.domain.snippet.SnippetGroupId;
import com.ynpe.fastremote.domain.snippet.SnippetVariable;
import com.ynpe.fastremote.domain.snippet.SnippetVariableGroup;
import com.ynpe.fastremote.domain.snippet.SnippetVariableGroupId;
import com.ynpe.fastremote.domain.snippet.SnippetVariableType;
import com.ynpe.fastremote.domain.terminal.RemoteCommandResult;
import com.ynpe.fastremote.domain.transfer.RemoteFileEntry;
import com.ynpe.fastremote.domain.transfer.TransferConflictChoice;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface AutomationGateway extends AutoCloseable {
    default CompletionStage<Object> catalogTool(AutomationSource source, AutomationCatalogTool tool,
            java.util.Map<String, Object> arguments) {
        return java.util.concurrent.CompletableFuture.failedFuture(
                new UnsupportedOperationException("Saved catalog management is unavailable"));
    }


    default CompletionStage<com.ynpe.fastremote.domain.terminal.TerminalOutput> readTerminal(AutomationSource source, String workspaceId,
            String generation, long after, int maximum, boolean screen) {
        return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("Terminal observation unavailable"));
    }
    default CompletionStage<List<WorkspaceOperationReceipt>> listWorkspaceOperations(AutomationSource source, String workspaceId) {
        return java.util.concurrent.CompletableFuture.completedFuture(List.of());
    }
    default CompletionStage<WorkspaceOperationSnapshot> readWorkspaceOperation(AutomationSource source, String operationId,
            long after, int maximum, int waitMillis) {
        return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("Workspace result unavailable"));
    }
    default CompletionStage<WorkspaceOperationReceipt> startTerminalCommand(AutomationSource source, HostId hostId,
            String command, boolean reuseExistingTab) {
        return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("Tracked terminal execution unavailable"));
    }
    default CompletionStage<WorkspaceOperationReceipt> startLocalTerminalCommand(AutomationSource source, String command, boolean reuseExistingTab) {
        return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("Tracked terminal execution unavailable"));
    }
    default CompletionStage<WorkspaceOperationReceipt> startSftpUpload(AutomationSource source, HostId hostId, List<Path> localSources,
            String remoteDirectory, TransferConflictChoice conflictChoice, boolean reuseExistingTab) {
        return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("Tracked SFTP unavailable"));
    }
    default CompletionStage<WorkspaceOperationReceipt> startSftpDownload(AutomationSource source, HostId hostId, String remoteSource,
            Path localDirectory, boolean reuseExistingTab) {
        return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("Tracked SFTP unavailable"));
    }
    /** Bytes downloaded by background automation transfers. */
    default long receivedBytes() { return 0L; }

    /** Bytes uploaded by background automation transfers. */
    default long sentBytes() { return 0L; }

    default CompletionStage<Boolean> openLocalTerminal(AutomationSource source) {
        return unsupported("Opening a local terminal");
    }

    default CompletionStage<Boolean> openLocalTerminal(
            AutomationSource source, boolean reuseExistingTab) {
        return openLocalTerminal(source);
    }

    default CompletionStage<Integer> closeWorkspaceTabs(
            AutomationSource source, AutomationWorkspaceScope scope) {
        return unsupported("Closing workspace tabs");
    }

    /** Lists the open FastRemote workspaces without capturing terminal scrollback or credentials. */
    default CompletionStage<List<AutomationWorkspaceItem>> listWorkspace(AutomationSource source) {
        return unsupported("Inspecting workspaces");
    }

    default CompletionStage<List<AutomationActiveHost>> listActiveHosts(AutomationSource source) {
        return unsupported("Listing active hosts");
    }
    default CompletionStage<AutomationConversationContext> conversationContext(AutomationSource source) {
        return unsupported("Reading the associated conversation host");
    }

    default CompletionStage<AutomationConversationContext> describeConversationContext(
            AutomationSource source, AutomationConversationContext context) {
        return java.util.concurrent.CompletableFuture.completedFuture(context);
    }

    /** Reads the current in-memory text of an open editor document. */
    default CompletionStage<String> readWorkspaceText(AutomationSource source, String workspaceId) {
        return unsupported("Reading workspace editor text");
    }

    /** Replaces an open editor document's in-memory text; it does not save it to disk or a server. */
    default CompletionStage<Boolean> writeWorkspaceText(
            AutomationSource source, String workspaceId, String content) {
        return unsupported("Editing workspace editor text");
    }

    /** Reads the bounded diagnostic or SFTP operation log associated with an open workspace. */
    default CompletionStage<String> readWorkspaceLog(AutomationSource source, String workspaceId) {
        return unsupported("Reading workspace logs");
    }

    default CompletionStage<Boolean> showToolWindow(
            AutomationSource source, AutomationToolWindow window) {
        return unsupported("Showing tool windows");
    }

    default CompletionStage<Boolean> showImportDialog(
            AutomationSource source, AutomationLibrary library) {
        return unsupported("Opening import dialogs");
    }

    default CompletionStage<Boolean> showExportDialog(
            AutomationSource source, AutomationLibrary library) {
        return unsupported("Opening export dialogs");
    }

    CompletionStage<List<AutomationHost>> listHosts(AutomationSource source);

    /**
     * Returns one bounded page from the complete saved-host catalog. Implementations must apply
     * the query before the page boundary so a matching host is never hidden by an earlier page.
     */
    default CompletionStage<List<AutomationHost>> listHosts(
            AutomationSource source, Optional<String> query, int offset, int limit) {
        Objects.requireNonNull(query, "query");
        if (offset < 0) throw new IllegalArgumentException("Host offset must not be negative");
        if (limit < 1) throw new IllegalArgumentException("Host limit must be positive");
        String expected = query.map(String::strip).orElse("").toLowerCase(Locale.ROOT);
        return listHosts(source).thenApply(hosts -> hosts.stream()
                .filter(host -> expected.isEmpty()
                        || host.id().toString().toLowerCase(Locale.ROOT).contains(expected)
                        || host.name().toLowerCase(Locale.ROOT).contains(expected)
                        || host.username().toLowerCase(Locale.ROOT).contains(expected)
                        || host.host().toLowerCase(Locale.ROOT).contains(expected)
                        || host.endpoint().toLowerCase(Locale.ROOT).contains(expected)
                        || host.groupPath().toLowerCase(Locale.ROOT).contains(expected))
                .skip(offset)
                .limit(limit)
                .toList());
    }

    default CompletionStage<List<AutomationHostGroup>> listHostGroups(AutomationSource source) {
        return unsupported("Reading host groups");
    }

    /** Exact group filter includes descendants; filtering always precedes pagination. */
    default CompletionStage<List<AutomationHost>> listHosts(AutomationSource source, Optional<String> query,
            Optional<com.ynpe.fastremote.domain.catalog.GroupId> groupId, int offset, int limit) {
        if (groupId.isEmpty()) return listHosts(source, query, offset, limit);
        if (offset < 0 || limit < 1) throw new IllegalArgumentException("Invalid host page");
        return listHosts(source, query, 0, Integer.MAX_VALUE).thenApply(hosts -> hosts.stream()
                .filter(host -> host.groupIds().contains(groupId.orElseThrow()))
                .skip(offset).limit(limit).toList());
    }

    default CompletionStage<List<AutomationCommand>> listCommandEntries(AutomationSource source) {
        return listCommands(source).thenCombine(listCommandGroups(source), (entries, groups) ->
                entries.stream().map(entry -> AutomationCommand.describe(entry, groups)).toList());
    }

    default CompletionStage<List<AutomationVariable>> listVariableEntries(AutomationSource source) {
        return listVariables(source).thenCombine(listVariableGroups(source), (entries, groups) ->
                entries.stream().map(entry -> AutomationVariable.describe(entry, groups)).toList());
    }

    default CompletionStage<AutomationCommand> addCommandEntry(AutomationSource source, String name,
            String command, String description, Optional<SnippetGroupId> groupId) {
        return listCommandGroups(source).thenCompose(groups -> addCommand(source, name, command, description, groupId)
                .thenApply(entry -> AutomationCommand.describe(entry, groups)));
    }

    default CompletionStage<AutomationVariable> addVariableEntry(AutomationSource source, String name,
            String variableName, SnippetVariableType type, String delimiter, String value, String description,
            Optional<SnippetVariableGroupId> groupId) {
        return listVariableGroups(source).thenCompose(groups -> addVariable(source, name, variableName, type,
                delimiter, value, description, groupId).thenApply(entry -> AutomationVariable.describe(entry, groups)));
    }

    default CompletionStage<List<Snippet>> listCommands(AutomationSource source) {
        return unsupported("Reading the command library");
    }

    default CompletionStage<List<SnippetGroup>> listCommandGroups(AutomationSource source) {
        return unsupported("Reading command groups");
    }

    default CompletionStage<SnippetGroup> addCommandGroup(
            AutomationSource source, String name, GroupColor color) {
        return unsupported("Adding a command group");
    }

    default CompletionStage<Snippet> addCommand(
            AutomationSource source, String name, String command, String description) {
        return unsupported("Adding a command");
    }

    default CompletionStage<Snippet> addCommand(
            AutomationSource source, String name, String command, String description,
            Optional<SnippetGroupId> groupId) {
        return unsupported("Adding a grouped command");
    }

    default CompletionStage<Boolean> deleteCommand(AutomationSource source, String commandId) {
        return unsupported("Deleting a command");
    }

    default CompletionStage<List<SnippetVariable>> listVariables(AutomationSource source) {
        return unsupported("Reading the variable library");
    }

    default CompletionStage<List<SnippetVariableGroup>> listVariableGroups(AutomationSource source) {
        return unsupported("Reading variable groups");
    }

    default CompletionStage<SnippetVariableGroup> addVariableGroup(
            AutomationSource source, String name, GroupColor color) {
        return unsupported("Adding a variable group");
    }

    default CompletionStage<SnippetVariable> addVariable(AutomationSource source, String name,
            String variableName, SnippetVariableType type, String delimiter, String value, String description) {
        return unsupported("Adding a variable");
    }

    default CompletionStage<SnippetVariable> addVariable(AutomationSource source, String name,
            String variableName, SnippetVariableType type, String delimiter, String value, String description,
            Optional<SnippetVariableGroupId> groupId) {
        return unsupported("Adding a grouped variable");
    }

    default CompletionStage<Boolean> deleteVariable(AutomationSource source, String variableId) {
        return unsupported("Deleting a variable");
    }

    CompletionStage<Boolean> activateHost(AutomationSource source, HostId hostId);

    default CompletionStage<Boolean> activateHost(
            AutomationSource source, HostId hostId, boolean reuseExistingTab) {
        return activateHost(source, hostId);
    }

    default CompletionStage<Boolean> openSftp(AutomationSource source, HostId hostId) {
        return unsupported("Opening a visible SFTP workspace");
    }

    default CompletionStage<Boolean> openSftp(
            AutomationSource source, HostId hostId, boolean reuseExistingTab) {
        return openSftp(source, hostId);
    }

    default CompletionStage<Boolean> changeSftpRemoteDirectory(
            AutomationSource source, HostId hostId, String directory) {
        return unsupported("Changing the visible SFTP remote directory");
    }

    default CompletionStage<Boolean> changeSftpLocalDirectory(
            AutomationSource source, HostId hostId, Path directory) {
        return unsupported("Changing the visible SFTP local directory");
    }

    default CompletionStage<com.ynpe.fastremote.domain.terminal.LocalCommandResult> runLocalCommand(
            AutomationSource source, String command, Optional<Path> workingDirectory) {
        return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("Local background execution unavailable"));
    }

    CompletionStage<RemoteCommandResult> runCommand(
            AutomationSource source, HostId hostId, String command);

    default CompletionStage<Boolean> runTerminalCommand(
            AutomationSource source, HostId hostId, String command) {
        return java.util.concurrent.CompletableFuture.failedFuture(
                new UnsupportedOperationException("Visible terminal automation is unavailable"));
    }

    default CompletionStage<Boolean> runTerminalCommand(
            AutomationSource source, HostId hostId, String command, boolean reuseExistingTab) {
        return runTerminalCommand(source, hostId, command);
    }

    default CompletionStage<Boolean> runLocalTerminalCommand(
            AutomationSource source, String command) {
        return java.util.concurrent.CompletableFuture.failedFuture(
                new UnsupportedOperationException("Visible local terminal automation is unavailable"));
    }

    default CompletionStage<Boolean> runLocalTerminalCommand(
            AutomationSource source, String command, boolean reuseExistingTab) {
        return runLocalTerminalCommand(source, command);
    }

    CompletionStage<List<RemoteFileEntry>> listRemote(
            AutomationSource source, HostId hostId, String directory);

    default CompletionStage<RemoteHostSystem> getRemoteSystem(AutomationSource source, HostId hostId) {
        return unsupported("Detecting the remote operating system");
    }

    default CompletionStage<String> readRemoteText(
            AutomationSource source, HostId hostId, String path) {
        return unsupported("Reading remote text files");
    }

    default CompletionStage<Boolean> writeRemoteText(
            AutomationSource source, HostId hostId, String path, String content, boolean overwrite) {
        return unsupported("Writing remote text files");
    }

    CompletionStage<AutomationLocalListing> listLocal(
            AutomationSource source, Optional<Path> directory);

    default CompletionStage<String> readLocalText(AutomationSource source, Path path) {
        return unsupported("Reading local text files");
    }

    default CompletionStage<Boolean> writeLocalText(
            AutomationSource source, Path path, String content, boolean overwrite) {
        return unsupported("Writing local text files");
    }

    default CompletionStage<Boolean> createLocalDirectory(AutomationSource source, Path path) {
        return unsupported("Creating local directories");
    }

    default CompletionStage<Boolean> renameLocal(AutomationSource source, Path sourcePath, Path targetPath) {
        return unsupported("Renaming local files");
    }

    default CompletionStage<Boolean> deleteLocal(AutomationSource source, Path path, boolean recursive) {
        return unsupported("Deleting local files");
    }

    CompletionStage<AutomationOperationId> upload(
            AutomationSource source, HostId hostId, Path localSource, String remoteTarget);

    CompletionStage<AutomationOperationId> uploadIntoDirectory(
            AutomationSource source, HostId hostId, List<Path> localSources, String remoteDirectory);

    default CompletionStage<AutomationOperationId> uploadIntoDirectory(
            AutomationSource source, HostId hostId, List<Path> localSources, String remoteDirectory,
            TransferConflictChoice conflictChoice) {
        if (conflictChoice == TransferConflictChoice.CANCEL) {
            return uploadIntoDirectory(source, hostId, localSources, remoteDirectory);
        }
        return unsupported("Background upload conflict policies");
    }

    default CompletionStage<Boolean> uploadInSftp(
            AutomationSource source, HostId hostId, List<Path> localSources, String remoteDirectory) {
        return java.util.concurrent.CompletableFuture.failedFuture(
                new UnsupportedOperationException("Visible SFTP automation is unavailable"));
    }

    default CompletionStage<Boolean> uploadInSftp(
            AutomationSource source, HostId hostId, List<Path> localSources, String remoteDirectory,
            TransferConflictChoice conflictChoice) {
        return uploadInSftp(source, hostId, localSources, remoteDirectory);
    }

    default CompletionStage<Boolean> uploadInSftp(
            AutomationSource source, HostId hostId, List<Path> localSources, String remoteDirectory,
            TransferConflictChoice conflictChoice, boolean reuseExistingTab) {
        return uploadInSftp(source, hostId, localSources, remoteDirectory, conflictChoice);
    }

    CompletionStage<AutomationOperationId> download(
            AutomationSource source, HostId hostId, String remoteSource, Path localTarget);

    default CompletionStage<Boolean> downloadInSftp(
            AutomationSource source, HostId hostId, String remoteSource, Path localDirectory) {
        return java.util.concurrent.CompletableFuture.failedFuture(
                new UnsupportedOperationException("Visible SFTP automation is unavailable"));
    }

    default CompletionStage<Boolean> downloadInSftp(
            AutomationSource source, HostId hostId, String remoteSource, Path localDirectory,
            boolean reuseExistingTab) {
        return downloadInSftp(source, hostId, remoteSource, localDirectory);
    }

    default CompletionStage<Boolean> createRemoteDirectory(
            AutomationSource source, HostId hostId, String path) {
        return unsupported("Creating remote directories");
    }

    default CompletionStage<Boolean> createRemoteFile(
            AutomationSource source, HostId hostId, String path) {
        return unsupported("Creating remote files");
    }

    default CompletionStage<Boolean> renameRemote(
            AutomationSource source, HostId hostId, String sourcePath, String targetPath) {
        return unsupported("Renaming remote files");
    }

    default CompletionStage<Boolean> deleteRemote(
            AutomationSource source, HostId hostId, String path, boolean recursive) {
        return unsupported("Deleting remote files");
    }

    default CompletionStage<Boolean> setRemotePermissions(
            AutomationSource source, HostId hostId, String path, int permissions) {
        return unsupported("Changing remote permissions");
    }

    CompletionStage<Optional<AutomationOperationStatus>> operation(AutomationOperationId id);

    default CompletionStage<Optional<AutomationOperationStatus>> operation(
            AutomationSource source, AutomationOperationId id) {
        return operation(id);
    }

    CompletionStage<Boolean> cancel(AutomationOperationId id);

    default CompletionStage<Boolean> cancel(AutomationSource source, AutomationOperationId id) {
        return cancel(id);
    }

    @Override void close();

    private static <T> CompletionStage<T> unsupported(String action) {
        return java.util.concurrent.CompletableFuture.failedFuture(
                new UnsupportedOperationException(action + " is unavailable"));
    }
}
