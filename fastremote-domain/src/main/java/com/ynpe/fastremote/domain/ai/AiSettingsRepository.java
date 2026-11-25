// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.ai;

import java.io.IOException;

public interface AiSettingsRepository {
    AiSettings load() throws IOException;
    void save(AiSettings settings) throws IOException;
}
