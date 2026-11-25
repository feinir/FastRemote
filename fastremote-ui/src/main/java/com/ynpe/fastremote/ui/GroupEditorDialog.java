// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.catalog.GroupColor;
import com.ynpe.fastremote.domain.catalog.GroupId;
import com.ynpe.fastremote.domain.catalog.HostGroup;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.binding.Bindings;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Window;

final class GroupEditorDialog {
    static final double FORM_LABEL_WIDTH = FormLabel.ENGLISH_BASELINE_WIDTH;
    static final double FORM_FIELD_GAP = 15.0;
    static final double FORM_ROW_HEIGHT = FormLabel.TWO_LINE_HEIGHT;
    static final double FORM_ROW_GAP = 0.0;
    static final double FORM_CONTROL_HEIGHT = 34.0;
    static final double FORM_FIELD_MIN_WIDTH = 240.0;
    static final double DIALOG_WIDTH = 400.0;

    private GroupEditorDialog() {}

    static Optional<FlatGroupRequest> showFlat(Window owner, String title, String initialName, GroupColor initialColor) {
        Dialog<FlatGroupRequest> dialog = new Dialog<>();
        dialog.initOwner(owner); dialog.setTitle(title); dialog.setHeaderText(null);
        DialogSpacing.contentToButtonBar(dialog);
        ButtonType save = new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a3030bf8f16dc63c"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, save);
        TextField name = new TextField(initialName);
        ComboBox<ColorChoice> color = new ComboBox<>();
        color.getItems().setAll(new ColorChoice(GroupColor.BLUE, com.ynpe.fastremote.i18n.Texts.text("legacy.java.67035e6b7f82acdb")), new ColorChoice(GroupColor.GREEN, com.ynpe.fastremote.i18n.Texts.text("legacy.java.30c83dc25b12c32c")),
                new ColorChoice(GroupColor.ORANGE, com.ynpe.fastremote.i18n.Texts.text("legacy.java.aa1e899dd35771b0")), new ColorChoice(GroupColor.PURPLE, com.ynpe.fastremote.i18n.Texts.text("legacy.java.2ed9c6fabac2013a")),
                new ColorChoice(GroupColor.RED, com.ynpe.fastremote.i18n.Texts.text("legacy.java.789f61dbfcc7a2d2")), new ColorChoice(GroupColor.GRAY, com.ynpe.fastremote.i18n.Texts.text("legacy.java.051e67d913f4a72e")));
        color.setCellFactory(ignored -> colorCell()); color.setButtonCell(colorCell());
        color.getItems().stream().filter(value -> value.color() == initialColor).findFirst().ifPresent(color::setValue);
        AnchorPane form = alignedForm(2);
        addRow(form, 0, com.ynpe.fastremote.i18n.Texts.text("legacy.java.d7e266bdc8064193"), name);
        addRow(form, 1, com.ynpe.fastremote.i18n.Texts.text("legacy.java.52c15454e2682d1e"), color);
        dialog.getDialogPane().setContent(DialogSpacing.anchoredContent(form));
        dialog.getDialogPane().setPrefWidth(DIALOG_WIDTH);
        Button button = (Button) dialog.getDialogPane().lookupButton(save);
        button.addEventFilter(ActionEvent.ACTION, event -> { if (name.getText().isBlank()) { DialogSpacing.focusAtEnd(name); event.consume(); } });
        dialog.setResultConverter(value -> value == save ? new FlatGroupRequest(name.getText().strip(), color.getValue().color()) : null);
        DialogSpacing.focusFirstInputOnShown(dialog);
        return dialog.showAndWait();
    }

    record FlatGroupRequest(String name, GroupColor color) {}

    static Optional<GroupRequest> show(Window owner, List<HostGroup> groups, Optional<GroupId> suggestedParent) {
        return show(owner, groups, suggestedParent, Optional.empty(), Mode.CREATE);
    }

    static Optional<GroupRequest> edit(Window owner, List<HostGroup> groups, HostGroup group) {
        return show(owner, groups, group.parentId(), Optional.of(group), Mode.EDIT);
    }

    static Optional<GroupRequest> copy(Window owner, List<HostGroup> groups, HostGroup group) {
        return show(owner, groups, group.parentId(), Optional.of(group), Mode.COPY);
    }

