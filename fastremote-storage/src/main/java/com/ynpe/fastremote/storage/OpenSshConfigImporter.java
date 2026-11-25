// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import com.ynpe.fastremote.domain.connection.AuthenticationType;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Safely reads the portable host subset of OpenSSH client configuration files. */
public final class OpenSshConfigImporter {
    private static final long MAX_CONFIG_BYTES = 4L * 1024 * 1024;
    private static final long MAX_KEY_BYTES = 1024L * 1024;
    private static final int MAX_FILES = 256;
    private static final int MAX_INCLUDE_DEPTH = 16;
    private static final int MAX_DIRECTIVES = 100_000;
    private static final int MAX_HOSTS = 20_000;
    private static final Set<String> SUPPORTED = Set.of(
            "host", "hostname", "user", "port", "identityfile", "identitiesonly", "include",
            "proxyjump", "proxycommand", "match");

    public Inspection inspect(Path source) throws IOException {
        Parsed parsed = parse(source);
        return parsed.inspection();
    }

    public ImportBundle read(Path source, Map<Path, char[]> encryptedKeyPassphrases) throws IOException {
        Objects.requireNonNull(encryptedKeyPassphrases, "encryptedKeyPassphrases");
        Parsed parsed = parse(source);
        Map<Path, char[]> normalizedPassphrases = new LinkedHashMap<>();
        encryptedKeyPassphrases.forEach((path, value) -> {
            Path normalized;
            try {
                normalized = path.toRealPath();
            } catch (IOException ignored) {
                normalized = path.toAbsolutePath().normalize();
            }
            normalizedPassphrases.put(normalized, Objects.requireNonNull(value, "passphrase").clone());
        });
        List<CredentialEntry> credentials = new ArrayList<>();
        try {
            Map<String, CredentialEntry> byIdentity = new LinkedHashMap<>();
            List<HostEntry> hosts = new ArrayList<>();
            for (ResolvedHost host : parsed.hosts()) {
                Optional<String> credentialKey = Optional.empty();
                AuthenticationType authentication = AuthenticationType.PASSWORD;
                if (host.identity().isPresent()) {
                    KeyInfo key = host.identity().orElseThrow();
                    char[] passphrase = key.encrypted()
                            ? normalizedPassphrases.get(key.path())
                            : new char[0];
                    if (passphrase != null) {
                        String sourceKey = key.path() + "\u0000" + host.username();
                        CredentialEntry credential = byIdentity.get(sourceKey);
                        if (credential == null) {
                            String fileName = key.path().getFileName().toString();
                            credential = new CredentialEntry(sourceKey, fileName + " · " + host.username(),
                                    host.username(), key.path(), passphrase);
                            byIdentity.put(sourceKey, credential);
                            credentials.add(credential);
                        }
                        credentialKey = Optional.of(sourceKey);
                        authentication = AuthenticationType.PRIVATE_KEY;
                    }
                }
                hosts.add(new HostEntry(host.alias(), host.hostName(), host.port(), host.username(),
                        authentication, credentialKey, host.jumpAlias(), host.sortOrder()));
            }
            return new ImportBundle(credentials, hosts, parsed.inspection());
        } catch (Throwable failure) {
            credentials.forEach(CredentialEntry::close);
            throw failure;
        } finally {
            normalizedPassphrases.values().forEach(value -> Arrays.fill(value, '\0'));
        }
    }

