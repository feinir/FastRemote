// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.snippet;

import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class SnippetTemplateEngine {
    private static final int MAX_RANDOM_LENGTH = 4096;
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String HEX = "0123456789abcdef";

    private final Clock clock;
    private final SecureRandom random;

    public SnippetTemplateEngine() {
        this(Clock.systemDefaultZone(), new SecureRandom());
    }

    public SnippetTemplateEngine(Clock clock, SecureRandom random) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
    }

    public SnippetTemplateAnalysis analyze(String template) {
        List<Expression> expressions = parse(template);
        Map<String, SnippetInput> inputs = new LinkedHashMap<>();
        Set<String> variables = new LinkedHashSet<>();
        Set<String> sessionFields = new LinkedHashSet<>();
        for (Expression expression : expressions) {
            switch (expression.kind) {
                case "input" -> {
                    SnippetInput input = input(expression);
                    SnippetInput previous = inputs.putIfAbsent(input.name(), input);
                    if (previous != null && !previous.equals(input)) {
                        throw new SnippetTemplateException("同名输入项定义不一致：" + input.name());
                    }
                    input.variableName().ifPresent(variables::add);
                }
                case "variable" -> variables.add(variable(expression).name());
                case "session" -> sessionFields.add(sessionField(expression));
                case "now" -> validateNow(expression);
                case "random" -> validateRandom(expression);
                default -> throw new SnippetTemplateException("未知模板类型：" + expression.kind);
            }
            validateEncodings(expression.encodings);
        }
        return new SnippetTemplateAnalysis(new ArrayList<>(inputs.values()), variables, sessionFields);
    }

    public String render(String template, SnippetTemplateContext context) {
        Objects.requireNonNull(context, "context");
        List<Segment> segments = parseSegments(template);
        StringBuilder result = new StringBuilder(template.length());
        for (Segment segment : segments) {
            if (segment.literal != null) {
                result.append(segment.literal);
                continue;
            }
            Expression expression = segment.expression;
            String value = switch (expression.kind) {
                case "input" -> resolveInput(input(expression), context.inputs());
                case "variable" -> resolveVariable(variable(expression), context.variables());
                case "session" -> required(context.sessionFields(), sessionField(expression), "当前连接属性不可用：");
                case "now" -> now(expression);
                case "random" -> random(expression);
                default -> throw new SnippetTemplateException("未知模板类型：" + expression.kind);
            };
            result.append(applyEncodings(value, expression.encodings));
        }
        return result.toString();
    }

    private static String resolveInput(SnippetInput input, Map<String, String> values) {
        String value = values.get(input.name());
        if (value == null) value = input.defaultValue().orElse(null);
        if (value == null) throw new SnippetTemplateException("缺少本次输入：" + input.name());
        if (input.required() && value.isBlank()) throw new SnippetTemplateException("本次输入不能为空：" + input.name());
        return value;
    }

    private static String resolveVariable(
            VariableReference reference, Map<String, SnippetVariable> variables) {
        SnippetVariable variable = variables.get(reference.name());
        if (variable == null) throw new SnippetTemplateException("变量不存在：" + reference.name());
        return variable.indexedValue(reference.index());
    }

    private static String required(Map<String, String> values, String name, String prefix) {
        String value = values.get(name);
        if (value == null) throw new SnippetTemplateException(prefix + name);
        return value;
    }

    private String now(Expression expression) {
        String pattern = requireArgument(expression, 0, "now 缺少时间格式");
        try {
            DateTimeFormatter formatter = new DateTimeFormatterBuilder().appendPattern(pattern)
                    .toFormatter(Locale.ENGLISH);
            return formatter.format(ZonedDateTime.now(clock));
        } catch (IllegalArgumentException failure) {
            throw new SnippetTemplateException("now 时间格式不正确：" + pattern, failure);
        }
    }

    private static void validateNow(Expression expression) {
        if (expression.arguments.size() != 1) throw new SnippetTemplateException("now 需要且只接受一个时间格式");
        try {
            new DateTimeFormatterBuilder().appendPattern(expression.arguments.getFirst()).toFormatter(Locale.ENGLISH);
        } catch (IllegalArgumentException failure) {
            throw new SnippetTemplateException("now 时间格式不正确：" + expression.arguments.getFirst(), failure);
        }
    }

    private String random(Expression expression) {
        validateRandom(expression);
        String type = expression.arguments.getFirst().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "string" -> characters(ALPHANUMERIC, length(expression.arguments.get(1)));
            case "number" -> number(length(expression.arguments.get(1)));
            case "hex" -> characters(HEX, length(expression.arguments.get(1)));
            case "lower" -> characters(LOWER, length(expression.arguments.get(1)));
            case "upper" -> characters(UPPER, length(expression.arguments.get(1)));
            case "ip" -> ipv4();
            case "float" -> decimal(expression.arguments.get(1));
            case "int" -> integer(expression.arguments.get(1));
            case "uuid" -> uuid(expression.arguments.size() == 2);
            default -> throw new SnippetTemplateException("未知 random 类型：" + type);
        };
    }

    private static void validateRandom(Expression expression) {
        if (expression.arguments.isEmpty()) throw new SnippetTemplateException("random 缺少类型");
        String type = expression.arguments.getFirst().toLowerCase(Locale.ROOT);
        switch (type) {
            case "string", "number", "hex", "lower", "upper" -> {
                if (expression.arguments.size() != 2) throw new SnippetTemplateException("random " + type + " 需要长度");
                length(expression.arguments.get(1));
            }
            case "ip" -> {
                if (expression.arguments.size() != 1) throw new SnippetTemplateException("random IP 不接受额外参数");
            }
            case "float" -> {
                if (expression.arguments.size() != 2 || !expression.arguments.get(1).matches("[1-9][0-9]{0,3}\\.[1-9][0-9]{0,3}")) {
                    throw new SnippetTemplateException("random float 格式必须为 m.n，且位数大于 0");
                }
                String[] sizes = expression.arguments.get(1).split("\\.");
                length(sizes[0]);
                length(sizes[1]);
            }
            case "int" -> {
                if (expression.arguments.size() != 2) throw new SnippetTemplateException("random int 需要上限或 a-b 范围");
                parseIntegerRange(expression.arguments.get(1));
            }
            case "uuid" -> {
                if (expression.arguments.size() > 2
                        || expression.arguments.size() == 2 && !"non".equalsIgnoreCase(expression.arguments.get(1))) {
                    throw new SnippetTemplateException("random uuid 只支持可选参数 non");
                }
            }
            default -> throw new SnippetTemplateException("未知 random 类型：" + type);
        }
    }

    private String characters(String alphabet, int length) {
        StringBuilder value = new StringBuilder(length);
        for (int index = 0; index < length; index++) value.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return value.toString();
    }

    private String number(int length) {
        StringBuilder value = new StringBuilder(length);
        value.append((char) ('1' + random.nextInt(9)));
        for (int index = 1; index < length; index++) value.append((char) ('0' + random.nextInt(10)));
        return value.toString();
    }

    private String decimal(String format) {
        String[] sizes = format.split("\\.");
        return number(length(sizes[0])) + "." + characters("0123456789", length(sizes[1]));
    }

    private String integer(String format) {
        BigInteger[] range = parseIntegerRange(format);
        BigInteger size = range[1].subtract(range[0]).add(BigInteger.ONE);
        BigInteger offset;
        do {
            offset = new BigInteger(size.bitLength(), random);
        } while (offset.compareTo(size) >= 0);
        return range[0].add(offset).toString();
    }

    private static BigInteger[] parseIntegerRange(String format) {
        if (format.matches("[1-9][0-9]{0,127}")) {
            return new BigInteger[] {BigInteger.ONE, new BigInteger(format)};
        }
        if (!format.matches("[0-9]{1,128}-[0-9]{1,128}")) {
            throw new SnippetTemplateException("random int 范围必须为正整数 n 或非负整数 a-b");
        }
        String[] parts = format.split("-", -1);
        BigInteger minimum = new BigInteger(parts[0]);
        BigInteger maximum = new BigInteger(parts[1]);
        if (minimum.compareTo(maximum) > 0) throw new SnippetTemplateException("random int 范围下限不能大于上限");
        return new BigInteger[] {minimum, maximum};
    }

    private String ipv4() {
        while (true) {
            int value = random.nextInt();
            int first = value >>> 24 & 0xff;
            int second = value >>> 16 & 0xff;
            if (first == 0 || first == 10 || first == 127 || first >= 224) continue;
            if (first == 100 && second >= 64 && second <= 127) continue;
            if (first == 169 && second == 254) continue;
            if (first == 172 && second >= 16 && second <= 31) continue;
            if (first == 192 && (second == 0 || second == 168)) continue;
            if (first == 192 && second == 88 && (value >>> 8 & 0xff) == 99) continue;
            if (first == 198 && (second == 18 || second == 19)) continue;
            if (first == 198 && second == 51 && (value >>> 8 & 0xff) == 100) continue;
            if (first == 203 && second == 0 && (value >>> 8 & 0xff) == 113) continue;
            return first + "." + second + "." + (value >>> 8 & 0xff) + "." + (value & 0xff);
        }
    }

    private String uuid(boolean compact) {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        bytes[6] = (byte) (bytes[6] & 0x0f | 0x40);
        bytes[8] = (byte) (bytes[8] & 0x3f | 0x80);
        String hex = hex(bytes);
        if (compact) return hex;
        return hex.substring(0, 8) + '-' + hex.substring(8, 12) + '-' + hex.substring(12, 16) + '-'
                + hex.substring(16, 20) + '-' + hex.substring(20);
    }

    private static int length(String value) {
        try {
            int length = Integer.parseInt(value);
            if (length < 1 || length > MAX_RANDOM_LENGTH) throw new NumberFormatException();
            return length;
        } catch (NumberFormatException failure) {
            throw new SnippetTemplateException("随机长度必须在 1 到 " + MAX_RANDOM_LENGTH + " 之间");
        }
    }

    private static String applyEncodings(String value, List<String> encodings) {
        String current = value;
        for (String encoding : encodings) {
            current = switch (encoding.toLowerCase(Locale.ROOT)) {
                case "base64" -> Base64.getEncoder().encodeToString(current.getBytes(StandardCharsets.UTF_8));
                case "hex" -> hex(current.getBytes(StandardCharsets.UTF_8));
                case "url" -> URLEncoder.encode(current, StandardCharsets.UTF_8)
                        .replace("+", "%20").replace("%7E", "~").replace("*", "%2A");
                case "md5" -> digest("MD5", current);
                case "sha1" -> digest("SHA-1", current);
                case "sha256" -> digest("SHA-256", current);
                case "sha512" -> digest("SHA-512", current);
                case "upper" -> current.toUpperCase(Locale.ROOT);
                case "lower" -> current.toLowerCase(Locale.ROOT);
                default -> throw new SnippetTemplateException("未知编码：" + encoding);
            };
        }
        return current;
    }

    private static String digest(String algorithm, String value) {
        try {
            return hex(MessageDigest.getInstance(algorithm).digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("JDK 缺少摘要算法：" + algorithm, failure);
        }
    }

    private static String hex(byte[] bytes) {
        char[] result = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            result[index * 2] = HEX.charAt(value >>> 4);
            result[index * 2 + 1] = HEX.charAt(value & 0xf);
        }
        return new String(result);
    }

    private static void validateEncodings(List<String> encodings) {
        for (String encoding : encodings) {
            if (!Set.of("base64", "hex", "url", "md5", "sha1", "sha256", "sha512", "upper", "lower")
                    .contains(encoding.toLowerCase(Locale.ROOT))) {
                throw new SnippetTemplateException("未知编码：" + encoding);
            }
        }
    }

    private static SnippetInput input(Expression expression) {
        if (expression.arguments.isEmpty()) throw new SnippetTemplateException("input 缺少名称");
        String name = expression.arguments.getFirst().strip();
        Optional<String> defaultValue = Optional.empty();
        Optional<String> variableName = Optional.empty();
        boolean required = true;
        for (int index = 1; index < expression.arguments.size(); index++) {
            String option = expression.arguments.get(index);
            int equals = option.indexOf('=');
            if (equals <= 0) throw new SnippetTemplateException("input 参数格式不正确：" + option);
            String key = option.substring(0, equals).strip().toLowerCase(Locale.ROOT);
            String value = unquote(option.substring(equals + 1).strip());
            switch (key) {
                case "default" -> defaultValue = Optional.of(value);
                case "variable" -> variableName = Optional.of(SnippetVariable.requireValidVariableName(value));
                case "required" -> {
                    if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                        throw new SnippetTemplateException("input required 只能是 true 或 false");
                    }
                    required = Boolean.parseBoolean(value);
                }
                default -> throw new SnippetTemplateException("未知 input 参数：" + key);
            }
        }
        return new SnippetInput(name, defaultValue, variableName, required);
    }

    private static VariableReference variable(Expression expression) {
        if (expression.arguments.isEmpty() || expression.arguments.size() > 2) {
            throw new SnippetTemplateException("variable 需要变量名，并只支持可选 index 参数");
        }
        String name;
        try {
            name = SnippetVariable.requireValidVariableName(expression.arguments.getFirst());
        } catch (IllegalArgumentException invalid) {
            throw new SnippetTemplateException(invalid.getMessage(), invalid);
        }
        int index = 0;
        if (expression.arguments.size() == 2) {
            String option = expression.arguments.get(1);
            int equals = option.indexOf('=');
            if (equals <= 0 || !"index".equalsIgnoreCase(option.substring(0, equals).strip())) {
                throw new SnippetTemplateException("variable 可选参数格式必须为 index=非负整数");
            }
            String value = option.substring(equals + 1).strip();
            try {
                index = Integer.parseInt(value);
                if (index < 0) throw new NumberFormatException();
            } catch (NumberFormatException invalid) {
                throw new SnippetTemplateException("variable index 必须是非负整数：" + value, invalid);
            }
        }
        return new VariableReference(name, index);
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return value;
    }

    private static String singleName(Expression expression, String label) {
        if (expression.arguments.size() != 1 || expression.arguments.getFirst().isBlank()) {
            throw new SnippetTemplateException(label + "需要且只接受一个名称");
        }
        return expression.arguments.getFirst().strip();
    }

    private static String sessionField(Expression expression) {
        String field = singleName(expression, "当前连接属性").toLowerCase(Locale.ROOT);
        if (!Set.of("name", "host", "port", "username").contains(field)) {
            throw new SnippetTemplateException("未知当前连接属性：" + field);
        }
        return field;
    }

    private static String requireArgument(Expression expression, int index, String message) {
        if (expression.arguments.size() <= index) throw new SnippetTemplateException(message);
        return expression.arguments.get(index);
    }

    private static List<Expression> parse(String template) {
        return parseSegments(template).stream().filter(segment -> segment.expression != null)
                .map(segment -> segment.expression).toList();
    }

    private static List<Segment> parseSegments(String template) {
        Objects.requireNonNull(template, "template");
        List<Segment> segments = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        int index = 0;
        while (index < template.length()) {
            if (template.startsWith("\\{{", index)) {
                literal.append("{{");
                index += 3;
                continue;
            }
            if (!template.startsWith("{{", index)) {
                literal.append(template.charAt(index++));
                continue;
            }
            if (!literal.isEmpty()) {
                segments.add(Segment.literal(literal.toString()));
                literal.setLength(0);
            }
            int end = template.indexOf("}}", index + 2);
            if (end < 0) throw new SnippetTemplateException("模板缺少结束标记 }}");
            String body = template.substring(index + 2, end).strip();
            if (body.isEmpty()) throw new SnippetTemplateException("模板内容不能为空");
            segments.add(Segment.expression(expression(body)));
            index = end + 2;
        }
        if (!literal.isEmpty()) segments.add(Segment.literal(literal.toString()));
        return segments;
    }

    private static Expression expression(String body) {
        List<String> pipeline = splitQuoted(body, '|');
        String expressionBody = pipeline.getFirst();
        int firstComma = firstUnquoted(expressionBody, ',');
        String kind = (firstComma < 0 ? expressionBody : expressionBody.substring(0, firstComma))
                .strip().toLowerCase(Locale.ROOT);
        if (kind.isEmpty()) throw new SnippetTemplateException("模板类型不能为空");
        List<String> arguments;
        if (firstComma < 0) {
            arguments = List.of();
        } else if ("now".equals(kind)) {
            arguments = List.of(expressionBody.substring(firstComma + 1).strip());
        } else {
            arguments = splitQuoted(expressionBody.substring(firstComma + 1), ',').stream().map(String::strip).toList();
        }
        List<String> encodings = pipeline.subList(1, pipeline.size()).stream().map(String::strip).toList();
        if (encodings.stream().anyMatch(String::isEmpty)) throw new SnippetTemplateException("编码名称不能为空");
        return new Expression(kind, arguments, encodings);
    }

    private static int firstUnquoted(String value, char target) {
        boolean doubleQuoted = false;
        boolean singleQuoted = false;
        boolean escaped = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (escaped) escaped = false;
            else if (character == '\\' && doubleQuoted) escaped = true;
            else if (character == '"' && !singleQuoted) doubleQuoted = !doubleQuoted;
            else if (character == '\'' && !doubleQuoted) singleQuoted = !singleQuoted;
            else if (character == target && !doubleQuoted && !singleQuoted) return index;
        }
        if (doubleQuoted || singleQuoted) throw new SnippetTemplateException("模板中存在未闭合的引号");
        return -1;
    }

    private static List<String> splitQuoted(String value, char delimiter) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean doubleQuoted = false;
        boolean singleQuoted = false;
        boolean escaped = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (escaped) {
                current.append(character);
                escaped = false;
            } else if (character == '\\' && doubleQuoted) {
                current.append(character);
                escaped = true;
            } else if (character == '"' && !singleQuoted) {
                doubleQuoted = !doubleQuoted;
                current.append(character);
            } else if (character == '\'' && !doubleQuoted) {
                singleQuoted = !singleQuoted;
                current.append(character);
            } else if (character == delimiter && !doubleQuoted && !singleQuoted) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        if (doubleQuoted || singleQuoted) throw new SnippetTemplateException("模板中存在未闭合的引号");
        parts.add(current.toString());
        return parts;
    }

    private record Expression(String kind, List<String> arguments, List<String> encodings) {}

    private record VariableReference(String name, int index) {}

    private static final class Segment {
        private final String literal;
        private final Expression expression;

        private Segment(String literal, Expression expression) {
            this.literal = literal;
            this.expression = expression;
        }

        private static Segment literal(String value) { return new Segment(value, null); }
        private static Segment expression(Expression value) { return new Segment(null, value); }
    }
}
