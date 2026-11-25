// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.terminal;

import java.net.URL;
import java.util.Objects;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;

/** Registers the redistributable interface, Unicode fallback, and terminal fonts bundled with FastRemote. */
public final class BundledFontRegistry {
    public static final String HARMONY_OS_SANS_FAMILY = "HarmonyOS Sans SC";
    public static final String HARMONY_OS_SANS_TC_FAMILY = "HarmonyOS Sans TC";
    public static final String NOTO_SANS_CJK_JP_FAMILY = "Noto Sans CJK JP";
    public static final String NOTO_SANS_CJK_KR_FAMILY = "Noto Sans CJK KR";
    public static final String NOTO_SANS_THAI_FAMILY = "Noto Sans Thai";
    public static final String BUNDLED_MONOSPACE_FAMILY = "JetBrains Mono NL";
    private static final String HARMONY_FONT_RESOURCE = "/com/ynpe/fastremote/terminal/fonts/HarmonyOS_Sans_SC.ttf";
    private static final String HARMONY_TC_FONT_RESOURCE = "/com/ynpe/fastremote/terminal/fonts/HarmonyOS_Sans_TC.ttf";
    private static final String NOTO_JP_FONT_RESOURCE = "/com/ynpe/fastremote/terminal/fonts/NotoSansCJKjp-Regular.otf";
    private static final String NOTO_KR_FONT_RESOURCE = "/com/ynpe/fastremote/terminal/fonts/NotoSansCJKkr-Regular.otf";
    private static final String NOTO_THAI_FONT_RESOURCE = "/com/ynpe/fastremote/terminal/fonts/NotoSansThai-Regular.ttf";
    private static final String MONOSPACE_FONT_RESOURCE = "/com/ynpe/fastremote/terminal/fonts/JetBrainsMonoNL-Regular.ttf";
    private static boolean loaded;

    private BundledFontRegistry() {}

    public static synchronized void load() {
        if (loaded) return;
        loadFont(HARMONY_FONT_RESOURCE, HARMONY_OS_SANS_FAMILY);
        loadFont(HARMONY_TC_FONT_RESOURCE, HARMONY_OS_SANS_TC_FAMILY);
        loadFont(NOTO_JP_FONT_RESOURCE, NOTO_SANS_CJK_JP_FAMILY);
        loadFont(NOTO_KR_FONT_RESOURCE, NOTO_SANS_CJK_KR_FAMILY);
        loadFont(NOTO_THAI_FONT_RESOURCE, NOTO_SANS_THAI_FAMILY);
        loadFont(MONOSPACE_FONT_RESOURCE, BUNDLED_MONOSPACE_FAMILY);
        loaded = true;
    }

    private static void loadFont(String resourcePath, String expectedFamily) {
        URL resource = BundledFontRegistry.class.getResource(resourcePath);
        if (resource == null) throw new IllegalStateException("Bundled font is missing: " + resourcePath);
        Font font = Font.loadFont(resource.toExternalForm(), 14.0);
        if (font == null || !expectedFamily.equals(font.getFamily())) {
            throw new IllegalStateException("Unable to register bundled font family: " + expectedFamily);
        }
    }

    public static Font harmonyFont(double size) {
        load();
        return Font.font(harmonyFamily(), size);
    }

    public static Font harmonyFont(double size, FontWeight weight, FontPosture posture) {
        load();
        return Font.font(harmonyFamily(),
                Objects.requireNonNull(weight, "weight"), Objects.requireNonNull(posture, "posture"), size);
    }

    public static String harmonyFamily() {
        return switch (com.ynpe.fastremote.i18n.Texts.localizer().resolvedLanguage()) {
            case TRADITIONAL_CHINESE -> HARMONY_OS_SANS_TC_FAMILY;
            case JAPANESE -> NOTO_SANS_CJK_JP_FAMILY;
            case KOREAN -> NOTO_SANS_CJK_KR_FAMILY;
            case THAI -> NOTO_SANS_THAI_FAMILY;
            default -> HARMONY_OS_SANS_FAMILY;
        };
    }

    public static String terminalFallbackFamily(int codePoint) {
        return switch (Character.UnicodeScript.of(codePoint)) {
            case THAI -> NOTO_SANS_THAI_FAMILY;
            case HANGUL -> NOTO_SANS_CJK_KR_FAMILY;
            case HIRAGANA, KATAKANA -> NOTO_SANS_CJK_JP_FAMILY;
            case HAN -> switch (com.ynpe.fastremote.i18n.Texts.localizer().resolvedLanguage()) {
                case TRADITIONAL_CHINESE -> HARMONY_OS_SANS_TC_FAMILY;
                case JAPANESE -> NOTO_SANS_CJK_JP_FAMILY;
                case KOREAN -> NOTO_SANS_CJK_KR_FAMILY;
                default -> HARMONY_OS_SANS_FAMILY;
            };
            default -> HARMONY_OS_SANS_FAMILY;
        };
    }

    public static Font bundledMonospaceFont(double size) {
        load();
        return Font.font(BUNDLED_MONOSPACE_FAMILY, size);
    }

    /** Uses an explicitly configured installed font, otherwise the bundled terminal monospace font. */
    public static Font terminalPrimaryFont(String family, double size) {
        load();
        String requested = Objects.requireNonNull(family, "family").strip();
        return Font.getFamilies().stream().anyMatch(requested::equalsIgnoreCase)
                ? Font.font(requested, size)
                : bundledMonospaceFont(size);
    }
}