    private static Optional<GroupRequest> show(
            Window owner, List<HostGroup> groups, Optional<GroupId> suggestedParent,
            Optional<HostGroup> source, Mode mode) {
        HostGroup initial = source.orElse(null);
        Dialog<GroupRequest> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(switch (mode) {
            case CREATE -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.3d49d9fcb6d1bbc5");
            case EDIT -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.d00b2a96c3b35930");
            case COPY -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.124ae8893e84a75e");
        });
        dialog.setHeaderText(mode == Mode.EDIT ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.975d191db671b01a") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.a747b16039d60fed"));
        DialogSpacing.contentToButtonBar(dialog);
        ButtonType create = new ButtonType(mode == Mode.EDIT ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.991bb7cfe5a81550") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.46c66ba271615685"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, create);
        dialog.setResultConverter(DialogResultPolicy.explicitOnly());

        TextField name = new TextField();
        if (initial != null) name.setText(initial.name() + (mode == Mode.COPY ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.6ded4e175cd7f0e1") : ""));
        name.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5a767cfcfedb5f4d"));
        RadioButton primary = new RadioButton(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e110b1aa3ffba31e"));
        RadioButton secondary = new RadioButton(com.ynpe.fastremote.i18n.Texts.text("legacy.java.bb37347be9f72f65"));
        ToggleGroup level = new ToggleGroup();
        primary.setToggleGroup(level);
        secondary.setToggleGroup(level);
        primary.setSelected(true);

        ComboBox<ParentChoice> parent = new ComboBox<>();
        parent.setMaxWidth(Double.MAX_VALUE);
        groups.stream().filter(group -> group.parentId().isEmpty()
                        && (initial == null || !group.id().equals(initial.id())))
                .sorted(Comparator.comparingInt(HostGroup::sortOrder)
                        .thenComparing(HostGroup::name, String.CASE_INSENSITIVE_ORDER))
                .map(group -> new ParentChoice(group.id(), group.name()))
                .forEach(parent.getItems()::add);
        suggestedParent.flatMap(id -> parent.getItems().stream().filter(choice -> choice.id().equals(id)).findFirst())
                .ifPresent(choice -> {
                    secondary.setSelected(true);
                    parent.setValue(choice);
                });
        if (parent.getValue() == null && !parent.getItems().isEmpty()) parent.getSelectionModel().selectFirst();
        parent.disableProperty().bind(secondary.selectedProperty().not());

        ComboBox<ColorChoice> color = new ComboBox<>();
        color.setMaxWidth(Double.MAX_VALUE);
        color.getItems().setAll(
                new ColorChoice(GroupColor.BLUE, com.ynpe.fastremote.i18n.Texts.text("legacy.java.67035e6b7f82acdb")), new ColorChoice(GroupColor.GREEN, com.ynpe.fastremote.i18n.Texts.text("legacy.java.30c83dc25b12c32c")),
                new ColorChoice(GroupColor.ORANGE, com.ynpe.fastremote.i18n.Texts.text("legacy.java.aa1e899dd35771b0")), new ColorChoice(GroupColor.PURPLE, com.ynpe.fastremote.i18n.Texts.text("legacy.java.2ed9c6fabac2013a")),
                new ColorChoice(GroupColor.RED, com.ynpe.fastremote.i18n.Texts.text("legacy.java.789f61dbfcc7a2d2")), new ColorChoice(GroupColor.GRAY, com.ynpe.fastremote.i18n.Texts.text("legacy.java.051e67d913f4a72e")));
        color.getSelectionModel().selectFirst();
        color.setCellFactory(ignored -> colorCell());
        color.setButtonCell(colorCell());
        if (initial != null) color.getItems().stream().filter(choice -> choice.color() == initial.color())
                .findFirst().ifPresent(color::setValue);
        Label validation = new Label();
        validation.getStyleClass().add("host-editor-validation");
        validation.setWrapText(true);
        validation.setVisible(false);
        validation.setManaged(false);

        AnchorPane form = alignedForm(4);
        List<String> labels = formLabels();
        addRow(form, 0, labels.get(0), name);
        HBox levelChoice = new HBox(18, primary, secondary);
        levelChoice.setAlignment(Pos.CENTER_LEFT);
        addRow(form, 1, labels.get(1), levelChoice);
        addRow(form, 2, labels.get(2), parent);
        addRow(form, 3, labels.get(3), color);
        GridPane content = new GridPane();
        content.setVgap(10.0);
        content.setMaxWidth(Double.MAX_VALUE);
        form.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(form, Priority.ALWAYS);
        content.add(form, 0, 0);
        content.add(validation, 0, 1);
        dialog.getDialogPane().setContent(DialogSpacing.anchoredContent(content));
        dialog.getDialogPane().setPrefWidth(DIALOG_WIDTH);

        Button createButton = (Button) dialog.getDialogPane().lookupButton(create);
        createButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> name.getText().isBlank(), name.textProperty()));
        createButton.addEventFilter(ActionEvent.ACTION, event -> {
            if (secondary.isSelected() && parent.getValue() == null) {
                parent.requestFocus();
                validation.setText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.0fdeefb10bf659e3"));
                validation.setVisible(true);
                validation.setManaged(true);
                event.consume();
                return;
            }
            Optional<GroupId> parentId = secondary.isSelected()
                    ? Optional.of(parent.getValue().id()) : Optional.empty();
            dialog.setResult(new GroupRequest(name.getText().strip(), parentId, color.getValue().color()));
            event.consume();
            dialog.close();
        });
        DialogSpacing.focusFirstInputOnShown(dialog);
        return dialog.showAndWait();
    }

