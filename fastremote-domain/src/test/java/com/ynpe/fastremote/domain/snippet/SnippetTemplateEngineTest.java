// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.snippet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SnippetTemplateEngineTest {
    private final SnippetTemplateEngine engine = new SnippetTemplateEngine(
            Clock.fixed(Instant.parse("2026-08-22T08:09:10Z"), ZoneId.of("Asia/Bangkok")),
            new SecureRandom(new byte[] {1, 2, 3, 4}));

    @Test
    void analyzesInputsInFirstAppearanceOrderAndRejectsConflictingDuplicates() {
        var analysis = engine.analyze("{{input, path}} {{input,user,default=deploy,required=false}} {{input,path}}");

        assertEquals(java.util.List.of("path", "user"), analysis.inputs().stream().map(SnippetInput::name).toList());
        assertThrows(SnippetTemplateException.class,
                () -> engine.analyze("{{input,path}} {{input,path,required=false}}"));
    }

    @Test
    void rendersLiteralMultilineTextWithoutInterpretingBackslashEscapes() {
        String template = "printf '\\n'\napt install \\\n  curl\n\\{{literal}}";

        assertEquals(template.replace("\\{{", "{{"), engine.render(template, emptyContext()));
    }

    @Test
    void rendersAllContextNamespacesAndEncodingsFromLeftToRight() {
        String rendered = engine.render("""
                {{input, path|url}}
                {{variable, backup_dir|base64}}
                {{session, host|upper}}
                {{now, yyyyMMdd-HHmmss}}
                {{variable, user|md5|hex|upper}}
                """, new SnippetTemplateContext(
                Map.of("path", "/tmp/my file"),
                Map.of("backup_dir", variable("backup_dir", SnippetVariableType.SINGLE, "", "/backup"),
                        "user", variable("user", SnippetVariableType.SINGLE, "", "alice")),
                Map.of("host", "server.example")));

        assertEquals("""
                %2Ftmp%2Fmy%20file
                L2JhY2t1cA==
                SERVER.EXAMPLE
                20260822-150910
                3633383465326232313834626362663538656363663130636137613635363363
                """, rendered);
    }

    @Test
    void generatesDocumentedRandomFormats() {
        assertTrue(Pattern.matches("[A-Za-z0-9]{10}", render("{{random,string,10}}")));
        assertTrue(Pattern.matches("[1-9][0-9]{9}", render("{{random,number,10}}")));
        assertTrue(Pattern.matches("[0-9a-f]{10}", render("{{random,hex,10}}")));
        assertTrue(Pattern.matches("[a-z]{10}", render("{{random,lower,10}}")));
        assertTrue(Pattern.matches("[A-Z]{10}", render("{{random,upper,10}}")));
        assertTrue(Pattern.matches("[1-9][0-9]{2}\\.[0-9]{4}", render("{{random,float,3.4}}")));
        int bounded = Integer.parseInt(render("{{random,int,7}}"));
        assertTrue(bounded >= 1 && bounded <= 7);
        int ranged = Integer.parseInt(render("{{random,int,3-7}}"));
        assertTrue(ranged >= 3 && ranged <= 7);
        assertTrue(Pattern.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}",
                render("{{random,uuid}}")));
        assertTrue(Pattern.matches("[0-9a-f]{32}", render("{{random,uuid,non}}")));
    }

    @Test
    void generatedIpv4ExcludesSpecialRanges() {
        for (int index = 0; index < 100; index++) {
            String address = render("{{random,IP}}");
            String[] octets = address.split("\\.");
            assertEquals(4, octets.length);
            int first = Integer.parseInt(octets[0]);
            int second = Integer.parseInt(octets[1]);
            int third = Integer.parseInt(octets[2]);
            assertFalse(first == 0 || first == 10 || first == 127 || first >= 224);
            assertFalse(first == 100 && second >= 64 && second <= 127);
            assertFalse(first == 169 && second == 254);
            assertFalse(first == 172 && second >= 16 && second <= 31);
            assertFalse(first == 192 && (second == 0 || second == 168));
            assertFalse(first == 192 && second == 88 && third == 99);
            assertFalse(first == 198 && (second == 18 || second == 19));
            assertFalse(first == 198 && second == 51 && third == 100);
            assertFalse(first == 203 && second == 0 && third == 113);
        }
    }

    @Test
    void rejectsMissingValuesInvalidSyntaxAndNegativeIntegerRanges() {
        assertThrows(SnippetTemplateException.class,
                () -> engine.render("{{variable,missing}}", emptyContext()));
        assertThrows(SnippetTemplateException.class, () -> engine.analyze("{{random,int,-2-4}}"));
        assertThrows(SnippetTemplateException.class, () -> engine.analyze("{{random,lowpper,4}}"));
        assertThrows(SnippetTemplateException.class, () -> engine.analyze("{{random,S,10}}"));
        assertThrows(SnippetTemplateException.class, () -> engine.analyze("{{unknown,value}}"));
        assertThrows(SnippetTemplateException.class, () -> engine.analyze("{{input,name"));
    }

    @Test
    void analyzesInputVariableAndResolvesOneBasedArrayIndexes() {
        var analysis = engine.analyze("{{input,target,variable=hosts}} {{variable,hosts,index=2}}");
        SnippetVariable hosts = variable("hosts", SnippetVariableType.ARRAY, "||", "one||two||three");

        assertEquals(java.util.Set.of("hosts"), analysis.variables());
        assertEquals("one||two||three two", engine.render(
                "{{variable,hosts}} {{variable,hosts,index=2}}",
                new SnippetTemplateContext(Map.of(), Map.of("hosts", hosts), Map.of())));
        assertThrows(SnippetTemplateException.class, () -> engine.render(
                "{{variable,hosts,index=4}}",
                new SnippetTemplateContext(Map.of(), Map.of("hosts", hosts), Map.of())));
        assertThrows(SnippetTemplateException.class, () -> engine.render(
                "{{variable,scalar,index=1}}",
                new SnippetTemplateContext(Map.of(), Map.of("scalar",
                        variable("scalar", SnippetVariableType.SINGLE, "", "one")), Map.of())));
    }

    private String render(String template) {
        return engine.render(template, emptyContext());
    }

    private static SnippetTemplateContext emptyContext() {
        return new SnippetTemplateContext(Map.of(), Map.of(), Map.of());
    }

    private static SnippetVariable variable(
            String variableName, SnippetVariableType type, String delimiter, String value) {
        return new SnippetVariable(
                SnippetVariableId.create(), java.util.Optional.empty(), variableName, variableName,
                type, delimiter, value, "", 0, Instant.EPOCH, Instant.EPOCH);
    }

}
