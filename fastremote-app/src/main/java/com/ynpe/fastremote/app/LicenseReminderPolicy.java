// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

/** Tracks the once-per-unauthorized-episode startup reminder without consuming it while UI is blocked. */
final class LicenseReminderPolicy {
    private boolean shown;

    boolean claim(boolean presentationBlocked) {
        if (shown || presentationBlocked) return false;
        shown = true;
        return true;
    }

    boolean wasShown() {
        return shown;
    }

    void markAuthorized() {
        shown = false;
    }
}
