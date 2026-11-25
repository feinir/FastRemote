// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/** Extension-based syntax highlighting with bounded background work and TextMate state caching. */
final class SyntaxHighlighting {
    static final int GENERAL_HIGHLIGHT_LIMIT = 1024 * 1024;
    static final int SQL_HIGHLIGHT_LIMIT = 128 * 1024;
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(1, 1, 20L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(2), Thread.ofPlatform().daemon(true).name("fastremote-highlight-", 0).factory(),
            new ThreadPoolExecutor.DiscardOldestPolicy());
    private static final Map<String, List<Rule>> RULES = Map.ofEntries(
            Map.entry("java", with(common("\\b(abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|exports|extends|final|finally|float|for|goto|if|implements|import|instanceof|int|interface|long|module|native|new|non-sealed|open|opens|package|permits|private|protected|provides|public|record|requires|return|sealed|short|static|strictfp|super|switch|synchronized|this|throw|throws|to|transient|transitive|true|false|null|try|uses|var|void|volatile|while|with|yield)\\b", "//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/"),
                    rule("property", "@[A-Za-z_$][A-Za-z0-9_$.]*", 3, 0),
                    rule("function", "(?<=::)[A-Za-z_$][A-Za-z0-9_$]*", 4, 0))),
            Map.entry("kt", with(common("\\b(as|break|class|continue|do|else|false|for|fun|if|in|interface|is|null|object|package|return|super|this|throw|true|try|typealias|typeof|val|var|when|while|by|catch|constructor|delegate|dynamic|field|file|finally|get|import|init|param|property|receiver|set|setparam|where|actual|abstract|annotation|companion|const|crossinline|data|enum|expect|external|final|infix|inline|inner|internal|lateinit|noinline|open|operator|out|override|private|protected|public|reified|sealed|suspend|tailrec|vararg)\\b", "//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/"),
                    rule("property", "@[A-Za-z_][A-Za-z0-9_.]*|\\$[A-Za-z_][A-Za-z0-9_]*|\\$\\{[^}\\r\\n]+}", 3, 0))),
            Map.entry("groovy", with(common("\\b(as|assert|break|case|catch|class|const|continue|def|default|do|else|enum|extends|false|finally|for|goto|if|implements|import|in|instanceof|interface|new|null|package|permits|private|protected|public|record|return|sealed|static|strictfp|super|switch|synchronized|this|throw|throws|trait|transient|true|try|var|void|volatile|while|yield)\\b", "//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/"),
                    rule("string", "(?:[rubf]*)?\"\"\"[\\s\\S]*?\"\"\"|(?:[rubf]*)?'''[\\s\\S]*?'''|`[^`]*`", 1, Pattern.CASE_INSENSITIVE),
                    rule("property", "@[A-Za-z_][A-Za-z0-9_.]*|\\$[A-Za-z_][A-Za-z0-9_]*|\\$\\{[^}\\r\\n]+}", 3, 0))),
            Map.entry("scala", with(common("\\b(abstract|case|catch|class|def|do|else|enum|export|extends|false|final|finally|for|forSome|given|if|implicit|import|lazy|match|new|null|object|opaque|open|override|package|private|protected|return|sealed|super|then|this|throw|trait|transparent|true|try|type|using|val|var|while|with|yield)\\b", "//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/"),
                    rule("string", "\"\"\"[\\s\\S]*?\"\"\"", 1, 0),
                    rule("property", "@[A-Za-z_][A-Za-z0-9_.]*|\\$[A-Za-z_][A-Za-z0-9_]*|\\$\\{[^}\\r\\n]+}", 3, 0))),
            Map.entry("js", with(common("\\b(as|async|await|break|case|catch|class|const|continue|debugger|default|delete|do|else|export|extends|false|finally|for|from|function|get|if|import|in|instanceof|let|new|null|of|return|set|static|super|switch|this|throw|true|try|typeof|undefined|var|void|while|with|yield)\\b", "//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/"),
                    rule("string", "`(?:[^`\\\\]|\\\\[\\s\\S])*`", 1, 0),
                    rule("property", "#[A-Za-z_$][A-Za-z0-9_$]*|\\$\\{[^}\\r\\n]+}", 3, 0))),
            Map.entry("ts", with(common("\\b(abstract|any|as|asserts|async|await|bigint|boolean|break|case|catch|class|const|constructor|continue|debugger|declare|default|delete|do|else|enum|export|extends|false|finally|for|from|function|get|global|if|implements|import|in|infer|instanceof|interface|is|keyof|let|module|namespace|never|new|null|number|object|of|override|private|protected|public|readonly|require|return|satisfies|set|static|string|super|switch|symbol|this|throw|true|try|type|typeof|undefined|unique|unknown|var|void|while|with|yield)\\b", "//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/"),
                    rule("string", "`(?:[^`\\\\]|\\\\[\\s\\S])*`", 1, 0),
                    rule("property", "@[A-Za-z_$][A-Za-z0-9_$.]*|#[A-Za-z_$][A-Za-z0-9_$]*|\\$\\{[^}\\r\\n]+}", 3, 0))),
            Map.entry("py", with(common("\\b(and|as|assert|async|await|break|case|class|continue|def|del|elif|else|except|False|finally|for|from|global|if|import|in|is|lambda|match|None|nonlocal|not|or|pass|raise|return|True|try|while|with|yield)\\b", "#[^\\r\\n]*"),
                    rule("string", "(?i:[rubf]{0,2})(?:\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?''')", 1, 0),
                    rule("property", "@[A-Za-z_][A-Za-z0-9_.]*|\\b(?:self|cls)\\.[A-Za-z_][A-Za-z0-9_]*", 3, 0),
                    rule("function", "(?<=\\bdef[ \\t])[A-Za-z_][A-Za-z0-9_]*", 4, 0))),
            Map.entry("rb", with(common("\\b(alias|and|begin|break|case|class|def|defined|do|else|elsif|end|ensure|false|for|if|in|module|next|nil|not|or|redo|rescue|retry|return|self|super|then|true|undef|unless|until|when|while|yield)\\b", "#[^\\r\\n]*|^=begin(?:\\R|$)[\\s\\S]*?^=end(?:\\R|$)"),
                    rule("string", "%[qQwWiIxr]?[({\\[<].*?[)}\\]>]", 1, 0),
                    rule("property", "@@?[A-Za-z_][A-Za-z0-9_]*|\\$[A-Za-z_][A-Za-z0-9_]*|:[A-Za-z_][A-Za-z0-9_!?=]*", 3, 0),
                    rule("function", "(?<=\\bdef[ \\t])[A-Za-z_][A-Za-z0-9_!?=]*", 4, 0))),
            Map.entry("php", with(common("(?i)\\b(__halt_compiler|abstract|and|array|as|break|callable|case|catch|class|clone|const|continue|declare|default|die|do|echo|else|elseif|empty|enddeclare|endfor|endforeach|endif|endswitch|endwhile|enum|eval|exit|extends|false|final|finally|fn|for|foreach|function|global|goto|if|implements|include|include_once|instanceof|insteadof|interface|isset|list|match|namespace|new|null|or|print|private|protected|public|readonly|require|require_once|return|static|switch|throw|trait|true|try|unset|use|var|while|xor|yield|yield from)\\b", "//[^\\r\\n]*|#[^\\r\\n]*|/\\*[\\s\\S]*?\\*/"),
                    rule("string", "`(?:[^`\\\\]|\\\\.)*`|<<<['\"]?[A-Za-z_][A-Za-z0-9_]*['\"]?\\R[\\s\\S]*?\\R[A-Za-z_][A-Za-z0-9_]*;?", 1, 0),
                    rule("property", "\\$+[A-Za-z_][A-Za-z0-9_]*|->\\s*[A-Za-z_][A-Za-z0-9_]*|::\\s*[A-Za-z_][A-Za-z0-9_]*", 3, 0),
                    rule("function", "(?i)(?<=\\bfunction[ \\t])[A-Za-z_][A-Za-z0-9_]*|\\b[A-Za-z_][A-Za-z0-9_]*(?=\\s*\\()", 4, 0))),
            Map.entry("go", with(common("\\b(break|case|chan|const|continue|default|defer|else|fallthrough|for|func|go|goto|if|import|interface|map|package|range|return|select|struct|switch|type|var|true|false|iota|nil)\\b", "//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/"),
                    rule("keyword", "\\b(any|bool|byte|comparable|complex64|complex128|error|float32|float64|int|int8|int16|int32|int64|rune|string|uint|uint8|uint16|uint32|uint64|uintptr)\\b", 2, 0),
                    rule("function", "(?<=\\bfunc[ \\t])[A-Za-z_][A-Za-z0-9_]*", 4, 0),
                    rule("property", "\\b[A-Za-z_][A-Za-z0-9_]*(?=\\s*:=)", 3, 0))),
            Map.entry("rs", with(common("\\b(as|async|await|break|const|continue|crate|dyn|else|enum|extern|false|fn|for|if|impl|in|let|loop|match|mod|move|mut|pub|ref|return|self|Self|static|struct|super|trait|true|type|union|unsafe|use|where|while|abstract|become|box|do|final|macro|override|priv|typeof|unsized|virtual|yield)\\b", "//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/"),
                    rule("property", "#[!]?\\[[^]\\r\\n]+]|'[A-Za-z_][A-Za-z0-9_]*", 3, 0),
                    rule("function", "\\b[A-Za-z_][A-Za-z0-9_]*!|(?<=\\bfn[ \\t])[A-Za-z_][A-Za-z0-9_]*", 4, 0))),
            Map.entry("c", with(common("\\b(auto|break|case|char|const|continue|default|do|double|else|enum|extern|float|for|goto|if|inline|int|long|register|restrict|return|short|signed|sizeof|static|struct|switch|typedef|union|unsigned|void|volatile|while|_Alignas|_Alignof|_Atomic|_Bool|_Complex|_Generic|_Imaginary|_Noreturn|_Static_assert|_Thread_local)\\b", "//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/"),
                    rule("property", "^[ \\t]*#[ \\t]*(?:define|elif|else|endif|error|if|ifdef|ifndef|include|line|pragma|undef|warning)\\b[^\\r\\n]*", 3, Pattern.MULTILINE),
                    rule("string", "'(?:[^'\\\\]|\\\\.)+'", 1, 0))),
            Map.entry("cpp", with(common("\\b(alignas|alignof|and|and_eq|asm|atomic_cancel|atomic_commit|atomic_noexcept|auto|bitand|bitor|bool|break|case|catch|char|char8_t|char16_t|char32_t|class|compl|concept|const|consteval|constexpr|constinit|const_cast|continue|co_await|co_return|co_yield|decltype|default|delete|do|double|dynamic_cast|else|enum|explicit|export|extern|false|float|for|friend|goto|if|inline|int|long|mutable|namespace|new|noexcept|not|not_eq|nullptr|operator|or|or_eq|private|protected|public|reflexpr|register|reinterpret_cast|requires|return|short|signed|sizeof|static|static_assert|static_cast|struct|switch|synchronized|template|this|thread_local|throw|true|try|typedef|typeid|typename|union|unsigned|using|virtual|void|volatile|wchar_t|while|xor|xor_eq)\\b", "//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/"),
                    rule("property", "^[ \\t]*#[ \\t]*(?:define|elif|else|endif|error|if|ifdef|ifndef|include|line|pragma|undef|warning)\\b[^\\r\\n]*", 3, Pattern.MULTILINE),
                    rule("string", "(?:u8|u|U|L)?R\"[^()\\s\\\\]{0,16}\\([\\s\\S]*?\\)[^()\\s\\\\]{0,16}\"|(?:u8|u|U|L)?'(?:[^'\\\\]|\\\\.)+'", 1, 0),
                    rule("property", "\\b(?:std|this)::?[A-Za-z_][A-Za-z0-9_]*", 3, 0))),
            Map.entry("cs", with(common("\\b(abstract|as|async|await|base|bool|break|byte|case|catch|char|checked|class|const|continue|decimal|default|delegate|do|double|else|enum|event|explicit|extern|false|finally|fixed|float|for|foreach|goto|if|implicit|in|init|int|interface|internal|is|lock|long|namespace|new|nint|not|null|nuint|object|operator|or|out|override|params|partial|private|protected|public|readonly|record|ref|required|return|sbyte|scoped|sealed|short|sizeof|stackalloc|static|string|struct|switch|this|throw|true|try|typeof|uint|ulong|unchecked|unsafe|ushort|using|var|virtual|void|volatile|when|where|while|with|yield)\\b", "//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/"),
                    rule("string", "\\$?@\"(?:\"\"|[^\"])*\"|\\$\"(?:[^\"\\\\]|\\\\.)*\"", 1, 0),
                    rule("property", "\\[[A-Za-z_][A-Za-z0-9_.]*(?:Attribute)?(?:\\([^]\\r\\n]*\\))?]", 3, 0))),
            Map.entry("sh", with(common("\\b(case|coproc|do|done|elif|else|esac|fi|for|function|if|in|select|then|time|until|while|break|continue|declare|export|local|readonly|return|set|shift|source|typeset|unset|true|false)\\b", "#[^\\r\\n]*"),
                    rule("property", "\\$[A-Za-z_][A-Za-z0-9_]*|\\$[0-9@*#?$!_-]|\\$\\{[^}\\r\\n]+}|\\$\\([^\\r\\n)]*\\)", 3, 0),
                    rule("function", "^[ \\t]*(?:function[ \\t]+)?[A-Za-z_][A-Za-z0-9_.-]*(?=[ \\t]*(?:\\(\\))?[ \\t]*\\{)", 4, Pattern.MULTILINE))),
            Map.entry("ps1", with(common("(?i)\\b(begin|break|catch|class|continue|data|define|do|dynamicparam|else|elseif|end|enum|exit|filter|finally|for|foreach|from|function|hidden|if|in|param|process|return|static|switch|throw|trap|try|until|using|var|while|workflow|true|false|null)\\b", "#[^\\r\\n]*|<#[\\s\\S]*?#>"),
                    rule("property", "\\$\\{?[_A-Za-z][A-Za-z0-9_:?]*}?|@\\{[^}\\r\\n]*}", 3, 0),
                    rule("function", "\\b[A-Za-z][A-Za-z0-9]*-[A-Za-z][A-Za-z0-9-]*\\b", 4, Pattern.CASE_INSENSITIVE))),
            Map.entry("bat", List.of(
                    rule("comment", "^[ \\t]*(?:rem\\b|::)[^\\r\\n]*", 0, Pattern.MULTILINE | Pattern.CASE_INSENSITIVE),
                    rule("string", "\"[^\"]*\"", 1, 0),
                    rule("property", "%[^%\\r\\n]+%|![^!\\r\\n]+!", 2, 0),
                    rule("keyword", "\\b(call|cd|chdir|cls|copy|del|dir|echo|endlocal|erase|exit|for|goto|if|md|mkdir|move|pause|popd|pushd|rd|ren|rename|rmdir|set|setlocal|shift|start|type)\\b", 3, Pattern.CASE_INSENSITIVE))),
            Map.entry("pl", with(common("\\b(__DATA__|__END__|and|cmp|continue|CORE|do|else|elsif|eq|for|foreach|ge|given|goto|grep|gt|if|last|le|local|lt|map|my|ne|next|no|not|or|our|package|print|printf|q|qq|qr|qw|qx|redo|require|return|say|sort|state|sub|tr|unless|until|use|when|while|xor|y|undef)\\b", "#[^\\r\\n]*|^=(?:pod|head\\d|item|over|back|begin|end|for|encoding)[\\s\\S]*?^=cut[^\\r\\n]*"),
                    rule("property", "[$@%][A-Za-z_][A-Za-z0-9_:]*|[$@%]\\{[^}\\r\\n]+}", 3, 0),
                    rule("function", "(?<=\\bsub[ \\t])[A-Za-z_][A-Za-z0-9_:]*", 4, 0))),
            Map.entry("lua", with(common("\\b(and|break|do|else|elseif|end|false|for|function|goto|if|in|local|nil|not|or|repeat|return|then|true|until|while)\\b", "--\\[=*\\[[\\s\\S]*?]\\=*]|--[^\\r\\n]*"),
                    rule("string", "\\[=*\\[[\\s\\S]*?]\\=*]", 1, 0),
                    rule("property", "\\b[A-Za-z_][A-Za-z0-9_]*(?=\\s*=)", 3, 0))),
            Map.entry("swift", with(common("\\b(associatedtype|actor|as|async|await|break|case|catch|class|continue|convenience|copy|default|defer|deinit|didSet|distributed|do|dynamic|else|enum|extension|fallthrough|false|fileprivate|final|for|func|get|guard|if|import|indirect|in|infix|init|inout|internal|is|isolated|lazy|let|macro|mutating|nil|nonisolated|nonmutating|open|operator|optional|override|package|postfix|precedencegroup|prefix|private|protocol|public|repeat|required|rethrows|return|self|Self|set|some|static|struct|subscript|super|switch|throw|throws|true|try|typealias|unowned|var|weak|where|while|willSet|yield)\\b", "//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/"),
                    rule("string", "#*\"(?:[^\"\\\\]|\\\\.)*\"#*|\"\"\"[\\s\\S]*?\"\"\"", 1, 0),
                    rule("property", "@[A-Za-z_][A-Za-z0-9_]*|#[A-Za-z_][A-Za-z0-9_]*|\\\\\\([^)]*\\)", 3, 0))),
            Map.entry("dart", with(common("\\b(abstract|as|assert|async|await|base|break|case|catch|class|const|continue|covariant|default|deferred|do|dynamic|else|enum|export|extends|extension|external|factory|false|final|finally|for|Function|get|hide|if|implements|import|in|interface|is|late|library|mixin|new|null|of|on|operator|part|required|rethrow|return|sealed|set|show|static|super|switch|sync|this|throw|true|try|typedef|var|void|when|while|with|yield)\\b", "//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/"),
                    rule("string", "r?'''[\\s\\S]*?'''|r?\"\"\"[\\s\\S]*?\"\"\"", 1, 0),
                    rule("property", "@[A-Za-z_][A-Za-z0-9_.]*|\\$[A-Za-z_][A-Za-z0-9_]*|\\$\\{[^}\\r\\n]+}", 3, 0))),
            Map.entry("r", with(common("\\b(function|if|else|for|in|while|repeat|next|break|TRUE|FALSE|NULL|Inf|NaN|NA|NA_integer_|NA_real_|NA_complex_|NA_character_)\\b", "#[^\\r\\n]*"),
                    rule("string", "`[^`\\r\\n]+`", 1, 0),
                    rule("property", "\\b[A-Za-z.][A-Za-z0-9._]*(?=\\s*<-)|\\$[A-Za-z.][A-Za-z0-9._]*", 3, 0))),
            Map.entry("sql", List.of(
                    rule("comment", "--[^\\r\\n]*|/\\*[^*]*+(?:\\*(?!/)[^*]*+)*\\*/", 0, Pattern.MULTILINE),
                    rule("string", "'(?:''|[^'])*+'|\"(?:\"\"|[^\"])*+\"", 1, Pattern.MULTILINE),
                    rule("keyword", "\\b(SELECT|FROM|WHERE|INSERT|INTO|VALUES|UPDATE|SET|DELETE|CREATE|TABLE|ALTER|DROP|JOIN|LEFT|RIGHT|INNER|OUTER|UNION|GROUP\\s+BY|ORDER\\s+BY|HAVING|AND|OR|NOT|IN|LIKE|BETWEEN|EXISTS|CASE|WHEN|THEN|END|DISTINCT|LIMIT|OFFSET|AS|ON)\\b", 3, Pattern.MULTILINE | Pattern.CASE_INSENSITIVE),
                    rule("function", "\\b(COUNT|SUM|AVG|MIN|MAX|COALESCE|NULLIF|NOW|CURRENT_TIMESTAMP|DATE|ROUND|LOWER|UPPER|SUBSTRING|CAST|CONVERT)\\b(?=\\s*\\()", 2, Pattern.MULTILINE | Pattern.CASE_INSENSITIVE))),
            Map.entry("json", List.of(rule("comment", "//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/", 0, Pattern.MULTILINE), rule("property", "\"(?:[^\"\\\\]|\\\\.)*\"(?=\\s*:)", 2, 0), rule("string", "\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'", 1, 0), rule("number", "(?<![A-Za-z_])-?(?:0[xX][0-9a-fA-F]+|\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)\\b", 3, 0), rule("keyword", "\\b(true|false|null|NaN|Infinity|undefined)\\b", 2, 0))),
            Map.entry("xml", markup()), Map.entry("html", markup()),
            Map.entry("css", List.of(rule("comment", "/\\*[\\s\\S]*?\\*/", 0, 0), rule("string", "\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'", 1, 0), rule("keyword", "@[A-Za-z-]+|!important\\b|\\b(?:inherit|initial|revert|unset|none|auto)\\b", 2, Pattern.CASE_INSENSITIVE), rule("property", "--[A-Za-z0-9_-]+|[A-Za-z-]+(?=\\s*:)", 2, 0), rule("function", "\\b(?:var|calc|min|max|clamp|rgb|rgba|hsl|hsla|url|linear-gradient|radial-gradient|conic-gradient|translate|rotate|scale)(?=\\s*\\()", 3, Pattern.CASE_INSENSITIVE), rule("number", "(?:#[0-9a-fA-F]{3,8}\\b)|(?<![A-Za-z_-])-?(?:\\d*\\.)?\\d+(?:%|[A-Za-z]+)?", 3, 0))),
            Map.entry("yaml", List.of(rule("comment", "#[^\\r\\n]*", 0, Pattern.MULTILINE), rule("string", "\"(?:[^\"\\\\]|\\\\.)*\"|'(?:''|[^'])*'|^[ \\t]*[|>][-+]?[^\\r\\n]*", 1, Pattern.MULTILINE), rule("property", "^[ \\t-]*(?:[?][ \\t]+)?[^#\\s][^:\\r\\n]*?(?=[ \\t]*:(?:[ \\t]|$))", 2, Pattern.MULTILINE), rule("property", "[&*!][A-Za-z0-9_.-]+|^---$|^\\.\\.\\.$", 3, Pattern.MULTILINE), rule("number", "(?<![A-Za-z_])-?(?:0[xX][0-9a-fA-F]+|0[oO][0-7]+|\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)\\b", 3, 0), rule("keyword", "\\b(true|false|null|yes|no|on|off|y|n|~)\\b", 2, Pattern.CASE_INSENSITIVE))),
            Map.entry("toml", List.of(rule("comment", "#[^\\r\\n]*", 0, Pattern.MULTILINE), rule("string", "\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''|\"(?:[^\"\\\\]|\\\\.)*\"|'[^']*'", 1, 0), rule("keyword", "^[ \\t]*\\[\\[?[^]\\r\\n]+\\]\\]?", 2, Pattern.MULTILINE), rule("property", "^[ \\t]*(?:[A-Za-z0-9_-]+|\"(?:[^\"\\\\]|\\\\.)*\"|'[^']*')(?:\\s*\\.\\s*(?:[A-Za-z0-9_-]+|\"[^\"]*\"|'[^']*'))*(?=[ \\t]*=)", 2, Pattern.MULTILINE), rule("number", "(?<![A-Za-z_])-?(?:0[xX][0-9a-fA-F_]+|0[oO][0-7_]+|0[bB][01_]+|\\d[\\d_]*(?:\\.[\\d_]+)?(?:[eE][+-]?[\\d_]+)?)\\b", 3, 0), rule("function", "\\b\\d{4}-\\d{2}-\\d{2}(?:[Tt ][0-9:.+-]+[Zz]?)?\\b", 3, 0), rule("keyword", "\\b(true|false|inf|nan)\\b", 2, Pattern.CASE_INSENSITIVE))),
            Map.entry("ini", configRules("^[ \\t]*[;#][^\\r\\n]*")),
            Map.entry("properties", configRules("^[ \\t]*[#!][^\\r\\n]*")),
            Map.entry("conf", List.of(rule("comment", "^[ \\t]*[#;][^\\r\\n]*", 0, Pattern.MULTILINE), rule("string", "\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'", 1, 0), rule("keyword", "^[ \\t]*\\[[^]\\r\\n]+]|^[ \\t]*</?[A-Za-z_][^>\\r\\n]*>", 2, Pattern.MULTILINE), rule("property", "^[ \\t]*[A-Za-z_][A-Za-z0-9_.-]*(?=[ \\t]*(?:=|:|\\s))", 2, Pattern.MULTILINE), rule("property", "\\$\\{?[A-Za-z_][A-Za-z0-9_]*}?|%[A-Za-z_][A-Za-z0-9_]*%", 3, 0), rule("number", "(?<![A-Za-z_])-?\\d+(?:\\.\\d+)?(?:ms|s|m|h|d|k|m|g|kb|mb|gb|%)?\\b", 3, Pattern.CASE_INSENSITIVE), rule("keyword", "\\b(true|false|yes|no|on|off|null|none|allow|deny)\\b", 2, Pattern.CASE_INSENSITIVE))),
            Map.entry("docker", List.of(rule("comment", "^[ \\t]*#[^\\r\\n]*", 0, Pattern.MULTILINE), rule("string", "\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'", 1, 0), rule("property", "\\$\\{?[A-Za-z_][A-Za-z0-9_]*(?::[-+?][^}\\r\\n]*)?}?|--[A-Za-z][A-Za-z0-9-]*(?==)", 2, 0), rule("function", "(?i)(?<=\\bFROM[ \\t])[^\\s]+(?:[ \\t]+AS[ \\t]+[A-Za-z0-9_.-]+)?", 3, 0), rule("keyword", "^[ \\t]*(ADD|ARG|CMD|COPY|ENTRYPOINT|ENV|EXPOSE|FROM|HEALTHCHECK|LABEL|MAINTAINER|ONBUILD|RUN|SHELL|STOPSIGNAL|USER|VOLUME|WORKDIR)\\b", 3, Pattern.MULTILINE | Pattern.CASE_INSENSITIVE))),
            Map.entry("make", List.of(rule("comment", "(?<!\\$)#[^\\r\\n]*", 0, Pattern.MULTILINE), rule("string", "\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'", 1, 0), rule("property", "\\$[@%<?^+*|]|\\$[({][^)}\\r\\n]+[)}]|^[A-Za-z_][A-Za-z0-9_.-]*(?=[ \\t]*(?::?=|\\?=|\\+=|!=))", 2, Pattern.MULTILINE), rule("function", "^[^#\\s][^:=\\r\\n]*(?=:(?!=))|\\$\\((?:addprefix|addsuffix|basename|call|dir|filter|filter-out|findstring|firstword|foreach|join|notdir|origin|patsubst|shell|sort|strip|subst|suffix|value|wildcard|word|wordlist|words)\\b", 3, Pattern.MULTILINE), rule("keyword", "\\b(define|else|endef|endif|export|if|ifdef|ifeq|ifndef|ifneq|include|override|private|sinclude|undefine|unexport|vpath)\\b", 2, 0))),
            Map.entry("hcl", List.of(rule("comment", "#[^\\r\\n]*|//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/", 0, Pattern.MULTILINE), rule("string", "<<-?[A-Za-z_][A-Za-z0-9_]*\\R[\\s\\S]*?\\R[A-Za-z_][A-Za-z0-9_]*|\"(?:[^\"\\\\]|\\\\.)*\"", 1, 0), rule("property", "^[ \\t]*[A-Za-z_][A-Za-z0-9_-]*(?=[ \\t]*=)|\\$\\{[^}\\r\\n]+}|\\b(?:var|local|module|data|path|terraform|each|count)\\.[A-Za-z_][A-Za-z0-9_.-]*", 2, Pattern.MULTILINE), rule("keyword", "\\b(resource|data|variable|output|module|provider|terraform|locals|dynamic|for|in|if|true|false|null)\\b", 2, 0), rule("function", "\\b[A-Za-z_][A-Za-z0-9_-]*(?=\\s*\\()", 3, 0), rule("number", "(?<![A-Za-z_])-?\\d+(?:\\.\\d+)?\\b", 3, 0))),
            Map.entry("graphql", List.of(rule("comment", "#[^\\r\\n]*", 0, Pattern.MULTILINE), rule("string", "\"\"\"[\\s\\S]*?\"\"\"|\"(?:[^\"\\\\]|\\\\.)*\"", 1, 0), rule("keyword", "\\b(query|mutation|subscription|fragment|schema|scalar|type|interface|union|enum|input|extend|directive|implements|repeatable|on|true|false|null)\\b", 2, 0), rule("property", "\\$[A-Za-z_][A-Za-z0-9_]*|@[A-Za-z_][A-Za-z0-9_]*", 3, 0), rule("function", "\\b[A-Za-z_][A-Za-z0-9_]*(?=\\s*\\()", 3, 0), rule("number", "(?<![A-Za-z_])-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\b", 3, 0))),
            Map.entry("proto", with(common("\\b(syntax|import|weak|public|package|option|message|enum|service|rpc|returns|stream|repeated|optional|required|reserved|oneof|map|extensions|extend|to|max|true|false)\\b", "//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/"),
                    rule("keyword", "\\b(double|float|int32|int64|uint32|uint64|sint32|sint64|fixed32|fixed64|sfixed32|sfixed64|bool|string|bytes)\\b", 2, 0),
                    rule("property", "^[ \\t]*(?:repeated|optional|required)?[ \\t]*[.A-Za-z_][A-Za-z0-9_.<>]*[ \\t]+[A-Za-z_][A-Za-z0-9_]*(?=[ \\t]*=)", 3, Pattern.MULTILINE),
                    rule("function", "(?<=\\brpc[ \\t])[A-Za-z_][A-Za-z0-9_]*", 4, 0))),
            Map.entry("cmake", List.of(rule("comment", "#[^\\r\\n]*|#\\[=*[\\s\\S]*?]=*]", 0, Pattern.MULTILINE), rule("string", "\"(?:[^\"\\\\]|\\\\.)*\"", 1, 0), rule("property", "\\$\\{[^}]+}", 2, 0), rule("function", "\\b[A-Za-z_][A-Za-z0-9_]*(?=\\s*\\()", 3, Pattern.CASE_INSENSITIVE), rule("keyword", "\\b(if|elseif|else|endif|foreach|endforeach|while|endwhile|function|endfunction|macro|endmacro|return|break|continue)\\b", 2, Pattern.CASE_INSENSITIVE))),
            Map.entry("diff", List.of(rule("comment", "^(?:---|\\+\\+\\+|@@).*?$", 0, Pattern.MULTILINE), rule("string", "^\\+[^+].*$", 1, Pattern.MULTILINE), rule("keyword", "^-[^-].*$", 2, Pattern.MULTILINE))),
            Map.entry("md", List.of(rule("comment", "<!--[\\s\\S]*?-->", 0, 0), rule("string", "```[\\s\\S]*?```|~~~[\\s\\S]*?~~~|`[^`\\r\\n]+`", 1, Pattern.MULTILINE), rule("keyword", "^(?:#{1,6}[ \\t]+.*|[=-]{3,}[ \\t]*|>[ \\t]?.*|[ \\t]*(?:[-+*]|\\d+[.)])[ \\t]+.*)$", 2, Pattern.MULTILINE), rule("property", "(?:\\*\\*|__|~~)(?=\\S).+?(?<=\\S)(?:\\*\\*|__|~~)|(?<!\\*)\\*(?=\\S).+?(?<=\\S)\\*", 3, 0), rule("function", "!?\\[[^]\\r\\n]+]\\([^\\s)]+(?:[ \\t]+\"[^\"]*\")?\\)|\\[[^]\\r\\n]+]\\[[^]\\r\\n]*]", 3, 0)))
    );