    private static Parsed parse(Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        List<Directive> directives = new ArrayList<>();
        ParseLimits limits = new ParseLimits();
        flatten(source.toAbsolutePath().normalize(), directives, new HashSet<>(), limits, 0);

        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        int wildcardPatterns = 0;
        int ignoredOptions = 0;
        for (Directive directive : directives) {
            if (!SUPPORTED.contains(directive.key())) ignoredOptions++;
            if (!directive.key().equals("host")) continue;
            for (String pattern : directive.values()) {
                if (isConcreteAlias(pattern)) aliases.add(pattern);
                else wildcardPatterns++;
            }
        }
        if (aliases.size() > MAX_HOSTS) throw new IOException("OpenSSH 配置中的具体 Host 超过 20000 个限制");

        List<ResolvedHost> hosts = new ArrayList<>();
        int proxyHosts = 0;
        int invalidHosts = 0;
        int missingIdentities = 0;
        Map<Path, KeyInfo> keys = new LinkedHashMap<>();
        int sortOrder = 0;
        Map<String, List<String>> jumpChains = new LinkedHashMap<>();
        for (String alias : aliases) {
            HostOptions options = evaluate(alias, directives);
            if (options.proxyCommand) {
                proxyHosts++;
                continue;
            }
            List<String> jumpAliases = parseJumpAliases(options.proxyJump);
            if (!jumpAliases.isEmpty()) {
                proxyHosts++;
                if (jumpAliases.stream().anyMatch(jump -> !aliases.contains(jump))) {
                    invalidHosts++;
                    continue;
                }
                jumpChains.put(alias, jumpAliases);
            }
            String hostName = expandPercentHost(options.hostName == null ? alias : options.hostName, alias);
            String username = options.username == null ? System.getProperty("user.name", "") : options.username;
            int port;
            try {
                port = options.port == null ? 22 : Integer.parseInt(options.port);
                if (port < 1 || port > 65535 || username.isBlank() || hostName.isBlank()) throw new IllegalArgumentException();
            } catch (RuntimeException failure) {
                invalidHosts++;
                continue;
            }
            Optional<KeyInfo> identity = Optional.empty();
            if (!options.identityFiles.isEmpty()) {
                for (IdentitySpec configured : options.identityFiles) {
                    if (configured.value().equalsIgnoreCase("none")) continue;
                    Path keyPath = resolveIdentity(
                            configured.value(), configured.base(), alias, username, hostName);
                    Optional<KeyInfo> readable = inspectKey(keyPath);
                    if (readable.isPresent()) {
                        KeyInfo key = readable.orElseThrow();
                        keys.putIfAbsent(key.path(), key);
                        identity = Optional.of(key);
                        break;
                    }
                }
                if (identity.isEmpty()) missingIdentities++;
            }
            hosts.add(new ResolvedHost(alias, hostName, port, username, identity, Optional.empty(), sortOrder));
            sortOrder += 10;
        }
        Map<String, String> jumpByAlias = new LinkedHashMap<>();
        jumpChains.forEach((target, chain) -> {
            for (int index = 1; index < chain.size(); index++) {
                jumpByAlias.put(chain.get(index), chain.get(index - 1));
            }
            jumpByAlias.put(target, chain.getLast());
        });
        hosts = hosts.stream().map(host -> new ResolvedHost(
                host.alias(), host.hostName(), host.port(), host.username(), host.identity(),
                Optional.ofNullable(jumpByAlias.get(host.alias())), host.sortOrder())).toList();
        List<KeyInfo> encrypted = keys.values().stream().filter(KeyInfo::encrypted).toList();
        Inspection inspection = new Inspection(hosts.size(), keys.size(), encrypted.size(), missingIdentities,
                proxyHosts, invalidHosts, wildcardPatterns, ignoredOptions, limits.files, List.copyOf(keys.values()));
        return new Parsed(List.copyOf(hosts), inspection);
    }

