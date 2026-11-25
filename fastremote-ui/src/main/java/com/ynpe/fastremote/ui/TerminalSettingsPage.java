// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static com.ynpe.fastremote.ui.SettingsPageLayout.page;
import static com.ynpe.fastremote.ui.SettingsPageLayout.card;
import static com.ynpe.fastremote.ui.SettingsPageLayout.labeled;
import static com.ynpe.fastremote.ui.SettingsPageLayout.check;
import static com.ynpe.fastremote.ui.SettingsPageLayout.commandArea;
import static com.ynpe.fastremote.ui.SettingsPageLayout.policyInfo;
import static com.ynpe.fastremote.ui.SettingsPageLayout.combo;
import static com.ynpe.fastremote.ui.SettingsPageLayout.enumConverter;
import static com.ynpe.fastremote.ui.SettingsPageLayout.spinner;
import static com.ynpe.fastremote.ui.SettingsPageLayout.clampedSpinnerValue;
import static com.ynpe.fastremote.ui.SettingsPageLayout.rootMessage;
import static com.ynpe.fastremote.ui.SettingsFontSelector.SearchableFontSelector;
import static com.ynpe.fastremote.ui.SettingsPageLayout.SELECTION_CONTROL_MIN_WIDTH;
import com.ynpe.fastremote.domain.settings.TerminalColorScheme;
import com.ynpe.fastremote.domain.settings.TerminalCursorShape;
import com.ynpe.fastremote.domain.settings.TerminalPreferences;
import com.ynpe.fastremote.domain.settings.TerminalType;
import com.ynpe.fastremote.domain.settings.TerminalColorPalette;
import com.ynpe.fastremote.domain.settings.RemoteProbeSettings;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.stage.Window;
import javafx.stage.FileChooser;
import javafx.scene.text.Font;

final class TerminalSettingsPage {
    private TerminalSettingsPage() {}

