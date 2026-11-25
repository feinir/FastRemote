// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.snippet.Snippet;
import com.ynpe.fastremote.domain.snippet.SnippetGroup;
import com.ynpe.fastremote.domain.snippet.SnippetGroupId;
import com.ynpe.fastremote.domain.snippet.SnippetId;
import com.ynpe.fastremote.domain.snippet.SnippetInput;
import com.ynpe.fastremote.domain.snippet.SnippetTemplateEngine;
import com.ynpe.fastremote.domain.snippet.SnippetVariable;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToIntFunction;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.stage.Window;

/** Owns code-snippet editing, template input and local-variable dialogs. */
final class SnippetDialogs {
    static final double FORM_LABEL_WIDTH = FormLabel.ENGLISH_BASELINE_WIDTH;
    private static final double FORM_CONTROL_HEIGHT = 34.0;
    static final double FORM_ROW_HEIGHT = 44.0;
    private static final double FORM_FIELD_MIN_WIDTH = 300.0;
    static final double COMMAND_CONTENT_HEIGHT = 240.0;
    static final double DIALOG_WIDTH = 720.0;
    static final double DIALOG_HEIGHT = 760.0;
    static final List<String> TEMPLATE_HELP_LINES = List.of(
            com.ynpe.fastremote.i18n.Texts.text("legacy.java.7dcff8e8bb250f48")
                    + "ping {{input,IP,default=1.1.1.1,variable=server_list,required=true}}",
            com.ynpe.fastremote.i18n.Texts.text("legacy.java.c7e0b85b732d7957"),
            com.ynpe.fastremote.i18n.Texts.text("legacy.java.30119129d8ce0096"),
            com.ynpe.fastremote.i18n.Texts.text("legacy.java.b91cfdda4c3605d8"),
            com.ynpe.fastremote.i18n.Texts.text("legacy.java.828b4dd6bf246491")
                    + "{{random,hex,10}}、{{random,lower,10}}、{{random,upper,10}}、{{random,IP}}、"
                    + "{{random,float,3.4}}、{{random,int,7}}、{{random,int,3-7}}、"
                    + "{{random,uuid}}、{{random,uuid,non}}");
    static final String ENCODING_HELP = com.ynpe.fastremote.i18n.Texts.text("legacy.java.883204678c8feb18")
            + "base64、hex、url、md5、sha1、sha256、sha512、upper、lower。"
            + com.ynpe.fastremote.i18n.Texts.text("legacy.java.4408503056bf6ae8");

    private SnippetDialogs() { }

