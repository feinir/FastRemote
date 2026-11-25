// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

public record ConfigurationSelection(boolean program, boolean hosts, boolean credentials,
        boolean hostCredentials, boolean commands, boolean variables) {
    public boolean isEmpty() {
        return !(program || hosts || credentials || hostCredentials || commands || variables);
    }
}
