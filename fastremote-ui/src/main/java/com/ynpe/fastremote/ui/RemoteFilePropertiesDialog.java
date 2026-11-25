// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.transfer.RemoteFileAttributes;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** Chinese POSIX permission editor for one remote SFTP object. */
final class RemoteFilePropertiesDialog {
    static final double PREFERRED_CONTENT_WIDTH = 530.0;
    static final double PERMISSION_LABEL_WIDTH = 100.0;
    static final double PERMISSION_ROW_HEIGHT = 44.0;
    private static final int[] SPECIAL_BITS = {04000, 02000, 01000};
    private static final int[][] ACCESS_BITS = {{0400, 0200, 0100}, {0040, 0020, 0010}, {0004, 0002, 0001}};

    private RemoteFilePropertiesDialog() { }

    static Optional<Integer> show(Window owner, RemoteFileAttributes attributes) {
        Dialog<Integer> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.24d7280c734b7fa9") + attributes.name());
        dialog.setHeaderText(null);
        DialogSpacing.contentToButtonBar(dialog);
        ButtonType save = new ButtonType(com.ynpe.fastremote.i18n.Texts.text("legacy.java.fac2a67ad87807c4"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, save);

        Label title = new Label(attributes.name());
        title.getStyleClass().add("remote-properties-name");
        GridPane information = information(attributes);

        GridPane ownership = grid();
        ownership.addRow(0, fixedLabel(com.ynpe.fastremote.i18n.Texts.text("legacy.java.43a7f4b4c5c88a2a")), value(attributes.owner()));
        ownership.addRow(1, fixedLabel(com.ynpe.fastremote.i18n.Texts.text("legacy.java.963ead08d78d597c")), value(attributes.group()));

        TextField octal = new TextField(String.format(Locale.ROOT, "%04o", attributes.permissions()));
        octal.setPromptText("0000–7777");
        octal.setAccessibleText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b77599aaefb245eb"));
        octal.setPrefColumnCount(8);
        Label validation = new Label();
        validation.getStyleClass().add("validation-error");
        validation.setManaged(false);
        validation.setVisible(false);

        CheckBox[] special = {new CheckBox(com.ynpe.fastremote.i18n.Texts.text("legacy.java.daad694623c726b5")), new CheckBox(com.ynpe.fastremote.i18n.Texts.text("legacy.java.9f0007d1bc680c58")), new CheckBox(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d4e85e84c3ebdb68"))};
        List.of(special).forEach(box -> {
            box.setMinWidth(Region.USE_PREF_SIZE);
            box.setAlignment(Pos.CENTER_LEFT);
        });
        CheckBox[][] access = new CheckBox[3][3];
        GridPane permissionGrid = grid();
        for (int row = 0; row < 4; row++) {
            RowConstraints constraints = new RowConstraints(
                    PERMISSION_ROW_HEIGHT, PERMISSION_ROW_HEIGHT, PERMISSION_ROW_HEIGHT);
            constraints.setValignment(VPos.CENTER);
            permissionGrid.getRowConstraints().add(constraints);
        }
        permissionGrid.add(permissionLabel(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a5c81a52b1b51262")), 0, 0);
        HBox specialRow = new HBox(12, special);
        specialRow.setAlignment(Pos.CENTER_LEFT);
        GridPane.setValignment(specialRow, VPos.CENTER);
        permissionGrid.add(specialRow, 1, 0, 3, 1);
        String[] rowNames = {com.ynpe.fastremote.i18n.Texts.text("legacy.java.43a7f4b4c5c88a2a"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.963ead08d78d597c"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.7f23fc9757e37ee9")};
        String[] columnNames = {com.ynpe.fastremote.i18n.Texts.text("legacy.java.534cb3fa8fbf373f"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.5c783c4679655185"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.32c8d373afe08328")};
        for (int row = 0; row < access.length; row++) {
            permissionGrid.add(permissionLabel(rowNames[row]), 0, row + 1);
            for (int column = 0; column < access[row].length; column++) {
                access[row][column] = new CheckBox(columnNames[column]);
                access[row][column].setMinWidth(Region.USE_PREF_SIZE);
                access[row][column].setAlignment(Pos.CENTER_LEFT);
                GridPane.setValignment(access[row][column], VPos.CENTER);
                permissionGrid.add(access[row][column], column + 1, row + 1);
            }
        }

        AtomicBoolean synchronizing = new AtomicBoolean();
        Runnable updateChecks = () -> {
            Integer permissions = parsePermissions(octal.getText());
            boolean valid = permissions != null;
            Node saveButton = dialog.getDialogPane().lookupButton(save);
            saveButton.setDisable(!valid);
            validation.setText(valid ? "" : com.ynpe.fastremote.i18n.Texts.text("legacy.java.fdf16490d48c2573"));
            validation.setManaged(!valid);
            validation.setVisible(!valid);
            if (!valid || !synchronizing.compareAndSet(false, true)) return;
            try {
                for (int index = 0; index < special.length; index++)
                    special[index].setSelected((permissions & SPECIAL_BITS[index]) != 0);
                for (int row = 0; row < access.length; row++)
                    for (int column = 0; column < access[row].length; column++)
                        access[row][column].setSelected((permissions & ACCESS_BITS[row][column]) != 0);
            } finally {
                synchronizing.set(false);
            }
        };
        Runnable updateOctal = () -> {
            if (!synchronizing.compareAndSet(false, true)) return;
            try {
                int permissions = 0;
                for (int index = 0; index < special.length; index++)
                    if (special[index].isSelected()) permissions |= SPECIAL_BITS[index];
                for (int row = 0; row < access.length; row++)
                    for (int column = 0; column < access[row].length; column++)
                        if (access[row][column].isSelected()) permissions |= ACCESS_BITS[row][column];
                octal.setText(String.format(Locale.ROOT, "%04o", permissions));
            } finally {
                synchronizing.set(false);
            }
        };
        octal.textProperty().addListener((ignored, previous, current) -> updateChecks.run());
        List.of(special).forEach(box -> box.selectedProperty().addListener((ignored, previous, current) -> updateOctal.run()));
        for (CheckBox[] row : access) List.of(row).forEach(box ->
                box.selectedProperty().addListener((ignored, previous, current) -> updateOctal.run()));
        updateChecks.run();

        HBox attributeRow = new HBox(12, fixedLabel(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b77599aaefb245eb")), octal);
        attributeRow.setAlignment(Pos.CENTER_LEFT);
        VBox content = new VBox(10, title, new Separator(), information, new Separator(), ownership,
                new Separator(), attributeRow, permissionGrid, validation);
        content.setPadding(new Insets(2));
        content.setMinWidth(PREFERRED_CONTENT_WIDTH);
        content.setPrefWidth(PREFERRED_CONTENT_WIDTH);
        dialog.getDialogPane().setContent(DialogSpacing.anchoredContent(content));
        dialog.setResizable(true);
        dialog.setResultConverter(button -> button == save ? parsePermissions(octal.getText()) : null);
        dialog.setOnShown(event -> Platform.runLater(() -> DialogSpacing.focusAtEnd(octal)));
        return dialog.showAndWait();
    }

    static GridPane information(RemoteFileAttributes attributes) {
        GridPane information = grid();
        int row = 0;
        information.addRow(row++, fixedLabel(com.ynpe.fastremote.i18n.Texts.text("legacy.java.ba40014ff496f64e")), value(type(attributes)));
        if (attributes.symbolicLink()) {
            Label target = value(attributes.symbolicLinkTarget().orElse("—"));
            target.setId("remote-properties-link-target");
            target.setTooltip(new javafx.scene.control.Tooltip(target.getText()));
            information.addRow(row++, fixedLabel(com.ynpe.fastremote.i18n.Texts.text("sftp.properties.linkTarget")), target);
        }
        information.addRow(row++, fixedLabel(com.ynpe.fastremote.i18n.Texts.text("legacy.java.1fb4d574da92f1c1")), value(parent(attributes.path())));
        information.addRow(row++, fixedLabel(com.ynpe.fastremote.i18n.Texts.text("legacy.java.50db7447b966f5ef")), value(formatSize(attributes.size())));
        information.addRow(row++, fixedLabel(com.ynpe.fastremote.i18n.Texts.text("legacy.java.257bbcc44c839db4")), value(attributes.modifiedAt()
                .map(value -> com.ynpe.fastremote.i18n.Texts.localizer()
                        .formatDateTime(value, ZoneId.systemDefault()))
                .orElse(com.ynpe.fastremote.i18n.Texts.text("legacy.java.4d8c1c5b42830791"))));

        return information;
    }

    static Integer parsePermissions(String text) {
        if (text == null || !text.matches("[0-7]{4}")) return null;
        return Integer.parseInt(text, 8);
    }

    private static GridPane grid() {
        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(8);
        return grid;
    }

    private static Label value(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(label, Priority.ALWAYS);
        return label;
    }

    private static Label fixedLabel(String text) {
        Label label = new Label(text);
        label.setMinWidth(Region.USE_PREF_SIZE);
        return label;
    }

    private static Label permissionLabel(String text) {
        Label label = fixedLabel(text);
        label.setWrapText(true);
        label.setMinWidth(PERMISSION_LABEL_WIDTH);
        label.setPrefWidth(PERMISSION_LABEL_WIDTH);
        label.setMaxWidth(PERMISSION_LABEL_WIDTH);
        label.setMinHeight(PERMISSION_ROW_HEIGHT);
        label.setAlignment(Pos.CENTER_LEFT);
        GridPane.setValignment(label, VPos.CENTER);
        return label;
    }

    private static String type(RemoteFileAttributes attributes) {
        if (attributes.symbolicLink()) return com.ynpe.fastremote.i18n.Texts.text("legacy.java.35d313a2d6423b1d");
        return attributes.directory() ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.52daa71ebc310581") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.39932f24fe11a6ba");
    }

    private static String parent(String path) {
        if (path.equals("/")) return "/";
        int separator = path.lastIndexOf('/');
        return separator <= 0 ? "/" : path.substring(0, separator);
    }

    private static String formatSize(long bytes) {
        var localizer = com.ynpe.fastremote.i18n.Texts.localizer();
        return localizer.text("size.withExactBytes", localizer.formatBytes(bytes), localizer.formatNumber(bytes));
    }
}
