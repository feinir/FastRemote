// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.settings.GlobalSettings;
import com.ynpe.fastremote.domain.settings.InterfaceLanguage;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InterfaceLanguageSettingsTest {
    @TempDir Path temporaryDirectory;

    @Test
    void defaultsToSystemAndRoundTripsStableLanguageTag() throws Exception {
        Path path = temporaryDirectory.resolve("global-config.yaml");
        var repository = new YamlGlobalSettingsRepository(new YamlGlobalConfigStore(path));

        GlobalSettings defaults = repository.load();
        assertEquals(InterfaceLanguage.SYSTEM, defaults.windowSettings().interfaceLanguage());

        repository.save(defaults.withWindowSettings(defaults.windowSettings()
                .withInterfaceLanguage(InterfaceLanguage.TRADITIONAL_CHINESE)));

        assertEquals(InterfaceLanguage.TRADITIONAL_CHINESE,
                repository.load().windowSettings().interfaceLanguage());
        assertTrue(Files.readString(path).contains("interfaceLanguage: zh-TW"));
    }
}
