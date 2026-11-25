// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import javafx.stage.FileChooser;

final class ConfigurationFileChooser {
    private ConfigurationFileChooser() { }

    static FileChooser forExport(String title, String filterDescription, boolean macOs) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        // NSSavePanel supplies the selected extension; including it in the suggested
        // name can produce .frc.frc. Keep the native result intact for overwrite consent.
        chooser.setInitialFileName(macOs ? "fastremote-config" : "fastremote-config.frc");
        FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter(filterDescription, "*.frc");
        chooser.getExtensionFilters().add(filter);
        chooser.setSelectedExtensionFilter(filter);
        return chooser;
    }
}