    private SyntaxHighlighting() { }

    static Binding install(CodeArea area, String fileName) {
        java.util.concurrent.atomic.AtomicReference<String> language = new java.util.concurrent.atomic.AtomicReference<>(
                java.util.Objects.requireNonNullElse(language(fileName), "plain"));
        TextMateSyntaxHighlighter.Session textMate = TextMateSyntaxHighlighter.session();
        AtomicLong revision = new AtomicLong();
        Runnable schedule = () -> {
            long requested = revision.incrementAndGet();
            String content = area.getText();
            String requestedLanguage = language.get();
            CompletableFuture.supplyAsync(() -> compute(content, requestedLanguage, textMate), EXECUTOR)
                    .thenAccept(spans -> Platform.runLater(() -> {
                        if (revision.get() == requested && area.getLength() == spans.length()) area.setStyleSpans(0, spans);
                    }));
        };
        PauseTransition debounce = new PauseTransition();
        org.reactfx.Subscription subscription = area.multiPlainChanges().subscribe(change -> {
            debounce.stop();
            debounce.setDuration(javafx.util.Duration.millis(language.get().equals("sql") ? 700 : 300));
            debounce.setOnFinished(event -> schedule.run());
            debounce.playFromStart();
        });
        area.sceneProperty().addListener((ignored, before, after) -> {
            if (before != null && after == null) { debounce.stop(); subscription.unsubscribe(); }
        });
        schedule.run();
        return new Binding() {
            @Override public String language() { return language.get(); }
            @Override public void setLanguage(String selected) {
                String normalized = supportedLanguages().contains(selected) ? selected : "plain";
                language.set(normalized);
                schedule.run();
            }
            @Override public void refresh() { schedule.run(); }
        };
    }

