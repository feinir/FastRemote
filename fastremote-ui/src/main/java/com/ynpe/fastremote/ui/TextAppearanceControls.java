// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.settings.TextAppearance;
import com.ynpe.fastremote.i18n.Texts;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import javafx.beans.InvalidationListener;
import javafx.scene.control.Button;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextFormatter;
import javafx.util.StringConverter;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.Slider;
import javafx.scene.layout.Priority;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

/** Shared compact controls; preview owns no document, subscription or background task. */
final class TextAppearanceControls extends VBox {
    private final Spinner<Double> lineSpacing;
    private final Spinner<Double> letterSpacing;
    private final TextSpacingPreview preview = new TextSpacingPreview();
    private final Supplier<String> family;
    private final IntSupplier size;
    private final boolean terminal;

    TextAppearanceControls(TextAppearance appearance, boolean terminal,
            Supplier<String> family, IntSupplier size) {
        super(10);
        this.family = family;
        this.size = size;
        this.terminal = terminal;
        lineSpacing = spacingSpinner(TextAppearance.MIN_LINE_SPACING, TextAppearance.MAX_LINE_SPACING, appearance.lineSpacing());
        letterSpacing = spacingSpinner(TextAppearance.MIN_LETTER_SPACING, TextAppearance.MAX_LETTER_SPACING, appearance.letterSpacing());
        lineSpacing.setId("text-line-spacing");
        letterSpacing.setId("text-letter-spacing");
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(10);
        Slider lineSlider = spacingSlider(lineSpacing, "text.appearance.lineSpacing");
        Slider letterSlider = spacingSlider(letterSpacing, "text.appearance.letterSpacing");
        grid.addRow(0, label("text.appearance.lineSpacing", lineSlider), lineSlider, lineSpacing);
        grid.addRow(1, label("text.appearance.letterSpacing", letterSlider), letterSlider, letterSpacing);
        Button reset = new Button(Texts.text("text.appearance.reset"));
        reset.setId("text-appearance-reset");
        reset.setOnAction(event -> {
            TextAppearance defaults = terminal ? TextAppearance.terminalDefaults() : TextAppearance.editorDefaults();
            setSpacing(lineSpacing, defaults.lineSpacing());
            setSpacing(letterSpacing, defaults.letterSpacing());
        });
        preview.setAccessibleText(Texts.text("text.appearance.preview"));
        getChildren().addAll(grid, reset, preview);
        onChange(this::refreshPreview);
        refreshPreview();
    }

    private static Spinner<Double> spacingSpinner(double min, double max, double initial) {
        var values = new SpinnerValueFactory.DoubleSpinnerValueFactory(min, max, initial, 0.1);
        var spinner = new Spinner<Double>(values);
        values.setConverter(new StringConverter<>() {
            @Override public String toString(Double value) {
                return String.format(java.util.Locale.ROOT, "%.1f", value);
            }
            @Override public Double fromString(String text) {
                if (text == null || !text.matches("-?\\d+(?:[.,]\\d?)?")) return values.getValue();
                double parsed = Double.parseDouble(text.replace(',', '.'));
                return Math.round(Math.max(min, Math.min(max, parsed)) * 10) / 10.0;
            }
        });
        spinner.setEditable(true);
        spinner.setPrefWidth(90);
        spinner.setMinWidth(90);
        spinner.setMaxWidth(90);
        spinner.getEditor().setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().matches("-?\\d*(?:[.,]\\d?)?") ? change : null));
        spinner.focusedProperty().addListener((ignored, before, focused) -> {
            if (!focused) spinner.commitValue();
        });
        return spinner;
    }

    private static Slider spacingSlider(Spinner<Double> spinner, String labelKey) {
        var values = (SpinnerValueFactory.DoubleSpinnerValueFactory) spinner.getValueFactory();
        Slider slider = new Slider(values.getMin(), values.getMax(), values.getValue());
        slider.setId(spinner.getId() + "-slider");
        slider.setShowTickMarks(true);
        slider.setShowTickLabels(true);
        slider.setMajorTickUnit(values.getMax() > 5 ? 5 : 1);
        slider.setMinorTickCount(4);
        slider.setBlockIncrement(0.1);
        // Visible ticks stay readable; the listener rounds dragging to 0.1 px independently.
        slider.setSnapToTicks(false);
        slider.setPrefWidth(320);
        slider.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(slider, Priority.ALWAYS);
        slider.setAccessibleText(Texts.text(labelKey));
        spinner.setAccessibleText(Texts.text(labelKey));
        boolean[] synchronizing = {false};
        slider.valueProperty().addListener((ignored, before, after) -> {
            if (synchronizing[0]) return;
            synchronizing[0] = true;
            try {
                double rounded = Math.round(after.doubleValue() * 10) / 10.0;
                slider.setValue(rounded);
                setSpacing(spinner, rounded);
            } finally { synchronizing[0] = false; }
        });
        InvalidationListener syncSlider = ignored -> {
            if (synchronizing[0]) return;
            synchronizing[0] = true;
            try {
                // Keep partial numeric input intact while reflecting its effective value.
                slider.setValue(readSpacing(spinner));
            } finally { synchronizing[0] = false; }
        };
        spinner.valueProperty().addListener(syncSlider);
        spinner.getEditor().textProperty().addListener(syncSlider);
        return slider;
    }

    private static void setSpacing(Spinner<Double> spinner, double value) {
        spinner.getValueFactory().setValue(value);
        // Also replace pending text when the stored value already equals the target.
        spinner.getEditor().setText(spinner.getValueFactory().getConverter().toString(value));
    }

    private static double readSpacing(Spinner<Double> spinner) {
        return spinner.getValueFactory().getConverter().fromString(spinner.getEditor().getText());
    }

    private static Label label(String key, javafx.scene.Node control) {
        Label label = new Label(Texts.text(key));
        label.setLabelFor(control);
        return label;
    }

    TextAppearance value() {
        return new TextAppearance(readSpacing(lineSpacing), readSpacing(letterSpacing));
    }

    void onChange(Runnable action) {
        InvalidationListener listener = ignored -> action.run();
        lineSpacing.valueProperty().addListener(listener);
        letterSpacing.valueProperty().addListener(listener);
        lineSpacing.getEditor().textProperty().addListener(listener);
        letterSpacing.getEditor().textProperty().addListener(listener);
    }

    void refreshPreview() {
        if (family.get() == null) return;
        preview.configure(Font.font(family.get(), size.getAsInt()), value(), terminal);
    }
}
