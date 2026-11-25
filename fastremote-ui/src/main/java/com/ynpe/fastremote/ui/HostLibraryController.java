// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.Objects;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TreeView;

public final class HostLibraryController {
    interface Actions { void createHost(); void createGroup(); }
    @FXML private TextField hostSearchField;
    @FXML private ToggleButton hostAllFilter;
    @FXML private ToggleButton hostFavoriteFilter;
    @FXML private ToggleButton hostRecentFilter;
    @FXML private TreeView<String> hostTree;
    private Actions actions;
    void bind(Actions value) { actions = Objects.requireNonNull(value, "actions"); }
    @FXML private void createHost() { actions.createHost(); }
    @FXML private void createGroup() { actions.createGroup(); }
    TextField searchField() { return hostSearchField; }
    ToggleButton allFilter() { return hostAllFilter; }
    ToggleButton favoriteFilter() { return hostFavoriteFilter; }
    ToggleButton recentFilter() { return hostRecentFilter; }
    TreeView<String> tree() { return hostTree; }
}
