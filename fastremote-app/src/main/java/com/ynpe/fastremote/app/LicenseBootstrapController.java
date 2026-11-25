// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import com.ynpe.fastremote.domain.license.LicenseSnapshot;
import com.ynpe.fastremote.i18n.Localizer;
import com.ynpe.fastremote.licensing.LicenseGateway;
import java.time.ZoneId;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

final class LicenseBootstrapController {
    enum Operation { STATUS_CHECK, START_TRIAL, FORMAL_ACTIVATION }

    private final LicenseGateway gateway;
    private final Localizer localizer;
    private final Runnable openOffline;
    private final CompletableFuture<LicenseSnapshot> authorized = new CompletableFuture<>();
    @FXML private Label statusLabel;
    @FXML private TextField licenseCode;
    @FXML private Button activateButton;
    @FXML private Button trialButton;
    @FXML private Button retryButton;
    @FXML private Button openOfflineButton;
    private LicenseSnapshot currentSnapshot;

    LicenseBootstrapController(LicenseGateway gateway, Localizer localizer, Runnable openOffline) {
        this.gateway = gateway;
        this.localizer = localizer;
        this.openOffline = openOffline;
    }

    @FXML private void initialize() { checkStatus(); }

    CompletionStage<LicenseSnapshot> authorized() { return authorized; }

    @FXML private void openOffline() { openOffline.run(); }
    @FXML private void checkStatus() { run(gateway::status, Operation.STATUS_CHECK); }
    @FXML private void startTrial() { run(gateway::startTrial, Operation.START_TRIAL); }

    @FXML private void activate() {
        char[] code = licenseCode.getText().strip().toCharArray();
        if (code.length == 0) {
            setStatus(localizer.text("license.error.codeRequired"), true);
            return;
        }
        run(() -> gateway.activate(code), Operation.FORMAL_ACTIVATION);
    }

    private void run(Supplier<CompletionStage<LicenseSnapshot>> operation, Operation kind) {
        busy(true);
        setStatus(localizer.text("license.working"), false);
        operation.get().whenComplete((snapshot, error) -> Platform.runLater(() -> {
            if (error != null) {
                busy(false);
                setStatus(LicenseUiMessages.error(localizer, error), true);
                return;
            }
            currentSnapshot = snapshot;
            busy(false);
            if (shouldCompleteAuthorization(kind, snapshot)) {
                licenseCode.clear();
                String expiry = snapshot.expiresAt().map(value -> localizer.formatDateTime(value,
                        ZoneId.systemDefault())).orElse("-");
                setStatus(localizer.text("license.authorized", expiry), false);
                authorized.complete(snapshot);
            } else if (isActiveTrial(snapshot)) {
                String expiry = snapshot.expiresAt().map(value -> localizer.formatDateTime(value,
                        ZoneId.systemDefault())).orElse("-");
                setStatus(localizer.text("license.trial.activeUpgrade", expiry), false);
            } else {
                setStatus(LicenseUiMessages.status(localizer, snapshot.status()), true);
            }
        }));
    }

    static boolean shouldCompleteAuthorization(Operation operation, LicenseSnapshot snapshot) {
        if (!snapshot.status().permitsCommercialUse()) return false;
        return switch (operation) {
            case START_TRIAL -> true;
            case STATUS_CHECK, FORMAL_ACTIVATION -> !isActiveTrial(snapshot);
        };
    }

    static boolean isActiveTrial(LicenseSnapshot snapshot) {
        return snapshot.status() == com.ynpe.fastremote.domain.license.LicenseStatus.TRIAL_ACTIVE
                && "trial".equals(snapshot.kind());
    }

    private void setStatus(String text, boolean error) {
        statusLabel.setText(text);
        statusLabel.getStyleClass().remove("license-status-error");
        if (error) statusLabel.getStyleClass().add("license-status-error");
    }

    private void busy(boolean value) {
        activateButton.setDisable(value);
        trialButton.setDisable(value || isActiveTrialSnapshot());
        retryButton.setDisable(value);
        openOfflineButton.setDisable(value);
    }

    private boolean isActiveTrialSnapshot() {
        return currentSnapshot != null && isActiveTrial(currentSnapshot);
    }
}
