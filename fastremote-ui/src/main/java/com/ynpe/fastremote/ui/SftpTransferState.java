// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

enum SftpTransferState {
    WAITING(com.ynpe.fastremote.i18n.Texts.text("legacy.java.26c8cfcbf763073f")),
    RUNNING(com.ynpe.fastremote.i18n.Texts.text("legacy.java.da7c77fc4e1bc651")),
    PAUSED(com.ynpe.fastremote.i18n.Texts.text("legacy.java.eb0c326b60ae897a")),
    CANCELLED(com.ynpe.fastremote.i18n.Texts.text("legacy.java.a37778f17c5f3ee5")),
    COMPLETED(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f28461bb49c85647")),
    FAILED(com.ynpe.fastremote.i18n.Texts.text("legacy.java.28384d7afd2e4fa6"));

    private final String label;

    SftpTransferState(String label) {
        this.label = label;
    }

    String label() {
        return label;
    }

    boolean canPause() { return this == WAITING || this == RUNNING; }
    boolean canCancel() { return this == WAITING || this == RUNNING || this == PAUSED; }
    boolean canResume() { return this == PAUSED; }
    boolean canRetry() { return this == COMPLETED || this == FAILED; }
    boolean canDelete() { return this == PAUSED || this == CANCELLED || this == COMPLETED || this == FAILED; }
}
