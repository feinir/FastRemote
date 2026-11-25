// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Parses connection parameters from command text without executing the command. */
final class QuickConnectCommandParser {
    private QuickConnectCommandParser() { }

    static ParsedCommand parse(String commandText) {
        List<String> tokens = tokenize(commandText == null ? "" : commandText.strip());
        if (tokens.isEmpty()) throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.2d1a99b20ae5b816"));

        int commandIndex = 0;
        String password = null;
        if (baseName(tokens.getFirst()).equals("sshpass")) {
            commandIndex = 1;
            while (commandIndex < tokens.size()) {
                String token = tokens.get(commandIndex);
                if (token.equals("-p")) {
                    password = requiredValue(tokens, ++commandIndex, "sshpass -p");
                    commandIndex++;
                } else if (token.startsWith("-p") && token.length() > 2) {
                    password = token.substring(2);
                    commandIndex++;
                } else if (token.equals("--")) {
                    commandIndex++;
                    break;
                } else if (token.startsWith("-")) {
                    throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f8509dc08c8d8ee9") + token);
                } else {
                    break;
                }
            }
        }
        if (commandIndex >= tokens.size()) throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.faffac87513bf64e"));

        String command = baseName(tokens.get(commandIndex)).toLowerCase(Locale.ROOT);
        if (!List.of("ssh", "sftp").contains(command)) {
            throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.f82bbe3bf6e8649f"));
        }

        Integer port = null;
        String username = null;
        String privateKey = null;
        List<String> operands = new ArrayList<>();
        for (int index = commandIndex + 1; index < tokens.size(); index++) {
            String token = tokens.get(index);
            if (token.equals("--")) {
                operands.addAll(tokens.subList(index + 1, tokens.size()));
                break;
            }
            if (token.equals("-i")) {
                privateKey = requiredValue(tokens, ++index, "-i");
            } else if (token.startsWith("-i") && token.length() > 2) {
                privateKey = token.substring(2);
            } else if (token.equals("-l")) {
                username = requiredValue(tokens, ++index, "-l");
            } else if (token.startsWith("-l") && token.length() > 2) {
                username = token.substring(2);
            } else if (isPortOption(command, token)) {
                port = parsePort(requiredValue(tokens, ++index, token));
            } else if (isJoinedPortOption(command, token)) {
                port = parsePort(token.substring(2));
            } else if (token.equals("-o")) {
                String option = requiredValue(tokens, ++index, "-o");
                String[] pair = option.split("=", 2);
                if (pair.length == 2 && (pair[0].equalsIgnoreCase("ProxyJump")
                        || pair[0].equalsIgnoreCase("ProxyCommand"))) {
                    throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.80a0fccb584f6ec3"));
                }
                if (pair.length == 2 && pair[0].equalsIgnoreCase("Port")) {
                    port = parsePort(pair[1]);
                } else if (pair.length == 2 && pair[0].equalsIgnoreCase("User")) {
                    username = pair[1];
                } else if (pair.length == 2 && pair[0].equalsIgnoreCase("IdentityFile")) {
                    privateKey = pair[1];
                } else {
                    throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d4f3c2b3976a37bd") + option);
                }
            } else if (token.equals("-J")) {
                throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.80a0fccb584f6ec3"));
            } else if (takesValue(token)) {
                requiredValue(tokens, ++index, token);
            } else if (token.startsWith("-")) {
                throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e6843fa1dd5a8955") + token);
            } else if (!token.startsWith("-")) {
                operands.add(token);
            }
        }

        Endpoint endpoint = operands.stream()
                .map(QuickConnectCommandParser::endpoint)
                .filter(value -> value != null)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e9af050718194792")));
        if (endpoint.username() != null && !endpoint.username().isBlank()) username = endpoint.username();
        if (endpoint.port() != null) port = endpoint.port();
        return new ParsedCommand(command, endpoint.host(), Optional.ofNullable(username),
                Optional.ofNullable(port), Optional.ofNullable(password), Optional.ofNullable(privateKey));
    }

    private static boolean isPortOption(String command, String token) {
        return command.equals("ssh") ? token.equals("-p") : token.equals("-P");
    }

    private static boolean isJoinedPortOption(String command, String token) {
        return token.length() > 2 && (command.equals("ssh") ? token.startsWith("-p") : token.startsWith("-P"));
    }

    private static boolean takesValue(String token) {
        return List.of("-B", "-b", "-c", "-E", "-F", "-S", "-w")
                .contains(token);
    }

    private static Endpoint endpoint(String operand) {
        String value = operand;
        if (value.startsWith("sftp://") || value.startsWith("ssh://")) {
            int scheme = value.indexOf("://") + 3;
            value = value.substring(scheme);
            int slash = value.indexOf('/');
            if (slash >= 0) value = value.substring(0, slash);
            String user = null;
            int at = value.lastIndexOf('@');
            if (at >= 0) {
                user = value.substring(0, at);
                value = value.substring(at + 1);
            }
            Integer port = null;
            int colon = value.lastIndexOf(':');
            if (colon > 0 && value.indexOf(':') == colon) {
                port = parsePort(value.substring(colon + 1));
                value = value.substring(0, colon);
            }
            return new Endpoint(user, stripBrackets(value), port);
        }
        int at = value.lastIndexOf('@');
        String user = at >= 0 ? value.substring(0, at) : null;
        String hostAndPath = at >= 0 ? value.substring(at + 1) : value;
        String host = hostAndPath;
        if (hostAndPath.startsWith("[")) {
            int close = hostAndPath.indexOf(']');
            if (close > 0) host = hostAndPath.substring(0, close + 1);
        } else {
            int colon = hostAndPath.indexOf(':');
            if (colon >= 0) host = hostAndPath.substring(0, colon);
        }
        if (host.isBlank()) return null;
        return new Endpoint(user, stripBrackets(host), null);
    }

    private static String stripBrackets(String host) {
        return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) throw new NumberFormatException();
            return port;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.584c7ad845c11aa4"), failure);
        }
    }

    private static String requiredValue(List<String> tokens, int index, String option) {
        if (index >= tokens.size()) throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.d12bf8ce797131c0") + option + com.ynpe.fastremote.i18n.Texts.text("legacy.java.c20ac537b5d4d493"));
        return tokens.get(index);
    }

    private static String baseName(String token) {
        int slash = Math.max(token.lastIndexOf('/'), token.lastIndexOf('\\'));
        return slash < 0 ? token : token.substring(slash + 1);
    }

    static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (escaped) {
                current.append(value);
                escaped = false;
            } else if (value == '\\' && quote != '\'') {
                escaped = true;
            } else if (quote != 0) {
                if (value == quote) quote = 0;
                else current.append(value);
            } else if (value == '\'' || value == '"') {
                quote = value;
            } else if (Character.isWhitespace(value)) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(value);
            }
        }
        if (escaped) current.append('\\');
        if (quote != 0) throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.6956f2af28278941"));
        if (!current.isEmpty()) tokens.add(current.toString());
        return List.copyOf(tokens);
    }

    record ParsedCommand(
            String protocol,
            String host,
            Optional<String> username,
            Optional<Integer> port,
            Optional<String> password,
            Optional<String> privateKey) { }

    private record Endpoint(String username, String host, Integer port) { }
}
