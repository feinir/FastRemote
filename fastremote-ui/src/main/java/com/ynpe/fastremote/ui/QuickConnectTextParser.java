// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Infers SSH connection fields from short, unstructured clipboard text without executing it. */
final class QuickConnectTextParser {
    private static final int MAX_TEXT_LENGTH_EXCLUSIVE = 256;
    private static final Pattern IPV4 = Pattern.compile(
            "(?<![A-Za-z0-9_.-])((?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}"
                    + "(?:25[0-5]|2[0-4]\\d|1?\\d?\\d))(?![A-Za-z0-9_.-])");
    private static final Pattern IPV6 = Pattern.compile(
            "(?<![A-Za-z0-9])(?:\\[([0-9A-Fa-f:]+)]|([0-9A-Fa-f:]*:[0-9A-Fa-f:]*:[0-9A-Fa-f:]*))"
                    + "(?![A-Za-z0-9])");
    private static final Pattern DOMAIN = Pattern.compile(
            "(?i)(?<!\\S)((?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.){1,4}"
                    + "[a-z]{2,63})(?=\\s|$|:\\d{4,5}(?=\\s|$))");
    private static final Pattern COLON_PORT = Pattern.compile(
            ":(\\d{4,5})(?![A-Za-z0-9_])");
    private static final Pattern PORT = Pattern.compile(
            "(?<![:A-Za-z0-9_])(\\d{4,5})(?![A-Za-z0-9_])");
    private static final String VALUE_SEPARATOR = "(?:\\s*[:：/=]\\s*|\\s+)";
    private static final String USERNAME_LABEL =
            "(?:\u7528\u6237\u540d|\u7528\u6237|ssh[\\s_-]*user(?:name)?|username|user|login|u)";
    private static final Pattern LABELED_USERNAME = Pattern.compile(
            "(?i)(?<![A-Za-z0-9_])" + USERNAME_LABEL
                    + VALUE_SEPARATOR + "([a-z_][a-z0-9._-]{0,31})(?=\\s|[/=：:]|$)");
    private static final Pattern COMMON_USERNAME = Pattern.compile(
            "(?<!\\S)(root|ubuntu|centos|user|debian|admin)(?=\\s|[/：:]|$)");
    private static final Pattern LOWERCASE_USERNAME = Pattern.compile(
            "(?<!\\S)([a-z]{3,12})(?=\\s|[/：:]|$)");
    private static final String PASSWORD_LABEL =
            "(?:\u5bc6\u7801|ssh[\\s_-]*(?:password|pass|passwd|pwd|p)|password|passwd|pass|pwd|p)";
    private static final Pattern LABELED_PASSWORD = Pattern.compile(
            "(?i)(?<![A-Za-z0-9_])" + PASSWORD_LABEL
                    + VALUE_SEPARATOR + "([^\\s:：]\\S*)");
    private static final Pattern PASSWORD_LABEL_OCCURRENCE = Pattern.compile(
            "(?i)(?<![A-Za-z0-9_])" + PASSWORD_LABEL + "(?=\\s|[/=：:]|$)");
    private static final Pattern LABELED_CREDENTIALS_ONLY = Pattern.compile(
            "(?i)^\\s*" + USERNAME_LABEL + VALUE_SEPARATOR + "([a-z_][a-z0-9._-]{0,31})\\s+"
                    + PASSWORD_LABEL + VALUE_SEPARATOR + "([^\\s:：]\\S*)\\s*$");
    private static final Pattern FIRST_VALUE = Pattern.compile(
            "^\\s*(?:[:：/=]\\s*)?([^\\s:：]\\S*)");
    private static final Set<String> NON_USERNAME_WORDS = Set.of(
            "port", "password", "passwd", "pass", "pwd", "p", "username", "user", "login", "u",
            "ssh", "sshuser", "ssh_user", "sshusername", "ssh_username", "sshpass", "ssh_pass");

    private QuickConnectTextParser() { }

