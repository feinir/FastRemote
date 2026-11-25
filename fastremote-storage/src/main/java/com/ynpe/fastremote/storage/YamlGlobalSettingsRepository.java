// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import com.ynpe.fastremote.domain.settings.GlobalSettings;
import com.ynpe.fastremote.domain.settings.GlobalSettingsRepository;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;

public final class YamlGlobalSettingsRepository implements GlobalSettingsRepository {
    private final YamlGlobalConfigStore store;

    public YamlGlobalSettingsRepository(YamlGlobalConfigStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public GlobalSettings load() throws IOException {
        return decodeSettings(YamlGlobalConfigStore.requireMap(store.read(), "settings"));
    }

    public static GlobalSettings decodeSettings(Map<String, Object> settings) throws IOException {
        return GlobalSettingsCodec.decodeSettings(settings);
    }

    @Override
    public void save(GlobalSettings settings) throws IOException {
        Objects.requireNonNull(settings, "settings");
        try {
            store.update(document -> {
                try {
                    YamlGlobalConfigStore.requireMap(document, "settings").putAll(encodeSettings(settings));
                    return null;
                } catch (IOException failure) {
                    throw new SettingsMutationException(failure);
                }
            });
        } catch (SettingsMutationException failure) {
            throw (IOException) failure.getCause();
        }
    }

    public static Map<String, Object> encodeSettings(GlobalSettings settings) {
        return GlobalSettingsCodec.encodeSettings(settings);
    }

    private static final class SettingsMutationException extends RuntimeException {
        private SettingsMutationException(IOException cause) {
            super(cause);
        }
    }
}
