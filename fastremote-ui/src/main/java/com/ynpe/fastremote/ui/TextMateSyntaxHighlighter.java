// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.eclipse.tm4e.core.grammar.IGrammar;
import org.eclipse.tm4e.core.grammar.IStateStack;
import org.eclipse.tm4e.core.grammar.IToken;
import org.eclipse.tm4e.core.registry.IGrammarSource;
import org.eclipse.tm4e.core.registry.IRegistryOptions;
import org.eclipse.tm4e.core.registry.Registry;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/** UI-independent TextMate tokenization adapted to the editor's small CSS style vocabulary. */
final class TextMateSyntaxHighlighter {
    private static final Duration LINE_TIME_LIMIT = Duration.ofSeconds(2);
    private static final Map<String, String> LANGUAGE_SCOPES = Map.ofEntries(
            Map.entry("java", "source.java"), Map.entry("groovy", "source.groovy"),
            Map.entry("js", "source.js"), Map.entry("ts", "source.ts"),
            Map.entry("py", "source.python"), Map.entry("rb", "source.ruby"),
            Map.entry("php", "source.php"), Map.entry("go", "source.go"),
            Map.entry("rs", "source.rust"), Map.entry("c", "source.c"),
            Map.entry("cpp", "source.cpp"), Map.entry("cs", "source.cs"),
            Map.entry("swift", "source.swift"), Map.entry("dart", "source.dart"),
            Map.entry("r", "source.r"), Map.entry("sh", "source.shell"),
            Map.entry("ps1", "source.powershell"), Map.entry("bat", "source.batchfile"),
            Map.entry("pl", "source.perl"), Map.entry("lua", "source.lua"),
            Map.entry("json", "source.json"), Map.entry("xml", "text.xml"),
            Map.entry("html", "text.html.derivative"), Map.entry("css", "source.css"),
            Map.entry("yaml", "source.yaml"), Map.entry("ini", "source.ini"),
            Map.entry("properties", "source.dotenv"), Map.entry("docker", "source.dockerfile"),
            Map.entry("make", "source.makefile"), Map.entry("diff", "source.diff"),
            Map.entry("md", "text.html.markdown"));

    private static final Map<String, String> GRAMMAR_RESOURCES = Map.ofEntries(
            grammar("source.batchfile", "bat/bat.tmLanguage.json"),
            grammar("source.c", "cpp/c.tmLanguage.json"),
            grammar("source.cpp", "cpp/cpp.tmLanguage.json"),
            grammar("source.c.platform", "cpp/source.c.platform.tmLanguage.json"),
            grammar("source.cs", "csharp/csharp.tmLanguage.json"),
            grammar("source.css", "css/css.tmLanguage.json"),
            grammar("source.css.less", "less/less.tmLanguage.json"),
            grammar("source.css.scss", "scss/scss.tmLanguage.json"),
            grammar("source.sassdoc", "scss/source.sassdoc.tmLanguage.json"),
            grammar("source.dart", "dart/dart.tmLanguage.json"),
            grammar("source.diff", "diff/diff.tmLanguage.json"),
            grammar("source.dockerfile", "docker/dockerfile.tmLanguage.json"),
            grammar("source.dotenv", "dotenv/dotenv.tmLanguage.json"),
            grammar("source.go", "go/go.tmLanguage.json"),
            grammar("source.groovy", "groovy/groovy.tmLanguage.json"),
            grammar("text.html.derivative", "html/html.tmLanguage.json"),
            grammar("text.html.basic", "html/text.html.basic.tmLanguage.json"),
            grammar("source.ini", "ini/ini.tmLanguage.json"),
            grammar("source.java", "java/java.tmLanguage.json"),
            grammar("source.js", "javascript/javascript.tmLanguage.json"),
            grammar("source.js.jsx", "javascript/javascriptreact.tmLanguage.json"),
            grammar("source.js.regexp", "javascript/source.js.regexp.tmLanguage.plist"),
            grammar("source.json", "json/json.tmLanguage.json"),
            grammar("source.json.comments", "json/jsonc.tmLanguage.json"),
            grammar("source.json.lines", "json/jsonl.tmLanguage.json"),
            grammar("source.lua", "lua/lua.tmLanguage.json"),
            grammar("source.makefile", "make/makefile.tmLanguage.json"),
            grammar("text.html.markdown", "markdown/markdown.tmLanguage.json"),
            grammar("source.perl", "perl/perl.tmLanguage.json"),
            grammar("source.php", "php/php.tmLanguage.json"),
            grammar("source.powershell", "powershell/powershell.tmLanguage.json"),
            grammar("source.python", "python/python.tmLanguage.json"),
            grammar("source.regexp.python", "python/source.regexp.python.tmLanguage.json"),
            grammar("source.r", "r/r.tmLanguage.json"),
            grammar("source.ruby", "ruby/ruby.tmLanguage.json"),
            grammar("source.rust", "rust/rust.tmLanguage.json"),
            grammar("source.shell", "shellscript/shellscript.tmLanguage.json"),
            grammar("source.swift", "swift/swift.tmLanguage.json"),
            grammar("source.ts", "typescript/typescript.tmLanguage.json"),
            grammar("source.tsx", "typescript/typescriptreact.tmLanguage.json"),
            grammar("text.xml", "xml/xml.tmLanguage.json"),
            grammar("text.xml.xsl", "xml/xsl.tmLanguage.json"),
            grammar("source.yaml", "yaml/yaml.tmLanguage.json"));

