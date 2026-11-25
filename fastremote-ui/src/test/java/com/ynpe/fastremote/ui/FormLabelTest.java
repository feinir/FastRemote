// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.settings.InterfaceLanguage;
import com.ynpe.fastremote.i18n.Localizer;
import com.ynpe.fastremote.i18n.Texts;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FormLabelTest {
    @BeforeAll
    static void startJavaFxToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(5, TimeUnit.SECONDS));
    }

    @Test
    void usesTheEnglishBaselineFormColumnWidth() {
        assertEquals(85.0, FormLabel.naturalWidthForCharacters(4));
        assertEquals(85.0, FormLabel.naturalWidthForCharacters(2));
    }

    @Test
    void alignsLocalizedFormTextToTheLeftWithinTheSharedColumn() {
        assertEquals(Pos.CENTER_LEFT, FormLabel.rowAlignment());
    }

    @Test
    void wrapsLongTranslationsInsideTheEnglishBaselineColumn() {
        FormLabel label = new FormLabel(
                "A deliberately long localized field label", 85.0, FormLabel.TWO_LINE_HEIGHT);

        assertTrue(label.isWrapText());
        assertEquals(85.0, label.getMinWidth());
        assertEquals(85.0, label.getPrefWidth());
        assertEquals(85.0, label.getMaxWidth());
        assertEquals(48.0, label.getPrefHeight());
    }

    @Test
    void addsInvisibleUnicodeLineBreakOpportunitiesForThaiWhilePreservingAccessibleText() {
        Localizer previous = Texts.localizer();
        try {
            Texts.configure(Localizer.forLanguage(InterfaceLanguage.THAI));
            String original = "แหล่งที่มาของข้อมูลประจำตัว";
            FormLabel label = new FormLabel(original, 85.0, FormLabel.TWO_LINE_HEIGHT);

            assertTrue(label.getText().contains("\u200B"));
            assertEquals(original, label.text());
            assertEquals(original, label.getAccessibleText());
        } finally {
            Texts.configure(previous);
        }
    }

    @Test
    void rejectsAFormColumnWithoutAnyCharacterWidth() {
        assertThrows(IllegalArgumentException.class, () -> FormLabel.naturalWidthForCharacters(0));
        assertThrows(IllegalArgumentException.class, () -> FormLabel.naturalWidthForCharacters(-1));
    }

    @Test
    void sessionCertificateFieldAndChooserShareTheCompactControlHeight() {
        TextField field = new TextField();
        Button chooser = FileSelectionButton.create("Choose certificate");

        var row = HostEditorDialog.fileSelectionRow(field, chooser);

        assertEquals(HostEditorDialog.FORM_CONTROL_HEIGHT, field.getPrefHeight());
        assertEquals(HostEditorDialog.FORM_CONTROL_HEIGHT, field.getMaxHeight());
        assertEquals(HostEditorDialog.FORM_CONTROL_HEIGHT, chooser.getPrefHeight());
        assertEquals(HostEditorDialog.FORM_CONTROL_HEIGHT, chooser.getMaxHeight());
        assertEquals(HostEditorDialog.FORM_CONTROL_HEIGHT, row.getPrefHeight());
        assertEquals(HostEditorDialog.FORM_CONTROL_HEIGHT, row.getMaxHeight());
        assertEquals(Pos.CENTER_LEFT, row.getAlignment());
        assertTrue(!row.isFillHeight());
    }
}
