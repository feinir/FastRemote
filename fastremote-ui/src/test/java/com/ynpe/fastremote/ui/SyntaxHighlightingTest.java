// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class SyntaxHighlightingTest {
    @Test
    void shellArithmeticExpansionHasARepositoryRuleAndHighlightsNumbers() throws Exception {
        try (var input = TextMateSyntaxHighlighter.class.getResourceAsStream("/syntaxes/shellscript/shellscript.tmLanguage.json")) {
            var grammar = new String(java.util.Objects.requireNonNull(input).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(grammar.contains("\"arithmetic_dollar\":"), "Referenced shell arithmetic rule must be bundled");
        }
        String shell = "echo \"$((2 + 3))\"\n";
        assertStyle(shell, "2", "sh", "number");
        assertStyle(shell, "3", "sh", "number");
        assertHighlighted(shell, "sh");
    }

    @Test
    void recognizesCommonLanguagesAndConfigurationFormatsCaseInsensitively() {
        assertEquals("java", SyntaxHighlighting.language("Main.java"));
        assertEquals("sql", SyntaxHighlighting.language("schema.SQL"));
        assertEquals("yaml", SyntaxHighlighting.language("compose.yml"));
        assertEquals("conf", SyntaxHighlighting.language("nginx.CONF"));
        assertEquals("ini", SyntaxHighlighting.language("application.cfg"));
        assertEquals("ini", SyntaxHighlighting.language("settings.ini"));
        assertEquals("php", SyntaxHighlighting.language("index.PHP"));
        assertEquals("kt", SyntaxHighlighting.language("build.gradle.kts"));
        assertNull(SyntaxHighlighting.language("README"));
    }

    @Test
    void recognizesExtensionlessAndToolSpecificFileNames() {
        assertEquals("docker", SyntaxHighlighting.language("/srv/app/Dockerfile.production"));
        assertEquals("make", SyntaxHighlighting.language("C:\\src\\GNUmakefile"));
        assertEquals("cmake", SyntaxHighlighting.language("CMakeLists.txt"));
        assertEquals("sh", SyntaxHighlighting.language(".zshrc"));
        assertEquals("properties", SyntaxHighlighting.language(".env.local"));
        assertEquals("ini", SyntaxHighlighting.language("worker.service"));
        assertEquals("conf", SyntaxHighlighting.language(".gitignore"));
    }

    @Test
    void sqlHighlightingKeepsFullSpanLengthBeyondItsSafeScanWindow() {
        String sql = "SELECT value FROM sample;\n".repeat(12_000);
        assertEquals(sql.length(), SyntaxHighlighting.compute(sql, "sql").length());
    }

    @Test
    void manualSyntaxChoicesIncludeAddedCommonFormats() {
        assertTrue(SyntaxHighlighting.supportedLanguages().contains("plain"));
        assertTrue(SyntaxHighlighting.supportedLanguages().contains("sql"));
        assertTrue(SyntaxHighlighting.supportedLanguages().contains("conf"));
        assertTrue(SyntaxHighlighting.supportedLanguages().contains("ini"));
        assertTrue(SyntaxHighlighting.supportedLanguages().contains("php"));
        assertTrue(SyntaxHighlighting.supportedLanguages().contains("docker"));
        assertTrue(SyntaxHighlighting.supportedLanguages().contains("hcl"));
    }

    @Test
    void addedRulesProduceStyledSpansAndPreserveDocumentLength() {
        assertHighlighted("[server]\nport=8080\nenabled=true\n", "ini");
        assertHighlighted("<?php function hello() { return true; }", "php");
        assertHighlighted("FROM eclipse-temurin:21\nENV APP_HOME=/app\n", "docker");
        assertHighlighted("resource \"aws_instance\" \"web\" { count = 2 }", "hcl");
    }

    @Test
    void phpHighlightsVariablesFunctionsKeywordsAndStringsSeparately() {
        String php = "<?php\n$data_name = $_POST['data_name'];\nfunction normalize_data($value) { return $value; }";
        assertStyle(php, "$data_name", "php", "property");
        assertStyle(php, "$_POST", "php", "property");
        assertStyle(php, "'data_name'", "php", "string");
        assertStyle(php, "function", "php", "keyword");
        assertStyle(php, "normalize_data", "php", "function");
    }

    @Test
    void phpKeepsMultilineStateAndHighlightsInterpolationInsideHeredoc() {
        String php = "<?php\n/* comment\n$data_name stays a comment\n*/\n"
                + "$html = <<<HTML\n<section>$embedded_name</section>\nHTML;\n";
        assertStyle(php, "$data_name stays", "php", "comment");
        assertStyle(php, "$embedded_name</section>", "php", "property");
    }

    @Test
    void languageSpecificRulesCoverRepresentativeConstructs() {
        assertStyle("@cached\ndef load_data():\n    return \"\"\"value\"\"\"", "@cached", "py", "function");
        assertStyle("const text = `hello ${name}`; cache.set(text);", "`hello ${name}`", "js", "string");
        assertStyle("#include <stdio.h>\nint main(void) {}", "#include <stdio.h>", "c", "keyword");
        assertStyle("build() { echo ${HOME}; }", "${HOME}", "sh", "property");
        assertStyle(":root { --accent: #aabbcc; width: calc(100% - 2rem); }", "--accent", "css", "property");
        assertStyle("created = 2026-09-19T12:30:00Z", "2026-09-19T12:30:00Z", "toml", "function");
        assertStyle("defaults: &defaults\n  enabled: true", "&defaults", "yaml", "property");
    }

    @Test
    void nonSqlFilesScanUpToOneMiBWhileSqlKeepsItsSmallerLimit() {
        assertEquals(1024 * 1024, SyntaxHighlighting.GENERAL_HIGHLIGHT_LIMIT);
        assertEquals(128 * 1024, SyntaxHighlighting.SQL_HIGHLIGHT_LIMIT);
    }

    @Test
    void detailedPhpHighlightingRemainsBoundedNearOneMiB() {
        String line = "$data_name = normalize_data($_POST['data_name']); // normalize\n";
        String php = line.repeat(SyntaxHighlighting.GENERAL_HIGHLIGHT_LIMIT / line.length() + 2);
        assertTimeout(Duration.ofSeconds(10), () ->
                assertEquals(php.length(), SyntaxHighlighting.compute(php, "php").length()));
    }

    @Test
    void loadsBundledTextMateGrammarsInsteadOfUsingRegexFallback() {
        for (String language : List.of("java", "php", "html", "yaml", "properties")) {
            assertTrue(TextMateSyntaxHighlighter.isOperational(language), language);
        }
        assertEquals(0, SyntaxHighlighting.compute("", "java").length());
    }

    @Test
    void textMateSessionRetokenizesOnlyChangedStatefulLines() {
        var session = TextMateSyntaxHighlighter.session();
        String initial = "class Demo {\n  int first = 1;\n  int second = 2;\n}\n";
        assertEquals(initial.length(), session.compute(initial, "java", initial.length()).length());
        assertEquals(4, session.lastTokenizedLineCount());

        String edited = initial.replace("first = 1", "first = 3");
        assertEquals(edited.length(), session.compute(edited, "java", edited.length()).length());
        assertEquals(1, session.lastTokenizedLineCount());
    }

    private static void assertHighlighted(String source, String language) {
        var spans = SyntaxHighlighting.compute(source, language);
        assertEquals(source.length(), spans.length());
        assertTrue(spans.stream().anyMatch(span -> !span.getStyle().isEmpty()), language);
    }

    private static void assertStyle(String source, String token, String language, String expectedStyle) {
        int target = source.indexOf(token);
        assertTrue(target >= 0, token);
        int offset = 0;
        for (var span : SyntaxHighlighting.compute(source, language)) {
            if (target < offset + span.getLength()) {
                assertTrue(span.getStyle().contains(expectedStyle),
                        () -> language + " token " + token + " has style " + span.getStyle());
                return;
            }
            offset += span.getLength();
        }
        throw new AssertionError("No style span for " + language + " token " + token);
    }
}
