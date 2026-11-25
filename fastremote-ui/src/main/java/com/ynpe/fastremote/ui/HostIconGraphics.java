// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.catalog.HostIcon;
import com.ynpe.fastremote.ui.LucideIconView.Icon;
import java.util.Objects;

/** Maps persisted host identities to semantically matching Lucide icons. */
final class HostIconGraphics {
    private HostIconGraphics() {}

    static Icon icon(HostIcon icon) {
        return switch (Objects.requireNonNull(icon, "icon")) {
            case SERVER -> Icon.SERVER;
            case LINUX -> Icon.LINUX;
            case WINDOWS -> Icon.WINDOWS;
            case FREEBSD -> Icon.FREEBSD;
            case UNIX -> Icon.SQUARE_TERMINAL;
            case MACOS -> Icon.APPLE;
            case DATABASE -> Icon.DATABASE;
            case KUBERNETES -> Icon.BOXES;
            case CONTAINER -> Icon.CONTAINER;
            case CLOUD -> Icon.CLOUD;
            case NETWORK -> Icon.NETWORK;
            case STORAGE -> Icon.HARD_DRIVE;
            case WORKSTATION -> Icon.MONITOR;
            case ROUTER -> Icon.ROUTER;
            case FIREWALL -> Icon.SHIELD_CHECK;
        };
    }

    static String path(HostIcon icon) {
        return LucideIconPaths.path(icon(icon));
    }
}
