// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.net.URL;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import javafx.scene.Parent;
import javafx.scene.text.Font;

/** Registers and selects FastRemote's bundled interface fonts. */
public final class InterfaceFontPolicy {
    private static final String APPLIED_DECLARATION_KEY =
            InterfaceFontPolicy.class.getName() + ".fontDeclaration";
    static final String HARMONY_SC_FAMILY = "HarmonyOS Sans SC";
    static final String HARMONY_TC_FAMILY = "HarmonyOS Sans TC";
    static final String NOTO_JP_FAMILY = "Noto Sans CJK JP";
    static final String NOTO_KR_FAMILY = "Noto Sans CJK KR";
    static final String NOTO_THAI_FAMILY = "Noto Sans Thai";
    private static final String FONT_ROOT = "/com/ynpe/fastremote/terminal/fonts/";
    private static final Set<String> loadedFontFamilies = new HashSet<>();

    private InterfaceFontPolicy() {}

    public static void apply(Parent root) {
        Objects.requireNonNull(root, "root");
        root.getStyleClass().removeIf(style -> style.startsWith("locale-")
                || style.startsWith("platform-"));
        root.getStyleClass().add(platformStyleClass(System.getProperty("os.name", "")));
        String languageTag = com.ynpe.fastremote.i18n.Texts.localizer().resolvedLanguage()
                .persistedTag().toLowerCase(Locale.ROOT);
        root.getStyleClass().add("locale-" + languageTag);
        String family = interfaceFontFamily(languageTag);
        loadBundledFont(family);
        applyFontFamily(root, family);
    }

    static String platformStyleClass(String osName) {
        String normalized = Objects.requireNonNull(osName, "osName").toLowerCase(Locale.ROOT);
        if (normalized.contains("mac")) return "platform-macos";
        if (normalized.contains("win")) return "platform-windows";
        return "platform-linux";
    }

    static String interfaceFontFamily(String languageTag) {
        return switch (Objects.requireNonNull(languageTag, "languageTag").toLowerCase(Locale.ROOT)) {
            case "zh-tw" -> HARMONY_TC_FAMILY;
            case "ja" -> NOTO_JP_FAMILY;
            case "ko" -> NOTO_KR_FAMILY;
            case "th" -> NOTO_THAI_FAMILY;
            default -> HARMONY_SC_FAMILY;
        };
    }

    static synchronized void loadBundledFonts() {
        loadBundledFont(HARMONY_SC_FAMILY);
        loadBundledFont(HARMONY_TC_FAMILY);
        loadBundledFont(NOTO_JP_FAMILY);
        loadBundledFont(NOTO_KR_FAMILY);
        loadBundledFont(NOTO_THAI_FAMILY);
    }

    static synchronized void loadBundledFont(String family) {
        if (loadedFontFamilies.contains(family)) return;
        loadFont(fontFileName(family), family);
        loadedFontFamilies.add(family);
    }

    static String fontFileName(String family) {
        return switch (Objects.requireNonNull(family, "family")) {
            case HARMONY_SC_FAMILY -> "HarmonyOS_Sans_SC.ttf";
            case HARMONY_TC_FAMILY -> "HarmonyOS_Sans_TC.ttf";
            case NOTO_JP_FAMILY -> "NotoSansCJKjp-Regular.otf";
            case NOTO_KR_FAMILY -> "NotoSansCJKkr-Regular.otf";
            case NOTO_THAI_FAMILY -> "NotoSansThai-Regular.ttf";
            default -> throw new IllegalArgumentException("Unsupported interface font family: " + family);
        };
    }

    private static void loadFont(String fileName, String expectedFamily) {
        URL resource = InterfaceFontPolicy.class.getResource(FONT_ROOT + fileName);
        if (resource == null) throw new IllegalStateException("Bundled interface font is missing: " + fileName);
        Font font = Font.loadFont(resource.toExternalForm(), 14.0);
        if (font == null || !expectedFamily.equals(font.getFamily())) {
            throw new IllegalStateException("Unable to register bundled interface font: " + expectedFamily);
        }
    }

    private static void applyFontFamily(Parent root, String family) {
        Object previous = root.getProperties().remove(APPLIED_DECLARATION_KEY);
        String current = root.getStyle() == null ? "" : root.getStyle();
        if (previous instanceof String declaration) current = current.replace(declaration, "");
        String declaration = "-fx-font-family: \"" + family.replace("\"", "") + "\";";
        root.getProperties().put(APPLIED_DECLARATION_KEY, declaration);
        root.setStyle((current.strip() + " " + declaration).strip());
    }
}
