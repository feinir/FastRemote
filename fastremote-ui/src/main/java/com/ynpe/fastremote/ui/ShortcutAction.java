// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import javafx.scene.input.KeyCombination;

/** Actions exposed by FastRemote's single customizable keyboard shortcut scheme. */
enum ShortcutAction {
    OPEN_COMMAND_PALETTE(com.ynpe.fastremote.i18n.Texts.text("legacy.java.63c73c4730f4473e"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.36dd5fe70f7a2479"), "Shortcut+Shift+P"),
    OPEN_LOCAL_TERMINAL(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a5574109f0208e89"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.a9f4ff897bec7384"), "Shortcut+Shift+T"),
    OPEN_QUICK_CONNECT(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a5574109f0208e89"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.bec3d896410b5294"), "Shortcut+Shift+L"),
    OPEN_SNIPPET_LIBRARY(com.ynpe.fastremote.i18n.Texts.text("menu.tools"), com.ynpe.fastremote.i18n.Texts.text("library.commands"), "Shortcut+Shift+O"),
    CREATE_HOST(com.ynpe.fastremote.i18n.Texts.text("legacy.java.278dda9e9d5dce65"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.22a3f0c4b4bd8606"), "Shortcut+N"),
    OPEN_HOST_LIBRARY(com.ynpe.fastremote.i18n.Texts.text("legacy.java.278dda9e9d5dce65"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.c5d922b7585ad2eb"), "Shortcut+Shift+H"),
    CLOSE_ACTIVE_TAB(com.ynpe.fastremote.i18n.Texts.text("legacy.java.37979829426b0cdb"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.f79fcfe5fcbb1df6"), "Shortcut+W"),
    RECONNECT_ACTIVE_SESSION(com.ynpe.fastremote.i18n.Texts.text("legacy.java.37979829426b0cdb"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.a112954b713598cd"), "Shortcut+Shift+R"),
    NEXT_TAB(com.ynpe.fastremote.i18n.Texts.text("legacy.java.37979829426b0cdb"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.700fb84329fe724e"), "Ctrl+Tab"),
    PREVIOUS_TAB(com.ynpe.fastremote.i18n.Texts.text("legacy.java.37979829426b0cdb"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.b5aee7793ea77807"), "Ctrl+Shift+Tab"),
    OPEN_SETTINGS(com.ynpe.fastremote.i18n.Texts.text("legacy.java.63c73c4730f4473e"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.dd9c562cd32d54a5"), "Shortcut+Comma"),
    TOGGLE_HOST_LIBRARY(com.ynpe.fastremote.i18n.Texts.text("legacy.java.453eeceb25d826be"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.278dda9e9d5dce65"), "Shortcut+Alt+B"),
    TOGGLE_QUICK_CONNECT(com.ynpe.fastremote.i18n.Texts.text("legacy.java.453eeceb25d826be"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.bec3d896410b5294"), "Shortcut+Alt+Q"),
    TOGGLE_SNIPPETS(com.ynpe.fastremote.i18n.Texts.text("legacy.java.453eeceb25d826be"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.4466bdad43dae482"), "Shortcut+Alt+C"),
    TOGGLE_CREDENTIALS(com.ynpe.fastremote.i18n.Texts.text("legacy.java.453eeceb25d826be"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.bb2b5d546de68220"), "Shortcut+Alt+V"),
    TOGGLE_AI_ASSISTANT(com.ynpe.fastremote.i18n.Texts.text("legacy.java.453eeceb25d826be"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.1b0049fcfd4163fe"), "Shortcut+Shift+I"),
    TOGGLE_DIAGNOSTIC_LOG(com.ynpe.fastremote.i18n.Texts.text("legacy.java.453eeceb25d826be"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.bb1d19b85634606e"), "Shortcut+Alt+G"),
    FOCUS_CONTEXT_SEARCH(com.ynpe.fastremote.i18n.Texts.text("legacy.java.7c27592242b84c28"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.9bf574311469f818"), "Shortcut+F", Scope.TOOL_CONTEXT),
    OPEN_SELECTED_LIBRARY_ITEM(com.ynpe.fastremote.i18n.Texts.text("legacy.java.7c27592242b84c28"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.2d5a8e9c45578560"), "Enter", Scope.LIBRARY_CONTEXT),
    EDIT_SELECTED_LIBRARY_ITEM(com.ynpe.fastremote.i18n.Texts.text("legacy.java.7c27592242b84c28"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.051836569928a9f9"), "Shortcut+E", Scope.LIBRARY_CONTEXT),
    COPY_SELECTED_LIBRARY_ITEM(com.ynpe.fastremote.i18n.Texts.text("legacy.java.7c27592242b84c28"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.63d90d977348ab1f"), "Shortcut+D", Scope.LIBRARY_CONTEXT),
    DELETE_SELECTED_LIBRARY_ITEM(com.ynpe.fastremote.i18n.Texts.text("legacy.java.7c27592242b84c28"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.2f9daa828907b93f"), deleteDefault(System.getProperty("os.name", "")), Scope.LIBRARY_CONTEXT),
    SFTP_REFRESH("SFTP", com.ynpe.fastremote.i18n.Texts.text("legacy.java.a33a0adfa0be29dd"), "F5", Scope.SFTP_CONTEXT),
    SFTP_RENAME("SFTP", com.ynpe.fastremote.i18n.Texts.text("legacy.java.0d0cbac2eee54113"), renameDefault(System.getProperty("os.name", "")), Scope.SFTP_CONTEXT),
    SFTP_DELETE("SFTP", com.ynpe.fastremote.i18n.Texts.text("legacy.java.b4bae7547c4f96c8"), "Delete", Scope.SFTP_CONTEXT),
    SFTP_NEW_FOLDER("SFTP", com.ynpe.fastremote.i18n.Texts.text("legacy.java.84244abc71de03ac"), "Shortcut+Shift+N", Scope.SFTP_CONTEXT),
    SFTP_UPLOAD("SFTP", com.ynpe.fastremote.i18n.Texts.text("legacy.java.a09ddbe254f773dd"), "Shortcut+Shift+U", Scope.SFTP_CONTEXT),
    SFTP_DOWNLOAD("SFTP", com.ynpe.fastremote.i18n.Texts.text("legacy.java.4c6154b5a19377e2"), "Shortcut+Shift+D", Scope.SFTP_CONTEXT),
    SFTP_EDIT("SFTP", com.ynpe.fastremote.i18n.Texts.text("editor.edit"), "Shortcut+E", Scope.SFTP_CONTEXT),
    SFTP_TOGGLE_HIDDEN("SFTP", com.ynpe.fastremote.i18n.Texts.text("legacy.java.63276bd43690f937"), "Alt+Shift+H", Scope.SFTP_CONTEXT),
    TERMINAL_COPY(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e2a76ef1f12e147f"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.63d90d977348ab1f"), terminalDefault("C", true), Scope.TERMINAL_CONTEXT),
    TERMINAL_PASTE(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e2a76ef1f12e147f"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.33517926747180e6"), terminalDefault("V", true), Scope.TERMINAL_CONTEXT),
    TERMINAL_FIND(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e2a76ef1f12e147f"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.3003318e729a860b"), terminalDefault("F", false), Scope.TERMINAL_CONTEXT);

    enum Scope { GLOBAL, TOOL_CONTEXT, LIBRARY_CONTEXT, SFTP_CONTEXT, TERMINAL_CONTEXT }

    private final String category;
    private final String label;
    private final String defaultShortcut;
    private final Scope scope;

    ShortcutAction(String category, String label, String defaultShortcut) {
        this(category, label, defaultShortcut, Scope.GLOBAL);
    }

    ShortcutAction(String category, String label, String defaultShortcut, Scope scope) {
        this.category = category;
        this.label = label;
        this.defaultShortcut = defaultShortcut;
        this.scope = scope;
    }

    String id() { return name(); }
    String category() { return category; }
    String label() { return label; }
    String defaultShortcut() { return defaultShortcut; }
    Scope scope() { return scope; }

    KeyCombination defaultCombination() {
        return KeyCombination.valueOf(defaultShortcut);
    }

    private static String terminalDefault(String key, boolean shiftOutsideMac) {
        boolean mac = isMac(System.getProperty("os.name", ""));
        return mac ? "Meta+" + key : "Ctrl+" + (shiftOutsideMac ? "Shift+" : "") + key;
    }

    static String renameDefault(String osName) {
        return isMac(osName) ? "Enter" : "F2";
    }

    static String deleteDefault(String osName) {
        return isMac(osName) ? "Shortcut+Backspace" : "Delete";
    }

    private static boolean isMac(String osName) {
        return osName.toLowerCase(java.util.Locale.ROOT).contains("mac");
    }
}
