// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import com.ynpe.fastremote.domain.settings.TextAppearance;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** YAML mapping for Settings values. */
final class SettingsYamlValues {
    private SettingsYamlValues() {}

    static Map<String, Object> appearanceValues(TextAppearance value) {
        return Map.of("lineSpacing", value.lineSpacing(), "letterSpacing", value.letterSpacing());
    }

    static TextAppearance textAppearance(Object value, TextAppearance defaults) throws IOException {
        if (value == null) return defaults;
        if (!(value instanceof Map<?, ?> raw)) throw new IOException("Text appearance must be a mapping");
        @SuppressWarnings("unchecked") Map<String, Object> values = (Map<String, Object>) raw;
        // Old 1.2.2 bold preferences are deliberately ignored and disappear on the next save.
        return new TextAppearance(optionalSpacing(values, "lineSpacing", defaults.lineSpacing()),
                optionalSpacing(values, "letterSpacing", defaults.letterSpacing()));
    }

    static double optionalSpacing(Map<String, Object> values, String key, double fallback) throws IOException {
        Object value = values.get(key);
        if (value == null) return fallback;
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IOException("Text spacing must be a finite number");
        }
        return number.doubleValue();
    }

    static List<String> stringList(Object value, String name) throws IOException {
        if (value == null) return List.of();
        if (!(value instanceof List<?> list)) throw new IOException("SFTP setting '" + name + "' must be a list");
        List<String> result = new java.util.ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof String text)) throw new IOException("SFTP setting '" + name + "' must contain strings");
            result.add(text);
        }
        return List.copyOf(result);
    }

    static int optionalInteger(Map<String, Object> values, String key, int fallback) throws IOException {
        Object value = values.get(key);
        if (value == null) return fallback;
        if (value instanceof Number number) return number.intValue();
        throw new IOException("Global setting '" + key + "' must be an integer");
    }

    static long optionalLong(Map<String, Object> values, String key, long fallback) throws IOException {
        Object value = values.get(key);
        if (value == null) return fallback;
        if (value instanceof Number number) return number.longValue();
        throw new IOException("Global setting '" + key + "' must be an integer");
    }

    static String optionalString(Map<String, Object> values, String key, String fallback) throws IOException {
        Object value = values.get(key);
        if (value == null) return fallback;
        if (value instanceof String text && !text.isBlank()) return text;
        throw new IOException("Global setting '" + key + "' must be a non-empty string");
    }

    static String optionalText(Map<String, Object> values, String key) throws IOException {
        Object value = values.get(key);
        if (value == null) return "";
        if (value instanceof String text) return text;
        throw new IOException("Global setting '" + key + "' must be a string");
    }

    static String string(Map<String, Object> values, String key) throws IOException {
        Object value = values.get(key);
        if (value instanceof String text && !text.isBlank()) return text;
        throw new IOException("Global setting '" + key + "' must be a non-empty string");
    }

    static int integer(Map<String, Object> values, String key) throws IOException {
        Object value = values.get(key);
        if (value instanceof Number number) return number.intValue();
        throw new IOException("Global setting '" + key + "' must be an integer");
    }

    static <E extends Enum<E>> E enumValue(
            Map<String, Object> values, String key, Class<E> type) throws IOException {
        Object value = values.get(key);
        if (value instanceof String text) {
            try {
                return Enum.valueOf(type, text);
            } catch (IllegalArgumentException invalid) {
                throw new IOException("Global setting '" + key + "' has an invalid value", invalid);
            }
        }
        throw new IOException("Global setting '" + key + "' must be a string");
    }

    static boolean bool(Map<String, Object> values, String key) throws IOException {
        Object value = values.get(key);
        if (value instanceof Boolean bool) return bool;
        throw new IOException("Global setting '" + key + "' must be true or false");
    }

    static boolean optionalBool(
            Map<String, Object> values, String key, boolean defaultValue) throws IOException {
        if (!values.containsKey(key)) return defaultValue;
        return bool(values, key);
    }

    static double number(Map<String, Object> values, String key) throws IOException {
        Object value = values.get(key);
        if (value instanceof Number number) {
            double result = number.doubleValue();
            if (Double.isFinite(result) && result > 0.0) return result;
        }
        throw new IOException("Global setting '" + key + "' must be a positive finite number");
    }

    static double finiteNumber(Map<String, Object> values, String key) throws IOException {
        Object value = values.get(key);
        if (value instanceof Number number) {
            double result = number.doubleValue();
            if (Double.isFinite(result)) return result;
        }
        throw new IOException("Global setting '" + key + "' must be a finite number");
    }
}
