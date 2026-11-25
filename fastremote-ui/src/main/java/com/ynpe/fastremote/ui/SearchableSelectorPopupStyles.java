// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.ArrayList;
import javafx.scene.Parent;
import javafx.scene.Scene;

/** Applies the owning scene's effective stylesheet set to a detached searchable-selector popup. */
final class SearchableSelectorPopupStyles {
    private SearchableSelectorPopupStyles() {
    }

    static void copyFrom(Scene scene, Parent popupContent) {
        // Popup's scene root receives the Modena root palette only when shown. We measure
        // the content before show(), so establish the palette here before applyCss().
        if (!popupContent.getStyleClass().contains("root")) popupContent.getStyleClass().add("root");
        var stylesheets = new ArrayList<String>(scene.getStylesheets());
        Parent root = scene.getRoot();
        if (root != null) stylesheets.addAll(root.getStylesheets());
        var effectiveStylesheets = stylesheets.stream().distinct().toList();
        if (!popupContent.getStylesheets().equals(effectiveStylesheets)) {
            popupContent.getStylesheets().setAll(effectiveStylesheets);
        }
        // A detached Popup inherits neither the owner's platform class nor its inline font family.
        // Use the same interface policy instead of copying unrelated window-root inline styles.
        InterfaceFontPolicy.apply(popupContent);
    }
}
