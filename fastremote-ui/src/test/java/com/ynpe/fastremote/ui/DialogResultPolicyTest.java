// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertNull;

import javafx.scene.control.ButtonType;
import org.junit.jupiter.api.Test;

class DialogResultPolicyTest {
    @Test
    void cancelNeverBecomesADomainDialogResult() {
        assertNull(DialogResultPolicy.<Object>explicitOnly().call(ButtonType.CANCEL));
    }
}
