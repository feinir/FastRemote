// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import com.ynpe.fastremote.domain.settings.WindowSettings;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Resolves one startup display scale from explicit desktop evidence before JavaFX starts. */
final class DesktopDisplayScale {
    static final String GTK_SCALE_PROPERTY = "glass.gtk.uiScale";
    static final String WINDOWS_UI_SCALE_PROPERTY = "glass.win.uiScale";
    static final String WINDOWS_RENDER_SCALE_PROPERTY = "glass.win.renderScale";
    private static final Duration PROBE_TIMEOUT = Duration.ofMillis(750);
    private static final Pattern NUMBER = Pattern.compile("(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern MONITOR_SCALE = Pattern.compile(
            "<scale>\\s*([0-9]+(?:\\.[0-9]+)?)\\s*</scale>", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOGICAL_MONITOR = Pattern.compile(
            "<logicalmonitor>(.*?)</logicalmonitor>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private DesktopDisplayScale() {
    }

    record Resolution(double factor, Source source, boolean forced) {
        Resolution {
            if (!valid(factor)) throw new IllegalArgumentException("Invalid display scale: " + factor);
        }
    }

    enum Source { FORCED_SETTING, GLASS_GTK, GNOME_MONITORS, XFT_DPI, GNOME, XFCE, KDE, JAVA2D, DEFAULT }

    static Resolution configureBeforeJavaFx(WindowSettings settings) {
        return configureBeforeJavaFx(settings, detectNativeScale());
    }

    static Resolution configureBeforeJavaFx(WindowSettings settings, Resolution nativeScale) {
        return configureForOs(settings, nativeScale, System.getProperty("os.name", ""));
    }

    static Resolution configureForOs(WindowSettings settings, Resolution nativeScale, String osName) {
        boolean linux = isLinux(osName);
        boolean windows = isWindows(osName);
        if (!linux && !windows) return new Resolution(1.0, Source.DEFAULT, false);

        if (settings.forceHiDpi()) {
            double forced = settings.hiDpiScalePercent() / 100.0;
            if (linux) System.setProperty(GTK_SCALE_PROPERTY, scaleText(forced));
            if (windows) {
                String percent = settings.hiDpiScalePercent() + "%";
                System.setProperty(WINDOWS_UI_SCALE_PROPERTY, percent);
                System.setProperty(WINDOWS_RENDER_SCALE_PROPERTY, percent);
            }
            return new Resolution(forced, Source.FORCED_SETTING, true);
        }

        Resolution resolved = nativeScale;
        if (linux) System.setProperty(GTK_SCALE_PROPERTY, scaleText(resolved.factor()));
        return resolved;
    }

    /** Detects the desktop's real scale without consulting the user's window override. */
    static Resolution detectNativeScale() {
        String osName = System.getProperty("os.name", "");
        if (isLinux(osName)) return inferLinuxScale();
        if (isWindows(osName)) return inferWindowsScale();
        return new Resolution(1.0, Source.DEFAULT, false);
    }

    private static Resolution inferLinuxScale() {
        OptionalDouble glass = parseScale(System.getProperty(GTK_SCALE_PROPERTY));
        if (glass.isPresent()) return resolution(glass.getAsDouble(), Source.GLASS_GTK);

        OptionalDouble monitors = monitorsXmlScale(Path.of(System.getProperty("user.home", "."), ".config", "monitors.xml"));
        if (monitors.isPresent()) return resolution(monitors.getAsDouble(), Source.GNOME_MONITORS);

        OptionalDouble xft = xftDpiScale(firstNonBlank(
                System.getProperty("Xft.dpi"), System.getenv("XFT_DPI"), command("xrdb", "-query")));
        if (xft.isPresent()) return resolution(xft.getAsDouble(), Source.XFT_DPI);

        OptionalDouble gnome = parseFirstNumber(command(
                "gsettings", "get", "org.gnome.desktop.interface", "scaling-factor"));
        if (gnome.isPresent() && gnome.getAsDouble() > 0.0) return resolution(gnome.getAsDouble(), Source.GNOME);

        OptionalDouble xfce = xftDpiScale(command(
                "xfconf-query", "-c", "xsettings", "-p", "/Xft/DPI"));
        if (xfce.isPresent()) return resolution(xfce.getAsDouble(), Source.XFCE);

        OptionalDouble kde = kdeScale();
        if (kde.isPresent()) return resolution(kde.getAsDouble(), Source.KDE);

        OptionalDouble java2d = java2dScale();
        return java2d.isPresent()
                ? resolution(java2d.getAsDouble(), Source.JAVA2D)
                : new Resolution(1.0, Source.DEFAULT, false);
    }

    private static Resolution inferWindowsScale() {
        OptionalDouble java2d = java2dScale();
        return java2d.isPresent()
                ? resolution(java2d.getAsDouble(), Source.JAVA2D)
                : new Resolution(1.0, Source.DEFAULT, false);
    }

    static OptionalDouble monitorsXmlScale(Path path) {
        try {
            String xml = Files.readString(path, StandardCharsets.UTF_8);
            Matcher logicalMonitors = LOGICAL_MONITOR.matcher(xml);
            while (logicalMonitors.find()) {
                String monitor = logicalMonitors.group(1);
                if (Pattern.compile("<primary>\\s*yes\\s*</primary>", Pattern.CASE_INSENSITIVE)
                        .matcher(monitor).find()) {
                    Matcher scale = MONITOR_SCALE.matcher(monitor);
                    if (scale.find()) return parseScale(scale.group(1));
                }
            }
            Matcher scales = MONITOR_SCALE.matcher(xml);
            List<Double> values = new ArrayList<>();
            while (scales.find()) parseScale(scales.group(1)).ifPresent(values::add);
            if (values.isEmpty()) return OptionalDouble.empty();
            double first = values.getFirst();
            return values.stream().allMatch(value -> Math.abs(value - first) < 0.001)
                    ? OptionalDouble.of(first) : OptionalDouble.empty();
        } catch (IOException | RuntimeException ignored) {
            return OptionalDouble.empty();
        }
    }

    static OptionalDouble parseScale(String value) {
        if (value == null || value.isBlank()) return OptionalDouble.empty();
        String text = value.trim().toLowerCase(Locale.ROOT);
        double divisor = 1.0;
        if (text.endsWith("%")) divisor = 100.0;
        else if (text.endsWith("dpi")) divisor = 96.0;
        Matcher matcher = NUMBER.matcher(text);
        if (!matcher.find()) return OptionalDouble.empty();
        try {
            double scale = Double.parseDouble(matcher.group(1)) / divisor;
            return valid(scale) ? OptionalDouble.of(scale) : OptionalDouble.empty();
        } catch (NumberFormatException ignored) {
            return OptionalDouble.empty();
        }
    }

    static OptionalDouble xftDpiScale(String value) {
        if (value == null || value.isBlank()) return OptionalDouble.empty();
        Pattern pattern = value.toLowerCase(Locale.ROOT).contains("xft.dpi")
                ? Pattern.compile("(?im)^\\s*Xft\\.dpi\\s*[:=]\\s*([0-9]+(?:\\.[0-9]+)?)\\s*$")
                : Pattern.compile("^\\s*([0-9]+(?:\\.[0-9]+)?)\\s*$");
        Matcher matcher = pattern.matcher(value);
        if (!matcher.find()) return OptionalDouble.empty();
        try {
            double dpi = Double.parseDouble(matcher.group(1));
            double scale = dpi / 96.0;
            return valid(scale) ? OptionalDouble.of(scale) : OptionalDouble.empty();
        } catch (NumberFormatException ignored) {
            return OptionalDouble.empty();
        }
    }

    private static OptionalDouble kdeScale() {
        for (String candidate : List.of(
                valueOrEmpty(System.getenv("QT_SCALE_FACTOR")),
                valueOrEmpty(System.getenv("QT_SCREEN_SCALE_FACTORS")))) {
            OptionalDouble parsed = parseScale(candidate);
            if (parsed.isPresent()) return parsed;
        }
        Path globals = Path.of(System.getProperty("user.home", "."), ".config", "kdeglobals");
        try {
            for (String line : Files.readAllLines(globals, StandardCharsets.UTF_8)) {
                if (line.trim().startsWith("ScaleFactor=")) {
                    OptionalDouble parsed = parseScale(line.substring(line.indexOf('=') + 1));
                    if (parsed.isPresent()) return parsed;
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // KDE evidence is optional.
        }
        OptionalDouble kwin = scalesInJsonFile(Path.of(System.getProperty("user.home", "."),
                ".config", "kwinoutputconfig.json"));
        if (kwin.isPresent()) return kwin;
        Path kscreen = Path.of(System.getProperty("user.home", "."), ".local", "share", "kscreen");
        try (Stream<Path> files = Files.list(kscreen)) {
            List<Double> values = new ArrayList<>();
            files.filter(Files::isRegularFile).limit(64).forEach(path ->
                    scalesInJsonFile(path).ifPresent(values::add));
            return oneUnambiguousScale(values);
        } catch (IOException | RuntimeException ignored) {
            return OptionalDouble.empty();
        }
    }

    private static OptionalDouble scalesInJsonFile(Path path) {
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            Matcher matcher = Pattern.compile("\\\"scale\\\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)")
                    .matcher(json);
            List<Double> values = new ArrayList<>();
            while (matcher.find()) parseScale(matcher.group(1)).ifPresent(values::add);
            return oneUnambiguousScale(values);
        } catch (IOException | RuntimeException ignored) {
            return OptionalDouble.empty();
        }
    }

    private static OptionalDouble oneUnambiguousScale(List<Double> values) {
        if (values.isEmpty()) return OptionalDouble.empty();
        double first = values.getFirst();
        return values.stream().allMatch(value -> Math.abs(value - first) < 0.001)
                ? OptionalDouble.of(first) : OptionalDouble.empty();
    }

    private static OptionalDouble java2dScale() {
        try {
            double scale = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration().getDefaultTransform().getScaleX();
            return valid(scale) ? OptionalDouble.of(scale) : OptionalDouble.empty();
        } catch (RuntimeException | LinkageError unavailable) {
            return OptionalDouble.empty();
        }
    }

    private static OptionalDouble parseFirstNumber(String value) {
        return parseScale(value);
    }

    private static String command(String... command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(PROBE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return "";
            }
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException | InterruptedException | RuntimeException ignored) {
            if (ignored instanceof InterruptedException) Thread.currentThread().interrupt();
            return "";
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "";
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static Resolution resolution(double factor, Source source) {
        return new Resolution(factor, source, false);
    }

    private static boolean valid(double scale) {
        return Double.isFinite(scale) && scale >= 1.0 && scale <= 4.0;
    }

    private static String scaleText(double scale) {
        return Double.toString(scale);
    }

    static boolean isLinux(String osName) {
        return osName.toLowerCase(Locale.ROOT).contains("linux");
    }

    static boolean isWindows(String osName) {
        return osName.toLowerCase(Locale.ROOT).contains("win");
    }
}
