// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.Objects;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;

public final class DiagnosticLogController {
    interface Actions {
        void togglePlacement();
        void clear();
        void revealCurrentFile();
        void openCleanupSettings();
    }

    @FXML private TextArea diagnosticLogTextArea;
    @FXML private Button diagnosticLogPlacementButton;
    @FXML private LucideIconView diagnosticLogPlacementIcon;
    @FXML private Tooltip diagnosticLogPlacementTooltip;
    @FXML private Button diagnosticLogRevealButton;
    @FXML private Tooltip diagnosticLogRevealTooltip;
    @FXML private Tooltip diagnosticLogPathTooltip;
    @FXML private Label diagnosticLogPathLabel;
    private Actions actions;
    @FXML private void initialize() {
        String revealLabel = PlatformFileManagerLabel.reveal();
        diagnosticLogRevealButton.setAccessibleText(revealLabel);
        diagnosticLogRevealTooltip.setText(revealLabel);
    }
    void bind(Actions value) { actions = Objects.requireNonNull(value, "actions"); }
    @FXML private void togglePlacement() { actions().togglePlacement(); }
    @FXML private void clearDiagnosticLog() { actions().clear(); }
    @FXML private void revealCurrentLogFile() { actions().revealCurrentFile(); }
    @FXML private void openLogCleanupSettings() { actions().openCleanupSettings(); }
    private Actions actions() {
        if (actions == null) throw new IllegalStateException("Diagnostic log actions are not bound");
        return actions;
    }
    TextArea textArea() { return diagnosticLogTextArea; }
    Button placementButton() { return diagnosticLogPlacementButton; }
    LucideIconView placementIcon() { return diagnosticLogPlacementIcon; }
    Tooltip placementTooltip() { return diagnosticLogPlacementTooltip; }
    Label pathLabel() { return diagnosticLogPathLabel; }
    Tooltip pathTooltip() { return diagnosticLogPathTooltip; }
}
