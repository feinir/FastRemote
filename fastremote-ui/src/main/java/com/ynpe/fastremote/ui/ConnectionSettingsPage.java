// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static com.ynpe.fastremote.ui.SettingsPageLayout.page;
import static com.ynpe.fastremote.ui.SettingsPageLayout.card;
import static com.ynpe.fastremote.ui.SettingsPageLayout.labeled;
import static com.ynpe.fastremote.ui.SettingsPageLayout.readOnlyCommandArea;
import static com.ynpe.fastremote.ui.SettingsPageLayout.policyInfo;
import static com.ynpe.fastremote.ui.SettingsPageLayout.spinner;
import static com.ynpe.fastremote.ui.TerminalSettingsPage.TerminalControls;
import com.ynpe.fastremote.domain.settings.ConnectionSettings;
import com.ynpe.fastremote.domain.settings.RemoteProbeCommandCatalog;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Spinner;

final class ConnectionSettingsPage {
    private ConnectionSettingsPage() {}

    static Node connectionPage(ConnectionControls controls, TerminalControls terminal) {
        return page(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a5574109f0208e89"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.5dca4518883358e3"),
                card(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b506b65eb77c855b"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.3ea5e64122faff7e"),
                        labeled(com.ynpe.fastremote.i18n.Texts.text("legacy.java.84b0b71bc5c62ed9"), controls.connectTimeout()),
                        labeled(com.ynpe.fastremote.i18n.Texts.text("legacy.java.136365b3593b68c1"), controls.authenticationTimeout()),
                        labeled(com.ynpe.fastremote.i18n.Texts.text("legacy.java.00e0ce810e525d53"), controls.keepAliveInterval())),
                card(com.ynpe.fastremote.i18n.Texts.text("legacy.java.041b0b61a57a77ef"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.2802e80ac4090afc"),
                        controls.disconnectIdleTerminal(),
                        labeled(com.ynpe.fastremote.i18n.Texts.text("legacy.java.78027723daf25d36"), controls.idleTimeout())),
                card(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2d416962f877e902"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.85f8be2804e8efd4"),
                        terminal.probeServerInformation(),
                        policyInfo(com.ynpe.fastremote.i18n.Texts.text("legacy.java.710ddd3a2a9bf994"))),
                card(com.ynpe.fastremote.i18n.Texts.text("legacy.java.827e1030a816314a"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.1be757c35c815285"),
                        labeled("Linux", readOnlyCommandArea(com.ynpe.fastremote.i18n.Texts.translateExternalText(
                                RemoteProbeCommandCatalog.displayText("Linux")))),
                        labeled("macOS", readOnlyCommandArea(com.ynpe.fastremote.i18n.Texts.translateExternalText(
                                RemoteProbeCommandCatalog.displayText("macOS")))),
                        labeled("FreeBSD", readOnlyCommandArea(com.ynpe.fastremote.i18n.Texts.translateExternalText(
                                RemoteProbeCommandCatalog.displayText("FreeBSD")))),
                        labeled("Windows", readOnlyCommandArea(com.ynpe.fastremote.i18n.Texts.translateExternalText(
                                RemoteProbeCommandCatalog.displayText("Windows"))))),
                card(com.ynpe.fastremote.i18n.Texts.text("legacy.java.120d894d82aaba4b"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.e150470a6b66ca42"),
                        labeled("Linux", terminal.linuxSupplement()),
                        labeled("macOS", terminal.macosSupplement()),
                        labeled("FreeBSD", terminal.freebsdSupplement()),
                        labeled("Windows", terminal.windowsSupplement())));
    }

    record ConnectionControls(
            Spinner<Integer> connectTimeout,
            Spinner<Integer> authenticationTimeout,
            Spinner<Integer> keepAliveInterval,
            CheckBox disconnectIdleTerminal,
            Spinner<Integer> idleTimeout) {
        static ConnectionControls from(ConnectionSettings settings) {
            CheckBox disconnect = new CheckBox(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d9327d18aac31ed8"));
            disconnect.setSelected(settings.disconnectIdleTerminal());
            ConnectionControls controls = new ConnectionControls(
                    spinner(ConnectionSettings.MIN_CONNECT_TIMEOUT_SECONDS,
                            ConnectionSettings.MAX_CONNECT_TIMEOUT_SECONDS, settings.connectTimeoutSeconds()),
                    spinner(ConnectionSettings.MIN_AUTHENTICATION_TIMEOUT_SECONDS,
                            ConnectionSettings.MAX_AUTHENTICATION_TIMEOUT_SECONDS,
                            settings.authenticationTimeoutSeconds()),
                    spinner(ConnectionSettings.MIN_KEEP_ALIVE_INTERVAL_SECONDS,
                            ConnectionSettings.MAX_KEEP_ALIVE_INTERVAL_SECONDS,
                            settings.keepAliveIntervalSeconds()),
                    disconnect,
                    spinner(ConnectionSettings.MIN_TERMINAL_IDLE_TIMEOUT_MINUTES,
                            ConnectionSettings.MAX_TERMINAL_IDLE_TIMEOUT_MINUTES,
                            settings.terminalIdleTimeoutMinutes()));
            controls.updateIdleControlState();
            return controls;
        }

        void updateIdleControlState() {
            idleTimeout.setDisable(!disconnectIdleTerminal.isSelected());
        }

        ConnectionSettings settings() {
            return new ConnectionSettings(
                    connectTimeout.getValue(), authenticationTimeout.getValue(), keepAliveInterval.getValue(),
                    disconnectIdleTerminal.isSelected(), idleTimeout.getValue());
        }
    }
}