    private static final Engine ENGINE = new Engine();

    private TextMateSyntaxHighlighter() { }

    static boolean supports(String language) {
        return LANGUAGE_SCOPES.containsKey(language);
    }

    static Session session() {
        return new Session();
    }

    static boolean isOperational(String language) {
        return supports(language) && ENGINE.grammar(language) != null;
    }

    static StyleSpans<Collection<String>> compute(String content, String language, int limit) {
        return new Session().compute(content, language, limit);
    }

    static final class Session {
        private String language;
        private List<CachedLine> cache = List.of();
        private int lastTokenizedLineCount;

        synchronized StyleSpans<Collection<String>> compute(String content, String requestedLanguage, int limit) {
            lastTokenizedLineCount = 0;
            if (content.isEmpty()) return StyleSpans.singleton(Collections.emptyList(), 0);
            String source = content.substring(0, Math.min(content.length(), limit));
            if (!supports(requestedLanguage)) return null;
            IGrammar grammar = ENGINE.grammar(requestedLanguage);
            if (grammar == null) return null;

            List<String> lines = lines(source);
            int unchangedPrefix = requestedLanguage.equals(language) ? commonPrefix(lines, cache) : 0;
            List<CachedLine> next = new ArrayList<>(lines.size());
            next.addAll(cache.subList(0, Math.min(unchangedPrefix, cache.size())));
            IStateStack state = unchangedPrefix == 0 ? null : next.get(unchangedPrefix - 1).stateAfter();

            for (int index = unchangedPrefix; index < lines.size(); index++) {
                String rawLine = lines.get(index);
                if (canReuseSuffix(lines, index, state)) {
                    next.addAll(cache.subList(index, cache.size()));
                    break;
                }
                int tokenLength = contentLength(rawLine);
                var result = ENGINE.tokenize(grammar, rawLine.substring(0, tokenLength), state);
                lastTokenizedLineCount++;
                List<StyledRun> runs = runs(result.tokens(), tokenLength, rawLine.length());
                next.add(new CachedLine(rawLine, state, result.stateAfter(), runs));
                state = result.stateAfter();
            }

            language = requestedLanguage;
            cache = List.copyOf(next);
            StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
            for (CachedLine line : cache) {
                for (StyledRun run : line.runs()) builder.add(run.style(), run.length());
            }
            if (source.length() < content.length()) builder.add(Collections.emptyList(), content.length() - source.length());
            return builder.create();
        }

        synchronized int lastTokenizedLineCount() {
            return lastTokenizedLineCount;
        }

        private boolean canReuseSuffix(List<String> lines, int from, IStateStack state) {
            if (lines.size() != cache.size() || from >= cache.size()) return false;
            CachedLine old = cache.get(from);
            if (!old.text().equals(lines.get(from)) || !Objects.equals(old.stateBefore(), state)) return false;
            for (int index = from + 1; index < lines.size(); index++) {
                if (!cache.get(index).text().equals(lines.get(index))) return false;
            }
            return true;
        }
    }

    private static final class Engine {
        private final Registry registry;
        private final Map<String, IGrammar> grammars = new HashMap<>();

        private Engine() {
            registry = new Registry(new IRegistryOptions() {
                @Override public IGrammarSource getGrammarSource(String scopeName) {
                    String resource = GRAMMAR_RESOURCES.get(scopeName);
                    return resource == null ? null
                            : IGrammarSource.fromResource(TextMateSyntaxHighlighter.class, resource);
                }
            });
        }

