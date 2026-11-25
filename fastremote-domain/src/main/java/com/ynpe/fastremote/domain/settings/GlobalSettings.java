// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.settings;

import java.util.Set;

/** Version-independent global preferences that are not owned by a saved host. */
public record GlobalSettings(
        boolean leftDockVisible,
        boolean rightDockVisible,
        boolean bottomDockVisible,
        double leftDockWidth,
        double rightDockWidth,
        DockSide hostLibraryDock,
        DockSide quickConnectDock,
        DockSide snippetsDock,
        DockSide variableLibraryDock,
        DockSide credentialVaultDock,
        DockSide aiAssistantDock,
        DockSide diagnosticLogDock,
        Set<String> collapsedHostGroupIds,
        Set<HostTabTitlePart> hostTabTitleParts,
        boolean showSftpDirectoryTree,
        TerminalPreferences terminalPreferences,
        SftpSettings sftpSettings,
        ConnectionSettings connectionSettings,
        boolean openLocalTerminalOnStartup,
        WindowSettings windowSettings,
        DockDisplayMode leftDockDisplayMode,
        DockDisplayMode rightDockDisplayMode,
        double terminalActivityHeight,
        double sftpActivityHeight,
        java.util.List<String> leftToolOrder,
        java.util.List<String> rightToolOrder,
        CommandPaletteSettings commandPaletteSettings,
        DiagnosticLogSettings diagnosticLogSettings,
        boolean aiSessionsCollapsed) {
    public static final double DEFAULT_LEFT_DOCK_WIDTH = 245.0;
    public static final double DEFAULT_RIGHT_DOCK_WIDTH = 245.0;
    public static final double DEFAULT_TERMINAL_ACTIVITY_HEIGHT = 100.0;
    public static final double DEFAULT_SFTP_ACTIVITY_HEIGHT = 180.0;
    public static final double MAX_ACTIVITY_HEIGHT = 4_000.0;

    public GlobalSettings(
        boolean leftDockVisible,
        boolean rightDockVisible,
        boolean bottomDockVisible,
        double leftDockWidth,
        double rightDockWidth,
        DockSide hostLibraryDock,
        DockSide quickConnectDock,
        DockSide snippetsDock,
        DockSide variableLibraryDock,
        DockSide credentialVaultDock,
        DockSide aiAssistantDock,
        DockSide diagnosticLogDock,
        Set<String> collapsedHostGroupIds,
        Set<HostTabTitlePart> hostTabTitleParts,
        boolean showSftpDirectoryTree,
        TerminalPreferences terminalPreferences,
        SftpSettings sftpSettings,
        ConnectionSettings connectionSettings,
        boolean openLocalTerminalOnStartup,
        WindowSettings windowSettings,
        DockDisplayMode leftDockDisplayMode,
        DockDisplayMode rightDockDisplayMode,
        double terminalActivityHeight,
        double sftpActivityHeight,
        java.util.List<String> leftToolOrder,
        java.util.List<String> rightToolOrder,
        CommandPaletteSettings commandPaletteSettings,
        DiagnosticLogSettings diagnosticLogSettings) {
        this(
                leftDockVisible, rightDockVisible, bottomDockVisible, leftDockWidth,
                rightDockWidth, hostLibraryDock, quickConnectDock, snippetsDock,
                variableLibraryDock, credentialVaultDock, aiAssistantDock, diagnosticLogDock,
                collapsedHostGroupIds, hostTabTitleParts, showSftpDirectoryTree, terminalPreferences,
                sftpSettings, connectionSettings, openLocalTerminalOnStartup, windowSettings,
                leftDockDisplayMode, rightDockDisplayMode, terminalActivityHeight, sftpActivityHeight,
                leftToolOrder, rightToolOrder, commandPaletteSettings, diagnosticLogSettings,
                false);
    }

    public GlobalSettings {
        requirePositiveFinite(leftDockWidth, "leftDockWidth");
        requirePositiveFinite(rightDockWidth, "rightDockWidth");
        java.util.Objects.requireNonNull(hostLibraryDock, "hostLibraryDock");
        java.util.Objects.requireNonNull(quickConnectDock, "quickConnectDock");
        java.util.Objects.requireNonNull(snippetsDock, "snippetsDock");
        java.util.Objects.requireNonNull(variableLibraryDock, "variableLibraryDock");
        java.util.Objects.requireNonNull(credentialVaultDock, "credentialVaultDock");
        java.util.Objects.requireNonNull(aiAssistantDock, "aiAssistantDock");
        java.util.Objects.requireNonNull(diagnosticLogDock, "diagnosticLogDock");
        collapsedHostGroupIds = Set.copyOf(
                java.util.Objects.requireNonNull(collapsedHostGroupIds, "collapsedHostGroupIds"));
        if (collapsedHostGroupIds.size() > 10_000
                || collapsedHostGroupIds.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("collapsedHostGroupIds contains invalid values");
        }
        hostTabTitleParts = Set.copyOf(
                java.util.Objects.requireNonNull(hostTabTitleParts, "hostTabTitleParts"));
        java.util.Objects.requireNonNull(terminalPreferences, "terminalPreferences");
        java.util.Objects.requireNonNull(sftpSettings, "sftpSettings");
        java.util.Objects.requireNonNull(connectionSettings, "connectionSettings");
        java.util.Objects.requireNonNull(windowSettings, "windowSettings");
        java.util.Objects.requireNonNull(leftDockDisplayMode, "leftDockDisplayMode");
        java.util.Objects.requireNonNull(rightDockDisplayMode, "rightDockDisplayMode");
        requireActivityHeight(terminalActivityHeight, "terminalActivityHeight");
        requireActivityHeight(sftpActivityHeight, "sftpActivityHeight");
        leftToolOrder = validatedToolOrder(leftToolOrder, "leftToolOrder");
        rightToolOrder = validatedToolOrder(rightToolOrder, "rightToolOrder");
        java.util.Objects.requireNonNull(commandPaletteSettings, "commandPaletteSettings");
        java.util.Objects.requireNonNull(diagnosticLogSettings, "diagnosticLogSettings");
    }

    public GlobalSettings(
            boolean leftDockVisible, boolean rightDockVisible, boolean bottomDockVisible,
            double leftDockWidth, double rightDockWidth,
            DockSide hostLibraryDock, DockSide quickConnectDock, DockSide snippetsDock,
            DockSide variableLibraryDock, DockSide credentialVaultDock, DockSide aiAssistantDock,
            DockSide diagnosticLogDock, Set<String> collapsedHostGroupIds,
            Set<HostTabTitlePart> hostTabTitleParts, boolean showSftpDirectoryTree,
            TerminalPreferences terminalPreferences, SftpSettings sftpSettings,
            ConnectionSettings connectionSettings, boolean openLocalTerminalOnStartup,
            WindowSettings windowSettings, DockDisplayMode leftDockDisplayMode,
            DockDisplayMode rightDockDisplayMode, double terminalActivityHeight,
            double sftpActivityHeight, java.util.List<String> leftToolOrder,
            java.util.List<String> rightToolOrder, CommandPaletteSettings commandPaletteSettings) {
        this(leftDockVisible, rightDockVisible, bottomDockVisible, leftDockWidth, rightDockWidth,
                hostLibraryDock, quickConnectDock, snippetsDock, variableLibraryDock,
                credentialVaultDock, aiAssistantDock, diagnosticLogDock, collapsedHostGroupIds,
                hostTabTitleParts, showSftpDirectoryTree, terminalPreferences, sftpSettings,
                connectionSettings, openLocalTerminalOnStartup, windowSettings, leftDockDisplayMode,
                rightDockDisplayMode, terminalActivityHeight, sftpActivityHeight, leftToolOrder,
                rightToolOrder, commandPaletteSettings, DiagnosticLogSettings.defaults());
    }

    public GlobalSettings(
            boolean leftDockVisible, boolean rightDockVisible, boolean bottomDockVisible,
            double leftDockWidth, double rightDockWidth,
            DockSide hostLibraryDock, DockSide quickConnectDock, DockSide snippetsDock,
            DockSide variableLibraryDock, DockSide credentialVaultDock, DockSide aiAssistantDock,
            DockSide diagnosticLogDock, Set<String> collapsedHostGroupIds,
            Set<HostTabTitlePart> hostTabTitleParts, boolean showSftpDirectoryTree,
            TerminalPreferences terminalPreferences, SftpSettings sftpSettings,
            ConnectionSettings connectionSettings, boolean openLocalTerminalOnStartup,
            WindowSettings windowSettings, DockDisplayMode leftDockDisplayMode,
            DockDisplayMode rightDockDisplayMode, double terminalActivityHeight,
            double sftpActivityHeight, java.util.List<String> leftToolOrder,
            java.util.List<String> rightToolOrder) {
        this(leftDockVisible, rightDockVisible, bottomDockVisible, leftDockWidth, rightDockWidth,
                hostLibraryDock, quickConnectDock, snippetsDock, variableLibraryDock,
                credentialVaultDock, aiAssistantDock, diagnosticLogDock, collapsedHostGroupIds,
                hostTabTitleParts, showSftpDirectoryTree, terminalPreferences, sftpSettings,
                connectionSettings, openLocalTerminalOnStartup, windowSettings, leftDockDisplayMode,
                rightDockDisplayMode, terminalActivityHeight, sftpActivityHeight, leftToolOrder,
                rightToolOrder, CommandPaletteSettings.defaults(), DiagnosticLogSettings.defaults());
    }

    public GlobalSettings(
            boolean leftDockVisible, boolean rightDockVisible, boolean bottomDockVisible,
            double leftDockWidth, double rightDockWidth,
            DockSide hostLibraryDock, DockSide quickConnectDock, DockSide snippetsDock,
            DockSide credentialVaultDock, DockSide aiAssistantDock, DockSide diagnosticLogDock,
            Set<String> collapsedHostGroupIds, Set<HostTabTitlePart> hostTabTitleParts,
            boolean showSftpDirectoryTree, TerminalPreferences terminalPreferences,
            SftpSettings sftpSettings, ConnectionSettings connectionSettings,
            boolean openLocalTerminalOnStartup, WindowSettings windowSettings,
            DockDisplayMode leftDockDisplayMode, DockDisplayMode rightDockDisplayMode,
            double terminalActivityHeight, double sftpActivityHeight) {
        this(leftDockVisible, rightDockVisible, bottomDockVisible, leftDockWidth, rightDockWidth,
                hostLibraryDock, quickConnectDock, snippetsDock, DockSide.RIGHT, credentialVaultDock,
                aiAssistantDock, diagnosticLogDock, collapsedHostGroupIds, hostTabTitleParts,
                showSftpDirectoryTree, terminalPreferences, sftpSettings, connectionSettings,
                openLocalTerminalOnStartup, windowSettings, leftDockDisplayMode, rightDockDisplayMode,
                terminalActivityHeight, sftpActivityHeight, defaultLeftToolOrder(), defaultRightToolOrder());
    }

    public GlobalSettings(
            boolean leftDockVisible, boolean rightDockVisible, boolean bottomDockVisible,
            double leftDockWidth, double rightDockWidth,
            DockSide hostLibraryDock, DockSide quickConnectDock, DockSide snippetsDock,
            DockSide credentialVaultDock, DockSide aiAssistantDock, DockSide diagnosticLogDock,
            Set<String> collapsedHostGroupIds, Set<HostTabTitlePart> hostTabTitleParts,
            boolean showSftpDirectoryTree, TerminalPreferences terminalPreferences,
            SftpSettings sftpSettings, ConnectionSettings connectionSettings,
            boolean openLocalTerminalOnStartup, WindowSettings windowSettings,
            DockDisplayMode leftDockDisplayMode, DockDisplayMode rightDockDisplayMode) {
        this(leftDockVisible, rightDockVisible, bottomDockVisible, leftDockWidth, rightDockWidth,
                hostLibraryDock, quickConnectDock, snippetsDock, credentialVaultDock, aiAssistantDock,
                diagnosticLogDock, collapsedHostGroupIds, hostTabTitleParts, showSftpDirectoryTree,
                terminalPreferences, sftpSettings, connectionSettings, openLocalTerminalOnStartup,
                windowSettings, leftDockDisplayMode, rightDockDisplayMode,
                DEFAULT_TERMINAL_ACTIVITY_HEIGHT, DEFAULT_SFTP_ACTIVITY_HEIGHT);
    }

    public GlobalSettings(
            boolean leftDockVisible, boolean rightDockVisible, boolean bottomDockVisible,
            double leftDockWidth, double rightDockWidth,
            DockSide hostLibraryDock, DockSide quickConnectDock, DockSide snippetsDock,
            DockSide credentialVaultDock, DockSide aiAssistantDock, DockSide diagnosticLogDock,
            Set<String> collapsedHostGroupIds, Set<HostTabTitlePart> hostTabTitleParts,
            boolean showSftpDirectoryTree, TerminalPreferences terminalPreferences,
            SftpSettings sftpSettings, ConnectionSettings connectionSettings,
            boolean openLocalTerminalOnStartup, WindowSettings windowSettings) {
        this(leftDockVisible, rightDockVisible, bottomDockVisible, leftDockWidth, rightDockWidth,
                hostLibraryDock, quickConnectDock, snippetsDock, credentialVaultDock, aiAssistantDock,
                diagnosticLogDock, collapsedHostGroupIds, hostTabTitleParts, showSftpDirectoryTree,
                terminalPreferences, sftpSettings, connectionSettings, openLocalTerminalOnStartup,
                windowSettings, DockDisplayMode.FLOATING, DockDisplayMode.FLOATING);
    }

    public GlobalSettings(
            boolean leftDockVisible, boolean rightDockVisible, boolean bottomDockVisible,
            double leftDockWidth, double rightDockWidth,
            DockSide hostLibraryDock, DockSide quickConnectDock, DockSide snippetsDock,
            DockSide credentialVaultDock, DockSide aiAssistantDock,
            Set<String> collapsedHostGroupIds, Set<HostTabTitlePart> hostTabTitleParts,
            boolean showSftpDirectoryTree, TerminalPreferences terminalPreferences,
            SftpSettings sftpSettings, ConnectionSettings connectionSettings,
            boolean openLocalTerminalOnStartup) {
        this(leftDockVisible, rightDockVisible, bottomDockVisible, leftDockWidth, rightDockWidth,
                hostLibraryDock, quickConnectDock, snippetsDock, credentialVaultDock, aiAssistantDock,
                DockSide.RIGHT,
                collapsedHostGroupIds, hostTabTitleParts, showSftpDirectoryTree,
                terminalPreferences, sftpSettings, connectionSettings, openLocalTerminalOnStartup,
                WindowSettings.defaults());
    }

    public GlobalSettings(
            boolean leftDockVisible, boolean rightDockVisible, boolean bottomDockVisible,
            double leftDockWidth, double rightDockWidth,
            DockSide hostLibraryDock, DockSide quickConnectDock, DockSide snippetsDock,
            DockSide credentialVaultDock, DockSide aiAssistantDock, DockSide diagnosticLogDock,
            Set<String> collapsedHostGroupIds, Set<HostTabTitlePart> hostTabTitleParts,
            boolean showSftpDirectoryTree, TerminalPreferences terminalPreferences,
            SftpSettings sftpSettings, ConnectionSettings connectionSettings,
            boolean openLocalTerminalOnStartup) {
        this(leftDockVisible, rightDockVisible, bottomDockVisible, leftDockWidth, rightDockWidth,
                hostLibraryDock, quickConnectDock, snippetsDock, credentialVaultDock, aiAssistantDock,
                diagnosticLogDock, collapsedHostGroupIds, hostTabTitleParts, showSftpDirectoryTree,
                terminalPreferences, sftpSettings, connectionSettings, openLocalTerminalOnStartup,
                WindowSettings.defaults());
    }

    public GlobalSettings(
            boolean leftDockVisible, boolean rightDockVisible, boolean bottomDockVisible,
            double leftDockWidth, double rightDockWidth,
            DockSide hostLibraryDock, DockSide quickConnectDock, DockSide snippetsDock,
            DockSide credentialVaultDock, DockSide aiAssistantDock,
            Set<String> collapsedHostGroupIds, Set<HostTabTitlePart> hostTabTitleParts,
            boolean showSftpDirectoryTree, TerminalPreferences terminalPreferences,
            SftpSettings sftpSettings, ConnectionSettings connectionSettings) {
        this(leftDockVisible, rightDockVisible, bottomDockVisible, leftDockWidth, rightDockWidth,
                hostLibraryDock, quickConnectDock, snippetsDock, credentialVaultDock, aiAssistantDock,
                DockSide.RIGHT,
                collapsedHostGroupIds, hostTabTitleParts, showSftpDirectoryTree,
                terminalPreferences, sftpSettings, connectionSettings, false);
    }

    public GlobalSettings(
            boolean leftDockVisible, boolean rightDockVisible, boolean bottomDockVisible,
            double leftDockWidth, double rightDockWidth,
            DockSide hostLibraryDock, DockSide quickConnectDock, DockSide snippetsDock,
            DockSide credentialVaultDock, DockSide aiAssistantDock, DockSide diagnosticLogDock,
            Set<String> collapsedHostGroupIds, Set<HostTabTitlePart> hostTabTitleParts,
            boolean showSftpDirectoryTree, TerminalPreferences terminalPreferences,
            SftpSettings sftpSettings, ConnectionSettings connectionSettings) {
        this(leftDockVisible, rightDockVisible, bottomDockVisible, leftDockWidth, rightDockWidth,
                hostLibraryDock, quickConnectDock, snippetsDock, credentialVaultDock, aiAssistantDock,
                diagnosticLogDock, collapsedHostGroupIds, hostTabTitleParts, showSftpDirectoryTree,
                terminalPreferences, sftpSettings, connectionSettings, false);
    }

    public GlobalSettings(
            boolean leftDockVisible,
            boolean rightDockVisible,
            boolean bottomDockVisible,
            double leftDockWidth,
            double rightDockWidth,
            DockSide hostLibraryDock,
            DockSide quickConnectDock,
            DockSide snippetsDock,
            DockSide credentialVaultDock) {
        this(leftDockVisible, rightDockVisible, bottomDockVisible,
                leftDockWidth, rightDockWidth,
                hostLibraryDock, quickConnectDock, snippetsDock, credentialVaultDock,
                DockSide.RIGHT, DockSide.RIGHT,
                Set.of(), Set.of(HostTabTitlePart.NAME), true, TerminalPreferences.defaults(),
                SftpSettings.defaults(), ConnectionSettings.defaults());
    }

    public GlobalSettings(
            boolean leftDockVisible,
            boolean rightDockVisible,
            boolean bottomDockVisible,
            double leftDockWidth,
            double rightDockWidth,
            DockSide hostLibraryDock,
            DockSide quickConnectDock,
            DockSide snippetsDock,
            DockSide credentialVaultDock,
            Set<String> collapsedHostGroupIds) {
        this(leftDockVisible, rightDockVisible, bottomDockVisible,
                leftDockWidth, rightDockWidth,
                hostLibraryDock, quickConnectDock, snippetsDock, credentialVaultDock,
                DockSide.RIGHT, DockSide.RIGHT,
                collapsedHostGroupIds, Set.of(HostTabTitlePart.NAME), true, TerminalPreferences.defaults(),
                SftpSettings.defaults(), ConnectionSettings.defaults(), false);
    }

    public GlobalSettings(
            boolean leftDockVisible,
            boolean rightDockVisible,
            boolean bottomDockVisible,
            double leftDockWidth,
            double rightDockWidth,
            DockSide hostLibraryDock,
            DockSide quickConnectDock,
            DockSide snippetsDock,
            DockSide credentialVaultDock,
            Set<String> collapsedHostGroupIds,
            Set<HostTabTitlePart> hostTabTitleParts,
            boolean showSftpDirectoryTree) {
        this(leftDockVisible, rightDockVisible, bottomDockVisible,
                leftDockWidth, rightDockWidth,
                hostLibraryDock, quickConnectDock, snippetsDock, credentialVaultDock,
                DockSide.RIGHT, DockSide.RIGHT,
                collapsedHostGroupIds, hostTabTitleParts, showSftpDirectoryTree,
                TerminalPreferences.defaults(), SftpSettings.defaults(), ConnectionSettings.defaults());
    }

    public GlobalSettings(
            boolean leftDockVisible,
            boolean rightDockVisible,
            boolean bottomDockVisible,
            double leftDockWidth,
            double rightDockWidth,
            DockSide hostLibraryDock,
            DockSide quickConnectDock,
            DockSide snippetsDock,
            DockSide credentialVaultDock,
            Set<String> collapsedHostGroupIds,
            Set<HostTabTitlePart> hostTabTitleParts,
            boolean showSftpDirectoryTree,
            TerminalPreferences terminalPreferences) {
        this(leftDockVisible, rightDockVisible, bottomDockVisible,
                leftDockWidth, rightDockWidth,
                hostLibraryDock, quickConnectDock, snippetsDock, credentialVaultDock,
                DockSide.RIGHT, DockSide.RIGHT, collapsedHostGroupIds, hostTabTitleParts,
                showSftpDirectoryTree, terminalPreferences, SftpSettings.defaults(), ConnectionSettings.defaults());
    }

    public GlobalSettings(
            boolean leftDockVisible,
            boolean rightDockVisible,
            boolean bottomDockVisible,
            double leftDockWidth,
            double rightDockWidth,
            DockSide hostLibraryDock,
            DockSide quickConnectDock,
            DockSide snippetsDock,
            DockSide credentialVaultDock,
            DockSide aiAssistantDock,
            Set<String> collapsedHostGroupIds,
            Set<HostTabTitlePart> hostTabTitleParts,
            boolean showSftpDirectoryTree,
            TerminalPreferences terminalPreferences,
            SftpSettings sftpSettings) {
        this(leftDockVisible, rightDockVisible, bottomDockVisible,
                leftDockWidth, rightDockWidth,
                hostLibraryDock, quickConnectDock, snippetsDock, credentialVaultDock,
                aiAssistantDock, DockSide.RIGHT, collapsedHostGroupIds, hostTabTitleParts,
                showSftpDirectoryTree, terminalPreferences, sftpSettings, ConnectionSettings.defaults());
    }

    public GlobalSettings(
            boolean leftDockVisible,
            boolean rightDockVisible,
            boolean bottomDockVisible,
            double leftDockWidth,
            double rightDockWidth,
            DockSide hostLibraryDock,
            DockSide quickConnectDock,
            DockSide snippetsDock,
            DockSide credentialVaultDock,
            DockSide aiAssistantDock,
            Set<String> collapsedHostGroupIds,
            Set<HostTabTitlePart> hostTabTitleParts,
            boolean showSftpDirectoryTree,
            TerminalPreferences terminalPreferences) {
        this(leftDockVisible, rightDockVisible, bottomDockVisible,
                leftDockWidth, rightDockWidth,
                hostLibraryDock, quickConnectDock, snippetsDock, credentialVaultDock,
                aiAssistantDock, DockSide.RIGHT, collapsedHostGroupIds, hostTabTitleParts,
                showSftpDirectoryTree, terminalPreferences, SftpSettings.defaults(), ConnectionSettings.defaults());
    }

    public GlobalSettings withCollapsedHostGroupIds(Set<String> groupIds) {
        return new GlobalSettings(
                leftDockVisible, rightDockVisible, bottomDockVisible,
                leftDockWidth, rightDockWidth,
                hostLibraryDock, quickConnectDock, snippetsDock, variableLibraryDock, credentialVaultDock,
                aiAssistantDock, diagnosticLogDock,
                groupIds, hostTabTitleParts, showSftpDirectoryTree, terminalPreferences, sftpSettings,
                connectionSettings, openLocalTerminalOnStartup, windowSettings,
                leftDockDisplayMode, rightDockDisplayMode, terminalActivityHeight, sftpActivityHeight,
                leftToolOrder, rightToolOrder, commandPaletteSettings, diagnosticLogSettings, aiSessionsCollapsed);
    }

    public GlobalSettings withSftpSettings(SftpSettings settings) {
        return new GlobalSettings(
                leftDockVisible, rightDockVisible, bottomDockVisible,
                leftDockWidth, rightDockWidth,
                hostLibraryDock, quickConnectDock, snippetsDock, variableLibraryDock, credentialVaultDock,
                aiAssistantDock, diagnosticLogDock, collapsedHostGroupIds, hostTabTitleParts,
                showSftpDirectoryTree, terminalPreferences, settings, connectionSettings,
                openLocalTerminalOnStartup, windowSettings, leftDockDisplayMode, rightDockDisplayMode,
                terminalActivityHeight, sftpActivityHeight, leftToolOrder, rightToolOrder,
                commandPaletteSettings, diagnosticLogSettings, aiSessionsCollapsed);
    }

    public GlobalSettings withShowSftpDirectoryTree(boolean show) {
        return new GlobalSettings(
                leftDockVisible, rightDockVisible, bottomDockVisible,
                leftDockWidth, rightDockWidth,
                hostLibraryDock, quickConnectDock, snippetsDock, variableLibraryDock, credentialVaultDock,
                aiAssistantDock, diagnosticLogDock, collapsedHostGroupIds, hostTabTitleParts,
                show, terminalPreferences, sftpSettings, connectionSettings,
                openLocalTerminalOnStartup, windowSettings, leftDockDisplayMode, rightDockDisplayMode,
                terminalActivityHeight, sftpActivityHeight, leftToolOrder, rightToolOrder,
                commandPaletteSettings, diagnosticLogSettings, aiSessionsCollapsed);
    }

    public GlobalSettings withWindowSettings(WindowSettings settings) {
        return new GlobalSettings(
                leftDockVisible, rightDockVisible, bottomDockVisible,
                leftDockWidth, rightDockWidth,
                hostLibraryDock, quickConnectDock, snippetsDock, variableLibraryDock, credentialVaultDock,
                aiAssistantDock, diagnosticLogDock, collapsedHostGroupIds, hostTabTitleParts,
                showSftpDirectoryTree, terminalPreferences, sftpSettings, connectionSettings,
                openLocalTerminalOnStartup, settings, leftDockDisplayMode, rightDockDisplayMode,
                terminalActivityHeight, sftpActivityHeight, leftToolOrder, rightToolOrder,
                commandPaletteSettings, diagnosticLogSettings, aiSessionsCollapsed);
    }

    public GlobalSettings withDockDisplayModes(DockDisplayMode left, DockDisplayMode right) {
        return new GlobalSettings(
                leftDockVisible, rightDockVisible, bottomDockVisible,
                leftDockWidth, rightDockWidth,
                hostLibraryDock, quickConnectDock, snippetsDock, variableLibraryDock, credentialVaultDock,
                aiAssistantDock, diagnosticLogDock, collapsedHostGroupIds, hostTabTitleParts,
                showSftpDirectoryTree, terminalPreferences, sftpSettings, connectionSettings,
                openLocalTerminalOnStartup, windowSettings, left, right,
                terminalActivityHeight, sftpActivityHeight, leftToolOrder, rightToolOrder,
                commandPaletteSettings, diagnosticLogSettings, aiSessionsCollapsed);
    }

    public GlobalSettings withToolDockAssignments(
            DockSide hosts, DockSide quickConnect, DockSide snippets, DockSide variables,
            DockSide credentials, DockSide aiAssistant, DockSide diagnosticLog) {
        return new GlobalSettings(
                leftDockVisible, rightDockVisible, bottomDockVisible, leftDockWidth, rightDockWidth,
                hosts, quickConnect, snippets, variables, credentials, aiAssistant, diagnosticLog,
                collapsedHostGroupIds, hostTabTitleParts, showSftpDirectoryTree,
                terminalPreferences, sftpSettings, connectionSettings, openLocalTerminalOnStartup,
                windowSettings, leftDockDisplayMode, rightDockDisplayMode, terminalActivityHeight,
                sftpActivityHeight, leftToolOrder, rightToolOrder, commandPaletteSettings,
                diagnosticLogSettings, aiSessionsCollapsed);
    }

    public GlobalSettings withActivityHeights(double terminalHeight, double sftpHeight) {
        return new GlobalSettings(
                leftDockVisible, rightDockVisible, bottomDockVisible,
                leftDockWidth, rightDockWidth,
                hostLibraryDock, quickConnectDock, snippetsDock, variableLibraryDock, credentialVaultDock,
                aiAssistantDock, diagnosticLogDock, collapsedHostGroupIds, hostTabTitleParts,
                showSftpDirectoryTree, terminalPreferences, sftpSettings, connectionSettings,
                openLocalTerminalOnStartup, windowSettings, leftDockDisplayMode, rightDockDisplayMode,
                terminalHeight, sftpHeight, leftToolOrder, rightToolOrder, commandPaletteSettings,
                diagnosticLogSettings, aiSessionsCollapsed);
    }

    public GlobalSettings withToolWindowLayout(
            DockSide variablesDock, java.util.List<String> leftOrder, java.util.List<String> rightOrder) {
        return new GlobalSettings(
                leftDockVisible, rightDockVisible, bottomDockVisible, leftDockWidth, rightDockWidth,
                hostLibraryDock, quickConnectDock, snippetsDock, variablesDock, credentialVaultDock,
                aiAssistantDock, diagnosticLogDock, collapsedHostGroupIds, hostTabTitleParts,
                showSftpDirectoryTree, terminalPreferences, sftpSettings, connectionSettings,
                openLocalTerminalOnStartup, windowSettings, leftDockDisplayMode, rightDockDisplayMode,
                terminalActivityHeight, sftpActivityHeight, leftOrder, rightOrder,
                commandPaletteSettings, diagnosticLogSettings, aiSessionsCollapsed);
    }

    public GlobalSettings withCommandPaletteSettings(CommandPaletteSettings settings) {
        return new GlobalSettings(
                leftDockVisible, rightDockVisible, bottomDockVisible, leftDockWidth, rightDockWidth,
                hostLibraryDock, quickConnectDock, snippetsDock, variableLibraryDock,
                credentialVaultDock, aiAssistantDock, diagnosticLogDock, collapsedHostGroupIds,
                hostTabTitleParts, showSftpDirectoryTree, terminalPreferences, sftpSettings,
                connectionSettings, openLocalTerminalOnStartup, windowSettings, leftDockDisplayMode,
                rightDockDisplayMode, terminalActivityHeight, sftpActivityHeight, leftToolOrder,
                rightToolOrder, settings, diagnosticLogSettings, aiSessionsCollapsed);
    }

    public GlobalSettings withDiagnosticLogSettings(DiagnosticLogSettings settings) {
        return new GlobalSettings(
                leftDockVisible, rightDockVisible, bottomDockVisible, leftDockWidth, rightDockWidth,
                hostLibraryDock, quickConnectDock, snippetsDock, variableLibraryDock,
                credentialVaultDock, aiAssistantDock, diagnosticLogDock, collapsedHostGroupIds,
                hostTabTitleParts, showSftpDirectoryTree, terminalPreferences, sftpSettings,
                connectionSettings, openLocalTerminalOnStartup, windowSettings, leftDockDisplayMode,
                rightDockDisplayMode, terminalActivityHeight, sftpActivityHeight, leftToolOrder,
                rightToolOrder, commandPaletteSettings, settings, aiSessionsCollapsed);
    }

    public GlobalSettings withAiSessionsCollapsed(boolean collapsed) {
        return new GlobalSettings(
                leftDockVisible, rightDockVisible, bottomDockVisible, leftDockWidth, rightDockWidth,
                hostLibraryDock, quickConnectDock, snippetsDock, variableLibraryDock,
                credentialVaultDock, aiAssistantDock, diagnosticLogDock, collapsedHostGroupIds,
                hostTabTitleParts, showSftpDirectoryTree, terminalPreferences, sftpSettings,
                connectionSettings, openLocalTerminalOnStartup, windowSettings, leftDockDisplayMode,
                rightDockDisplayMode, terminalActivityHeight, sftpActivityHeight, leftToolOrder,
                rightToolOrder, commandPaletteSettings, diagnosticLogSettings, collapsed);
    }

    public static GlobalSettings defaults() {
        GlobalSettings settings = new GlobalSettings(
                false,
                false,
                false,
                DEFAULT_LEFT_DOCK_WIDTH,
                DEFAULT_RIGHT_DOCK_WIDTH,
                DockSide.LEFT,
                DockSide.LEFT,
                DockSide.RIGHT,
                DockSide.LEFT,
                DockSide.RIGHT,
                DockSide.RIGHT,
                Set.of(),
                Set.of(HostTabTitlePart.NAME),
                true,
                TerminalPreferences.defaults(),
                SftpSettings.defaults(),
                ConnectionSettings.defaults(), false, WindowSettings.defaults(),
                DockDisplayMode.FLOATING, DockDisplayMode.FLOATING,
                DEFAULT_TERMINAL_ACTIVITY_HEIGHT, DEFAULT_SFTP_ACTIVITY_HEIGHT);
        return settings.withToolDockAssignments(
                        DockSide.RIGHT, DockSide.RIGHT, DockSide.RIGHT, DockSide.RIGHT,
                        DockSide.RIGHT, DockSide.RIGHT, DockSide.RIGHT)
                .withToolWindowLayout(DockSide.RIGHT,
                        java.util.List.of(),
                        java.util.List.of("QUICK_CONNECT", "HOST_LIBRARY", "CREDENTIAL_VAULT",
                                "SNIPPETS", "VARIABLE_LIBRARY", "AI_ASSISTANT", "DIAGNOSTIC_LOG"));
    }

    public static java.util.List<String> defaultLeftToolOrder() {
        return java.util.List.of("QUICK_CONNECT", "HOST_LIBRARY", "CREDENTIAL_VAULT");
    }

    public static java.util.List<String> defaultRightToolOrder() {
        return java.util.List.of("SNIPPETS", "VARIABLE_LIBRARY", "AI_ASSISTANT", "DIAGNOSTIC_LOG");
    }

    private static java.util.List<String> validatedToolOrder(java.util.List<String> order, String label) {
        java.util.List<String> values = java.util.List.copyOf(java.util.Objects.requireNonNull(order, label));
        if (values.size() > 64 || values.stream().anyMatch(value -> value == null || value.isBlank())
                || values.stream().distinct().count() != values.size()) {
            throw new IllegalArgumentException(label + " contains invalid values");
        }
        return values;
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
    }

    private static void requireActivityHeight(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0 || value > MAX_ACTIVITY_HEIGHT) {
            throw new IllegalArgumentException(name + " must be positive, finite and at most "
                    + MAX_ACTIVITY_HEIGHT);
        }
    }
}