    static List<String> supportedLanguages() {
        return List.of("plain", "java", "kt", "groovy", "scala", "js", "ts", "py", "rb", "php",
                "go", "rs", "c", "cpp", "cs", "swift", "dart", "r", "sh", "ps1", "bat", "pl",
                "lua", "sql", "json", "xml", "html", "css", "yaml", "toml", "ini", "properties",
                "conf", "docker", "make", "hcl", "graphql", "proto", "cmake", "diff", "md");
    }

    static String displayName(String language) {
        return switch (language) {
            case "plain" -> "Plain Text"; case "js" -> "JavaScript"; case "ts" -> "TypeScript";
            case "kt" -> "Kotlin"; case "py" -> "Python"; case "rb" -> "Ruby"; case "php" -> "PHP";
            case "rs" -> "Rust"; case "toml" -> "TOML";
            case "cpp" -> "C++"; case "cs" -> "C#"; case "sh" -> "Shell"; case "ps1" -> "PowerShell";
            case "bat" -> "Batch"; case "pl" -> "Perl"; case "r" -> "R"; case "docker" -> "Dockerfile";
            case "make" -> "Makefile"; case "hcl" -> "HCL / Terraform"; case "graphql" -> "GraphQL";
            case "proto" -> "Protocol Buffers"; case "cmake" -> "CMake"; case "diff" -> "Diff / Patch";
            case "ini" -> "INI / Config"; case "properties" -> "Properties / Env";
            case "conf" -> "Generic Config";
            case "json" -> "JSON"; case "xml" -> "XML"; case "html" -> "HTML"; case "css" -> "CSS";
            case "yaml" -> "YAML"; case "md" -> "Markdown"; case "sql" -> "SQL";
            default -> language.substring(0, 1).toUpperCase(Locale.ROOT) + language.substring(1);
        };
    }

