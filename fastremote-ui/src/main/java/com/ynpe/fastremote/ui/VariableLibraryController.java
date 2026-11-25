// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.Objects;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeView;

public final class VariableLibraryController {
    interface Actions { void createVariable(); void createGroup(); }
    @FXML private TextField variableSearchField;
    @FXML private TreeView<String> variableTree;
    private Actions actions;
    void bind(Actions value) { actions = Objects.requireNonNull(value, "actions"); }
    @FXML private void createVariable() { actions.createVariable(); }
    @FXML private void createVariableGroup() { actions.createGroup(); }
    TextField searchField() { return variableSearchField; }
    TreeView<String> tree() { return variableTree; }
}
