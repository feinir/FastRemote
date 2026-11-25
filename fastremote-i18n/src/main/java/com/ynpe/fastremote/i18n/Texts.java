// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.i18n;

import com.ynpe.fastremote.domain.settings.InterfaceLanguage;
import java.util.Objects;

/** Process-wide text facade. The selected locale is immutable after application startup. */
public final class Texts {
    private static volatile Localizer localizer = Localizer.forLanguage(InterfaceLanguage.SIMPLIFIED_CHINESE);

    private Texts() {
    }

    public static void configure(Localizer selected) {
        localizer = Objects.requireNonNull(selected, "selected");
    }

    public static String text(String key, Object... arguments) {
        return localizer.text(key, arguments);
    }

    /** Localizes known validation/storage message fragments without coupling inner modules to i18n. */
    public static String translateExternalText(String value) {
        return localizer.translateExternalText(value);
    }

    public static Localizer localizer() {
        return localizer;
    }
}
