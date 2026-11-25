// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import java.util.Locale;

enum ApplicationEdition {
    COMMERCIAL, COMMUNITY, STORE;

    private static final ApplicationEdition CURRENT = valueOf(ApplicationVersion.productProperty("edition")
            .toUpperCase(Locale.ROOT));

    static ApplicationEdition current() {
        return CURRENT;
    }

}
