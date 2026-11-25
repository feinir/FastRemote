// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.settings;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Persisted interface-language preference; {@link #SYSTEM} resolves at application startup. */
public enum InterfaceLanguage {
    SYSTEM("system", true),
    ENGLISH("en", true),
    SIMPLIFIED_CHINESE("zh-CN", true),
    TRADITIONAL_CHINESE("zh-TW", true),
    JAPANESE("ja", true),
    KOREAN("ko", true),
    SPANISH("es", true),
    BRAZILIAN_PORTUGUESE("pt-BR", true),
    THAI("th", true),
    FRENCH("fr", true),
    ITALIAN("it", true),
    GERMAN("de", true),
    RUSSIAN("ru", true);

    private final String persistedTag;
    private final boolean published;

    InterfaceLanguage(String persistedTag, boolean published) {
        this.persistedTag = persistedTag;
        this.published = published;
    }

    public String persistedTag() {
        return persistedTag;
    }

    public boolean published() {
        return published;
    }

    public static List<InterfaceLanguage> publishedPreferences() {
        return Arrays.stream(values()).filter(InterfaceLanguage::published).toList();
    }

    public static List<InterfaceLanguage> publishedLanguages() {
        return Arrays.stream(values()).filter(language -> language != SYSTEM && language.published).toList();
    }

    public Locale locale() {
        return this == SYSTEM ? resolveSystem(Locale.getDefault()).locale() : Locale.forLanguageTag(persistedTag);
    }

    public InterfaceLanguage resolved() {
        return this == SYSTEM ? resolveSystem(Locale.getDefault()) : this;
    }

    public static InterfaceLanguage fromPersistedTag(String value) {
        String tag = Objects.requireNonNullElse(value, "system").strip();
        return Arrays.stream(values()).filter(item -> item.published && item.persistedTag.equalsIgnoreCase(tag))
                .findFirst().orElse(SYSTEM);
    }

    public static InterfaceLanguage resolveSystem(Locale locale) {
        Locale value = Objects.requireNonNull(locale, "locale");
        String language = value.getLanguage();
        if (Locale.CHINESE.getLanguage().equals(language)) {
            String script = value.getScript();
            String country = value.getCountry();
            return "Hant".equalsIgnoreCase(script) || "TW".equalsIgnoreCase(country)
                    || "HK".equalsIgnoreCase(country) || "MO".equalsIgnoreCase(country)
                    ? TRADITIONAL_CHINESE : SIMPLIFIED_CHINESE;
        }
        return Arrays.stream(values())
                .filter(item -> item != SYSTEM && item.published && item.locale().getLanguage().equals(language))
                .findFirst().orElse(ENGLISH);
    }
}
