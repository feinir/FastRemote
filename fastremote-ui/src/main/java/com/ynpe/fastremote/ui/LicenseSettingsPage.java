// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static com.ynpe.fastremote.ui.SettingsPageLayout.page;
import static com.ynpe.fastremote.ui.SettingsPageLayout.card;
import static com.ynpe.fastremote.ui.SettingsPageLayout.labeled;
import static com.ynpe.fastremote.ui.SettingsPageLayout.policyInfo;
import com.ynpe.fastremote.i18n.Localizer;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Window;

final class LicenseSettingsPage {
    private LicenseSettingsPage() {}

    static Node licensePage(Window owner, Localizer localizer,
            java.util.function.Supplier<CompletionStage<com.ynpe.fastremote.domain.license.LicenseSnapshot>> statusOperation,
            java.util.function.Supplier<CompletionStage<com.ynpe.fastremote.domain.license.LicenseSnapshot>> deactivateOperation,
            Consumer<com.ynpe.fastremote.domain.license.LicenseSnapshot> licenseChanged,
            Runnable openActivation) {
        Label status = new Label(localizer.text("license.working"));
        Label serial = new Label("-");
        Label expiry = new Label("-");
        TextField deviceCode = new TextField();
        deviceCode.setEditable(false);
        Button copyDeviceCode = new Button(localizer.text("license.settings.deviceCode.copy"));
        copyDeviceCode.setOnAction(event -> {
            var content = new javafx.scene.input.ClipboardContent();
            content.putString(deviceCode.getText());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        });
        HBox deviceCodeRow = new HBox(8.0, deviceCode, copyDeviceCode);
        status.setWrapText(true);
        Button activate = new Button(localizer.text("license.activate"));
        Button activateFullVersion = new Button(localizer.text("license.activateFull"));
        activateFullVersion.setManaged(false);
        activateFullVersion.setVisible(false);
        Button deactivate = new Button(localizer.text("license.deactivate"));
        Button refresh = new Button(localizer.text("license.retry"));
        HBox actions = new HBox(8.0, activate, activateFullVersion, deactivate, refresh);
        Consumer<com.ynpe.fastremote.domain.license.LicenseSnapshot> render = snapshot -> {
            status.setText(snapshot.status().name());
            serial.setText(snapshot.licenseHint().isBlank() ? localizer.text("license.serial.unavailable") : snapshot.licenseHint());
            expiry.setText(snapshot.expiresAt().map(value -> localizer.formatDateTime(value,
                    java.time.ZoneId.systemDefault())).orElse("-"));
            deviceCode.setText(snapshot.deviceCode().isBlank() ? "-" : snapshot.deviceCode());
            copyDeviceCode.setDisable(snapshot.deviceCode().isBlank());
            boolean authorized = canDeactivateLicense(snapshot);
            boolean trialActive = canActivateFullVersion(snapshot);
            activate.setManaged(!trialActive);
            activate.setVisible(!trialActive);
            activate.setDisable(authorized);
            activateFullVersion.setManaged(trialActive);
            activateFullVersion.setVisible(trialActive);
            activateFullVersion.setDisable(!trialActive);
            deactivate.setDisable(!authorized);
        };
        Runnable load = () -> {
            refresh.setDisable(true);
            statusOperation.get().whenComplete((snapshot, error) -> Platform.runLater(() -> {
                refresh.setDisable(false);
                if (error != null) {
                    status.setText(localizer.text("license.error", "AGENT_UNAVAILABLE"));
                    serial.setText("-"); expiry.setText("-"); deviceCode.setText("-"); copyDeviceCode.setDisable(true);
                    activate.setManaged(true); activate.setVisible(true); activate.setDisable(false);
                    activateFullVersion.setManaged(false); activateFullVersion.setVisible(false);
                    deactivate.setDisable(true); return;
                }
                render.accept(snapshot);
                licenseChanged.accept(snapshot);
            }));
        };
        activate.setOnAction(event -> openActivation.run());
        activateFullVersion.setOnAction(event -> openActivation.run());
        refresh.setOnAction(event -> load.run());
        deactivate.setOnAction(event -> {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION, localizer.text("license.deactivate.confirm"),
                    ButtonType.CANCEL, ButtonType.OK);
            confirmation.initOwner(owner);
            confirmation.setTitle(localizer.text("license.title"));
            confirmation.setHeaderText(localizer.text("license.deactivate"));
            if (confirmation.showAndWait().filter(ButtonType.OK::equals).isEmpty()) return;
            deactivate.setDisable(true);
            deactivateOperation.get().whenComplete((snapshot, error) -> Platform.runLater(() ->
                    applyDeactivationResult(snapshot, error, render, licenseChanged, failure -> {
                        status.setText(localizer.text("license.error", "DEACTIVATION_FAILED"));
                        deactivate.setDisable(false);
                    })));
        });
        load.run();
        return page(localizer.text("license.settings.title"), localizer.text("license.settings.subtitle"),
                card(localizer.text("license.settings.status"), localizer.text("license.settings.statusHelp"),
                        labeled(localizer.text("license.settings.state"), status),
                        labeled(localizer.text("license.settings.serial"), serial),
                        labeled(localizer.text("license.settings.expires"), expiry),
                        labeled(localizer.text("license.settings.deviceCode"), deviceCodeRow),
                        policyInfo(localizer.text("license.settings.deviceCode.help")), actions));
    }

    static boolean canDeactivateLicense(com.ynpe.fastremote.domain.license.LicenseSnapshot snapshot) {
        return snapshot.status().permitsCommercialUse()
                || snapshot.status() == com.ynpe.fastremote.domain.license.LicenseStatus.INTEGRITY_ERROR;
    }

    static boolean canActivateFullVersion(com.ynpe.fastremote.domain.license.LicenseSnapshot snapshot) {
        return snapshot.status() == com.ynpe.fastremote.domain.license.LicenseStatus.TRIAL_ACTIVE
                && "trial".equals(snapshot.kind());
    }

    static void applyDeactivationResult(
            com.ynpe.fastremote.domain.license.LicenseSnapshot snapshot,
            Throwable error,
            Consumer<com.ynpe.fastremote.domain.license.LicenseSnapshot> render,
            Consumer<com.ynpe.fastremote.domain.license.LicenseSnapshot> licenseChanged,
            Consumer<Throwable> failure) {
        if (error != null) {
            failure.accept(error);
            return;
        }
        render.accept(snapshot);
        licenseChanged.accept(snapshot);
    }
}