        synchronized IGrammar grammar(String language) {
            if (grammars.containsKey(language)) return grammars.get(language);
            String scope = LANGUAGE_SCOPES.get(language);
            IGrammar grammar;
            try {
                grammar = scope == null ? null : registry.loadGrammar(scope);
            } catch (RuntimeException | LinkageError failure) {
                grammar = null;
            }
            grammars.put(language, grammar);
            return grammar;
        }

        synchronized TokenizedLine tokenize(IGrammar grammar, String line, IStateStack state) {
            try {
                var result = grammar.tokenizeLine(line, state, LINE_TIME_LIMIT);
                return new TokenizedLine(List.of(result.getTokens()), result.getRuleStack());
            } catch (RuntimeException | StackOverflowError failure) {
                return new TokenizedLine(List.of(), state);
            }
        }
    }

    private static List<StyledRun> runs(List<IToken> tokens, int contentLength, int rawLength) {
        List<StyledRun> runs = new ArrayList<>();
        int position = 0;
        for (IToken token : tokens) {
            int start = Math.max(position, Math.min(contentLength, token.getStartIndex()));
            int end = Math.max(start, Math.min(contentLength, token.getEndIndex()));
            addRun(runs, Collections.emptyList(), start - position);
            addRun(runs, style(token.getScopes()), end - start);
            position = end;
        }
        addRun(runs, Collections.emptyList(), contentLength - position);
        addRun(runs, Collections.emptyList(), rawLength - contentLength);
        return List.copyOf(runs);
    }

    private static Collection<String> style(List<String> scopes) {
        for (int index = scopes.size() - 1; index >= 0; index--) {
            String scope = scopes.get(index);
            if (scope.startsWith("comment") || scope.contains(".comment")) return List.of("comment");
        }
        for (int index = scopes.size() - 1; index >= 0; index--) {
            String scope = scopes.get(index);
            if (scope.startsWith("string") || scope.startsWith("regexp")) return List.of("string");
            if (scope.startsWith("constant.numeric")) return List.of("number");
            if (scope.startsWith("entity.name.function") || scope.startsWith("support.function")) return List.of("function");
            if (scope.startsWith("variable") || scope.startsWith("entity.other")
                    || scope.startsWith("support.type.property-name") || scope.startsWith("entity.name.type")
                    || scope.startsWith("entity.name.class") || scope.startsWith("entity.name.namespace")
                    || scope.startsWith("punctuation.definition.variable")
                    || scope.contains(".property.anchor.") || scope.startsWith("punctuation.definition.anchor")) {
                return List.of("property");
            }
            if (scope.startsWith("keyword") || scope.startsWith("storage")
                    || scope.startsWith("constant.language") || scope.startsWith("entity.name.tag")) return List.of("keyword");
        }
        return Collections.emptyList();
    }

    private static void addRun(List<StyledRun> runs, Collection<String> style, int length) {
        if (length <= 0) return;
        if (!runs.isEmpty() && runs.getLast().style().equals(style)) {
            StyledRun previous = runs.removeLast();
            runs.add(new StyledRun(style, previous.length() + length));
        } else {
            runs.add(new StyledRun(style, length));
        }
    }

    private static int commonPrefix(List<String> lines, List<CachedLine> cached) {
        int limit = Math.min(lines.size(), cached.size());
        int index = 0;
        while (index < limit && lines.get(index).equals(cached.get(index).text())) index++;
        return index;
    }

    private static List<String> lines(String source) {
        if (source.isEmpty()) return List.of();
        List<String> lines = new ArrayList<>();
        int start = 0;
        while (start < source.length()) {
            int newline = source.indexOf('\n', start);
            int end = newline < 0 ? source.length() : newline + 1;
            lines.add(source.substring(start, end));
            start = end;
        }
        return lines;
    }

    private static int contentLength(String line) {
        int length = line.length();
        if (length > 0 && line.charAt(length - 1) == '\n') length--;
        if (length > 0 && line.charAt(length - 1) == '\r') length--;
        return length;
    }

    private static Map.Entry<String, String> grammar(String scope, String path) {
        return Map.entry(scope, "/syntaxes/" + path);
    }

    private record TokenizedLine(List<IToken> tokens, IStateStack stateAfter) { }
    private record StyledRun(Collection<String> style, int length) { }
    private record CachedLine(String text, IStateStack stateBefore, IStateStack stateAfter, List<StyledRun> runs) { }
}