    static QuickConnectCommandParser.ParsedCommand parse(String clipboardText) {
        String rawText = clipboardText == null ? "" : clipboardText;
        if (rawText.length() >= MAX_TEXT_LENGTH_EXCLUSIVE) {
            throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.336e2df1a800f6b3"));
        }
        String text = rawText.strip();
        if (text.isEmpty()) throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.aa57728efbf89cea"));
        if (looksLikeExplicitCommand(text)) {
            return withDefaults(QuickConnectCommandParser.parse(text));
        }

        TextMatch host = findHost(text).orElseThrow(
                () -> new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.81eda39dd671b7d1")));
        Optional<TextMatch> port = findPort(text, host);
        Optional<TextMatch> username = findUsername(text, host.end());
        int passwordStart = username.map(TextMatch::end)
                .orElseGet(() -> port.map(TextMatch::end).orElse(host.end()));
        Optional<String> password = findPassword(text, passwordStart);

        return new QuickConnectCommandParser.ParsedCommand(
                "ssh", host.value(), Optional.of(username.map(TextMatch::value).orElse("root")),
                Optional.of(port.map(match -> Integer.parseInt(match.value())).orElse(22)),
                password, Optional.empty());
    }

    static Optional<LabeledCredentials> parseLabeledCredentialsOnly(String clipboardText) {
        String rawText = clipboardText == null ? "" : clipboardText;
        if (rawText.length() >= MAX_TEXT_LENGTH_EXCLUSIVE) return Optional.empty();
        Matcher matcher = LABELED_CREDENTIALS_ONLY.matcher(rawText);
        return matcher.matches()
                ? Optional.of(new LabeledCredentials(matcher.group(1), matcher.group(2)))
                : Optional.empty();
    }

    private static QuickConnectCommandParser.ParsedCommand withDefaults(
            QuickConnectCommandParser.ParsedCommand parsed) {
        return new QuickConnectCommandParser.ParsedCommand(
                parsed.protocol(), parsed.host(), Optional.of(parsed.username().orElse("root")),
                Optional.of(parsed.port().orElse(22)), parsed.password(), parsed.privateKey());
    }

    private static boolean looksLikeExplicitCommand(String text) {
        int end = 0;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) end++;
        String token = text.substring(0, end);
        int slash = Math.max(token.lastIndexOf('/'), token.lastIndexOf('\\'));
        String base = (slash >= 0 ? token.substring(slash + 1) : token).toLowerCase(Locale.ROOT);
        return List.of("ssh", "sftp", "sshpass").contains(base);
    }

    private static Optional<TextMatch> findHost(String text) {
        return List.of(firstMatch(IPV4, text, 1), firstIpv6(text), firstMatch(DOMAIN, text, 1)).stream()
                .flatMap(Optional::stream)
                .min(Comparator.comparingInt(TextMatch::start));
    }

    private static Optional<TextMatch> firstIpv6(String text) {
        Matcher matcher = IPV6.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (candidate == null || candidate.chars().filter(value -> value == ':').count() < 2) continue;
            try {
                InetAddress address = InetAddress.getByName(candidate);
                if (address instanceof Inet6Address) {
                    return Optional.of(new TextMatch(candidate, matcher.start(), matcher.end()));
                }
            } catch (UnknownHostException ignored) {
                // Candidate only; invalid IPv6 text is allowed to fall through to other host forms.
            }
        }
        return Optional.empty();
    }

    private static Optional<TextMatch> findPort(String text, TextMatch host) {
        boolean unambiguousColonPort = !host.value().contains(":")
                || text.charAt(host.start()) == '[';
        if (unambiguousColonPort) {
            Matcher colonPort = COLON_PORT.matcher(text);
            colonPort.region(host.end(), text.length());
            if (colonPort.lookingAt()) {
                int value = Integer.parseInt(colonPort.group(1));
                if (value >= 1024 && value <= 65535) {
                    return Optional.of(new TextMatch(
                            colonPort.group(1), colonPort.start(1), colonPort.end(1)));
                }
            }
        }
        Matcher matcher = PORT.matcher(text);
        matcher.region(host.end(), text.length());
        while (matcher.find()) {
            int value;
            try {
                value = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (value >= 1024 && value <= 65535) {
                return Optional.of(new TextMatch(matcher.group(1), matcher.start(1), matcher.end(1)));
            }
        }
        return Optional.empty();
    }

    private static Optional<TextMatch> findUsername(String text, int start) {
        Optional<TextMatch> labeled = firstMatch(LABELED_USERNAME, text, start, 1);
        if (labeled.isPresent()) return labeled;
        Optional<TextMatch> common = firstMatch(COMMON_USERNAME, text, start, 1);
        if (common.isPresent()) return common;
        Matcher matcher = LOWERCASE_USERNAME.matcher(text);
        matcher.region(start, text.length());
        while (matcher.find()) {
            String value = matcher.group(1);
            if (!NON_USERNAME_WORDS.contains(value)) {
                return Optional.of(new TextMatch(value, matcher.start(1), matcher.end(1)));
            }
        }
        return Optional.empty();
    }

    private static Optional<String> findPassword(String text, int start) {
        Matcher labeled = LABELED_PASSWORD.matcher(text);
        labeled.region(start, text.length());
        if (labeled.find()) return Optional.of(labeled.group(1));
        Matcher invalidLabeled = PASSWORD_LABEL_OCCURRENCE.matcher(text);
        invalidLabeled.region(start, text.length());
        if (invalidLabeled.find()) return Optional.empty();
        Matcher first = FIRST_VALUE.matcher(text.substring(start));
        return first.find() ? Optional.of(first.group(1)) : Optional.empty();
    }

    private static Optional<TextMatch> firstMatch(Pattern pattern, String text, int group) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find()
                ? Optional.of(new TextMatch(matcher.group(group), matcher.start(group), matcher.end(group)))
                : Optional.empty();
    }

    private static Optional<TextMatch> firstMatch(Pattern pattern, String text, int start, int group) {
        Matcher matcher = pattern.matcher(text);
        matcher.region(start, text.length());
        return matcher.find()
                ? Optional.of(new TextMatch(matcher.group(group), matcher.start(group), matcher.end(group)))
                : Optional.empty();
    }

    private record TextMatch(String value, int start, int end) { }

    record LabeledCredentials(String username, String password) { }
}
