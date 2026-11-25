// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.snippet.SnippetVariable;
import com.ynpe.fastremote.domain.snippet.SnippetVariableGroup;
import com.ynpe.fastremote.domain.snippet.SnippetVariableGroupId;
import com.ynpe.fastremote.domain.snippet.SnippetVariableId;
import com.ynpe.fastremote.domain.snippet.SnippetVariableType;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToIntFunction;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.stage.Window;

/** Owns the variable editor used by the docked variable library. */
final class VariableDialogs {
    static final double FORM_LABEL_WIDTH = FormLabel.ENGLISH_BASELINE_WIDTH;
    static final double FORM_CONTROL_HEIGHT = 34.0;
    static final double FORM_ROW_HEIGHT = 44.0;
    static final double VALUE_CONTROL_HEIGHT = 130.0;
    static final double VALUE_ROW_HEIGHT = 140.0;
    static final double DIALOG_WIDTH = 660.0;
    static final double DIALOG_HEIGHT = 505.0;

    private VariableDialogs() { }

    static Optional<SnippetVariable> edit(
            Window owner,
            SnippetVariable existing,
            Optional<SnippetVariableGroupId> initialGroup,
            SnippetSnapshot snapshot,
            ToIntFunction<Optional<SnippetVariableGroupId>> nextOrder) {
        Dialog<SnippetVariable> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(existing == null ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.300a3219c4cdcda8") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.53307ee34518a327"));
        dialog.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.3f221baa70ca6dc1"));
        DialogSpacing.contentToButtonBar(dialog);
        ButtonType save = new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a3030bf8f16dc63c"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, save);