    interface Binding {
        String language();
        void setLanguage(String language);
        void refresh();
    }

    static String language(String fileName) {
        if (fileName == null) return null;
        String lower = fileName.replace('\\', '/').toLowerCase(Locale.ROOT);
        lower = lower.substring(lower.lastIndexOf('/') + 1);
        if (lower.equals("dockerfile") || lower.equals("containerfile")
                || lower.startsWith("dockerfile.") || lower.startsWith("containerfile.")) return "docker";
        if (lower.equals("makefile") || lower.equals("gnumakefile")) return "make";
        if (lower.equals("cmakelists.txt")) return "cmake";
        if (lower.equals("jenkinsfile")) return "groovy";
        if (List.of("gemfile", "rakefile", "vagrantfile", "podfile").contains(lower)) return "rb";
        if (lower.equals("package.swift")) return "swift";
        if (List.of(".bashrc", ".bash_profile", ".bash_aliases", ".zshrc", ".zprofile", ".profile", "gradlew").contains(lower)) return "sh";
        if (List.of(".editorconfig", ".gitconfig").contains(lower)) return "ini";
        if (lower.equals(".env") || lower.startsWith(".env.") || List.of(".npmrc", ".yarnrc").contains(lower)) return "properties";
        if (List.of(".gitignore", ".gitattributes", ".dockerignore", ".npmignore", ".eslintignore", ".prettierignore", ".htaccess", "hosts").contains(lower)) return "conf";
        int dot = lower.lastIndexOf('.');
        String extension = dot < 0 ? lower : lower.substring(dot + 1);
        return switch (extension) {
            case "java" -> "java"; case "kt", "kts" -> "kt"; case "groovy", "gradle" -> "groovy";
            case "scala", "sc" -> "scala"; case "js", "jsx", "mjs", "cjs" -> "js";
            case "ts", "tsx", "mts", "cts" -> "ts"; case "py", "pyw", "pyi" -> "py";
            case "rb" -> "rb"; case "php", "php3", "php4", "php5", "phtml" -> "php";
            case "go" -> "go"; case "rs" -> "rs";
            case "c", "h" -> "c"; case "cc", "cpp", "cxx", "hpp", "hxx" -> "cpp";
            case "cs", "csx" -> "cs"; case "swift" -> "swift"; case "dart" -> "dart"; case "r" -> "r";
            case "sh", "bash", "zsh", "ksh", "fish", "command" -> "sh";
            case "ps1", "psm1", "psd1" -> "ps1"; case "bat", "cmd" -> "bat";
            case "pl", "pm" -> "pl"; case "lua" -> "lua"; case "sql", "ddl", "dml" -> "sql";
            case "json", "jsonc", "json5", "jsonl", "ndjson", "geojson" -> "json";
            case "xml", "fxml", "svg", "xsd", "xsl", "xslt", "plist" -> "xml";
            case "html", "htm", "xhtml", "vue", "svelte" -> "html";
            case "css", "scss", "sass", "less" -> "css";
            case "yaml", "yml" -> "yaml"; case "toml" -> "toml";
            case "ini", "cfg", "desktop", "service", "socket", "timer", "mount", "target", "path" -> "ini";
            case "properties", "env" -> "properties"; case "conf", "config", "cnf" -> "conf";
            case "mk", "mak" -> "make"; case "tf", "tfvars", "hcl" -> "hcl";
            case "graphql", "gql" -> "graphql"; case "proto" -> "proto"; case "cmake" -> "cmake";
            case "diff", "patch", "rej" -> "diff"; case "md", "markdown", "mdown", "mkd" -> "md";
            default -> null;
        };
    }

