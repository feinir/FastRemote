// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.i18n;

import com.ynpe.fastremote.domain.settings.InterfaceLanguage;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;

/** Immutable application-localization context created once for each application run. */
public final class Localizer {
    public static final String BUNDLE_NAME = "com.ynpe.fastremote.i18n.messages";

    private final InterfaceLanguage preference;
    private final InterfaceLanguage resolvedLanguage;
    private final Locale locale;
    private final ResourceBundle bundle;

    private Localizer(InterfaceLanguage preference) {
        this.preference = Objects.requireNonNull(preference, "preference");
        resolvedLanguage = preference.resolved();
        locale = resolvedLanguage.locale();
        bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale, ResourceBundle.Control.getNoFallbackControl(
                ResourceBundle.Control.FORMAT_DEFAULT));
    }

    public static Localizer forLanguage(InterfaceLanguage language) {
        return new Localizer(language);
    }

    public static Localizer systemDefault() {
        return new Localizer(InterfaceLanguage.SYSTEM);
    }

    public InterfaceLanguage preference() {
        return preference;
    }

    public InterfaceLanguage resolvedLanguage() {
        return resolvedLanguage;
    }

    public Locale locale() {
        return locale;
    }

    public ResourceBundle bundle() {
        return bundle;
    }

    public String text(String key, Object... arguments) {
        Objects.requireNonNull(key, "key");
        String pattern;
        try {
            pattern = bundle.getString(key);
        } catch (MissingResourceException missing) {
            ResourceBundle english = ResourceBundle.getBundle(BUNDLE_NAME, Locale.ENGLISH);
            pattern = english.getString(key);
        }
        return arguments.length == 0 ? pattern : new MessageFormat(pattern, locale).format(arguments);
    }

    /** Translates known lower-layer message fragments at an outer user/protocol boundary. */
    public String translateExternalText(String value) {
        if (value == null || value.isBlank()) return value;
        StringBuilder translated = new StringBuilder(value.length());
        for (int index = 0; index < value.length();) {
            ExternalTextCatalog.Entry match = null;
            for (ExternalTextCatalog.Entry candidate : ExternalTextCatalog.ENTRIES) {
                if (value.startsWith(candidate.source(), index)) {
                    match = candidate;
                    break;
                }
            }
            if (match == null) {
                int codePoint = value.codePointAt(index);
                translated.appendCodePoint(codePoint);
                index += Character.charCount(codePoint);
            } else {
                translated.append(text(match.key()));
                index += match.source().length();
            }
        }
        return translated.toString();
    }

    public String formatNumber(long value) {
        return NumberFormat.getIntegerInstance(locale).format(value);
    }

    public String formatDateTime(Instant value, ZoneId zone) {
        Objects.requireNonNull(value, "value");
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withLocale(locale).withZone(Objects.requireNonNull(zone, "zone")).format(value);
    }

    public String formatDateTime(LocalDateTime value) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withLocale(locale).format(Objects.requireNonNull(value, "value"));
    }

    public String formatTime(LocalTime value) {
        return DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM)
                .withLocale(locale).format(Objects.requireNonNull(value, "value"));
    }

    public String formatBytes(long bytes) {
        long safe = Math.max(0L, bytes);
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        double value = safe;
        int unit = 0;
        while (value >= 1024.0 && unit < units.length - 1) {
            value /= 1024.0;
            unit++;
        }
        NumberFormat number = NumberFormat.getNumberInstance(locale);
        number.setMinimumFractionDigits(unit == 0 ? 0 : 1);
        number.setMaximumFractionDigits(unit == 0 ? 0 : 1);
        return number.format(value) + " " + units[unit];
    }

    public String formatDuration(Duration duration) {
        long milliseconds = Math.max(0L, Objects.requireNonNull(duration, "duration").toMillis());
        if (milliseconds < 1_000L) return text("duration.milliseconds", milliseconds);
        NumberFormat seconds = NumberFormat.getNumberInstance(locale);
        seconds.setMinimumFractionDigits(2);
        seconds.setMaximumFractionDigits(2);
        return text("duration.seconds", seconds.format(milliseconds / 1_000.0));
    }

    public String formatClockDuration(Duration duration) {
        long totalSeconds = Math.max(0L, Objects.requireNonNull(duration, "duration").toSeconds());
        NumberFormat field = NumberFormat.getIntegerInstance(locale);
        field.setGroupingUsed(false);
        field.setMinimumIntegerDigits(2);
        return field.format(totalSeconds / 3_600L) + ":"
                + field.format(totalSeconds / 60L % 60L) + ":" + field.format(totalSeconds % 60L);
    }
}
