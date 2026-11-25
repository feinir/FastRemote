// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import com.ynpe.fastremote.domain.catalog.GroupColor;
import com.ynpe.fastremote.domain.snippet.Snippet;
import com.ynpe.fastremote.domain.snippet.SnippetGroup;
import com.ynpe.fastremote.domain.snippet.SnippetVariable;
import com.ynpe.fastremote.domain.snippet.SnippetVariableGroup;
import com.ynpe.fastremote.domain.snippet.SnippetVariableType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Strict UTF-8 TSV codec shared by the command and variable libraries. */
final class SnippetLibraryTextCodec {
    static final String VARIABLES_HEADER = "@FASTREMOTE-VARIABLES:1";
    static final String COMMANDS_HEADER = "@FASTREMOTE-COMMANDS:1";
    static final String BUILTIN_COMMANDS_HEADER = "@FASTREMOTE-BUILTIN-COMMANDS:1";
    private static final int MAX_COMMANDS = 10_000;
    private static final int MAX_VARIABLES = 10_000;

    private SnippetLibraryTextCodec() { }

    static BuiltinDecoded decodeBuiltin(List<String> lines) {
        if (lines.isEmpty() || !BUILTIN_COMMANDS_HEADER.equals(lines.getFirst())) {
            throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.0e0359fc506df8f2"));
        }
        List<BuiltinGroupLine> groups = new ArrayList<>();
        List<BuiltinCommandLine> commands = new ArrayList<>();
        Set<String> groupIds = new HashSet<>();
        Set<String> commandIds = new HashSet<>();
        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index);
            int lineNumber = index + 1;
            if (line.isBlank()) continue;
            List<String> fields = fields(line, lineNumber);
            switch (fields.getFirst()) {
                case "@group" -> {
                    requireFieldCount(fields, 4, lineNumber,
                            com.ynpe.fastremote.i18n.Texts.text("legacy.java.6458598a47a7b20d"));
                    if (!groupIds.add(fields.get(1))) fail(lineNumber, com.ynpe.fastremote.i18n.Texts.text("legacy.java.d3ccb727759e00af") + fields.get(1));
                    groups.add(new BuiltinGroupLine(fields.get(1), fields.get(2),
                            color(fields.get(3), lineNumber), lineNumber));
                }
                case "@command" -> {
                    requireFieldCount(fields, 6, lineNumber,
                            com.ynpe.fastremote.i18n.Texts.text("legacy.java.3f760a013d58721f"));
                    if (!commandIds.add(fields.get(1))) fail(lineNumber, com.ynpe.fastremote.i18n.Texts.text("legacy.java.29107d2120ae13ac") + fields.get(1));
                    commands.add(new BuiltinCommandLine(fields.get(1), fields.get(2), fields.get(3),
                            fields.get(4), fields.get(5), lineNumber));
                    if (commands.size() > MAX_COMMANDS) fail(lineNumber, com.ynpe.fastremote.i18n.Texts.text("legacy.java.16948d2090cd485e"));
                }
                default -> fail(lineNumber, com.ynpe.fastremote.i18n.Texts.text("legacy.java.196855d0e194a14e") + fields.getFirst());
            }
        }
        if (commands.isEmpty()) throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2a2225d5d04d2c15"));
        for (BuiltinCommandLine command : commands) {
            if (!command.groupBuiltinId().isBlank() && !groupIds.contains(command.groupBuiltinId())) {
                fail(command.lineNumber(), com.ynpe.fastremote.i18n.Texts.text("legacy.java.4f4c5103f824b7c5") + command.groupBuiltinId());
            }
        }
        return new BuiltinDecoded(List.copyOf(groups), List.copyOf(commands));
    }

    static Decoded decode(List<String> lines, boolean commandsRequired) {
        return decode(lines, commandsRequired, false);
    }

    static Decoded decode(List<String> lines, boolean commandsRequired, boolean allowEmpty) {
        Section section = Section.NONE;
        boolean sawVariables = false;
        boolean sawCommands = false;
        Map<String, GroupLine> variableGroups = new LinkedHashMap<>();
        Map<String, GroupLine> commandGroups = new LinkedHashMap<>();
        List<VariableLine> variables = new ArrayList<>();
        List<CommandLine> commands = new ArrayList<>();
        Set<String> variableNames = new HashSet<>();
        Set<String> commandNames = new HashSet<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            int lineNumber = index + 1;
            if (line.isBlank()) continue;
            if (VARIABLES_HEADER.equals(line)) {
                if (sawVariables || sawCommands) fail(lineNumber, com.ynpe.fastremote.i18n.Texts.text("legacy.java.9bb1c350c08e1e4c"));
                sawVariables = true;
                section = Section.VARIABLES;
                continue;
            }
            if (COMMANDS_HEADER.equals(line)) {
                if (sawCommands) fail(lineNumber, com.ynpe.fastremote.i18n.Texts.text("legacy.java.abc8fec0c7ff048c"));
                sawCommands = true;
                section = Section.COMMANDS;
                continue;
            }
            if (section == Section.NONE) fail(lineNumber, com.ynpe.fastremote.i18n.Texts.text("legacy.java.edd83fab2352bb88"));
            List<String> fields = fields(line, lineNumber);
            switch (fields.getFirst()) {
                case "@group" -> {
                    requireFieldCount(fields, 3, lineNumber, com.ynpe.fastremote.i18n.Texts.text("legacy.java.3294c4a75a15a962"));
                    GroupLine group = new GroupLine(fields.get(1), color(fields.get(2), lineNumber), lineNumber);
                    Map<String, GroupLine> target = section == Section.VARIABLES ? variableGroups : commandGroups;
                    GroupLine previous = target.putIfAbsent(normalize(group.name()), group);
                    if (previous != null && previous.color() != group.color()) {
                        fail(lineNumber, com.ynpe.fastremote.i18n.Texts.text("legacy.java.d781e6f6a69e23f7") + group.name());
                    }
                }
                case "@variable" -> {
                    if (section != Section.VARIABLES) fail(lineNumber, com.ynpe.fastremote.i18n.Texts.text("legacy.java.be4460440787bb32"));
                    requireFieldCount(fields, 8, lineNumber,
                            com.ynpe.fastremote.i18n.Texts.text("legacy.java.fb1b0d011fdc27c4"));
                    VariableLine variable = new VariableLine(
                            fields.get(1), fields.get(2), fields.get(3), type(fields.get(4), lineNumber),
                            fields.get(5), fields.get(6), fields.get(7), lineNumber);
                    if (!variableNames.add(variable.variableName())) {
                        fail(lineNumber, com.ynpe.fastremote.i18n.Texts.text("legacy.java.9bb726297ddd7884") + variable.variableName());
                    }
                    variables.add(variable);
                    if (variables.size() > MAX_VARIABLES) fail(lineNumber, com.ynpe.fastremote.i18n.Texts.text("legacy.java.96924ec33fc866bd"));
                }
                case "@command" -> {
                    if (section != Section.COMMANDS) fail(lineNumber, com.ynpe.fastremote.i18n.Texts.text("legacy.java.ee9d6eca1ce5de8c"));
                    requireFieldCount(fields, 5, lineNumber,
                            com.ynpe.fastremote.i18n.Texts.text("legacy.java.c182945fbc4f7573"));
                    CommandLine command = new CommandLine(
                            fields.get(1), fields.get(2), fields.get(3), fields.get(4), lineNumber);
                    String key = normalize(command.groupName()) + '\0' + normalize(command.name());
                    if (!commandNames.add(key)) fail(lineNumber, com.ynpe.fastremote.i18n.Texts.text("legacy.java.d7d8ce46603eeff3") + command.name());
                    commands.add(command);
                    if (commands.size() > MAX_COMMANDS) fail(lineNumber, com.ynpe.fastremote.i18n.Texts.text("legacy.java.16948d2090cd485e"));
                }
                default -> fail(lineNumber, com.ynpe.fastremote.i18n.Texts.text("legacy.java.f28ef3e02fc39640") + fields.getFirst());
            }
        }
        if (!sawVariables && !sawCommands) throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.9ac0ea3bedc31cf3"));
        if (commandsRequired && !sawCommands) throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.39f5d41af4b6dee1"));
        if (!commandsRequired && sawCommands) throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.57c038fce04ca2ff"));
        validateGroupReferences(variableGroups, variables.stream().map(VariableLine::groupName).toList(), com.ynpe.fastremote.i18n.Texts.text("legacy.java.a772fa4ebe36b63d"));
        validateGroupReferences(commandGroups, commands.stream().map(CommandLine::groupName).toList(), com.ynpe.fastremote.i18n.Texts.text("legacy.java.928f87d4507bf9a0"));
        if (!allowEmpty && commandsRequired && commands.isEmpty()) throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2c23e370db5aafcf"));
        if (!allowEmpty && !commandsRequired && variables.isEmpty()) throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.43b0983f4a30f2ae"));
        return new Decoded(
                List.copyOf(variableGroups.values()), List.copyOf(variables),
                List.copyOf(commandGroups.values()), List.copyOf(commands));
    }

    static List<String> encodeVariables(
            List<SnippetVariableGroup> groups, List<SnippetVariable> variables) {
        List<String> lines = new ArrayList<>();
        lines.add(VARIABLES_HEADER);
        groups.forEach(group -> lines.add(record("@group", group.name(), group.color().name())));
        Map<com.ynpe.fastremote.domain.snippet.SnippetVariableGroupId, String> groupNames = new java.util.HashMap<>();
        groups.forEach(group -> groupNames.put(group.id(), group.name()));
        variables.forEach(variable -> lines.add(record(
                "@variable", variable.groupId().map(groupNames::get).orElse(""), variable.name(),
                variable.variableName(), variable.type().name(), variable.delimiter(), variable.value(),
                variable.description())));
        return lines;
    }

    static List<String> encodeCommands(
            List<SnippetVariableGroup> variableGroups,
            List<SnippetVariable> variables,
            List<SnippetGroup> commandGroups,
            List<Snippet> commands) {
        List<String> lines = variables.isEmpty()
                ? new ArrayList<>() : new ArrayList<>(encodeVariables(variableGroups, variables));
        lines.add(COMMANDS_HEADER);
        commandGroups.forEach(group -> lines.add(record("@group", group.name(), group.color().name())));
        Map<com.ynpe.fastremote.domain.snippet.SnippetGroupId, String> groupNames = new java.util.HashMap<>();
        commandGroups.forEach(group -> groupNames.put(group.id(), group.name()));
        commands.forEach(command -> lines.add(record(
                "@command", command.groupId().map(groupNames::get).orElse(""), command.name(),
                command.content(), command.description())));
        return lines;
    }

    static String record(String type, String... fields) {
        StringBuilder line = new StringBuilder(type);
        for (String field : fields) line.append('\t').append(encode(field));
        return line.toString();
    }

    private static List<String> fields(String line, int lineNumber) {
        String[] encoded = line.split("\\t", -1);
        List<String> result = new ArrayList<>(encoded.length);
        result.add(encoded[0]);
        for (int index = 1; index < encoded.length; index++) {
            result.add(decodeField(encoded[index], lineNumber));
        }
        return result;
    }

    private static String encode(String value) {
        StringBuilder encoded = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            switch (value.charAt(index)) {
                case '\\' -> encoded.append("\\\\");
                case '\t' -> encoded.append("\\t");
                case '\n' -> encoded.append("\\n");
                case '\r' -> encoded.append("\\r");
                default -> encoded.append(value.charAt(index));
            }
        }
        return encoded.toString();
    }

    private static String decodeField(String value, int lineNumber) {
        StringBuilder decoded = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character != '\\') {
                decoded.append(character);
                continue;
            }
            if (++index >= value.length()) fail(lineNumber, com.ynpe.fastremote.i18n.Texts.text("legacy.java.3042611a47f31a7e"));
            switch (value.charAt(index)) {
                case '\\' -> decoded.append('\\');
                case 't' -> decoded.append('\t');
                case 'n' -> decoded.append('\n');
                case 'r' -> decoded.append('\r');
                default -> fail(lineNumber, com.ynpe.fastremote.i18n.Texts.text("legacy.java.7443af3750a551fe") + value.charAt(index));
            }
        }
        return decoded.toString();
    }

    private static void validateGroupReferences(
            Map<String, GroupLine> groups, List<String> references, String label) {
        for (String reference : references) {
            if (!reference.isBlank() && !groups.containsKey(normalize(reference))) {
                throw new IllegalArgumentException(label + com.ynpe.fastremote.i18n.Texts.text("legacy.java.23ba9dd7764f066a") + reference);
            }
        }
    }

    private static GroupColor color(String value, int lineNumber) {
        try {
            return GroupColor.valueOf(value);
        } catch (IllegalArgumentException invalid) {
            fail(lineNumber, com.ynpe.fastremote.i18n.Texts.text("legacy.java.14884d9545c9db8f") + value);
            throw invalid;
        }
    }

    private static SnippetVariableType type(String value, int lineNumber) {
        try {
            return SnippetVariableType.valueOf(value);
        } catch (IllegalArgumentException invalid) {
            fail(lineNumber, com.ynpe.fastremote.i18n.Texts.text("legacy.java.08965a6048289e6b") + value);
            throw invalid;
        }
    }

    private static void requireFieldCount(List<String> fields, int count, int lineNumber, String format) {
        if (fields.size() != count) fail(lineNumber, com.ynpe.fastremote.i18n.Texts.text("legacy.java.5a23d76e277dad0a") + format);
    }

    private static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static void fail(int lineNumber, String message) {
        throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text(
                "error.line", lineNumber, com.ynpe.fastremote.i18n.Texts.translateExternalText(message)));
    }

    enum Section { NONE, VARIABLES, COMMANDS }

    record GroupLine(String name, GroupColor color, int lineNumber) {
        GroupLine {
            name = name.strip();
            if (name.isEmpty() || name.length() > 255) fail(lineNumber, com.ynpe.fastremote.i18n.Texts.text("legacy.java.0f1937b751e11078"));
        }
    }

    record VariableLine(
            String groupName,
            String name,
            String variableName,
            SnippetVariableType type,
            String delimiter,
            String value,
            String description,
            int lineNumber) {}

    record CommandLine(String groupName, String name, String content, String description, int lineNumber) {}

    record Decoded(
            List<GroupLine> variableGroups,
            List<VariableLine> variables,
            List<GroupLine> commandGroups,
            List<CommandLine> commands) {}

    record BuiltinGroupLine(
            String builtinId, String name, GroupColor color, int lineNumber) {}

    record BuiltinCommandLine(
            String builtinId, String groupBuiltinId, String name,
            String content, String description, int lineNumber) {}

    record BuiltinDecoded(List<BuiltinGroupLine> groups, List<BuiltinCommandLine> commands) {}
}
