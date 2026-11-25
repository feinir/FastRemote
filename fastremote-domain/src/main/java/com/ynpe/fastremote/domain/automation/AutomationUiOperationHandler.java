// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.automation;

import com.ynpe.fastremote.domain.catalog.HostId;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionStage;
import com.ynpe.fastremote.domain.transfer.TransferConflictChoice;

/** Desktop-only bridge for automation that must remain visible in terminal or SFTP workspaces. */
public interface AutomationUiOperationHandler {

    default CompletionStage<com.ynpe.fastremote.domain.terminal.TerminalOutput> readTerminal(String workspaceId,
            String generation, long after, int maximum, boolean screen) {
        return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("Terminal observation unavailable"));
    }
    default CompletionStage<List<WorkspaceOperationReceipt>> listWorkspaceOperations(String workspaceId) {
        return java.util.concurrent.CompletableFuture.completedFuture(List.of());
    }
    default CompletionStage<WorkspaceOperationSnapshot> readWorkspaceOperation(String operationId,
            long after, int maximum, int waitMillis) {
        return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("Workspace result unavailable"));
    }
    default CompletionStage<WorkspaceOperationReceipt> startTerminalCommand(HostId hostId,
            String command, boolean reuseExistingTab) {
        return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("Tracked terminal execution unavailable"));
    }
    default CompletionStage<WorkspaceOperationReceipt> startLocalTerminalCommand(String command, boolean reuseExistingTab) {
        return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("Tracked terminal execution unavailable"));
    }
    default CompletionStage<WorkspaceOperationReceipt> startSftpUpload(HostId hostId, List<Path> localSources,
            String remoteDirectory, TransferConflictChoice conflictChoice, long maximumBytes, boolean reuseExistingTab) {
        return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("Tracked SFTP unavailable"));
    }
    default CompletionStage<WorkspaceOperationReceipt> startSftpDownload(HostId hostId, String remoteSource,
            Path localDirectory, long maximumBytes, boolean reuseExistingTab) {
        return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("Tracked SFTP unavailable"));
    }
    default CompletionStage<Boolean> openLocalTerminal() {
        return java.util.concurrent.CompletableFuture.completedFuture(false);
    }
    default CompletionStage<Boolean> openLocalTerminal(boolean reuseExistingTab) {
        return openLocalTerminal();
    }
    default CompletionStage<Integer> closeWorkspaceTabs(AutomationWorkspaceScope scope) {
        return java.util.concurrent.CompletableFuture.completedFuture(0);
    }
    default CompletionStage<List<AutomationActiveHost>> listActiveHosts() {
        return java.util.concurrent.CompletableFuture.completedFuture(List.of());
    }
    default CompletionStage<AutomationConversationContext> conversationContext() {
        return java.util.concurrent.CompletableFuture.completedFuture(AutomationConversationContext.none());
    }
    default CompletionStage<java.util.Optional<com.ynpe.fastremote.domain.catalog.SavedHost>> resolveActiveHost(HostId id) {
        return java.util.concurrent.CompletableFuture.completedFuture(java.util.Optional.empty());
    }
    default CompletionStage<List<AutomationWorkspaceItem>> listWorkspace() {
        return java.util.concurrent.CompletableFuture.completedFuture(List.of());
    }
    default CompletionStage<String> readWorkspaceText(String workspaceId) {
        return java.util.concurrent.CompletableFuture.failedFuture(
                new UnsupportedOperationException("Workspace editor reading is unavailable"));
    }
    default CompletionStage<Boolean> writeWorkspaceText(String workspaceId, String content) {
        return java.util.concurrent.CompletableFuture.completedFuture(false);
    }
    default CompletionStage<String> readWorkspaceLog(String workspaceId) {
        return java.util.concurrent.CompletableFuture.failedFuture(
                new UnsupportedOperationException("Workspace log reading is unavailable"));
    }
    default CompletionStage<Boolean> showToolWindow(AutomationToolWindow window) {
        return java.util.concurrent.CompletableFuture.completedFuture(false);
    }
    default CompletionStage<Boolean> showImportDialog(AutomationLibrary library) {
        return java.util.concurrent.CompletableFuture.completedFuture(false);
    }
    default CompletionStage<Boolean> showExportDialog(AutomationLibrary library) {
        return java.util.concurrent.CompletableFuture.completedFuture(false);
    }
    CompletionStage<Boolean> runTerminalCommand(HostId hostId, String command);
    default CompletionStage<Boolean> runTerminalCommand(
            HostId hostId, String command, boolean reuseExistingTab) {
        return runTerminalCommand(hostId, command);
    }
    default CompletionStage<Boolean> openTerminal(HostId hostId, boolean reuseExistingTab) {
        return java.util.concurrent.CompletableFuture.completedFuture(false);
    }
    default CompletionStage<Boolean> openSftp(HostId hostId) {
        return java.util.concurrent.CompletableFuture.completedFuture(false);
    }
    default CompletionStage<Boolean> openSftp(HostId hostId, boolean reuseExistingTab) {
        return openSftp(hostId);
    }
    default CompletionStage<Boolean> changeSftpRemoteDirectory(HostId hostId, String directory) {
        return java.util.concurrent.CompletableFuture.completedFuture(false);
    }
    default CompletionStage<Boolean> changeSftpLocalDirectory(HostId hostId, Path directory) {
        return java.util.concurrent.CompletableFuture.completedFuture(false);
    }
    default CompletionStage<Boolean> runLocalTerminalCommand(String command) {
        return java.util.concurrent.CompletableFuture.completedFuture(false);
    }
    default CompletionStage<Boolean> runLocalTerminalCommand(String command, boolean reuseExistingTab) {
        return runLocalTerminalCommand(command);
    }
    CompletionStage<Boolean> uploadInSftp(HostId hostId, List<Path> localSources, String remoteDirectory);
    default CompletionStage<Boolean> uploadInSftp(
            HostId hostId, List<Path> localSources, String remoteDirectory,
            TransferConflictChoice conflictChoice) {
        return uploadInSftp(hostId, localSources, remoteDirectory);
    }
    default CompletionStage<Boolean> uploadInSftp(
            HostId hostId, List<Path> localSources, String remoteDirectory,
            TransferConflictChoice conflictChoice, long maximumBytes) {
        return uploadInSftp(hostId, localSources, remoteDirectory, conflictChoice);
    }
    default CompletionStage<Boolean> uploadInSftp(
            HostId hostId, List<Path> localSources, String remoteDirectory,
            TransferConflictChoice conflictChoice, long maximumBytes, boolean reuseExistingTab) {
        return uploadInSftp(hostId, localSources, remoteDirectory, conflictChoice, maximumBytes);
    }
    CompletionStage<Boolean> downloadInSftp(HostId hostId, String remoteSource, Path localDirectory);
    default CompletionStage<Boolean> downloadInSftp(
            HostId hostId, String remoteSource, Path localDirectory, long maximumBytes) {
        return downloadInSftp(hostId, remoteSource, localDirectory);
    }
    default CompletionStage<Boolean> downloadInSftp(
            HostId hostId, String remoteSource, Path localDirectory, long maximumBytes,
            boolean reuseExistingTab) {
        return downloadInSftp(hostId, remoteSource, localDirectory, maximumBytes);
    }
    default CompletionStage<Boolean> createRemoteDirectory(HostId hostId, String path) {
        return java.util.concurrent.CompletableFuture.completedFuture(false);
    }
    default CompletionStage<Boolean> createRemoteFile(HostId hostId, String path) {
        return java.util.concurrent.CompletableFuture.completedFuture(false);
    }
    default CompletionStage<Boolean> renameRemote(HostId hostId, String source, String target) {
        return java.util.concurrent.CompletableFuture.completedFuture(false);
    }
    default CompletionStage<Boolean> deleteRemote(HostId hostId, String path, boolean recursive) {
        return java.util.concurrent.CompletableFuture.completedFuture(false);
    }
    default CompletionStage<Boolean> setRemotePermissions(HostId hostId, String path, int permissions) {
        return java.util.concurrent.CompletableFuture.completedFuture(false);
    }
    default CompletionStage<Boolean> writeRemoteText(
            HostId hostId, String path, byte[] content, boolean overwrite) {
        return java.util.concurrent.CompletableFuture.completedFuture(false);
    }
}
