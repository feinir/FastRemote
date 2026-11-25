// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import com.ynpe.fastremote.domain.license.LicenseSnapshot;
import com.ynpe.fastremote.i18n.Localizer;
import com.ynpe.fastremote.licensing.LicenseGateway;
import java.time.ZoneId;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.zip.CRC32;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import com.ynpe.fastremote.ui.DialogSpacing;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

final class OfflineLicenseController {
    static final int QR_RENDER_SIZE = 1040;
    static final int QR_DISPLAY_SIZE = 420;
    static final double QR_DIALOG_WIDTH = 420.0;
    static final double QR_DIALOG_HEIGHT = 500.0;
    static final boolean QR_DIALOG_RESIZABLE = false;
    private static final int QR_QUIET_ZONE_MODULES = 4;
    private final LicenseGateway gateway;
    private final Localizer localizer;
    private final Runnable openOnline;
    private final CompletableFuture<LicenseSnapshot> authorized = new CompletableFuture<>();
    @FXML private Label statusLabel;
    @FXML private TextField licenseCode;
    @FXML private TextArea offlineRequest;
    @FXML private TextField activationSite;
    @FXML private Button showQrButton;
    @FXML private TextArea offlineLicense;
    @FXML private Button activateOfflineButton;
    @FXML private Button openOnlineButton;
    private Image activationQrImage;

    OfflineLicenseController(LicenseGateway gateway, Localizer localizer, Runnable openOnline) {
        this.gateway = gateway;
        this.localizer = localizer;
        this.openOnline = openOnline;
    }

    @FXML private void initialize() {
        activationSite.setText(LicenseRuntime.serviceUri().toString().replaceAll("/+$", "") + "/offline-activate");
        licenseCode.textProperty().addListener((ignored, previous, current) -> {
            offlineRequest.clear();
            activationQrImage = null;
            showQrButton.setDisable(true);
            if (isValidLicenseCode(current)) {
                generateOfflineRequest();
            } else {
                setStatus(localizer.text("license.offline.codeFormat"), false);
            }
        });
        setStatus(localizer.text("license.offline.codeFormat"), false);
    }

    static boolean isValidLicenseCode(String value) {
        String normalized = value == null ? "" : value.strip().toUpperCase(java.util.Locale.ROOT);
        if (!normalized.matches("FRM-(?:[0-9A-HJKMNP-TV-Z]{5}-){5}[0-9A-HJKMNP-TV-Z]{3}")) return false;
        String raw = normalized.substring(3).replace("-", "");
        String body = raw.substring(0, 26);
        CRC32 crc = new CRC32();
        crc.update(body.getBytes(StandardCharsets.US_ASCII));
        long checksum = crc.getValue() % 1024;
        String alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
        String expected = "" + alphabet.charAt((int) ((checksum >> 5) & 31)) + alphabet.charAt((int) (checksum & 31));
        return expected.equals(raw.substring(26));
    }

    CompletionStage<LicenseSnapshot> authorized() { return authorized; }

    @FXML private void openOnline() { openOnline.run(); }

    private void generateOfflineRequest() {
        busy(true);
        setStatus(localizer.text("license.offline.generating"), false);
        gateway.generateOfflineRequest(licenseCode.getText().strip().toCharArray()).whenComplete((request, error) -> Platform.runLater(() -> {
            busy(false);
            if (error != null) {
                setStatus(LicenseUiMessages.error(localizer, error), true);
                return;
            }
            offlineRequest.setText(request);
            Image qr = qrImage(activationSite.getText() + "#request=" +
                    URLEncoder.encode(request, StandardCharsets.UTF_8));
            activationQrImage = qr;
            showQrButton.setDisable(qr == null);
            setStatus(localizer.text(qr == null ? "license.offline.requestReadyNoQr" : "license.offline.requestReady"), false);
            offlineLicense.requestFocus();
        }));
    }

    private static Image qrImage(String value) {
        try {
            var matrix = new com.google.zxing.MultiFormatWriter().encode(value,
                    com.google.zxing.BarcodeFormat.QR_CODE, QR_RENDER_SIZE, QR_RENDER_SIZE,
                    java.util.Map.of(com.google.zxing.EncodeHintType.MARGIN, QR_QUIET_ZONE_MODULES));
            var output = new ByteArrayOutputStream();
            com.google.zxing.client.j2se.MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return new Image(new ByteArrayInputStream(output.toByteArray()));
        } catch (Exception error) {
            return null;
        }
    }

    @FXML private void copyOfflineRequest() {
        if (offlineRequest.getText().isBlank()) return;
        ClipboardContent content = new ClipboardContent();
        content.putString(offlineRequest.getText());
        Clipboard.getSystemClipboard().setContent(content);
        setStatus(localizer.text("license.offline.copied"), false);
    }

    @FXML private void copyActivationSite() {
        ClipboardContent content = new ClipboardContent();
        content.putString(activationSite.getText());
        Clipboard.getSystemClipboard().setContent(content);
        setStatus(localizer.text("license.offline.siteCopied"), false);
    }

    @FXML private void showActivationQr() {
        if (activationQrImage == null) return;
        ImageView imageView = new ImageView(activationQrImage);
        imageView.setFitWidth(QR_DISPLAY_SIZE);
        imageView.setFitHeight(QR_DISPLAY_SIZE);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(false);

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(localizer.text("license.offline.qrTitle"));
        dialog.initOwner(showQrButton.getScene().getWindow());
        dialog.setResizable(QR_DIALOG_RESIZABLE);
        DialogSpacing.prepare(dialog);
        DialogSpacing.configureResizableWindowGeometry(dialog,
                QR_DIALOG_WIDTH, QR_DIALOG_HEIGHT, QR_DIALOG_WIDTH, QR_DIALOG_HEIGHT);
        dialog.getDialogPane().setContent(imageView);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    @FXML private void activateOffline() {
        char[] content = offlineLicense.getText().strip().toCharArray();
        if (content.length == 0) {
            setStatus(localizer.text("license.offline.contentRequired"), true);
            return;
        }
        busy(true);
        setStatus(localizer.text("license.working"), false);
        gateway.activateOffline(content).whenComplete((snapshot, error) -> Platform.runLater(() -> {
            busy(false);
            if (error != null) {
                setStatus(LicenseUiMessages.error(localizer, error), true);
                return;
            }
            if (!snapshot.status().permitsCommercialUse()) {
                setStatus(LicenseUiMessages.status(localizer, snapshot.status()), true);
                return;
            }
            offlineLicense.clear();
            String expiry = snapshot.expiresAt().map(value -> localizer.formatDateTime(value,
                    ZoneId.systemDefault())).orElse("-");
            setStatus(localizer.text("license.authorized", expiry), false);
            authorized.complete(snapshot);
        }));
    }

    private void setStatus(String text, boolean error) {
        statusLabel.setText(text);
        statusLabel.getStyleClass().remove("license-status-error");
        if (error) statusLabel.getStyleClass().add("license-status-error");
    }

    private void busy(boolean value) {
        licenseCode.setDisable(value);
        showQrButton.setDisable(value || activationQrImage == null);
        activateOfflineButton.setDisable(value);
        openOnlineButton.setDisable(value);
    }
}