    static Node terminalPage(
            CheckBox titleName, CheckBox titleUser, CheckBox titleHost, CheckBox titlePort,
            TerminalControls controls) {
        HBox titleParts = new HBox(14.0, titleName, titleUser, titleHost, titlePort);
        Label fixedBehavior = policyInfo(
                com.ynpe.fastremote.i18n.Texts.text("legacy.java.d8f815d19306a6ac"));
        return page(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e2a76ef1f12e147f"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.d2239dbb2f65de42"),
                card(com.ynpe.fastremote.i18n.Texts.text("legacy.java.17bed392a98d0ea6"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.5086ce7e7f2bd46a"),
                        titleParts),
                card(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2ae75e16812360be"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.bd2c2b2b66bf9a38"),
                        labeled(com.ynpe.fastremote.i18n.Texts.text("legacy.java.6f949ac3522d3ba5"), controls.colorScheme()), controls.importColorScheme(),
                        policyInfo(com.ynpe.fastremote.i18n.Texts.text("legacy.java.36aa6f87216947ff"))),
                card(com.ynpe.fastremote.i18n.Texts.text("legacy.java.66c71d396612a969"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.2ad75eb0766ba724"),
                        labeled(com.ynpe.fastremote.i18n.Texts.text("legacy.java.078838da4218490b"), controls.fontFamily().node()),
                        labeled(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f71561b5af0abced"), controls.fontSize()),
                        controls.appearance(),
                        labeled(com.ynpe.fastremote.i18n.Texts.text("legacy.java.47a75ca0b8fef37c"), controls.cursorShape()),
                        controls.cursorBlink(),
                        policyInfo(com.ynpe.fastremote.i18n.Texts.text("terminal.fontFallbackPolicy"))),
                card(com.ynpe.fastremote.i18n.Texts.text("legacy.java.7d5bffb0f6219dcd"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.19390c71fa30b3d3"),
                        labeled(com.ynpe.fastremote.i18n.Texts.text("legacy.java.460d648dec104c7e"), controls.terminalType()),
                        labeled(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d695fd24622d95cd"), controls.initialColumns()),
                        labeled(com.ynpe.fastremote.i18n.Texts.text("legacy.java.be6dd23630850bb5"), controls.initialRows()),
                        policyInfo(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5e28904b4f44263d"))),
                card(com.ynpe.fastremote.i18n.Texts.text("legacy.java.305378ddd2b86b60"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.43727445fe083278"),
                        controls.openHyperlinksOnClick(),
                        controls.highlightHyperlinksOnHover(),
                        policyInfo(com.ynpe.fastremote.i18n.Texts.text("legacy.java.05c67660fa71b711"))),
                card(com.ynpe.fastremote.i18n.Texts.text("legacy.java.26f1ceb36574e2db"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.504997c3cb99ca65"), fixedBehavior));
    }

    record TerminalControls(
            ComboBox<String> colorScheme,
            SearchableFontSelector fontFamily,
            Spinner<Integer> fontSize,
            TextAppearanceControls appearance,
            ComboBox<TerminalCursorShape> cursorShape,
            CheckBox cursorBlink,
            ComboBox<TerminalType> terminalType,
            Spinner<Integer> initialColumns,
            Spinner<Integer> initialRows,
            CheckBox openHyperlinksOnClick,
            CheckBox highlightHyperlinksOnHover,
            CheckBox probeServerInformation,
            TextArea linuxSupplement,
            TextArea macosSupplement,
            TextArea freebsdSupplement,
            TextArea windowsSupplement,
            Button importColorScheme,
            AtomicReference<TerminalColorPalette> customPalette,
            AtomicReference<List<TerminalColorPalette>> importedPalettes) {
        private static final List<String> BUILT_IN_SCHEMES = List.of(
                "Fast-Dark", "Solarized Dark", "Fast-Light");
        private static final String SEPARATOR = "\u0000separator";

        static TerminalControls from(TerminalPreferences preferences) {
            ComboBox<String> colors = combo("Fast-Dark", "Solarized Dark", "Fast-Light");
            AtomicReference<TerminalColorPalette> custom =
                    new AtomicReference<>(preferences.customColorPalette().orElse(null));
            AtomicReference<List<TerminalColorPalette>> imported =
                    new AtomicReference<>(new java.util.ArrayList<>(preferences.importedColorPalettes()));
            if (custom.get() != null && imported.get().stream().noneMatch(value -> value.name().equals(custom.get().name())))
                imported.get().add(custom.get());
            rebuildColors(colors, imported.get());
            AtomicReference<String> previousColor = new AtomicReference<>("Fast-Dark");
            colors.valueProperty().addListener((ignored, previous, selected) -> {
                if (SEPARATOR.equals(selected)) Platform.runLater(() -> colors.setValue(previousColor.get()));
                else if (selected != null) previousColor.set(selected);
            });
            colors.setValue(switch (preferences.colorScheme()) {
                case FAST_DARK -> "Fast-Dark";
                case SOLARIZED_DARK -> "Solarized Dark";
                case FAST_LIGHT -> "Fast-Light";
                case CUSTOM -> custom.get() == null ? "Fast-Dark" : custom.get().name();
            });
            SearchableFontSelector fonts = new SearchableFontSelector(
                    systemFontFamilies(), preferences.fontFamily());
            ComboBox<TerminalCursorShape> cursor = new ComboBox<>();
            cursor.getItems().setAll(TerminalCursorShape.values());
            cursor.setValue(preferences.cursorShape());
            cursor.setConverter(enumConverter(value -> switch (value) {
                case BLOCK -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.36d89c8f512d8cce");
                case VERTICAL_BAR -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.6d1cdcbdee1f244b");
                case UNDERLINE -> com.ynpe.fastremote.i18n.Texts.text("legacy.java.d3b4a22b47250c5e");
            }));
            cursor.setMinWidth(SELECTION_CONTROL_MIN_WIDTH);
            CheckBox blink = check(com.ynpe.fastremote.i18n.Texts.text("legacy.java.9342b40e1c885cee"), preferences.cursorBlink());
            CheckBox openLinks = check(com.ynpe.fastremote.i18n.Texts.text("legacy.java.077e3d2f6b792fc9"), preferences.openHyperlinksOnClick());
            CheckBox highlightLinks = check(com.ynpe.fastremote.i18n.Texts.text("terminal.highlightHyperlinksOnHover"),
                    preferences.highlightHyperlinksOnHover());
            RemoteProbeSettings probe = preferences.remoteProbeSettings();
            CheckBox probeInformation = check(com.ynpe.fastremote.i18n.Texts.text("legacy.java.87f0b795bda30748"), probe.probeServerInformation());
            ComboBox<TerminalType> terminalType = new ComboBox<>();
            terminalType.getItems().setAll(TerminalType.values());
            terminalType.setValue(preferences.terminalType());
            terminalType.setMinWidth(SELECTION_CONTROL_MIN_WIDTH);
            Button importScheme = new Button(com.ynpe.fastremote.i18n.Texts.text("legacy.java.047376eebf865acc"));
            Spinner<Integer> size = spinner(TerminalPreferences.MIN_FONT_SIZE, TerminalPreferences.MAX_FONT_SIZE, preferences.fontSize());
            TextAppearanceControls appearance = new TextAppearanceControls(preferences.textAppearance(), true,
                    fonts::selectedFont, () -> spinnerValue(size));
            fonts.selectedFontProperty().addListener(ignored -> appearance.refreshPreview());
            size.valueProperty().addListener(ignored -> appearance.refreshPreview());
            TerminalControls controls = new TerminalControls(colors, fonts,
                    size, appearance, cursor, blink, terminalType,
                    spinner(TerminalPreferences.MIN_INITIAL_COLUMNS, TerminalPreferences.MAX_INITIAL_COLUMNS,
                            preferences.initialColumns()),
                    spinner(TerminalPreferences.MIN_INITIAL_ROWS, TerminalPreferences.MAX_INITIAL_ROWS,
                            preferences.initialRows()),
                    openLinks, highlightLinks, probeInformation,
                    commandArea(probe.linuxSupplement()), commandArea(probe.macosSupplement()),
                    commandArea(probe.freebsdSupplement()), commandArea(probe.windowsSupplement()),
                    importScheme, custom, imported);
            controls.updateProbeControlState();
            return controls;
        }

        void updateProbeControlState() {
            boolean disabled = !probeServerInformation.isSelected();
            supplementCommands().forEach(value -> value.setDisable(disabled));
        }

        List<TextArea> supplementCommands() {
            return List.of(linuxSupplement, macosSupplement, freebsdSupplement, windowsSupplement);
        }

        private void addImportedPalettes(List<TerminalColorPalette> palettes) {
            List<TerminalColorPalette> values = new java.util.ArrayList<>(importedPalettes.get());
            TerminalColorPalette last = null;
            for (TerminalColorPalette incoming : palettes) {
                TerminalColorPalette exact = values.stream().filter(value -> value.name().equalsIgnoreCase(incoming.name())
                        && sameColors(value, incoming)).findFirst().orElse(null);
                if (exact != null) { last = exact; continue; }
                String base = incoming.name(); String name = base; int suffix = 1;
                while (containsName(values, name)) name = base + "-" + suffix++;
                last = name.equals(base) ? incoming : new TerminalColorPalette(name, incoming.foreground(), incoming.background(),
                        incoming.cursor(), incoming.selectionBackground(), incoming.ansiColors());
                values.add(last);
                if (values.size() >= 1_000) break;
            }
            importedPalettes.set(values);
            rebuildColors(colorScheme, values);
            if (last != null) { customPalette.set(last); colorScheme.setValue(last.name()); }
        }

        TerminalPreferences preferences() {
            String selectedColor = colorScheme.getValue();
            TerminalColorScheme colors = selectedColorScheme(selectedColor);
            if (colors == TerminalColorScheme.CUSTOM) customPalette.set(importedPalettes.get().stream()
                    .filter(value -> value.name().equals(colorScheme.getValue())).findFirst().orElse(customPalette.get()));
            TerminalCursorShape cursor = Objects.requireNonNullElse(
                    cursorShape.getValue(), TerminalCursorShape.BLOCK);
            return new TerminalPreferences(
                    colors, fontFamily.selectedFont(), spinnerValue(fontSize), cursor, cursorBlink.isSelected(),
                    java.util.Optional.ofNullable(customPalette.get()), importedPalettes.get(),
                    Objects.requireNonNullElse(terminalType.getValue(), TerminalType.XTERM_256COLOR),
                    spinnerValue(initialColumns), spinnerValue(initialRows),
                    openHyperlinksOnClick.isSelected(),
                    new RemoteProbeSettings(
                            probeServerInformation.isSelected(),
                            linuxSupplement.getText(), macosSupplement.getText(),
                            freebsdSupplement.getText(), windowsSupplement.getText()), appearance.value(),
                    highlightHyperlinksOnHover.isSelected());
        }

        private static TerminalColorScheme selectedColorScheme(String selected) {
            return TerminalSettingsPage.selectedColorScheme(selected);
        }

        private static void rebuildColors(ComboBox<String> colors, List<TerminalColorPalette> palettes) {
            colors.getItems().setAll(BUILT_IN_SCHEMES);
            if (!palettes.isEmpty()) { colors.getItems().add(SEPARATOR); palettes.forEach(value -> colors.getItems().add(value.name())); }
            colors.setCellFactory(ignored -> new ListCell<>() {
                @Override protected void updateItem(String value, boolean empty) {
                    super.updateItem(value, empty); setText(empty || SEPARATOR.equals(value) ? null : value);
                    setGraphic(!empty && SEPARATOR.equals(value) ? new Separator() : null);
                    setDisable(!empty && SEPARATOR.equals(value));
                }
            });
        }

        private static boolean sameColors(TerminalColorPalette left, TerminalColorPalette right) {
            return left.foreground() == right.foreground() && left.background() == right.background()
                    && left.cursor() == right.cursor() && left.selectionBackground() == right.selectionBackground()
                    && left.ansiColors().equals(right.ansiColors());
        }

        private static boolean containsName(List<TerminalColorPalette> values, String name) {
            return values.stream().anyMatch(value -> value.name().equalsIgnoreCase(name));
        }

        static java.util.List<String> systemFontFamilies() {
            java.util.TreeSet<String> families = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            families.add("Monospaced");
            families.add("Monaco");
            families.add("JetBrains Mono NL");
            families.add("HarmonyOS Sans SC");
            families.add("HarmonyOS Sans TC");
            families.add("Noto Sans CJK JP");
            families.add("Noto Sans CJK KR");
            families.add("Noto Sans Thai");
            // JavaFX delegates enumeration to the active platform font backend.
            families.addAll(Font.getFamilies());
            return java.util.List.copyOf(families);
        }

        static int spinnerValue(Spinner<Integer> spinner) {
            if (spinner.getValueFactory() instanceof javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory values) {
                return clampedSpinnerValue(
                        spinner.getEditor().getText(), spinner.getValue(), values.getMin(), values.getMax());
            }
            return spinner.getValue();
        }
    }

    static TerminalColorScheme selectedColorScheme(String selected) {
        return switch (selected == null ? "Fast-Dark" : selected) {
            case "Solarized Dark" -> TerminalColorScheme.SOLARIZED_DARK;
            case "Fast-Light" -> TerminalColorScheme.FAST_LIGHT;
            case "Fast-Dark" -> TerminalColorScheme.FAST_DARK;
            default -> TerminalColorScheme.CUSTOM;
        };
    }

    static void importColorScheme(
            Window owner,
            TerminalControls controls,
            Function<List<Path>, CompletionStage<TerminalPaletteImportBatch>> importer,
            Runnable updateDirtyState) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.fd166d85f1531620"));
        chooser.getExtensionFilters().setAll(
                new FileChooser.ExtensionFilter(com.ynpe.fastremote.i18n.Texts.text("legacy.java.5d3826df05777df3"), "*.json", "*.itermcolors"),
                new FileChooser.ExtensionFilter(com.ynpe.fastremote.i18n.Texts.text("legacy.java.ec36ca4a1819dfa5"), "*.*"));
        chooser.getExtensionFilters().setAll(
                new FileChooser.ExtensionFilter(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b527281d2890eee9"), "*.json", "*.jsonc", "*.itermcolors"),
                new FileChooser.ExtensionFilter(com.ynpe.fastremote.i18n.Texts.text("legacy.java.ec36ca4a1819dfa5"), "*.*"));
        List<java.io.File> selected = PlatformFileChooser.showOpenMultipleDialog(chooser, owner);
        if (selected == null || selected.isEmpty()) return;
        if (selected.size() > 1_000) {
            new Alert(Alert.AlertType.ERROR, com.ynpe.fastremote.i18n.Texts.text("legacy.java.fa79808f2687aea0"), ButtonType.OK).showAndWait(); return;
        }
        if (selected.size() > 100) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.3b2ee82dd97c5c96") + selected.size() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.c2060f1d2b7f871e"),
                    ButtonType.CANCEL, ButtonType.OK);
            confirm.initOwner(owner);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        }
        controls.importColorScheme().setDisable(true);
        importer.apply(selected.stream().map(java.io.File::toPath).toList()).whenComplete((batch, error) -> Platform.runLater(() -> {
            controls.importColorScheme().setDisable(false);
            if (error != null) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.initOwner(owner);
                alert.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.b5f58abeba32c13e"));
                alert.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.0b5d984e337291a3"));
                alert.setContentText(rootMessage(error, com.ynpe.fastremote.i18n.Texts.text("legacy.java.77abecb661bb5f46")));
                alert.show();
                return;
            }
            controls.addImportedPalettes(batch.palettes());
            updateDirtyState.run();
            if (!batch.failures().isEmpty()) {
                Alert partial = new Alert(Alert.AlertType.WARNING);
                partial.initOwner(owner); partial.setTitle(com.ynpe.fastremote.i18n.Texts.text("legacy.java.c154ce8591e85cb6"));
                partial.setHeaderText(com.ynpe.fastremote.i18n.Texts.text("legacy.java.74ce1a1902e549bc") + batch.palettes().size() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.f264f8bb5d7a6f07") + batch.failures().size() + com.ynpe.fastremote.i18n.Texts.text("legacy.java.ff7a7e19a5bf15f4"));
                partial.setContentText(batch.failures().stream().limit(10)
                        .map(value -> value.source().getFileName() + "：" + rootMessage(new RuntimeException(value.message()), com.ynpe.fastremote.i18n.Texts.text("legacy.java.6aa27f14bff16faa")))
                        .collect(java.util.stream.Collectors.joining("\n")));
                partial.show();
            }
        }));
    }
}
