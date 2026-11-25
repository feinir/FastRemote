// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.catalog.GroupId;
import com.ynpe.fastremote.domain.catalog.SavedHost;
import com.ynpe.fastremote.domain.catalog.HostGroup;
import com.ynpe.fastremote.domain.connection.AuthenticationType;
import com.ynpe.fastremote.domain.connection.ConnectionProfile;
import com.ynpe.fastremote.domain.connection.Host;
import com.ynpe.fastremote.domain.connection.Port;
import com.ynpe.fastremote.domain.connection.Username;
import com.ynpe.fastremote.domain.vault.SecretId;
import com.ynpe.fastremote.domain.vault.VaultStatus;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HostEditorDialogTest {
    @BeforeAll
    static void startJavaFxToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(5, TimeUnit.SECONDS));
    }

    @Test
    void clipboardImportUsesTheTargetHostAsTheNewHostName() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                TextField name = new TextField();
                TextField host = new TextField();
                TextField port = new TextField();
                TextField username = new TextField();
                javafx.scene.control.ComboBox<AuthenticationType> authentication =
                        new javafx.scene.control.ComboBox<>();
                TextField privateKey = new TextField();
                AtomicReference<String> password = new AtomicReference<>();
                AtomicReference<String> passphrase = new AtomicReference<>();

                ConnectionClipboardImporter.apply(
                        ConnectionClipboardImporter.parse(
                                "sshpass -p secret ssh -p 22022 deploy@app.example.com"),
                        Optional.of(name), host, port, username, authentication,
                        password::set, privateKey, passphrase::set);

                assertEquals("app.example.com", name.getText());
                assertEquals("app.example.com", host.getText());
                assertEquals("22022", port.getText());
                assertEquals("deploy", username.getText());
                assertEquals(AuthenticationType.PASSWORD, authentication.getValue());
                assertEquals("secret", password.get());
                assertEquals("", passphrase.get());

                name.setText("existing-name");
                host.setText("existing.example.com");
                port.setText("2222");
                ConnectionClipboardImporter.apply(
                        ConnectionClipboardImporter.parse(
                                "login: smileserv   password: Example!Clipboard.SM##"),
                        Optional.of(name), host, port, username, authentication,
                        password::set, privateKey, passphrase::set);

                assertEquals("existing-name", name.getText());
                assertEquals("existing.example.com", host.getText());
                assertEquals("2222", port.getText());
                assertEquals("smileserv", username.getText());
                assertEquals("Example!Clipboard.SM##", password.get());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void hostAndGroupEditorsUseAnchoredLabelAndFieldColumns() {
        assertEquals(100.0, HostEditorDialog.formFieldLeft());
        assertEquals(440.0, HostEditorDialog.formHeight(10));
        assertEquals(10.0,
                HostEditorDialog.FORM_ROW_HEIGHT - HostEditorDialog.FORM_CONTROL_HEIGHT);
        assertEquals(160.0, HostEditorDialog.FORM_FIELD_MIN_WIDTH);
        assertEquals(260.0, HostEditorDialog.FORM_CELL_MIN_WIDTH);
        assertEquals(680.0, HostEditorDialog.DIALOG_WIDTH);
        assertEquals(705.0, HostEditorDialog.CREATE_DIALOG_HEIGHT);
        assertEquals(661.0, HostEditorDialog.EDIT_DIALOG_HEIGHT);
        assertEquals(705.0, HostEditorDialog.dialogHeight(HostEditorDialog.Mode.CREATE));
        assertEquals(661.0, HostEditorDialog.dialogHeight(HostEditorDialog.Mode.EDIT));
        assertEquals(661.0, HostEditorDialog.dialogHeight(HostEditorDialog.Mode.COPY));
        assertEquals(100.0, GroupEditorDialog.formFieldLeft());
        assertEquals(85.0, CredentialEditorDialog.LABEL_WIDTH);
        assertEquals(10.0,
                CredentialEditorDialog.FORM_ROW_HEIGHT - CredentialEditorDialog.FORM_CONTROL_HEIGHT);
        assertEquals(308.0, CredentialEditorDialog.formHeight(7));
        assertEquals(600.0, CredentialEditorDialog.DIALOG_WIDTH);
        assertEquals(404.0, CredentialEditorDialog.DIALOG_HEIGHT);
        assertEquals(400.0, GroupEditorDialog.DIALOG_WIDTH);
        assertEquals(192.0, GroupEditorDialog.formHeight(4));
        assertEquals(10.0, DialogSpacing.MARGIN);
    }

    @Test
    void credentialEditorUsesFixedContentGeometry() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                Dialog<Void> dialog = new Dialog<>();
                CredentialEditorDialog.configureDialogSizing(dialog);

                assertFalse(dialog.isResizable());
                assertEquals(CredentialEditorDialog.DIALOG_WIDTH,
                        dialog.getDialogPane().getPrefWidth());
                assertEquals(CredentialEditorDialog.DIALOG_WIDTH,
                        dialog.getDialogPane().getMinWidth());
                assertEquals(CredentialEditorDialog.DIALOG_HEIGHT,
                        dialog.getDialogPane().getPrefHeight());
                assertEquals(CredentialEditorDialog.DIALOG_HEIGHT,
                        dialog.getDialogPane().getMinHeight());
                assertEquals(CredentialEditorDialog.DIALOG_HEIGHT,
                        dialog.getDialogPane().getMaxHeight());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void hostDialogUsesModeSpecificContentGeometryAndLinuxResize()
            throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                for (HostEditorDialog.Mode mode : HostEditorDialog.Mode.values()) {
                    Dialog<Void> dialog = new Dialog<>();
                    HostEditorDialog.configureDialogSizing(dialog, mode);
                    double expectedHeight = HostEditorDialog.dialogHeight(mode);

                    assertEquals(LocalizedTextInputContextMenus.isLinux(), dialog.isResizable());
                    assertEquals(HostEditorDialog.DIALOG_WIDTH,
                            dialog.getDialogPane().getPrefWidth());
                    assertEquals(HostEditorDialog.DIALOG_WIDTH,
                            dialog.getDialogPane().getMinWidth());
                    assertEquals(expectedHeight, dialog.getDialogPane().getPrefHeight());
                    assertEquals(expectedHeight, dialog.getDialogPane().getMinHeight());
                    assertEquals(LocalizedTextInputContextMenus.isLinux() ? javafx.scene.layout.Region.USE_COMPUTED_SIZE : expectedHeight, dialog.getDialogPane().getMaxHeight());
                    assertEquals(expectedHeight,
                            DialogSpacing.configuredResizableWindowHeight(dialog.getDialogPane()));
                }
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void reservedConnectionFeedbackClearsTextWithoutLeavingTheLayout() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                Label hiddenFeedback = ConnectionFeedback.reservedLabel();
                assertFalse(hiddenFeedback.isVisible());
                assertTrue(hiddenFeedback.isManaged());
                assertEquals(ConnectionFeedback.RESERVED_HEIGHT, hiddenFeedback.getMinHeight());
                assertFalse(hiddenFeedback.getStyleClass().contains("connection-feedback-error"));
                for (ConnectionFeedback.Tone tone : ConnectionFeedback.Tone.values()) {
                    ConnectionFeedback.show(hiddenFeedback, "message", tone);
                    assertTrue(hiddenFeedback.isVisible());
                    assertTrue(hiddenFeedback.isManaged());
                    assertEquals("message", hiddenFeedback.getText());
                    ConnectionFeedback.clear(hiddenFeedback);
                    assertFalse(hiddenFeedback.isVisible());
                    assertTrue(hiddenFeedback.isManaged());
                    assertEquals(ConnectionFeedback.RESERVED_HEIGHT, hiddenFeedback.getMinHeight());
                    assertEquals("", hiddenFeedback.getText());
                    assertFalse(hiddenFeedback.getStyleClass().contains("connection-feedback-error"));
                    ConnectionFeedback.show(hiddenFeedback, "   ", tone);
                    assertFalse(hiddenFeedback.isVisible());
                    assertTrue(hiddenFeedback.isManaged());
                }
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void compactDialogContentKeepsTenPixelTopAndSideMarginsWithoutABottomSpacer() {
        var content = new javafx.scene.layout.Pane();

        DialogSpacing.anchoredContent(content);

        assertEquals(10.0, javafx.scene.layout.AnchorPane.getTopAnchor(content));
        assertEquals(10.0, javafx.scene.layout.AnchorPane.getLeftAnchor(content));
        assertEquals(10.0, javafx.scene.layout.AnchorPane.getRightAnchor(content));
        assertEquals(0.0, javafx.scene.layout.AnchorPane.getBottomAnchor(content));
    }

    @Test
    void hostEditorUsesTheSharedLocalizedLabelColumn() {
        assertEquals(List.of(
                "主机名称", "所属分组", "主机图标", "目标主机", "连接端口", "登录用户",
                "兼容模式", "凭证来源", "认证方式", "主机密码", "证书", "证书口令",
                "凭证库凭证", "连接路径"),
                HostEditorDialog.formLabels());
    }

    @Test
    void exposesSavedPromptAndCredentialLibrarySourcesInThatOrder() {
        assertEquals(
                List.of("保存密码/证书", "连接时输入", "从凭证库选择"),
                HostEditorDialog.newHostCredentialSources().stream()
                        .map(HostEditorDialog.CredentialSource::label).toList());
    }

    @Test
    void appendsNewHostAfterExistingHostsInTheSelectedGroup() {
        GroupId group = GroupId.create();
        ConnectionProfile profile = new ConnectionProfile(
                new Host("host.example.com"), new Port(22), new Username("ops"),
                AuthenticationType.PASSWORD, Optional.empty());
        List<SavedHost> hosts = List.of(
                SavedHost.create("one", Optional.of(group), profile, 0),
                SavedHost.create("two", Optional.of(group), profile, 20),
                SavedHost.create("root", Optional.empty(), profile, 100));

        assertEquals(30, HostEditorDialog.nextSortOrder(hosts, Optional.of(group)));
        assertEquals(110, HostEditorDialog.nextSortOrder(hosts, Optional.empty()));
    }

    @Test
    void hostLibraryDisplaysOnlyTheSavedName() {
        ConnectionProfile profile = new ConnectionProfile(
                new Host("192.0.2.25"), new Port(22), new Username("ops"),
                AuthenticationType.PASSWORD, Optional.empty());
        SavedHost host = SavedHost.create("生产数据库", Optional.empty(), profile, 0);

        assertEquals("生产数据库", HostCatalogModel.hostDisplayName(host));
    }

    @Test
    void requiresUnlockOnlyWhenSavingAHostBoundSecretIntoALockedVault() {
        assertTrue(HostEditorDialog.requiresVaultUnlock(
                HostEditorDialog.CredentialSource.save(), false));
        assertFalse(HostEditorDialog.requiresVaultUnlock(
                HostEditorDialog.CredentialSource.save(), true));
        assertFalse(HostEditorDialog.requiresVaultUnlock(
                HostEditorDialog.CredentialSource.prompt(), false));
        assertFalse(HostEditorDialog.requiresVaultUnlock(
                HostEditorDialog.CredentialSource.library(), false));
        assertTrue(HostEditorDialog.requiresVaultUnlock(
                HostEditorDialog.CredentialSource.current(SecretId.create()), false,
                HostEditorDialog.Mode.COPY));
        assertFalse(HostEditorDialog.requiresVaultUnlock(
                HostEditorDialog.CredentialSource.current(SecretId.create()), false,
                HostEditorDialog.Mode.EDIT));
    }

    @Test
    void editUsesTheActualCredentialSource() {
        assertEquals(HostEditorDialog.CredentialSourceKind.SAVE_TO_HOST,
                HostEditorDialog.initialCredentialSourceKind(HostEditorDialog.Mode.EDIT, true, false));
        assertEquals(HostEditorDialog.CredentialSourceKind.LIBRARY,
                HostEditorDialog.initialCredentialSourceKind(HostEditorDialog.Mode.EDIT, true, true));
        assertEquals(HostEditorDialog.CredentialSourceKind.PROMPT,
                HostEditorDialog.initialCredentialSourceKind(HostEditorDialog.Mode.EDIT, false, false));
    }

    @Test
    void editPreservesOnlyUnchangedBoundSecretsForBothSaveAndConnectionTest() {
        for (AuthenticationType auth : AuthenticationType.values()) {
            SavedHost host = SavedHost.create("fixture", Optional.empty(), new ConnectionProfile(
                    new Host("example.test"), new Port(22), new Username("fixture"), auth, Optional.empty()))
                    .withCredential(Optional.of(SecretId.create()));
            var save = HostEditorDialog.CredentialSource.save();
            var preserved = HostEditorDialog.submissionCredentialSource(
                    HostEditorDialog.Mode.EDIT, host, false, save, auth, "", "", "");
            assertEquals(host.credentialSecretId(), preserved.referenceId());
            assertEquals(save, HostEditorDialog.submissionCredentialSource(
                    HostEditorDialog.Mode.EDIT, host, false, save, auth,
                    "replacement", "/replacement/key", "replacement"));
            assertEquals(save, HostEditorDialog.submissionCredentialSource(
                    HostEditorDialog.Mode.EDIT, host, true, save, auth, "", "", ""));
            assertEquals(save, HostEditorDialog.submissionCredentialSource(
                    HostEditorDialog.Mode.EDIT, host, false, save,
                    auth == AuthenticationType.PASSWORD ? AuthenticationType.PRIVATE_KEY : AuthenticationType.PASSWORD,
                    "", "", ""));
            var prompt = HostEditorDialog.CredentialSource.prompt();
            assertEquals(prompt, HostEditorDialog.submissionCredentialSource(
                    HostEditorDialog.Mode.EDIT, host, false, prompt, auth, "", "", ""));
            var library = HostEditorDialog.CredentialSource.library();
            assertEquals(library, HostEditorDialog.submissionCredentialSource(
                    HostEditorDialog.Mode.EDIT, host, false, library, auth, "", "", ""));
        }
    }

    @Test
    void copyPreservesTheSourceCredentialMode() {
        assertEquals(HostEditorDialog.CredentialSourceKind.CURRENT_HOST,
                HostEditorDialog.initialCredentialSourceKind(
                        HostEditorDialog.Mode.COPY, true, false));
        assertEquals(HostEditorDialog.CredentialSourceKind.LIBRARY,
                HostEditorDialog.initialCredentialSourceKind(
                        HostEditorDialog.Mode.COPY, true, true));
        assertEquals(HostEditorDialog.CredentialSourceKind.PROMPT,
                HostEditorDialog.initialCredentialSourceKind(
                        HostEditorDialog.Mode.COPY, false, false));
        assertEquals(HostEditorDialog.CredentialSourceKind.SAVE_TO_HOST,
                HostEditorDialog.initialCredentialSourceKind(
                        HostEditorDialog.Mode.CREATE, false, false));
    }

    @Test
    void copyAddsTheCopySuffixAndUsesNewHostSemantics() {
        ConnectionProfile profile = new ConnectionProfile(
                new Host("copy.example.com"), new Port(22), new Username("ops"),
                AuthenticationType.PASSWORD, Optional.empty());
        SavedHost source = SavedHost.create("生产主机", Optional.empty(), profile, 0);

        assertEquals("生产主机 副本",
                HostEditorDialog.initialHostName(source, HostEditorDialog.Mode.COPY));
        assertEquals("生产主机",
                HostEditorDialog.initialHostName(source, HostEditorDialog.Mode.EDIT));
        assertTrue(HostEditorDialog.createsNewHost(HostEditorDialog.Mode.COPY));
        assertFalse(HostEditorDialog.createsNewHost(HostEditorDialog.Mode.EDIT));
    }

    @Test
    void savedPasswordAuthenticationRejectsAnEmptyHostPassword() {
        assertEquals(Optional.of("请输入主机密码"), HostEditorDialog.validateSavedHostPassword(
                HostEditorDialog.CredentialSource.save(), AuthenticationType.PASSWORD, ""));
        assertTrue(HostEditorDialog.validateSavedHostPassword(
                HostEditorDialog.CredentialSource.save(), AuthenticationType.PASSWORD, "secret").isEmpty());
        assertTrue(HostEditorDialog.validateSavedHostPassword(
                HostEditorDialog.CredentialSource.prompt(), AuthenticationType.PASSWORD, "").isEmpty());
        assertTrue(HostEditorDialog.validateSavedHostPassword(
                HostEditorDialog.CredentialSource.save(), AuthenticationType.PRIVATE_KEY, "").isEmpty());
    }

    @Test
    void connectionTestUnlocksVaultForTargetOrJumpHostStoredCredentials() {
        ConnectionProfile profile = new ConnectionProfile(
                new Host("jump.example.com"), new Port(22), new Username("ops"),
                AuthenticationType.PASSWORD, Optional.empty());
        SavedHost directJump = SavedHost.create("jump", Optional.empty(), profile, 0)
                .withCredential(Optional.of(SecretId.create()));
        SavedHost nestedJump = SavedHost.create("nested", Optional.empty(), profile, 10)
                .withJumpHost(Optional.of(directJump.id()));

        assertTrue(HostEditorDialog.connectionTestRequiresVault(
                Optional.of(SecretId.create()), Optional.empty(), List.of()));
        assertTrue(HostEditorDialog.connectionTestRequiresVault(
                Optional.empty(), Optional.of(nestedJump.id()), List.of(directJump, nestedJump)));
        assertFalse(HostEditorDialog.connectionTestRequiresVault(
                Optional.empty(), Optional.empty(), List.of(directJump, nestedJump)));
    }

    @Test
    void promptsForAConnectionSecretOnlyWhenTheHostHasNoStoredCredential() {
        ConnectionProfile profile = new ConnectionProfile(
                new Host("prompt.example.com"), new Port(22), new Username("ops"),
                AuthenticationType.PASSWORD, Optional.empty());
        SavedHost prompted = SavedHost.create("临时密码", Optional.empty(), profile, 0);
        SavedHost stored = prompted.withCredential(Optional.of(SecretId.create()));

        assertTrue(MainWindowController.requiresConnectionPrompt(prompted));
        assertFalse(MainWindowController.requiresConnectionPrompt(stored));
    }

    @Test
    void hostManagerUsesTheSameHostAndGroupDropRulesAsTheDockedCatalog() {
        ConnectionProfile profile = new ConnectionProfile(
                new Host("move.example.com"), new Port(22), new Username("ops"),
                AuthenticationType.PASSWORD, Optional.empty());
        SavedHost host = SavedHost.create("待移动", Optional.empty(), profile, 0);
        HostGroup group = HostGroup.root("目标组");
        TreeItem<String> hostItem = new TreeItem<>(host.name());
        TreeItem<String> groupItem = new TreeItem<>(group.name());
        Map<TreeItem<String>, SavedHost> hosts = new IdentityHashMap<>();
        Map<TreeItem<String>, HostGroup> groups = new IdentityHashMap<>();
        hosts.put(hostItem, host);
        groups.put(groupItem, group);

        assertTrue(LibraryTreeSupport.validCatalogDropTarget(
                groupItem, LibraryTreeSupport.DropPosition.INTO, hostItem, hosts, groups));
        assertFalse(LibraryTreeSupport.validCatalogDropTarget(
                groupItem, LibraryTreeSupport.DropPosition.BEFORE, hostItem, hosts, groups));
    }

    @Test
    void catalogSearchMatchesNameHostUsernameAndPortWithoutCaseSensitivityAndCombinesWithFilters() {
        SavedHost favorite = SavedHost.create(
                "Production DB", Optional.empty(), new ConnectionProfile(
                        new Host("DB.EXAMPLE.COM"), new Port(22022), new Username("DeployUser"),
                        AuthenticationType.PASSWORD, Optional.empty()), 0).withFavorite(true);
        SavedHost ordinary = SavedHost.create(
                "Test host", Optional.empty(), new ConnectionProfile(
                        new Host("test.example.com"), new Port(22), new Username("tester"),
                        AuthenticationType.PASSWORD, Optional.empty()), 10);
        HostCatalogSnapshot snapshot = new HostCatalogSnapshot(
                List.of(), List.of(favorite, ordinary), List.of(), VaultStatus.NOT_CREATED);

        assertEquals(List.of(favorite), HostCatalogModel.filteredCatalog(
                snapshot, "production", HostCatalogModel.HostFilter.ALL).hosts());
        assertEquals(List.of(favorite), HostCatalogModel.filteredCatalog(
                snapshot, "db.example.com", HostCatalogModel.HostFilter.ALL).hosts());
        assertEquals(List.of(favorite), HostCatalogModel.filteredCatalog(
                snapshot, "deployuser", HostCatalogModel.HostFilter.ALL).hosts());
        assertEquals(List.of(favorite), HostCatalogModel.filteredCatalog(
                snapshot, "22022", HostCatalogModel.HostFilter.ALL).hosts());
        assertEquals(List.of(favorite), HostCatalogModel.filteredCatalog(
                snapshot, "", HostCatalogModel.HostFilter.FAVORITES).hosts());
    }
}
