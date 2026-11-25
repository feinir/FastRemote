// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.settings.InterfaceLanguage;
import com.ynpe.fastremote.i18n.Localizer;
import com.ynpe.fastremote.i18n.Texts;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FormLabelVocabularyTest {
    private static final String HARMONY_SC = "HarmonyOS Sans SC";
    private static final String HARMONY_TC = "HarmonyOS Sans TC";
    private static final String NOTO_JP = "Noto Sans CJK JP";
    private static final String NOTO_KR = "Noto Sans CJK KR";
    private static final String NOTO_THAI = "Noto Sans Thai";

    @BeforeAll
    static void startJavaFxToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(5, TimeUnit.SECONDS));
        loadInterfaceFont("HarmonyOS_Sans_SC.ttf", HARMONY_SC);
        loadInterfaceFont("HarmonyOS_Sans_TC.ttf", HARMONY_TC);
        loadInterfaceFont("NotoSansCJKjp-Regular.otf", NOTO_JP);
        loadInterfaceFont("NotoSansCJKkr-Regular.otf", NOTO_KR);
        loadInterfaceFont("NotoSansThai-Regular.ttf", NOTO_THAI);
    }

    @Test
    void bundledHarmonyCollectionResolvesRegularAndBoldFaces() {
        InterfaceFontPolicy.loadBundledFonts();
        Font regular = Font.font(HARMONY_SC, 14);
        Font bold = Font.font(HARMONY_SC, FontWeight.BOLD, 14);
        assertTrue(regular.getFamily().equals(HARMONY_SC), regular + " names=" + Font.getFontNames(HARMONY_SC));
        assertTrue(bold.getFamily().equals(HARMONY_SC), bold + " names=" + Font.getFontNames(HARMONY_SC));
    }

    @Test
    void allDialogFamiliesExposeTheirAlignedFieldLabelsForLocalizationReview() {
        List<List<String>> dialogs = List.of(
                HostEditorDialog.formLabels(),
                GroupEditorDialog.formLabels(),
                CredentialEditorDialog.formLabels(),
                MainWindowController.quickConnectDialogFieldLabels(),
                VariableDialogs.formLabels());

        dialogs.forEach(labels -> assertTrue(!labels.isEmpty()));
        dialogs.stream().flatMap(List::stream).forEach(label -> assertTrue(!label.isBlank()));
    }

    @Test
    void everyPublishedTranslationFitsWithinTwoWrappedLinesOfTheEnglishBaselineColumn() throws Exception {
        Localizer previous = Texts.localizer();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                List<String> overflows = new java.util.ArrayList<>();
                for (InterfaceLanguage language : InterfaceLanguage.publishedLanguages()) {
                    Texts.configure(Localizer.forLanguage(language));
                    Font font = interfaceFont(language, FormLabel.FORM_FONT_SIZE);
                    List<List<String>> dialogs = List.of(
                            HostEditorDialog.formLabels(), GroupEditorDialog.formLabels(),
                            CredentialEditorDialog.formLabels(), MainWindowController.quickConnectDialogFieldLabels(),
                            VariableDialogs.formLabels());
                    dialogs.stream().flatMap(List::stream).forEach(label -> {
                        Text measurement = new Text(label);
                        measurement.setFont(font);
                        measurement.setWrappingWidth(FormLabel.ENGLISH_BASELINE_WIDTH);
                        if (measurement.getLayoutBounds().getHeight() > FormLabel.TWO_LINE_HEIGHT) {
                            overflows.add(language.persistedTag() + ": " + label + " = "
                                    + measurement.getLayoutBounds().getHeight());
                        }
                    });
                }
                assertTrue(overflows.isEmpty(), String.join("; ", overflows));
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                Texts.configure(previous);
                completed.countDown();
            }
        });
        assertTrue(completed.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void everyConnectionTranslationFitsTheNarrowerDialogColumnInTwoLines() throws Exception {
        Localizer previous = Texts.localizer();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                List<String> overflows = new java.util.ArrayList<>();
                for (InterfaceLanguage language : InterfaceLanguage.publishedLanguages()) {
                    Texts.configure(Localizer.forLanguage(language));
                    Font font = interfaceFont(language, FormLabel.FORM_FONT_SIZE);
                    List.of(HostEditorDialog.formLabels(),
                                    MainWindowController.quickConnectDialogFieldLabels())
                            .stream().flatMap(List::stream).forEach(label -> {
                                Text measurement = new Text(FormLabel.withSoftWrapOpportunities(label));
                                measurement.setFont(font);
                                measurement.setWrappingWidth(HostEditorDialog.FORM_LABEL_WIDTH);
                                if (measurement.getLayoutBounds().getHeight()
                                        > HostEditorDialog.FORM_LABEL_HEIGHT) {
                                    overflows.add(language.persistedTag() + ": " + label + " = "
                                            + measurement.getLayoutBounds().getHeight());
                                }
                            });
                }
                assertTrue(overflows.isEmpty(), String.join("; ", overflows));
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                Texts.configure(previous);
                completed.countDown();
            }
        });
        assertTrue(completed.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void everyQuickConnectTranslationFitsTheCompactDockInTwoLines() throws Exception {
        Localizer previous = Texts.localizer();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                for (InterfaceLanguage language : InterfaceLanguage.publishedLanguages()) {
                    Texts.configure(Localizer.forLanguage(language));
                    Font font = interfaceFont(language, 12.0);
                    MainWindowController.quickConnectDialogFieldLabels().forEach(label -> {
                        Text measurement = new Text(FormLabel.withSoftWrapOpportunities(label));
                        measurement.setFont(font);
                        measurement.setWrappingWidth(FormLabel.ENGLISH_BASELINE_WIDTH);
                        assertTrue(measurement.getLayoutBounds().getHeight() <= FormLabel.COMPACT_TWO_LINE_HEIGHT,
                                () -> language.persistedTag() + ": " + label + " = "
                                        + measurement.getLayoutBounds().getHeight());
                    });
                }
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                Texts.configure(previous);
                completed.countDown();
            }
        });
        assertTrue(completed.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("JavaFX assertion failed", failure.get());
    }

    @Test
    void builtinImportExplanationAndOverwriteOptionFitTheCompactContentWidth() {
        for (InterfaceLanguage language : InterfaceLanguage.publishedLanguages()) {
            Font font = interfaceFont(language, FormLabel.FORM_FONT_SIZE);
            String explanation = Localizer.forLanguage(language).text("builtin.import.header");
            Text wrapped = new Text(explanation);
            wrapped.setFont(font);
            wrapped.setWrappingWidth(460.0);
            Text baseline = new Text("Ag");
            baseline.setFont(font);
            assertTrue(wrapped.getLayoutBounds().getHeight()
                            <= baseline.getLayoutBounds().getHeight() * 2.2,
                    () -> language.persistedTag() + " import explanation exceeds two lines");

            Text overwriteOption = new Text(
                    Localizer.forLanguage(language).text("builtin.import.overwrite"));
            overwriteOption.setFont(font);
            assertTrue(overwriteOption.getLayoutBounds().getWidth() <= 300.0,
                    () -> language.persistedTag() + " overwrite option is too wide");
        }
    }

    @Test
    void longLanguagePrimaryToolbarLabelsStayCompact() {
        for (InterfaceLanguage language : List.of(
                InterfaceLanguage.GERMAN, InterfaceLanguage.RUSSIAN,
                InterfaceLanguage.SPANISH, InterfaceLanguage.BRAZILIAN_PORTUGUESE)) {
            Font font = interfaceFont(language, 13.0);
            Localizer localizer = Localizer.forLanguage(language);
            List.of(
                    "legacy.fxml.51b72143be70e638",
                    "legacy.fxml.bec3d896410b5294",
                    "legacy.fxml.58e21b8737ccbc49",
                    "legacy.fxml.359656f33c8dac51",
                    "legacy.fxml.1b0049fcfd4163fe",
                    "legacy.fxml.5d7d7a4dcc5011fe").forEach(key -> {
                        Text measurement = new Text(localizer.text(key));
                        measurement.setFont(font);
                        assertTrue(measurement.getLayoutBounds().getWidth() <= 120.0,
                                () -> language.persistedTag() + " " + key + ": " + measurement.getText());
                    });
        }
    }

    private static Font interfaceFont(InterfaceLanguage language, double size) {
        String family = switch (language) {
            case TRADITIONAL_CHINESE -> HARMONY_TC;
            case JAPANESE -> NOTO_JP;
            case KOREAN -> NOTO_KR;
            case THAI -> NOTO_THAI;
            default -> HARMONY_SC;
        };
        return Font.font(family, size);
    }

    private static void loadInterfaceFont(String name, String expectedFamily) {
        var resource = FormLabelVocabularyTest.class.getResource(
                "/com/ynpe/fastremote/terminal/fonts/" + name);
        assertTrue(resource != null, "Bundled interface font is missing: " + name);
        Font font = Font.loadFont(resource.toExternalForm(), FormLabel.FORM_FONT_SIZE);
        assertTrue(font != null && expectedFamily.equals(font.getFamily()),
                "Bundled interface font could not be registered: " + expectedFamily);
    }
}