    static Optional<Snippet> edit(
            Window owner,
            Snippet existing,
            Optional<SnippetGroupId> initialGroup,
            SnippetSnapshot snapshot,
            SnippetTemplateEngine templateEngine,
            ToIntFunction<Optional<SnippetGroupId>> nextOrder) {
        Dialog<Snippet> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(existing == null ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.cbd43842cef0016c") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.9ea7f032d0f39abe"));
        dialog.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("snippetEditor.header"));
        DialogSpacing.contentToButtonBar(dialog);
        ButtonType save = new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a3030bf8f16dc63c"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, save);

        TextField name = new TextField(existing == null ? "" : existing.name());
        name.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.838910be8554fb28"));
        TextField description = new TextField(existing == null ? "" : existing.description());
        description.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d2714d319e7fedac"));
        ComboBox<GroupChoice> group = new ComboBox<>();
        group.getItems().add(GroupChoice.root());
        snapshot.groups().stream().sorted(Comparator.comparing(SnippetGroup::name, String.CASE_INSENSITIVE_ORDER))
                .map(GroupChoice::group).forEach(group.getItems()::add);
        group.getItems().stream().filter(choice -> choice.id().equals(initialGroup)).findFirst()
                .ifPresentOrElse(group::setValue, () -> group.getSelectionModel().selectFirst());
        TextArea content = new TextArea(existing == null ? "" : existing.content());
        content.setPromptText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.ad7114fec88e289d"));
        content.setWrapText(false);
        content.setMinHeight(COMMAND_CONTENT_HEIGHT);
        content.setPrefHeight(COMMAND_CONTENT_HEIGHT);
        content.setMaxHeight(COMMAND_CONTENT_HEIGHT);

        GridPane help = templateHelp();
        Label validation = ConnectionFeedback.reservedLabel();
        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(0);
        addFormRow(form, 0, com.ynpe.fastremote.i18n.Texts.text("legacy.java.d44e9b3d3b31d37b"), name);
        addFormRow(form, 1, com.ynpe.fastremote.i18n.Texts.text("legacy.java.399ee69347726cee"), group);
        addFormRow(form, 2, com.ynpe.fastremote.i18n.Texts.text("legacy.java.4262c45dc797bb0c"), description);
        for (int row = 0; row < 3; row++) form.getRowConstraints().add(fixedFormRow());
        GridPane body = new GridPane();
        body.setVgap(10.0);
        body.add(form, 0, 0);
        body.add(content, 0, 1);
        body.add(help, 0, 2);
        body.add(validation, 0, 3);
        body.setPrefWidth(700);
        GridPane.setHgrow(form, javafx.scene.layout.Priority.ALWAYS);
        GridPane.setHgrow(content, javafx.scene.layout.Priority.ALWAYS);
        GridPane.setHgrow(help, javafx.scene.layout.Priority.ALWAYS);
        GridPane.setVgrow(content, javafx.scene.layout.Priority.ALWAYS);
        dialog.getDialogPane().setContent(DialogSpacing.anchoredContent(body));
        DialogSpacing.configureFixedWindowSize(dialog, DIALOG_WIDTH, DIALOG_HEIGHT);

        Node saveButton = dialog.getDialogPane().lookupButton(save);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                FormValidation.require(name, !name.getText().strip().isEmpty() && name.getText().strip().length() <= 255,
                        com.ynpe.fastremote.i18n.Texts.text("legacy.external.54291f38c5a7e14d"));
                FormValidation.require(description, description.getText().strip().length() <= 1024, com.ynpe.fastremote.i18n.Texts.text("legacy.external.3181b6c4c4ed8b33"));
                FormValidation.require(content, !content.getText().isEmpty(), com.ynpe.fastremote.i18n.Texts.text("legacy.external.e38721c2c4959496"));
                FormValidation.require(content, content.getText().length() <= Snippet.MAX_CONTENT_LENGTH, com.ynpe.fastremote.i18n.Texts.text("legacy.external.a85d7de7df8c28ce"));
                FormValidation.value(content, () -> templateEngine.analyze(content.getText()));
                snippet(existing, group.getValue().id(), name.getText(), description.getText(),
                        content.getText(), nextOrder);
                ConnectionFeedback.clear(validation);
            } catch (IllegalArgumentException failure) {
                ConnectionFeedback.show(validation,
                        com.ynpe.fastremote.i18n.Texts.translateExternalText(failure.getMessage()),
                        ConnectionFeedback.Tone.ERROR);
                event.consume();
            }
        });
        dialog.setResultConverter(button -> button == save
                ? snippet(existing, group.getValue().id(), name.getText(), description.getText(),
                        content.getText(), nextOrder)
                : null);
        DialogSpacing.focusFirstInputOnShown(dialog);
        return dialog.showAndWait();
    }

    private static Snippet snippet(
            Snippet existing,
            Optional<SnippetGroupId> group,
            String name,
            String description,
            String content,
            ToIntFunction<Optional<SnippetGroupId>> nextOrder) {
        Instant now = Instant.now();
        return new Snippet(existing == null ? SnippetId.create() : existing.id(), group, name, description, content,
                existing == null ? nextOrder.applyAsInt(group) : existing.sortOrder(),
                existing == null ? now : existing.createdAt(), now);
    }

    private static void addFormRow(GridPane form, int row, String text, Region field) {
        FormLabel label = new FormLabel(text, FORM_LABEL_WIDTH, FORM_ROW_HEIGHT);
        field.setMinWidth(FORM_FIELD_MIN_WIDTH);
        field.setMinHeight(FORM_CONTROL_HEIGHT);
        field.setPrefHeight(FORM_CONTROL_HEIGHT);
        if (field instanceof javafx.scene.control.Control control) control.setMaxWidth(Double.MAX_VALUE);
        form.addRow(row, label, field);
        GridPane.setHgrow(field, Priority.ALWAYS);
    }

    private static RowConstraints fixedFormRow() {
        RowConstraints row = new RowConstraints(FORM_ROW_HEIGHT, FORM_ROW_HEIGHT, FORM_ROW_HEIGHT);
        row.setValignment(javafx.geometry.VPos.CENTER);
        return row;
    }

    private static GridPane templateHelp() {
        GridPane help = new GridPane();
        help.setVgap(4.0);
        help.getStyleClass().add("snippet-template-card");
        Label title = new Label(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2f0c0a875d30f2ce"));
        title.getStyleClass().add("snippet-template-title");
        List<Label> templates = TEMPLATE_HELP_LINES.stream().map(SnippetDialogs::copy).toList();
        Label encoding = copy(ENCODING_HELP);
        Label failure = copy(com.ynpe.fastremote.i18n.Texts.text("legacy.java.261bc61efbf3cac7"));
        help.add(title, 0, 0);
        for (int index = 0; index < templates.size(); index++) help.add(templates.get(index), 0, index + 1);
        help.add(encoding, 0, templates.size() + 1);
        help.add(failure, 0, templates.size() + 2);
        java.util.stream.Stream.concat(templates.stream(), java.util.stream.Stream.of(encoding, failure))
                .forEach(label -> GridPane.setHgrow(label, javafx.scene.layout.Priority.ALWAYS));
        return help;
    }

    private static Label copy(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("snippet-template-copy");
        return label;
    }

    static Optional<Map<String, String>> requestInputs(
            Window owner, Snippet snippet, List<SnippetInput> inputs, List<SnippetVariable> variables) {
        if (inputs.isEmpty()) return Optional.of(Map.of());
        Dialog<Map<String, String>> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d954626b7cb2987b") + snippet.name());
        dialog.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.aeae4d4372aa99ef"));
        DialogSpacing.contentToButtonBar(dialog);
        ButtonType send = new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.7c9691192f1b7340"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, send);
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        Map<SnippetInput, InputControl> fields = new LinkedHashMap<>();
        for (int index = 0; index < inputs.size(); index++) {
            SnippetInput input = inputs.get(index);
            List<String> candidates = inputCandidates(input, variables);
            InputControl field;
            if (candidates.size() <= 1) {
                TextField text = new TextField(candidates.isEmpty() ? "" : candidates.getFirst());
                configureInputField(text);
                text.setPromptText(input.required() ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.11da9dc4428537c4") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.537352400c495fb8"));
                field = new InputControl(text, text::getText, text::requestFocus, () -> text.positionCaret(text.getLength()));
            } else {
                ComboBox<String> choices = new ComboBox<>();
                configureCandidateChoice(choices);
                choices.getItems().setAll(candidates);
                choices.getSelectionModel().selectFirst();
                field = new InputControl(choices, () -> choices.getEditor().getText(),
                        choices::requestFocus, () -> choices.getEditor().positionCaret(choices.getEditor().getLength()));
            }
            grid.addRow(index, new Label(input.name()), field.node());
            GridPane.setHgrow(field.node(), javafx.scene.layout.Priority.ALWAYS);
            fields.put(input, field);
        }
        dialog.getDialogPane().setContent(DialogSpacing.anchoredContent(grid));
        dialog.getDialogPane().lookupButton(send).addEventFilter(ActionEvent.ACTION, event ->
                {
                    for (var entry : fields.entrySet()) {
                        InputControl field = entry.getValue();
                        if (entry.getKey().required() && field.value().get().isBlank()) {
                            field.focus().run();
                            field.moveToEnd().run();
                            event.consume();
                            break;
                        }
                    }
                });
        dialog.setResultConverter(button -> {
            if (button != send) return null;
            Map<String, String> values = new LinkedHashMap<>();
            fields.forEach((input, field) -> values.put(input.name(), field.value().get()));
            return values;
        });
        InputControl firstField = fields.values().iterator().next();
        dialog.setOnShown(event -> Platform.runLater(() -> {
            firstField.focus().run();
            firstField.moveToEnd().run();
        }));
        return dialog.showAndWait();
    }

    static void configureInputField(Region field) {
        field.setMinWidth(300);
        field.setPrefWidth(300);
        field.setMaxWidth(300);
        field.getStyleClass().add("snippet-input-field");
    }

    static void configureCandidateChoice(ComboBox<String> choices) {
        configureInputField(choices);
        choices.setEditable(true);
        choices.getStyleClass().add("snippet-input-choice");
        var editorFocused = javafx.css.PseudoClass.getPseudoClass("editor-focused");
        choices.getEditor().focusedProperty().addListener((observable, before, focused) ->
                choices.pseudoClassStateChanged(editorFocused, focused));
        choices.getEditor().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.DOWN && !choices.isShowing()) {
                choices.show();
                event.consume();
            }
        });
    }

    static List<String> inputCandidates(SnippetInput input, List<SnippetVariable> variables) {
        List<String> candidates = new java.util.ArrayList<>();
        input.defaultValue().ifPresent(candidates::add);
        input.variableName().ifPresent(name -> {
            SnippetVariable variable = variables.stream()
                    .filter(value -> value.variableName().equals(name)).findFirst()
                    .orElseThrow(() -> new com.ynpe.fastremote.domain.snippet.SnippetTemplateException(
                            com.ynpe.fastremote.i18n.Texts.text("legacy.java.b6da7e3b5816c4e7") + name));
            candidates.addAll(variable.expandedValues());
        });
        return List.copyOf(candidates);
    }

    private record InputControl(
            Node node,
            java.util.function.Supplier<String> value,
            Runnable focus,
            Runnable moveToEnd) {}

    private record GroupChoice(Optional<SnippetGroupId> id, String label) {
        private GroupChoice {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(label, "label");
        }

        static GroupChoice root() { return new GroupChoice(Optional.empty(), com.ynpe.fastremote.i18n.Texts.text("legacy.java.e86ea3f280d920a2")); }
        static GroupChoice group(SnippetGroup group) {
            return new GroupChoice(Optional.of(group.id()), group.name());
        }
        @Override public String toString() { return label; }
    }
}
