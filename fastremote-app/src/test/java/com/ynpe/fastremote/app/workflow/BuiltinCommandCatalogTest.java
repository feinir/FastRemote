// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.settings.InterfaceLanguage;
import com.ynpe.fastremote.domain.snippet.SnippetTemplateEngine;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuiltinCommandCatalogTest {
    @Test
    void everyPublishedLanguageHasTheSameStableIdsOrderAndCommandSemantics() throws Exception {
        var reference = read(InterfaceLanguage.SIMPLIFIED_CHINESE);
        for (InterfaceLanguage language : InterfaceLanguage.publishedLanguages()) {
            var candidate = read(language);
            assertEquals(reference.groups().stream().map(SnippetLibraryTextCodec.BuiltinGroupLine::builtinId).toList(),
                    candidate.groups().stream().map(SnippetLibraryTextCodec.BuiltinGroupLine::builtinId).toList(),
                    language.persistedTag() + " group IDs");
            assertEquals(reference.commands().stream().map(SnippetLibraryTextCodec.BuiltinCommandLine::builtinId).toList(),
                    candidate.commands().stream().map(SnippetLibraryTextCodec.BuiltinCommandLine::builtinId).toList(),
                    language.persistedTag() + " command IDs");
            assertEquals(reference.commands().stream().map(SnippetLibraryTextCodec.BuiltinCommandLine::groupBuiltinId).toList(),
                    candidate.commands().stream().map(SnippetLibraryTextCodec.BuiltinCommandLine::groupBuiltinId).toList(),
                    language.persistedTag() + " group references");
            assertEquals(reference.commands().stream().map(command -> semanticTemplate(command.content())).toList(),
                    candidate.commands().stream().map(command -> semanticTemplate(command.content())).toList(),
                    language.persistedTag() + " command bodies");
            SnippetTemplateEngine engine = new SnippetTemplateEngine();
            candidate.commands().forEach(command -> assertDoesNotThrow(
                    () -> engine.analyze(command.content()),
                    language.persistedTag() + ":" + command.builtinId()));
        }
    }

    @Test
    void englishCatalogHasNoChineseUserFacingText() throws Exception {
        var english = read(InterfaceLanguage.ENGLISH);
        List<String> text = java.util.stream.Stream.concat(
                english.groups().stream().map(SnippetLibraryTextCodec.BuiltinGroupLine::name),
                english.commands().stream().flatMap(command -> java.util.stream.Stream.of(
                        command.name(), command.description(), inputLabels(command.content())))).toList();
        assertTrue(text.stream().noneMatch(value -> value.matches(".*[\\u3400-\\u9fff].*")));
    }

    @Test
    void everyLanguageKeepsOneConsistentTopLevelGroupNamingPattern() throws Exception {
        var expected = java.util.Map.ofEntries(
                java.util.Map.entry(InterfaceLanguage.ENGLISH,
                        List.of("Linux Essentials", "Windows Essentials", "macOS Essentials")),
                java.util.Map.entry(InterfaceLanguage.SIMPLIFIED_CHINESE,
                        List.of("Linux常用", "Windows常用", "macOS常用")),
                java.util.Map.entry(InterfaceLanguage.TRADITIONAL_CHINESE,
                        List.of("Linux 常用", "Windows 常用", "macOS 常用")),
                java.util.Map.entry(InterfaceLanguage.JAPANESE,
                        List.of("Linux 基本コマンド", "Windows 基本コマンド", "macOS 基本コマンド")),
                java.util.Map.entry(InterfaceLanguage.KOREAN,
                        List.of("Linux 필수 명령", "Windows 필수 명령", "macOS 필수 명령")),
                java.util.Map.entry(InterfaceLanguage.SPANISH,
                        List.of("Esenciales de Linux", "Esenciales de Windows", "Esenciales de macOS")),
                java.util.Map.entry(InterfaceLanguage.BRAZILIAN_PORTUGUESE,
                        List.of("Essenciais do Linux", "Essenciais do Windows", "Essenciais do macOS")),
                java.util.Map.entry(InterfaceLanguage.THAI,
                        List.of("คำสั่งพื้นฐานสำหรับ Linux", "คำสั่งพื้นฐานสำหรับ Windows", "คำสั่งพื้นฐานสำหรับ macOS")),
                java.util.Map.entry(InterfaceLanguage.FRENCH,
                        List.of("Essentiels Linux", "Essentiels Windows", "Essentiels macOS")),
                java.util.Map.entry(InterfaceLanguage.ITALIAN,
                        List.of("Essenziali di Linux", "Essenziali Windows", "Essenziali macOS")),
                java.util.Map.entry(InterfaceLanguage.GERMAN,
                        List.of("Linux-Grundlagen", "Windows-Grundlagen", "macOS-Grundlagen")),
                java.util.Map.entry(InterfaceLanguage.RUSSIAN,
                        List.of("Основные команды Linux", "Основные команды Windows", "Основные команды macOS")));
        for (InterfaceLanguage language : InterfaceLanguage.publishedLanguages()) {
            assertEquals(expected.get(language), read(language).groups().stream()
                    .map(SnippetLibraryTextCodec.BuiltinGroupLine::name)
                    .toList(), language.persistedTag());
        }
    }

    @Test
    void publishedCatalogCopyDoesNotEndWithHardcodedEllipses() throws Exception {
        for (InterfaceLanguage language : InterfaceLanguage.publishedLanguages()) {
            var catalog = read(language);
            var visibleCopy = java.util.stream.Stream.concat(
                    catalog.groups().stream().map(SnippetLibraryTextCodec.BuiltinGroupLine::name),
                    catalog.commands().stream().flatMap(command -> java.util.stream.Stream.concat(
                            java.util.stream.Stream.of(command.name(), command.description()),
                            inputLabelList(command.content()).stream()))).toList();
            visibleCopy.forEach(value -> {
                String stripped = value.strip();
                assertFalse(stripped.endsWith("…") || stripped.endsWith("..."),
                        language.persistedTag() + ":" + value);
            });
        }
    }

    private static SnippetLibraryTextCodec.BuiltinDecoded read(InterfaceLanguage language) throws Exception {
        String name = "/com/ynpe/fastremote/app/defaults/commands_"
                + language.persistedTag().replace('-', '_') + ".tsv";
        URL resource = BuiltinCommandCatalogTest.class.getResource(name);
        assertNotNull(resource, "Missing published command catalog " + name);
        try (var input = resource.openStream()) {
            return SnippetLibraryTextCodec.decodeBuiltin(
                    new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().toList());
        }
    }

    private static String semanticTemplate(String content) {
        return content.replaceAll("\\{\\{input,[^,}]+(?=[,}])", "{{input,<localized-label>");
    }

    private static String inputLabels(String content) {
        var labels = new StringBuilder();
        var matcher = java.util.regex.Pattern.compile("\\{\\{input,([^,}]+)").matcher(content);
        while (matcher.find()) labels.append(matcher.group(1));
        return labels.toString();
    }

    private static List<String> inputLabelList(String content) {
        var labels = new java.util.ArrayList<String>();
        var matcher = java.util.regex.Pattern.compile("\\{\\{input,([^,}]+)").matcher(content);
        while (matcher.find()) labels.add(matcher.group(1));
        return List.copyOf(labels);
    }
}