    private static void flatten(Path source, List<Directive> output, Set<Path> active, ParseLimits limits, int depth)
            throws IOException {
        if (depth > MAX_INCLUDE_DEPTH) throw new IOException("OpenSSH Include 递归超过 16 层限制");
        Path real;
        try {
            real = source.toRealPath();
        } catch (IOException failure) {
            throw new IOException("无法读取 OpenSSH 配置文件：" + source, failure);
        }
        if (!active.add(real)) throw new IOException("OpenSSH Include 存在循环：" + source);
        if (++limits.files > MAX_FILES) throw new IOException("OpenSSH Include 文件超过 256 个限制");
        long size = Files.size(real);
        if (!Files.isRegularFile(real) || !Files.isReadable(real) || size < 1 || size > MAX_CONFIG_BYTES) {
            throw new IOException("OpenSSH 配置文件为空、不可读或超过 4 MiB：" + source);
        }
        try {
            for (String logical : logicalLines(Files.readAllLines(real, StandardCharsets.UTF_8))) {
                List<String> tokens = tokenize(logical);
                if (tokens.isEmpty()) continue;
                String key = tokens.getFirst().toLowerCase(Locale.ROOT);
                List<String> values = List.copyOf(tokens.subList(1, tokens.size()));
                if (values.isEmpty()) continue;
                if (key.equals("include")) {
                    for (String value : values) {
                        for (Path included : expandInclude(value, real.getParent())) {
                            flatten(included, output, active, limits, depth + 1);
                        }
                    }
                } else {
                    output.add(new Directive(key, values, real.getParent()));
                    if (output.size() > MAX_DIRECTIVES) {
                        throw new IOException("OpenSSH 配置指令超过 100000 条限制");
                    }
                }
            }
        } finally {
            active.remove(real);
        }
    }