    static StyleSpans<Collection<String>> compute(String content, String language) {
        if (content.isEmpty()) return StyleSpans.singleton(Collections.emptyList(), 0);
        int limit = language.equals("sql") ? SQL_HIGHLIGHT_LIMIT : GENERAL_HIGHLIGHT_LIMIT;
        if (!language.equals("sql") && TextMateSyntaxHighlighter.supports(language)) {
            StyleSpans<Collection<String>> spans = TextMateSyntaxHighlighter.compute(content, language, limit);
            if (spans != null) return spans;
        }
        return computeRegex(content, language, limit);
    }

    private static StyleSpans<Collection<String>> compute(
            String content, String language, TextMateSyntaxHighlighter.Session textMate) {
        if (content.isEmpty()) return StyleSpans.singleton(Collections.emptyList(), 0);
        int limit = language.equals("sql") ? SQL_HIGHLIGHT_LIMIT : GENERAL_HIGHLIGHT_LIMIT;
        if (!language.equals("sql") && TextMateSyntaxHighlighter.supports(language)) {
            StyleSpans<Collection<String>> spans = textMate.compute(content, language, limit);
            if (spans != null) return spans;
        }
        return computeRegex(content, language, limit);
    }

    private static StyleSpans<Collection<String>> computeRegex(String content, String language, int limit) {
        String source = content.substring(0, Math.min(content.length(), limit));
        List<MatchSpan> matches = new ArrayList<>();
        for (Rule rule : RULES.getOrDefault(language, List.of())) {
            try {
                Matcher matcher = rule.pattern.matcher(source);
                while (matcher.find()) if (matcher.end() > matcher.start())
                    matches.add(new MatchSpan(matcher.start(), matcher.end(), rule.style, rule.priority));
            } catch (RuntimeException | StackOverflowError ignored) { }
        }
        matches.sort((a, b) -> a.start != b.start ? Integer.compare(a.start, b.start)
                : a.priority != b.priority ? Integer.compare(a.priority, b.priority)
                : Integer.compare(b.end - b.start, a.end - a.start));
        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
        int position = 0;
        for (MatchSpan match : matches) {
            if (match.end <= position) continue;
            int start = Math.max(position, match.start);
            if (start > position) builder.add(Collections.emptyList(), start - position);
            builder.add(Collections.singleton(match.style), match.end - start);
            position = match.end;
        }
        if (position < content.length()) builder.add(Collections.emptyList(), content.length() - position);
        return builder.create();
    }

