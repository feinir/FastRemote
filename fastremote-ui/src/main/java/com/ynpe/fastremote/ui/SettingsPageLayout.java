// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.settings.RemoteProbeSettings;
import java.util.function.Function;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

final class SettingsPageLayout {
    private SettingsPageLayout() {}

    static final double SELECTION_CONTROL_MIN_WIDTH = 210.0;
    static final double COMMAND_AREA_WIDTH_REDUCTION = 15.0;
    static VBox page(String title, String subtitle, Node... sections) {
        Label heading = new Label(title);
        heading.getStyleClass().add("settings-title");
        Label copy = new Label(subtitle);
        copy.setWrapText(true);
        copy.getStyleClass().add("settings-subtitle");
        VBox content = new VBox(12.0, heading, copy);
        content.getChildren().addAll(sections);
        content.setPadding(new Insets(20.0, 24.0, 20.0, 24.0));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setMinSize(0.0, 0.0);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("settings-scroll");
        VBox host = new VBox(scroll);
        host.setMinSize(0.0, 0.0);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return host;
    }

    static VBox fixedPage(String title, String subtitle, Node section) {
        Label heading = new Label(title);
        heading.getStyleClass().add("settings-title");
        Label copy = new Label(subtitle);
        copy.setWrapText(true);
        copy.getStyleClass().add("settings-subtitle");
        VBox content = new VBox(12.0, heading, copy, section);
        content.setPadding(new Insets(20.0, 24.0, 20.0, 24.0));
        content.setMinHeight(0.0);
        VBox.setVgrow(section, Priority.ALWAYS);
        return content;
    }

    static VBox card(String title, String description, Node... content) {
        Label heading = new Label(title);
        heading.getStyleClass().add("settings-card-title");
        Label copy = new Label(description);
        copy.setWrapText(true);
        copy.getStyleClass().add("settings-card-copy");
        VBox card = new VBox(8.0, heading, copy, new Separator());
        card.getChildren().addAll(content);
        card.getStyleClass().add("settings-card");
        return card;
    }

    static HBox labeled(String label, Node control) {
        Label text = new Label(label);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(14.0, text, spacer, control);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("settings-row");
        return row;
    }

    static CheckBox check(String text, boolean selected) {
        CheckBox check = new CheckBox(text);
        check.setSelected(selected);
        return check;
    }

    static TextArea commandArea(String value) {
        TextArea area = new TextArea(value) {
            @Override protected double computePrefWidth(double height) {
                return Math.max(computeMinWidth(height),
                        super.computePrefWidth(height) - COMMAND_AREA_WIDTH_REDUCTION);
            }
        };
        area.setWrapText(false);
        area.setPrefColumnCount(38);
        area.setPrefRowCount(3);
        area.setMaxWidth(Double.MAX_VALUE);
        area.setTextFormatter(new javafx.scene.control.TextFormatter<String>(change ->
                validSupplementText(change.getControlNewText()) ? change : null));
        return area;
    }

    static boolean validSupplementText(String value) {
        return value.length() <= RemoteProbeSettings.MAX_SUPPLEMENT_CHARS
                && value.lines().map(String::strip).filter(line -> !line.isEmpty()).count()
                <= RemoteProbeSettings.MAX_SUPPLEMENT_COMMANDS;
    }

    static TextArea readOnlyCommandArea(String value) {
        TextArea area = commandArea(value);
        area.setEditable(false);
        area.setFocusTraversable(false);
        area.setPrefRowCount(5);
        return area;
    }

    static Label policyInfo(String text) {
        Label label = new Label(text.strip());
        DialogSpacing.configureWrappingLabel(label);
        label.getStyleClass().add("settings-policy-info");
        return label;
    }

    static ComboBox<String> combo(String first, String... rest) {
        ComboBox<String> combo = new ComboBox<>();
        combo.getItems().add(first);
        combo.getItems().addAll(rest);
        combo.setValue(first);
        combo.setMinWidth(SELECTION_CONTROL_MIN_WIDTH);
        return combo;
    }

    static <T> javafx.util.StringConverter<T> enumConverter(java.util.function.Function<T, String> label) {
        return new javafx.util.StringConverter<>() {
            @Override public String toString(T value) { return value == null ? "" : label.apply(value); }
            @Override public T fromString(String value) { throw new UnsupportedOperationException(); }
        };
    }

    static Spinner<Integer> spinner(int min, int max, int value) {
        Spinner<Integer> spinner = new Spinner<>(min, max, value);
        spinner.setEditable(true);
        SingleLineInputEnterSupport.commitSpinnerOnEnter(spinner);
        spinner.setPrefWidth(120.0);
        return spinner;
    }

    static int clampedSpinnerValue(String editorText, int fallback, int minimum, int maximum) {
        try {
            int parsed = Integer.parseInt(editorText.strip());
            return Math.max(minimum, Math.min(maximum, parsed));
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    static String rootMessage(Throwable error, String fallback) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null || cause.getMessage().isBlank()
                ? fallback : com.ynpe.fastremote.i18n.Texts.translateExternalText(cause.getMessage());
    }
}