    private static List<Path> expandInclude(String raw, Path base) throws IOException {
        String expanded = expandHome(raw);
        Path patternPath = Path.of(expanded);
        if (!patternPath.isAbsolute()) patternPath = base.resolve(patternPath);
        patternPath = patternPath.normalize();
        String value = patternPath.toString();
        int wildcard = firstWildcard(value);
        if (wildcard < 0) return Files.exists(patternPath) ? List.of(patternPath) : List.of();
        int separator = value.substring(0, wildcard).lastIndexOf(java.io.File.separatorChar);
        Path directory = separator < 0 ? base : Path.of(value.substring(0, separator));
        String filePattern = value.substring(separator + 1);
        if (filePattern.indexOf(java.io.File.separatorChar) >= 0) return List.of();
        List<Path> matches = new ArrayList<>();
        if (!Files.isDirectory(directory)) return matches;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, filePattern)) {
            stream.forEach(matches::add);
        }
        matches.sort(Path::compareTo);
        return matches;
    }

    private static HostOptions evaluate(String alias, List<Directive> directives) {
        HostOptions result = new HostOptions();
        boolean active = true;
        for (Directive directive : directives) {
            switch (directive.key()) {
                case "host" -> active = matchesHost(alias, directive.values());
                case "match" -> active = false;
                case "hostname" -> { if (active && result.hostName == null) result.hostName = joined(directive.values()); }
                case "user" -> { if (active && result.username == null) result.username = joined(directive.values()); }
                case "port" -> { if (active && result.port == null) result.port = directive.values().getFirst(); }
                case "identityfile" -> {
                    if (active) result.identityFiles.add(new IdentitySpec(joined(directive.values()), directive.base()));
                }
                case "proxyjump" -> {
                    if (active && result.proxyJump == null) result.proxyJump = joined(directive.values());
                }
                case "proxycommand" -> {
                    if (active && !joined(directive.values()).equalsIgnoreCase("none")) result.proxyCommand = true;
                }
                default -> { }
            }
        }
        return result;
    }

    private static boolean matchesHost(String alias, List<String> patterns) {
        boolean positive = false;
        for (String raw : patterns) {
            boolean negated = raw.startsWith("!");
            String pattern = negated ? raw.substring(1) : raw;
            if (globMatches(alias, pattern)) {
                if (negated) return false;
                positive = true;
            }
        }
        return positive;
    }

    private static boolean globMatches(String value, String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') regex.append(".*");
            else if (c == '?') regex.append('.');
            else regex.append(Pattern.quote(String.valueOf(c)));
        }
        return Pattern.compile(regex.append('$').toString(), Pattern.CASE_INSENSITIVE).matcher(value).matches();
    }

    private static boolean isConcreteAlias(String value) {
        return !value.isBlank() && !value.startsWith("!") && firstWildcard(value) < 0;
    }

    private static List<String> parseJumpAliases(String configured) {
        if (configured == null || configured.isBlank() || configured.equalsIgnoreCase("none")) return List.of();
        List<String> result = new ArrayList<>();
        for (String raw : configured.split(",")) {
            String value = raw.strip();
            int at = value.lastIndexOf('@');
            if (at >= 0) value = value.substring(at + 1);
            if (value.startsWith("[")) {
                int close = value.indexOf(']');
                if (close > 0) value = value.substring(1, close);
            } else {
                int colon = value.lastIndexOf(':');
                if (colon > 0) value = value.substring(0, colon);
            }
            if (!value.isBlank()) result.add(value);
        }
        return List.copyOf(result);
    }

    private static int firstWildcard(String value) {
        int result = -1;
        for (char candidate : new char[] {'*', '?', '['}) {
            int index = value.indexOf(candidate);
            if (index >= 0 && (result < 0 || index < result)) result = index;
        }
        return result;
    }

    private static Path resolveIdentity(String value, Path base, String alias, String user, String host) {
        String expanded = expandHome(value)
                .replace("%h", host).replace("%n", alias).replace("%r", user).replace("%%", "%");
        Path path = Path.of(expanded);
        return (path.isAbsolute() ? path : base.resolve(path)).toAbsolutePath().normalize();
    }

    private static String expandHome(String value) {
        if (value.equals("~")) return System.getProperty("user.home", value);
        if (value.startsWith("~/")) return Path.of(System.getProperty("user.home", "~"), value.substring(2)).toString();
        return value;
    }

    private static String expandPercentHost(String value, String alias) {
        return value.replace("%h", alias).replace("%n", alias).replace("%%", "%");
    }

    private static Optional<KeyInfo> inspectKey(Path path) {
        try {
            if (path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pub")) return Optional.empty();
            long size = Files.size(path);
            if (!Files.isRegularFile(path) || !Files.isReadable(path) || size < 1 || size > MAX_KEY_BYTES) {
                return Optional.empty();
            }
            byte[] bytes = Files.readAllBytes(path);
            try {
                String text = new String(bytes, StandardCharsets.US_ASCII);
                if (!text.contains("PRIVATE KEY-----")) return Optional.empty();
                return Optional.of(new KeyInfo(path.toRealPath(), isEncryptedPrivateKey(bytes)));
            } finally {
                Arrays.fill(bytes, (byte) 0);
            }
        } catch (IOException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static boolean isEncryptedPrivateKey(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.US_ASCII);
        if (text.contains("BEGIN ENCRYPTED PRIVATE KEY") || text.contains("Proc-Type: 4,ENCRYPTED")) return true;
        int begin = text.indexOf("-----BEGIN OPENSSH PRIVATE KEY-----");
        int end = text.indexOf("-----END OPENSSH PRIVATE KEY-----");
        if (begin < 0 || end <= begin) return false;
        String body = text.substring(begin + "-----BEGIN OPENSSH PRIVATE KEY-----".length(), end)
                .replaceAll("\\s", "");
        try {
            ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(body));
            byte[] magic = "openssh-key-v1\u0000".getBytes(StandardCharsets.US_ASCII);
            if (buffer.remaining() < magic.length) return false;
            for (byte expected : magic) if (buffer.get() != expected) return false;
            int length = buffer.getInt();
            if (length < 1 || length > buffer.remaining()) return false;
            byte[] cipher = new byte[length];
            buffer.get(cipher);
            return !new String(cipher, StandardCharsets.US_ASCII).equals("none");
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static List<String> logicalLines(List<String> physical) throws IOException {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : physical) {
            current.append(line);
            int slashes = 0;
            for (int i = current.length() - 1; i >= 0 && current.charAt(i) == '\\'; i--) slashes++;
            if ((slashes & 1) == 1) {
                current.setLength(current.length() - 1);
                continue;
            }
            result.add(current.toString());
            current.setLength(0);
        }
        if (!current.isEmpty()) throw new IOException("OpenSSH 配置最后一行包含未完成的续行");
        return result;
    }

    private static List<String> tokenize(String line) throws IOException {
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean single = false;
        boolean doubly = false;
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escaped) { token.append(c); escaped = false; continue; }
            if (c == '\\' && !single) { escaped = true; continue; }
            if (c == '\'' && !doubly) { single = !single; continue; }
            if (c == '"' && !single) { doubly = !doubly; continue; }
            if (c == '#' && !single && !doubly) break;
            if ((Character.isWhitespace(c) || c == '=') && !single && !doubly) {
                if (!token.isEmpty()) { tokens.add(token.toString()); token.setLength(0); }
            } else token.append(c);
        }
        if (escaped || single || doubly) throw new IOException("OpenSSH 配置包含未闭合的转义或引号");
        if (!token.isEmpty()) tokens.add(token.toString());
        return tokens;
    }

    private static String joined(List<String> values) { return String.join(" ", values); }

    public record KeyInfo(Path path, boolean encrypted) {
        public KeyInfo { path = path.toAbsolutePath().normalize(); }
    }

    public record Inspection(int hosts, int readableIdentities, int encryptedIdentities, int missingIdentities,
            int proxyHosts, int invalidHosts, int wildcardPatterns, int ignoredOptions, int configFiles,
            List<KeyInfo> identities) {
        public Inspection { identities = List.copyOf(identities); }
        public boolean hasImportableSecrets() { return readableIdentities > 0; }
    }

    public record HostEntry(String name, String host, int port, String username,
            AuthenticationType authentication, Optional<String> credentialKey, Optional<String> jumpName,
            int sortOrder) { }

    public static final class CredentialEntry implements AutoCloseable {
        private final String sourceKey;
        private final String label;
        private final String username;
        private final Path privateKeySource;
        private char[] passphrase;

        CredentialEntry(String sourceKey, String label, String username, Path privateKeySource, char[] passphrase) {
            this.sourceKey = sourceKey;
            this.label = label;
            this.username = username;
            this.privateKeySource = privateKeySource;
            this.passphrase = passphrase.clone();
        }
        public String sourceKey() { return sourceKey; }
        public String label() { return label; }
        public String username() { return username; }
        public Path privateKeySource() { return privateKeySource; }
        public char[] copyPassphrase() {
            if (passphrase == null) throw new IllegalStateException("Credential has been cleared");
            return passphrase.clone();
        }
        @Override public void close() {
            if (passphrase != null) Arrays.fill(passphrase, '\0');
            passphrase = null;
        }
    }

    public record ImportBundle(List<CredentialEntry> credentials, List<HostEntry> hosts,
            Inspection inspection) implements AutoCloseable {
        public ImportBundle { credentials = List.copyOf(credentials); hosts = List.copyOf(hosts); }
        @Override public void close() { credentials.forEach(CredentialEntry::close); }
    }

    private record Directive(String key, List<String> values, Path base) { }
    private record IdentitySpec(String value, Path base) { }
    private record ResolvedHost(String alias, String hostName, int port, String username,
            Optional<KeyInfo> identity, Optional<String> jumpAlias, int sortOrder) { }
    private record Parsed(List<ResolvedHost> hosts, Inspection inspection) { }
    private static final class ParseLimits { int files; }
    private static final class HostOptions {
        String hostName; String username; String port; final List<IdentitySpec> identityFiles = new ArrayList<>();
        String proxyJump; boolean proxyCommand;
    }
}
