// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.settings.InterfaceLanguage;
import java.util.Locale;
import java.util.List;
import java.text.MessageFormat;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import java.util.Map;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LocalizerTest {
    @Test
    void japaneseUsesTheConventionalOkLabelAndACompactSecurityPageTitle() {
        Localizer japanese = Localizer.forLanguage(InterfaceLanguage.JAPANESE);
        assertEquals("OK", japanese.text("dialog.ok"));
        assertEquals("セキュリティと認証情報", japanese.text("legacy.java.f0018b2e95040e8a"));
    }

    @Test
    void publishedTranslationsKeepCoreUiTermsAndMessagesMeaningful() {
        assertEquals("SSH and SFTP client", Localizer.forLanguage(InterfaceLanguage.ENGLISH)
                .text("about.description"));
        assertEquals("SSH 和 SFTP 客户端", Localizer.forLanguage(InterfaceLanguage.SIMPLIFIED_CHINESE)
                .text("about.description"));
        assertEquals("Russo", Localizer.forLanguage(InterfaceLanguage.BRAZILIAN_PORTUGUESE)
                .text("language.ru"));
        assertEquals("いいえ", Localizer.forLanguage(InterfaceLanguage.JAPANESE).text("dialog.no"));
        assertEquals("Il y a {0} mois", Localizer.forLanguage(InterfaceLanguage.FRENCH)
                .text("ai.history.months"));
        assertEquals("{0} anni fa", Localizer.forLanguage(InterfaceLanguage.ITALIAN)
                .text("ai.history.years"));
        assertEquals("{0} e {1} usam o mesmo atalho",
                Localizer.forLanguage(InterfaceLanguage.BRAZILIAN_PORTUGUESE)
                        .text("shortcut.conflict"));
    }

    @Test
    void everyPublishedLanguageUsesReviewedNewHostCopy() {
        Map<InterfaceLanguage, List<String>> expected = Map.ofEntries(
                Map.entry(InterfaceLanguage.ENGLISH,
                        List.of("New Host", "New host", "Add a host to the Hosts Library")),
                Map.entry(InterfaceLanguage.SIMPLIFIED_CHINESE,
                        List.of("新建主机", "新建主机", "向主机库增加一台主机")),
                Map.entry(InterfaceLanguage.TRADITIONAL_CHINESE,
                        List.of("新增主機", "新增主機", "向主機庫新增一台主機")),
                Map.entry(InterfaceLanguage.JAPANESE,
                        List.of("新しいホスト", "新しいホスト", "ホストライブラリにホストを追加")),
                Map.entry(InterfaceLanguage.KOREAN,
                        List.of("새 호스트", "새 호스트", "호스트 라이브러리에 호스트 추가")),
                Map.entry(InterfaceLanguage.SPANISH,
                        List.of("Nuevo host", "Nuevo host", "Añadir un host a la biblioteca de hosts")),
                Map.entry(InterfaceLanguage.BRAZILIAN_PORTUGUESE,
                        List.of("Novo host", "Novo host", "Adicionar um host à biblioteca de hosts")),
                Map.entry(InterfaceLanguage.THAI,
                        List.of("โฮสต์ใหม่", "โฮสต์ใหม่", "เพิ่มโฮสต์ไปยังคลังโฮสต์")),
                Map.entry(InterfaceLanguage.FRENCH,
                        List.of("Nouvel hôte", "Nouvel hôte", "Ajouter un hôte à la bibliothèque d’hôtes")),
                Map.entry(InterfaceLanguage.ITALIAN,
                        List.of("Nuovo host", "Nuovo host", "Aggiungi un host alla libreria host")),
                Map.entry(InterfaceLanguage.GERMAN,
                        List.of("Neuer Host", "Neuer Host", "Einen Host zur Hostbibliothek hinzufügen")),
                Map.entry(InterfaceLanguage.RUSSIAN,
                        List.of("Новый хост", "Новый хост", "Добавить хост в библиотеку хостов")));

        for (InterfaceLanguage language : InterfaceLanguage.publishedLanguages()) {
            Localizer localizer = Localizer.forLanguage(language);
            assertEquals(expected.get(language), List.of(
                    localizer.text("action.newHost"),
                    localizer.text("legacy.java.22a3f0c4b4bd8606"),
                    localizer.text("hostEditor.createHeader")), language.persistedTag());
        }
    }

    @Test
    void everyPublishedLanguageNamesConfigurationImportsWithoutSessionTerminology() {
        Map<InterfaceLanguage, List<String>> expected = Map.ofEntries(
                Map.entry(InterfaceLanguage.ENGLISH,
                        List.of("Import Configuration", "Export Configuration", "FastRemote Configuration")),
                Map.entry(InterfaceLanguage.SIMPLIFIED_CHINESE,
                        List.of("导入配置", "导出配置", "FastRemote 配置")),
                Map.entry(InterfaceLanguage.TRADITIONAL_CHINESE,
                        List.of("匯入設定", "匯出設定", "FastRemote 設定")),
                Map.entry(InterfaceLanguage.JAPANESE,
                        List.of("設定をインポート", "設定をエクスポート", "FastRemote 設定")),
                Map.entry(InterfaceLanguage.KOREAN,
                        List.of("설정 가져오기", "설정 내보내기", "FastRemote 설정")),
                Map.entry(InterfaceLanguage.SPANISH,
                        List.of("Importar configuración", "Exportar configuración", "Configuración de FastRemote")),
                Map.entry(InterfaceLanguage.BRAZILIAN_PORTUGUESE,
                        List.of("Importar configuração", "Exportar configuração", "Configuração do FastRemote")),
                Map.entry(InterfaceLanguage.THAI,
                        List.of("นำเข้าการกำหนดค่า", "ส่งออกการกำหนดค่า", "การกำหนดค่า FastRemote")),
                Map.entry(InterfaceLanguage.FRENCH,
                        List.of("Importer la configuration", "Exporter la configuration", "Configuration FastRemote")),
                Map.entry(InterfaceLanguage.ITALIAN,
                        List.of("Importa configurazione", "Esporta configurazione", "Configurazione FastRemote")),
                Map.entry(InterfaceLanguage.GERMAN,
                        List.of("Konfiguration importieren", "Konfiguration exportieren", "FastRemote-Konfiguration")),
                Map.entry(InterfaceLanguage.RUSSIAN,
                        List.of("Импорт конфигурации", "Экспорт конфигурации", "Конфигурация FastRemote")));

        for (InterfaceLanguage language : InterfaceLanguage.publishedLanguages()) {
            Localizer localizer = Localizer.forLanguage(language);
            assertEquals(expected.get(language), List.of(
                    localizer.text("action.importHosts"),
                    localizer.text("action.exportHosts"),
                    localizer.text("import.fastremoteHosts")), language.persistedTag());
        }
    }

    @Test
    void everyPublishedLanguageUsesCertificateForQuickConnectionKeyMaterial() {
        Map<InterfaceLanguage, String> expected = Map.ofEntries(
                Map.entry(InterfaceLanguage.ENGLISH, "Certificate"),
                Map.entry(InterfaceLanguage.SIMPLIFIED_CHINESE, "证书"),
                Map.entry(InterfaceLanguage.TRADITIONAL_CHINESE, "憑證"),
                Map.entry(InterfaceLanguage.JAPANESE, "証明書"),
                Map.entry(InterfaceLanguage.KOREAN, "인증서"),
                Map.entry(InterfaceLanguage.SPANISH, "Certificado"),
                Map.entry(InterfaceLanguage.BRAZILIAN_PORTUGUESE, "Certificado"),
                Map.entry(InterfaceLanguage.THAI, "ใบรับรอง"),
                Map.entry(InterfaceLanguage.FRENCH, "Certificat"),
                Map.entry(InterfaceLanguage.ITALIAN, "Certificato"),
                Map.entry(InterfaceLanguage.GERMAN, "Zertifikat"),
                Map.entry(InterfaceLanguage.RUSSIAN, "Сертификат"));

        for (InterfaceLanguage language : InterfaceLanguage.publishedLanguages()) {
            assertEquals(expected.get(language),
                    Localizer.forLanguage(language).text("form.privateKey"), language.persistedTag());
        }
    }

    @Test
    void localFileFavoriteActionUsesTheSameCompactCopyAsSftpInEveryPublishedLanguage() {
        for (InterfaceLanguage language : InterfaceLanguage.publishedLanguages()) {
            Localizer localizer = Localizer.forLanguage(language);
            assertEquals(localizer.text("legacy.java.2e8a4ae9c22dca06"),
                    localizer.text("localFiles.addFavorite"), language.persistedTag());
        }
    }

    @Test
    void deactivationCopyExplainsThatTheDeviceAllowanceIsNotReturned() {
        String simplified = Localizer.forLanguage(InterfaceLanguage.SIMPLIFIED_CHINESE)
                .text("license.deactivate.confirm");
        String traditional = Localizer.forLanguage(InterfaceLanguage.TRADITIONAL_CHINESE)
                .text("license.deactivate.confirm");

        assertTrue(simplified.contains("不会返还设备额度"));
        assertFalse(simplified.contains("释放席位"));
        assertTrue(traditional.contains("不會返還裝置額度"));
        assertFalse(traditional.contains("釋放席位"));
    }

    @Test
    void sshCredentialTranslationsUsePrivateKeyPassphraseTerms() {
        Localizer thai = Localizer.forLanguage(InterfaceLanguage.THAI);
        assertEquals("วลีรหัสผ่านสำหรับกุญแจส่วนตัวหรือใบรับรอง SSH",
                thai.text("legacy.fxml.3b35d438fc942f49"));
        assertEquals("กุญแจส่วนตัว", thai.text("legacy.fxml.3ffe935f9d0ff77e"));
        assertEquals("วลีรหัสผ่านกุญแจส่วนตัว", thai.text("form.privateKeyPassphrase"));

        Localizer russian = Localizer.forLanguage(InterfaceLanguage.RUSSIAN);
        assertEquals("Новые учётные данные", russian.text("legacy.fxml.9ef774b3220dc7d9"));
        assertEquals("Парольная фраза закрытого ключа",
                russian.text("legacy.java.0550c993e3f9f19a"));
        assertEquals("Мастер-пароль", russian.text("legacy.java.f36f5f11edefb912"));
        assertEquals("Для тестирования подключения требуется ровно один набор учётных данных",
                russian.text("legacy.java.6ff586f69e9dcc20"));
        assertEquals("Хост ссылается на не включённые учётные данные",
                russian.text("legacy.external.7048fe05e7e0e957"));
    }

    @Test
    void everyPublishedLocaleContainsTheCompleteEnglishKeySet() throws Exception {
        Properties englishValues = directProperties("messages.properties");
        Set<String> english = englishValues.stringPropertyNames();
        for (InterfaceLanguage language : InterfaceLanguage.publishedLanguages()) {
            if (language == InterfaceLanguage.ENGLISH) continue;
            String suffix = language.persistedTag().replace('-', '_');
            Properties localized = directProperties("messages_" + suffix + ".properties");
            assertEquals(english, localized.stringPropertyNames(), language.persistedTag());
            for (String key : english) {
                assertEquals(arguments(englishValues.getProperty(key)), arguments(localized.getProperty(key)),
                        language.persistedTag() + ":" + key);
                assertMessageArgumentsRender(localized.getProperty(key), language.locale(),
                        language.persistedTag() + ":" + key);
            }
        }
    }

    @Test
    void activationAndChatGptCopyIsActuallyLocalizedInEveryNonEnglishLocale() throws Exception {
        Properties english = directProperties("messages.properties");
        for (InterfaceLanguage language : InterfaceLanguage.publishedLanguages()) {
            if (language == InterfaceLanguage.ENGLISH) continue;
            String suffix = language.persistedTag().replace('-', '_');
            Properties localized = directProperties("messages_" + suffix + ".properties");
            for (String key : english.stringPropertyNames()) {
                if (!(key.startsWith("license.") || key.startsWith("ai.chatgpt."))) continue;
                assertNotEquals(english.getProperty(key), localized.getProperty(key),
                        language.persistedTag() + ": untranslated English fallback for " + key);
            }
        }
    }

    @Test
    void staticUserFacingCopyDoesNotEndWithHardcodedEllipses() throws Exception {
        for (InterfaceLanguage language : InterfaceLanguage.publishedLanguages()) {
            String suffix = language == InterfaceLanguage.ENGLISH ? "" : "_"
                    + language.persistedTag().replace('-', '_');
            Properties localized = directProperties("messages" + suffix + ".properties");
            for (String key : localized.stringPropertyNames()) {
                String value = localized.getProperty(key).strip();
                boolean templateSyntax = value.contains("{{random,...}}");
                assertTrue(templateSyntax || !(value.endsWith("…") || value.endsWith("...")),
                        language.persistedTag() + ":" + key + "=" + value);
            }
        }
    }

    private static Properties directProperties(String fileName) throws Exception {
        Properties values = new Properties();
        try (var input = LocalizerTest.class.getResourceAsStream("/com/ynpe/fastremote/i18n/" + fileName)) {
            if (input == null) throw new AssertionError("Missing localization file: " + fileName);
            values.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        return values;
    }

    private static Set<String> arguments(String pattern) {
        var matcher = java.util.regex.Pattern.compile(
                "\\{(\\d+)(?:,(?:number|date|time|choice)(?:,[^}]*)?)?}").matcher(pattern);
        var values = new java.util.LinkedHashSet<String>();
        while (matcher.find()) values.add(matcher.group(1));
        return Set.copyOf(values);
    }

    private static void assertMessageArgumentsRender(String pattern, Locale locale, String source) {
        Set<String> indexes = arguments(pattern);
        if (indexes.isEmpty()) return;
        int maximum = indexes.stream().mapToInt(Integer::parseInt).max().orElseThrow();
        Object[] values = new Object[maximum + 1];
        for (int index = 0; index < values.length; index++) values[index] = "__ARG" + index + "__";
        String rendered;
        try {
            rendered = new MessageFormat(pattern, locale).format(values);
        } catch (IllegalArgumentException invalidPattern) {
            throw new AssertionError(source + " -> " + pattern, invalidPattern);
        }
        for (String index : indexes) {
            assertTrue(rendered.contains("__ARG" + index + "__"), source + " -> " + rendered);
        }
    }

    @Test
    void systemLocaleUsesEnglishForUnsupportedLanguages() {
        assertEquals(InterfaceLanguage.ENGLISH,
                InterfaceLanguage.resolveSystem(Locale.forLanguageTag("ar-EG")));
    }

    @Test
    void hongKongAndMacauUseTaiwanTraditionalChineseForNow() {
        assertEquals(InterfaceLanguage.TRADITIONAL_CHINESE,
                InterfaceLanguage.resolveSystem(Locale.forLanguageTag("zh-HK")));
        assertEquals(InterfaceLanguage.TRADITIONAL_CHINESE,
                InterfaceLanguage.resolveSystem(Locale.forLanguageTag("zh-MO")));
        assertEquals(InterfaceLanguage.TRADITIONAL_CHINESE,
                InterfaceLanguage.resolveSystem(Locale.forLanguageTag("zh-Hant")));
    }

    @Test
    void persistedTagsRemainStableAndUnknownValuesReturnToSystemDefault() {
        for (InterfaceLanguage language : InterfaceLanguage.publishedPreferences()) {
            assertEquals(language, InterfaceLanguage.fromPersistedTag(language.persistedTag()));
        }
        assertEquals(InterfaceLanguage.JAPANESE, InterfaceLanguage.fromPersistedTag("ja"));
        assertEquals(InterfaceLanguage.RUSSIAN, InterfaceLanguage.fromPersistedTag("ru"));
        assertEquals(InterfaceLanguage.SYSTEM, InterfaceLanguage.fromPersistedTag("unknown"));
    }

    @Test
    void userFacingNumbersSizesAndDatesUseTheSelectedLocale() {
        Localizer english = Localizer.forLanguage(InterfaceLanguage.ENGLISH);
        Localizer simplified = Localizer.forLanguage(InterfaceLanguage.SIMPLIFIED_CHINESE);
        assertEquals("12,345", english.formatNumber(12_345));
        assertEquals("1.5 KiB", english.formatBytes(1_536));
        assertEquals("Jan 2, 2026, 3:04:05\u202fAM",
                english.formatDateTime(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC));
        assertEquals("2026年1月2日 03:04:05",
                simplified.formatDateTime(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC));
        assertEquals("01:01:01", english.formatClockDuration(Duration.ofSeconds(3_661)));
    }

    @Test
    void lowerLayerMessageFragmentsAreTranslatedOnlyAtTheSelectedBoundary() {
        String source = "命令名称长度必须为 1 到 255; 变量不存在：demo";
        String english = Localizer.forLanguage(InterfaceLanguage.ENGLISH).translateExternalText(source);
        assertEquals("Command name length must be between 1 and 255; Variable does not exist: demo", english);
        assertFalse(english.matches(".*[\\u3400-\\u9fff].*"));
        assertEquals(source, Localizer.forLanguage(InterfaceLanguage.SIMPLIFIED_CHINESE)
                .translateExternalText(source));
    }
}
