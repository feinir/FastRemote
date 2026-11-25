// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.Objects;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeView;
import javafx.scene.layout.VBox;

public final class CredentialLibraryController {
    interface Actions { void createCredential(); void createGroup(); }
    @FXML private TextField credentialSearchField;
    @FXML private TreeView<String> credentialTree;
    @FXML private VBox credentialEmpty;
    private Actions actions;
    void bind(Actions value) { actions = Objects.requireNonNull(value, "actions"); }
    @FXML private void createReusableCredential() { actions.createCredential(); }
    @FXML private void createCredentialGroup() { actions.createGroup(); }
    TextField searchField() { return credentialSearchField; }
    TreeView<String> tree() { return credentialTree; }
    VBox emptyState() { return credentialEmpty; }
}
