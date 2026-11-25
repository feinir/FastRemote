// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

final class LocalFileWorkspaceView {
    record Loaded(Parent root, LocalFileWorkspaceController controller) { }

    static Loaded load(LocalFileWorkflow workflow, LocalFileWorkspaceController.Actions actions,
            Path initialDirectory) {
        LocalFileWorkspaceController controller = new LocalFileWorkspaceController(
                workflow, actions, initialDirectory);
        URL resource = Objects.requireNonNull(LocalFileWorkspaceView.class.getResource("LocalFileWorkspaceView.fxml"));
        FXMLLoader loader = new FXMLLoader(resource, com.ynpe.fastremote.i18n.Texts.localizer().bundle());
        loader.setController(controller);
        try {
            return new Loaded(loader.load(), controller);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load local file workspace", failure);
        }
    }

    private LocalFileWorkspaceView() { }
}