    static double formFieldLeft() {
        return FORM_LABEL_WIDTH + FORM_FIELD_GAP;
    }

    static List<String> formLabels() {
        return List.of(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d7e266bdc8064193"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.8b1d9dbf5d4f05b6"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.e110b1aa3ffba31e"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.52c15454e2682d1e"));
    }

    static double formHeight(int rowCount) {
        return rowCount <= 0 ? 0.0
                : rowCount * FORM_ROW_HEIGHT + (rowCount - 1) * FORM_ROW_GAP;
    }

    private static AnchorPane alignedForm(int rowCount) {
        AnchorPane form = new AnchorPane();
        form.getStyleClass().add("group-editor-fields-shell");
        double height = formHeight(rowCount);
        form.setMinHeight(height);
        form.setPrefHeight(height);
        form.setMaxHeight(height);
        return form;
    }

    private static void addRow(AnchorPane form, int row, String text, Region field) {
        FormLabel label = new FormLabel(text, FORM_LABEL_WIDTH, FORM_ROW_HEIGHT);
        field.setMinWidth(FORM_FIELD_MIN_WIDTH);
        field.setMinHeight(FORM_CONTROL_HEIGHT);
        field.setPrefHeight(FORM_CONTROL_HEIGHT);
        field.setMaxHeight(FORM_CONTROL_HEIGHT);
        if (field instanceof javafx.scene.control.Control control) control.setMaxWidth(Double.MAX_VALUE);
        double top = row * (FORM_ROW_HEIGHT + FORM_ROW_GAP);
        AnchorPane.setLeftAnchor(label, 0.0);
        AnchorPane.setTopAnchor(label, top);
        AnchorPane.setLeftAnchor(field, formFieldLeft());
        AnchorPane.setRightAnchor(field, 0.0);
        AnchorPane.setTopAnchor(field, top + (FORM_ROW_HEIGHT - FORM_CONTROL_HEIGHT) / 2.0);
        form.getChildren().addAll(label, field);
    }

    record GroupRequest(String name, Optional<GroupId> parentId, GroupColor color) {
        GroupRequest {
            name = Objects.requireNonNull(name, "name").strip();
            parentId = Objects.requireNonNull(parentId, "parentId");
            Objects.requireNonNull(color, "color");
        }
    }

    private record ParentChoice(GroupId id, String label) {
        @Override public String toString() { return label; }
    }

    private record ColorChoice(GroupColor color, String label) {
        @Override public String toString() { return label; }
    }

    private static ListCell<ColorChoice> colorCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(ColorChoice choice, boolean empty) {
                super.updateItem(choice, empty);
                setText(empty || choice == null ? null : choice.label());
                setGraphic(empty || choice == null ? null : new Circle(6, colorValue(choice.color())));
                setGraphicTextGap(9);
            }
        };
    }

    private static Color colorValue(GroupColor color) {
        return Color.web(switch (color) {
            case BLUE -> "#5b8def";
            case GREEN -> "#43a66b";
            case ORANGE -> "#e5943b";
            case PURPLE -> "#8b6bd6";
            case RED -> "#d65b63";
            case GRAY -> "#7b8794";
        });
    }

    private enum Mode { CREATE, EDIT, COPY }
}
