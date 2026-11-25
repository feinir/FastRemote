// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VariableDialogsTest {
    @Test
    void usesTheSharedMultilingualFormLabelWidth() {
        assertEquals(85.0, VariableDialogs.FORM_LABEL_WIDTH);
        assertEquals(10.0, VariableDialogs.FORM_ROW_HEIGHT - VariableDialogs.FORM_CONTROL_HEIGHT);
        assertEquals(10.0, VariableDialogs.VALUE_ROW_HEIGHT - VariableDialogs.VALUE_CONTROL_HEIGHT);
        assertEquals(660.0, VariableDialogs.DIALOG_WIDTH);
        assertEquals(505.0, VariableDialogs.DIALOG_HEIGHT);
    }
}
