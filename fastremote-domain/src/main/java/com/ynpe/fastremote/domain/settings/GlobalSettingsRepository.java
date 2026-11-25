// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.settings;

import java.io.IOException;

public interface GlobalSettingsRepository {
    GlobalSettings load() throws IOException;

    void save(GlobalSettings settings) throws IOException;
}