        TextField name = new TextField(existing == null ? "" : existing.name());
        name.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.8dc77eb54129c817"));
        TextField variableName = new TextField(existing == null ? "" : existing.variableName());
        variableName.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.34afaebdb3871ed3"));
        ComboBox<GroupChoice> group = new ComboBox<>();
        group.getItems().add(GroupChoice.root());
        snapshot.variableGroups().stream()
                .sorted(Comparator.comparing(SnippetVariableGroup::name, String.CASE_INSENSITIVE_ORDER))
                .map(GroupChoice::group).forEach(group.getItems()::add);
        Optional<SnippetVariableGroupId> selectedGroup = existing == null ? initialGroup : existing.groupId();
        group.getItems().stream().filter(choice -> choice.id().equals(selectedGroup)).findFirst()
                .ifPresentOrElse(group::setValue, () -> group.getSelectionModel().selectFirst());
        ComboBox<SnippetVariableType> type = new ComboBox<>();
        type.getItems().setAll(SnippetVariableType.SINGLE, SnippetVariableType.ARRAY);
        type.setValue(existing == null ? SnippetVariableType.SINGLE : existing.type());
        type.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(SnippetVariableType value) {
                return value == SnippetVariableType.ARRAY ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.685bb17fdb2bcf71") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.f26eb88fd9638843");
            }
            @Override public SnippetVariableType fromString(String value) { return null; }
        });
        TextField delimiter = new TextField(existing == null ? "" : existing.delimiter());
        delimiter.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2c9e85e98e78d129"));
        TextArea value = new TextArea(existing == null ? "" : existing.value());
        value.setWrapText(false);
        TextField description = new TextField(existing == null ? "" : existing.description());
        description.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.8d8a1fb43481cecc"));
        Label validation = ConnectionFeedback.reservedLabel();

        Runnable updateType = () -> {
            boolean array = type.getValue() == SnippetVariableType.ARRAY;
            delimiter.setDisable(!array);
            if (array && delimiter.getText().isEmpty()) delimiter.setText(",");
            if (!array) delimiter.clear();
        };
        type.valueProperty().addListener((ignored, previous, selected) -> updateType.run());
        updateType.run();

        GridPane form = new GridPane();
        form.setHgap(10.0);
        form.setVgap(0.0);
        java.util.List<String> labels = formLabels();
        addWideRow(form, 0, labels.get(0), name);
        addWideRow(form, 1, labels.get(1), variableName);
        addPairedRow(form, 2, labels.get(2), group, labels.get(3), type);
        addWideRow(form, 3, labels.get(4), delimiter);
        addWideRow(form, 4, labels.get(5), value);
        addWideRow(form, 5, labels.get(6), description);
        form.add(validation, 0, 6, 4, 1);
        for (int row = 0; row < 7; row++) {
            double height = row == 4 ? VALUE_ROW_HEIGHT : FORM_ROW_HEIGHT;
            form.getRowConstraints().add(fixedRow(height));
        }
        for (Node control : java.util.List.of(name, variableName, group, type, delimiter, value, description)) {
            GridPane.setHgrow(control, Priority.ALWAYS);
        }
        form.setMinWidth(620.0);
        form.setPrefWidth(620.0);
        dialog.getDialogPane().setContent(DialogSpacing.anchoredContent(form));
        DialogSpacing.configureFixedWindowSize(dialog, DIALOG_WIDTH, DIALOG_HEIGHT);

        java.util.function.Supplier<SnippetVariable> result = () -> variable(
                existing, group.getValue().id(), name.getText(), variableName.getText(), type.getValue(),
                delimiter.getText(), value.getText(), description.getText(), nextOrder);
        dialog.getDialogPane().lookupButton(save).addEventFilter(ActionEvent.ACTION, event -> {
            try {
                FormValidation.require(name, !name.getText().strip().isEmpty() && name.getText().strip().length() <= 255, com.ynpe.fastremote.i18n.Texts.text("legacy.external.0903550d23b4918e"));
                FormValidation.require(variableName, variableName.getText().strip().matches("[A-Za-z_][A-Za-z0-9_]{0,63}"), com.ynpe.fastremote.i18n.Texts.text("legacy.external.52196c11ba2a6fe5"));
                FormValidation.require(delimiter, type.getValue() != SnippetVariableType.ARRAY || !delimiter.getText().isEmpty(), com.ynpe.fastremote.i18n.Texts.text("legacy.external.76fc04b542f0e91c"));
                FormValidation.require(delimiter, delimiter.getText().length() <= 64 && delimiter.getText().indexOf('\r') < 0
                        && delimiter.getText().indexOf('\n') < 0 && delimiter.getText().indexOf('\0') < 0,
                        com.ynpe.fastremote.i18n.Texts.text("legacy.external.1b54a6b7cd2a2d32"));
                FormValidation.require(value, value.getText().length() <= 64 * 1024, com.ynpe.fastremote.i18n.Texts.text("legacy.external.79042e205c4babf4"));
                FormValidation.require(description, description.getText().strip().length() <= 1024, com.ynpe.fastremote.i18n.Texts.text("legacy.external.7deb8e76261f4e54"));
                SnippetVariable candidate = result.get();
                boolean duplicate = snapshot.variables().stream()
                        .filter(item -> existing == null || !item.id().equals(existing.id()))
                        .anyMatch(item -> item.variableName().equals(candidate.variableName()));
                FormValidation.require(variableName, !duplicate, com.ynpe.fastremote.i18n.Texts.text("legacy.java.63b1845cbfd5f0c3") + candidate.variableName());
                ConnectionFeedback.clear(validation);
            } catch (IllegalArgumentException invalid) {
                ConnectionFeedback.show(validation,
                        com.ynpe.fastremote.i18n.Texts.translateExternalText(invalid.getMessage()),
                        ConnectionFeedback.Tone.ERROR);
                event.consume();
            }
        });
        dialog.setResultConverter(button -> button == save ? result.get() : null);
        DialogSpacing.focusFirstInputOnShown(dialog);
        return dialog.showAndWait();
    }

    private static FormLabel formLabel(String text) {
        return new FormLabel(text, FORM_LABEL_WIDTH, FORM_ROW_HEIGHT);
    }

    private static void addWideRow(GridPane form, int row, String label, Node control) {
        configureControl(control, row == 4 ? VALUE_CONTROL_HEIGHT : FORM_CONTROL_HEIGHT);
        form.add(formLabel(label), 0, row);
        form.add(control, 1, row, 3, 1);
    }

    private static void addPairedRow(
            GridPane form, int row, String firstLabel, Node firstControl,
            String secondLabel, Node secondControl) {
        configureControl(firstControl, FORM_CONTROL_HEIGHT);
        configureControl(secondControl, FORM_CONTROL_HEIGHT);
        form.addRow(row, formLabel(firstLabel), firstControl, formLabel(secondLabel), secondControl);
    }

    private static void configureControl(Node node, double height) {
        if (node instanceof javafx.scene.layout.Region region) {
            region.setMinHeight(height);
            region.setPrefHeight(height);
            region.setMaxHeight(height);
            region.setMaxWidth(Double.MAX_VALUE);
        }
    }

    private static RowConstraints fixedRow(double height) {
        RowConstraints row = new RowConstraints(height, height, height);
        row.setValignment(javafx.geometry.VPos.CENTER);
        return row;
    }

    static java.util.List<String> formLabels() {
        return java.util.List.of(
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.d44e9b3d3b31d37b"),
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.9d9d763731b4b95f"),
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.399ee69347726cee"),
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.ba40014ff496f64e"),
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.0415f31a96cd090c"),
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.cda1d55c5231b853"),
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.4262c45dc797bb0c"));
    }

    private static SnippetVariable variable(
            SnippetVariable existing,
            Optional<SnippetVariableGroupId> group,
            String name,
            String variableName,
            SnippetVariableType type,
            String delimiter,
            String value,
            String description,
            ToIntFunction<Optional<SnippetVariableGroupId>> nextOrder) {
        Instant now = Instant.now();
        return new SnippetVariable(
                existing == null ? SnippetVariableId.create() : existing.id(), group,
                name, variableName, type, delimiter, value, description,
                existing == null ? nextOrder.applyAsInt(group) : existing.sortOrder(),
                existing == null ? now : existing.createdAt(), now);
    }

    private record GroupChoice(Optional<SnippetVariableGroupId> id, String label) {
        private GroupChoice {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(label, "label");
        }
        static GroupChoice root() { return new GroupChoice(Optional.empty(), com.ynpe.fastremote.i18n.Texts.text("legacy.java.e86ea3f280d920a2")); }
        static GroupChoice group(SnippetVariableGroup group) {
            return new GroupChoice(Optional.of(group.id()), group.name());
        }
        @Override public String toString() { return label; }
    }
}
