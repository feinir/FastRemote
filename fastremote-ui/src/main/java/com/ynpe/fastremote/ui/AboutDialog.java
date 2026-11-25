// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.awt.Desktop;
import java.net.URI;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** Presents product identity and the complete user-visible legal disclosure entry point. */
final class AboutDialog {
    private static final double INITIAL_WIDTH = 760.0;
    private static final double INITIAL_HEIGHT = 570.0;
    private static final double MINIMUM_WIDTH = 620.0;
    private static final double MINIMUM_HEIGHT = 460.0;

    private AboutDialog() {}

    static void show(Window owner) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.9dd854eef713258b"));
        dialog.setResizable(true);
        DialogSpacing.prepare(dialog);
        DialogSpacing.configureResizableWindowGeometry(dialog,
                INITIAL_WIDTH, INITIAL_HEIGHT, MINIMUM_WIDTH, MINIMUM_HEIGHT);

        String edition = LegalResources.edition();
        boolean commercial = edition.equals("commercial");
        Label product = new Label("FastRemote");
        product.getStyleClass().add("about-product-name");
        Label version = new Label(com.ynpe.fastremote.i18n.Texts.text("about.version")
                + " " + LegalResources.productVersion());
        version.getStyleClass().add("about-version");
        Label description = new Label(com.ynpe.fastremote.i18n.Texts.text("about.description"));
        description.setWrapText(true);
        Label copyright = new Label("Copyright © " + LegalResources.copyrightYear() + " "
                + LegalResources.legalEntity());
        copyright.setWrapText(true);
        Hyperlink website = new Hyperlink("https://www.ynpe.com");
        website.setOnAction(event -> openExternal("https://www.ynpe.com"));
        Hyperlink email = new Hyperlink("feinir@gmail.com");
        email.setOnAction(event -> openExternal("mailto:feinir@gmail.com"));
        HBox websiteRow = new HBox(8.0, new Label(com.ynpe.fastremote.i18n.Texts.text("about.website")), website);
        HBox emailRow = new HBox(8.0, new Label(com.ynpe.fastremote.i18n.Texts.text("about.developerEmail")), email);
        VBox overview = new VBox(10.0, product, version, description, copyright,
                wrapped(commercial ? com.ynpe.fastremote.i18n.Texts.text("about.proprietarySummary")
                        : "Mozilla Public License 2.0"), websiteRow, emailRow);
        overview.setPadding(new Insets(14.0));

        VBox tabs = DialogTabSwitcher.create(java.util.List.of(
                fixedTab(com.ynpe.fastremote.i18n.Texts.text("about.overview"), overview),
                textTab(com.ynpe.fastremote.i18n.Texts.text("about.license"),
                        LegalResources.text("LICENSE.txt") + (commercial ? "\n\n" + LegalResources.text("EULA.md") : "")),
                textTab(com.ynpe.fastremote.i18n.Texts.text("about.thirdParty"),
                        LegalResources.text("THIRD_PARTY_NOTICES.txt") + "\n\n"
                                + LegalResources.text("SOURCE_OFFER.txt") + "\n\n"
                                + LegalResources.text("TRADEMARKS.md")),
                textTab(com.ynpe.fastremote.i18n.Texts.text("about.privacyAi"), LegalResources.text("PRIVACY.md"))));
        tabs.setPrefSize(INITIAL_WIDTH - 20.0, INITIAL_HEIGHT - 80.0);
        tabs.setMinSize(0.0, 0.0);

        dialog.getDialogPane().setContent(tabs);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
        dialog.setResultConverter(DialogResultPolicy.explicitOnly());
        dialog.showAndWait();
    }

    private static Label wrapped(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private static DialogTabSwitcher.Page fixedTab(String title, javafx.scene.Node content) {
        return new DialogTabSwitcher.Page(title, content);
    }

    private static DialogTabSwitcher.Page textTab(String title, String text) {
        TextArea area = new TextArea(text);
        area.setEditable(false);
        area.setWrapText(true);
        area.setFocusTraversable(true);
        area.setPrefColumnCount(84);
        area.setPrefRowCount(28);
        area.setMinSize(0.0, 0.0);
        area.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        area.getStyleClass().add("legal-document-view");
        return fixedTab(title, area);
    }

    private static void openExternal(String value) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(value));
            }
        } catch (Exception ignored) { }
    }
}
