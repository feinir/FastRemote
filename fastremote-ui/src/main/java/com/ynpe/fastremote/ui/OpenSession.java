// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.catalog.SavedHost;
import com.ynpe.fastremote.domain.connection.ConnectionProfile;
import com.ynpe.fastremote.domain.transfer.RemoteFileEntry;
import com.ynpe.fastremote.ui.WorkspaceViewModel.ConnectionState;
import com.ynpe.fastremote.ui.WorkspaceViewModel.WorkspaceType;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

/** Mutable runtime state owned by one logical SSH connection and its terminal/SFTP content panes. */
final class OpenSession {
    static final int FILE_LOG_MAXIMUM_CHARACTERS = 512 * 1024;
    final String name;
    final ConnectionProfile profile;
    final WorkspaceType initialType;
    final UUID pairingId;
    final AtomicBoolean closed = new AtomicBoolean();
    TerminalHandle terminal;
    ConnectionAttempt attempt;
    Runnable cancelAttempt;
    SftpHandle sftp;
    CredentialDraft pendingCredential;
    TemporaryConnectionCredential temporaryCredential;
    SavedHost credentialOwner;
    com.ynpe.fastremote.domain.catalog.HostId catalogHostId;
    final Map<WorkspaceType, Tab> tabs = new EnumMap<>(WorkspaceType.class);
    SftpTransferQueue transferQueue;
    TableView<SftpTransferJob> transferTable;
    FilePanelMoveSupport<LocalFileEntry, java.nio.file.Path> localMoves;
    FilePanelMoveSupport<RemoteFileEntry, String> remoteMoves;
    List<LocalFileEntry> draggedLocalEntries = List.of();
    List<RemoteFileEntry> draggedRemoteEntries = List.of();
    Runnable pendingSftpDropAction;
    final java.util.ArrayDeque<String> localBack = new java.util.ArrayDeque<>();
    final java.util.ArrayDeque<String> localHistoryNext = new java.util.ArrayDeque<>();
    final java.util.ArrayDeque<String> remoteBack = new java.util.ArrayDeque<>();
    final java.util.ArrayDeque<String> remoteHistoryNext = new java.util.ArrayDeque<>();
    final ThrottledTextLog fileLog = new ThrottledTextLog(
            FILE_LOG_MAXIMUM_CHARACTERS, "sftp-file-log-flusher");
    TextArea fileLogView;
    TreeView<LocalFileEntry> localTree;
    TreeView<RemoteFileEntry> remoteTree;
    final Map<TreeItem<LocalFileEntry>, CompletableFuture<Void>> localTreeLoads = new IdentityHashMap<>();
    final Map<TreeItem<RemoteFileEntry>, CompletableFuture<Void>> remoteTreeLoads = new IdentityHashMap<>();
    TableView<LocalFileEntry> localTable;
    javafx.scene.Node localBrowser;
    TableView<RemoteFileEntry> remoteTable;
    TextField localPathField;
    TextField remotePathField;
    Label localStatisticsLabel;
    Label remoteStatisticsLabel;
    ServerOverview serverOverview;
    String serverOverviewFailure;
    TextArea serverOverviewView;
    boolean serverOverviewLoading;
    boolean synchronizingLocalTree;
    boolean synchronizingRemoteTree;
    long localDirectoryRequest;
    long localTreeGeneration;
    long remoteDirectoryRequest;
    String currentLocalPath = ".";
    String currentRemotePath = ".";
    volatile String latestInferredWorkingDirectory;
    volatile String remoteHome = ".";
    volatile boolean remoteHomeCaptured;
    boolean initialRemotePathFallbackPending;
    String pendingTerminalDirectory;
    String pendingRemoteSelectionPath;
    boolean suppressLocalHistory;
    boolean suppressRemoteHistory;
    String bookmarkKey;
    RemoteFilesHandle observedRemoteFiles;
    RemoteFilesHandle observationSource;
    String observationGeneration = java.util.UUID.randomUUID().toString();
    boolean automationOwned;
    boolean automationActivateTab;
    Tab insertionAnchor;
    ConnectionState state = ConnectionState.CONNECTING;
    boolean connectedOnce;
    TerminalHandle retainedTerminal;
    SftpHandle retainedSftp;
    String failureMessage = com.ynpe.fastremote.i18n.Texts.text("legacy.java.c35a3247c413ea4a");

    OpenSession(String name, ConnectionProfile profile, WorkspaceType initialType) {
        this(name, profile, initialType, UUID.randomUUID());
    }

    OpenSession(String name, ConnectionProfile profile, WorkspaceType initialType, UUID pairingId) {
        this.name = name == null ? "" : name.strip();
        this.profile = Objects.requireNonNull(profile, "profile");
        this.initialType = Objects.requireNonNull(initialType, "initialType");
        this.pairingId = Objects.requireNonNull(pairingId, "pairingId");
        this.bookmarkKey = "quick:" + profile.username().value() + "@"
                + profile.host().value() + ":" + profile.port().value();
    }
}