    private static List<Rule> common(String keywords, String comments) {
        return List.of(
                rule("comment", comments, 0, Pattern.MULTILINE),
                rule("string", "\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'", 1, Pattern.MULTILINE),
                rule("keyword", keywords, 2, Pattern.MULTILINE),
                rule("number", "(?<![A-Za-z_$])(?:0[xX][0-9a-fA-F_]+|0[bB][01_]+|0[oO][0-7_]+|(?:\\d[\\d_]*\\.?[\\d_]*|\\.[\\d_]+)(?:[eE][+-]?[\\d_]+)?)[fFdDlLuU]*\\b", 5, 0),
                rule("function", "\\b[A-Za-z_$][A-Za-z0-9_$]*(?=\\s*\\()", 4, 0));
    }
    private static List<Rule> with(List<Rule> base, Rule... additions) {
        List<Rule> rules = new ArrayList<>(base.size() + additions.length);
        rules.addAll(base);
        Collections.addAll(rules, additions);
        return List.copyOf(rules);
    }
    private static List<Rule> configRules(String comments) {
        return List.of(rule("comment", comments, 0, Pattern.MULTILINE),
                rule("string", "\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'", 1, 0),
                rule("keyword", "^[ \\t]*\\[[^]\\r\\n]+]", 2, Pattern.MULTILINE),
                rule("property", "^[ \\t]*(?:[^#!;\\s\\\\]|\\\\.)+?(?=[ \\t]*[=:])|\\$\\{?[A-Za-z_][A-Za-z0-9_]*}?|%[A-Za-z_][A-Za-z0-9_]*%", 2, Pattern.MULTILINE),
                rule("number", "(?<![A-Za-z_])-?\\d+(?:\\.\\d+)?(?:ms|s|m|h|d|k|m|g|kb|mb|gb|%)?\\b", 3, Pattern.CASE_INSENSITIVE),
                rule("keyword", "\\b(true|false|yes|no|on|off|null|none)\\b", 2, Pattern.CASE_INSENSITIVE));
    }
    private static List<Rule> markup() {
        return List.of(
                rule("comment", "<!--[\\s\\S]*?-->|<!\\[CDATA\\[[\\s\\S]*?]]>", 0, 0),
                rule("string", "\"[^\"]*\"|'[^']*'", 1, 0),
                rule("property", "\\b[A-Za-z_:][A-Za-z0-9:._-]*(?=\\s*=)|&(?:#[0-9]+|#x[0-9a-fA-F]+|[A-Za-z][A-Za-z0-9]+);", 2, 0),
                rule("keyword", "</?\\s*[A-Za-z][A-Za-z0-9:-]*|/?>|<!DOCTYPE\\b[^>]*>", 3, Pattern.CASE_INSENSITIVE));
    }
    private static Rule rule(String style, String regex, int priority, int flags) { return new Rule(style, Pattern.compile(regex, flags), priority); }
    private record Rule(String style, Pattern pattern, int priority) { }
    private record MatchSpan(int start, int end, String style, int priority) { }
}
