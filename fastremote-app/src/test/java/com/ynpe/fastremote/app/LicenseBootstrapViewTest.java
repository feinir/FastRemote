// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class LicenseBootstrapViewTest {
    @Test
    void declaresSeparateOnlineAndTextOnlyOfflineDialogs() throws Exception {
        String online = resource("/com/ynpe/fastremote/app/LicenseBootstrapView.fxml");
        String offline = resource("/com/ynpe/fastremote/app/OfflineLicenseView.fxml");

        assertTrue(online.contains("fx:id=\"licenseCode\""));
        assertTrue(online.contains("fx:id=\"licenseCode\" minWidth=\"0\""));
        assertTrue(online.contains("fx:id=\"trialButton\" minWidth=\"-Infinity\""));
        assertTrue(online.contains("maxWidth=\"Infinity\" HBox.hgrow=\"ALWAYS\""));
        assertTrue(online.contains("onAction=\"#openOffline\""));
        assertTrue(online.contains("styleClass=\"license-scroll\""));
        assertTrue(online.contains("styleClass=\"license-actions\""));
        assertFalse(online.contains("offlineRequest"));
        assertTrue(offline.contains("fx:id=\"offlineRequest\""));
        assertTrue(offline.contains("fx:id=\"offlineLicense\""));
        assertTrue(offline.contains("onAction=\"#copyOfflineRequest\""));
        assertTrue(offline.contains("fx:id=\"copyRequestButton\""));
        assertTrue(offline.contains("fx:id=\"copyActivationSiteButton\""));
        assertFalse(offline.contains("fx:id=\"copyRequestButton\" text=\"%license.offline.copy\" onAction=\"#copyOfflineRequest\" disable=\"true\""));
        assertFalse(offline.contains("fx:id=\"copyActivationSiteButton\" text=\"%license.offline.copySite\" onAction=\"#copyActivationSite\" disable=\"true\""));
        assertTrue(offline.contains("onAction=\"#showActivationQr\""));
        assertTrue(offline.contains("onAction=\"#activateOffline\""));
        assertTrue(offline.contains("onAction=\"#openOnline\""));
        assertTrue(offline.contains("styleClass=\"license-scroll\""));
        assertTrue(offline.contains("styleClass=\"license-actions\""));
        assertFalse(offline.contains("FileChooser"));
        assertFalse(offline.contains("FastRemote.request"));
        assertFalse(offline.contains("FastRemote.lic"));
        assertFalse(offline.contains("fx:id=\"activationQr\""));
        assertFalse(offline.contains("VBox.vgrow=\"ALWAYS\""));

        String styles = resource("/com/ynpe/fastremote/app/license-bootstrap.css");
        assertTrue(styles.contains(".license-status-error"));
        assertTrue(styles.contains("-fx-background-color"));
    }

    private static String resource(String name) throws Exception {
        try (var input = FastRemoteApplication.class.getResourceAsStream(name)) {
            assertTrue(input != null);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
