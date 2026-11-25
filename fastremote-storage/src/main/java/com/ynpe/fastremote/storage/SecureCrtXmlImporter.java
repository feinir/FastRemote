// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import com.ynpe.fastremote.domain.connection.AuthenticationType;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.bouncycastle.crypto.generators.BCrypt;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Reads SecureCRT XML exports without importing their UI, path or terminal preferences. */
public final class SecureCrtXmlImporter {
    private static final long MAX_XML_BYTES = 64L * 1024 * 1024;
    private static final long MAX_KEY_BYTES = 1024L * 1024;
    private static final int MAX_RECORDS = 20_000;
    private static final int MAX_DEPTH = 32;
    private static final byte[] ZERO_IV = new byte[16];

    public Inspection inspect(Path source) throws IOException {
        Parsed parsed = parse(source);
        return parsed.inspection();
    }

    public void verifyPassphrase(Path source, char[] passphrase) throws IOException {
        Objects.requireNonNull(passphrase, "passphrase");
        Parsed parsed = parse(source);
        String encrypted = parsed.firstEncryptedPassword();
        if (encrypted == null) return;
        char[] clear = decryptPassword(encrypted, passphrase);
        Arrays.fill(clear, '\0');
    }

    public ImportBundle read(Path source, char[] passphrase, String hostSpecificSuffix) throws IOException {
        Objects.requireNonNull(passphrase, "passphrase");
        String safeHostSpecificSuffix = Objects.requireNonNull(hostSpecificSuffix, "hostSpecificSuffix");
        Parsed parsed = parse(source);
        List<CredentialEntry> credentialEntries = new ArrayList<>();
        Map<String, CredentialEntry> shared = new HashMap<>();
        try {
            for (RawCredential credential : parsed.credentials()) {
                CredentialEntry converted = convertCredential(
                        "shared:" + credential.title(), credential.title(), credential, passphrase, true);
                if (converted != null) {
                    credentialEntries.add(converted);
                    shared.put(normalize(credential.title()), converted);
                }
            }

            Map<String, String> hostKeys = new HashMap<>();
            for (RawHost host : parsed.hosts()) {
                hostKeys.put(normalizeRouteName(host.sourceKey()), host.sourceKey());
                hostKeys.putIfAbsent(normalizeRouteName(host.name()), host.sourceKey());
            }
            List<HostEntry> hosts = new ArrayList<>();
            for (RawHost host : parsed.hosts()) {
                String credentialKey = null;
                AuthenticationType authentication = AuthenticationType.PASSWORD;
                String username = host.username();
                if (!host.credentialTitle().isBlank()) {
                    CredentialEntry referenced = shared.get(normalize(host.credentialTitle()));
                    if (referenced != null) {
                        credentialKey = referenced.sourceKey();
                        authentication = referenced.authentication();
                        if (!referenced.username().isBlank()) username = referenced.username();
                    }
                }
                if (credentialKey == null) {
                    CredentialEntry bound = convertCredential(
                            "host:" + host.sourceKey(), host.name() + safeHostSpecificSuffix,
                            host.asCredential(), passphrase, false);
                    if (bound != null) {
                        credentialEntries.add(bound);
                        credentialKey = bound.sourceKey();
                        authentication = bound.authentication();
                    }
                }
                if (username.isBlank()) continue;
                hosts.add(new HostEntry(
                        host.sourceKey(), host.name(), host.groupKey(), host.host(), host.port(),
                        username, authentication, Optional.ofNullable(credentialKey),
                        resolveFirewallHost(host.firewall(), hostKeys), host.sortOrder()));
            }
            return new ImportBundle(parsed.groups(), credentialEntries, hosts, parsed.inspection());
        } catch (Throwable failure) {
            credentialEntries.forEach(CredentialEntry::close);
            throw failure;
        }
    }

    private static CredentialEntry convertCredential(
            String sourceKey, String label, RawCredential raw, char[] passphrase, boolean reusable) throws IOException {
        Optional<Path> key = readableKey(raw.identityFile());
        boolean prefersKey = false;
        for (String configured : raw.authentications()) {
            String normalized = configured.toLowerCase(Locale.ROOT);
            if (normalized.contains("publickey") || normalized.contains("public key")) {
                prefersKey = true;
                break;
            }
            if (normalized.contains("password")) break;
        }
        if (prefersKey && key.isPresent()) {
            return new CredentialEntry(sourceKey, label, raw.username(), AuthenticationType.PRIVATE_KEY,
                    new char[0], key, reusable);
        }
        if (raw.passwordSaved() && !raw.encryptedPassword().isBlank()) {
            char[] clear = decryptPassword(raw.encryptedPassword(), passphrase);
            if (clear.length == 0) {
                Arrays.fill(clear, '\0');
                return null;
            }
            return new CredentialEntry(sourceKey, label, raw.username(), AuthenticationType.PASSWORD,
                    clear, Optional.empty(), reusable);
        }
        if (key.isPresent()) {
            return new CredentialEntry(sourceKey, label, raw.username(), AuthenticationType.PRIVATE_KEY,
                    new char[0], key, reusable);
        }
        return null;
    }

    private static Optional<Path> readableKey(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            Path path = Path.of(value).toAbsolutePath().normalize();
            String fileName = path.getFileName().toString();
            if (fileName.toLowerCase(Locale.ROOT).endsWith(".pub")) {
                String privateKeyName = fileName.substring(0, fileName.length() - ".pub".length());
                if (privateKeyName.isEmpty()) return Optional.empty();
                path = path.resolveSibling(privateKeyName);
            }
            long size = Files.size(path);
            return Files.isRegularFile(path) && Files.isReadable(path) && size > 0 && size <= MAX_KEY_BYTES
                    ? Optional.of(path) : Optional.empty();
        } catch (IOException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static Parsed parse(Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        long size = Files.size(source);
        if (!Files.isRegularFile(source) || size < 1 || size > MAX_XML_BYTES) {
            throw new IOException("SecureCRT XML 文件为空或超过 64 MiB 限制");
        }
        Document document;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            document = factory.newDocumentBuilder().parse(source.toFile());
        } catch (SAXException | RuntimeException failure) {
            throw new IOException("SecureCRT XML 文件格式无效", failure);
        } catch (Exception failure) {
            throw new IOException("无法安全读取 SecureCRT XML 文件", failure);
        }
        Element root = document.getDocumentElement();
        if (root == null || !"VanDyke".equals(root.getTagName())) {
            throw new IOException("所选文件不是 SecureCRT XML 导出文件");
        }
        Element hostsRoot = directKey(root, "Sessions");
        if (hostsRoot == null) throw new IOException("SecureCRT XML 文件中没有会话数据");

        List<GroupEntry> groups = new ArrayList<>();
        List<RawHost> hosts = new ArrayList<>();
        Counter counter = new Counter();
        walkHosts(hostsRoot, List.of(), groups, hosts, counter, 0);
        List<RawCredential> credentials = parseCredentials(directKey(root, "Credentials"), counter);
        Map<String, String> sharedUsernames = new HashMap<>();
        for (RawCredential credential : credentials) {
            sharedUsernames.put(normalize(credential.title()), credential.username());
        }
        int beforeUsernameValidation = hosts.size();
        hosts = hosts.stream().filter(host -> !host.username().isBlank()
                        || !sharedUsernames.getOrDefault(normalize(host.credentialTitle()), "").isBlank())
                .toList();
        counter.skipped += beforeUsernameValidation - hosts.size();

        int encrypted = 0;
        int certificateReferences = 0;
        int readableCertificates = 0;
        String firstEncrypted = null;
        for (RawCredential credential : credentials) {
            if (credential.passwordSaved() && !credential.encryptedPassword().isBlank()) {
                encrypted++;
                if (firstEncrypted == null) firstEncrypted = credential.encryptedPassword();
            }
            if (!credential.identityFile().isBlank()) {
                certificateReferences++;
                if (readableKey(credential.identityFile()).isPresent()) readableCertificates++;
            }
        }
        for (RawHost host : hosts) {
            if (host.passwordSaved() && !host.encryptedPassword().isBlank()) {
                encrypted++;
                if (firstEncrypted == null) firstEncrypted = host.encryptedPassword();
            }
            if (!host.identityFile().isBlank()) {
                certificateReferences++;
                if (readableKey(host.identityFile()).isPresent()) readableCertificates++;
            }
        }
        Inspection inspection = new Inspection(hosts.size(), groups.size(), credentials.size(), encrypted,
                certificateReferences, readableCertificates,
                certificateReferences - readableCertificates, counter.skipped);
        return new Parsed(groups, credentials, hosts, inspection, firstEncrypted);
    }

    private static void walkHosts(
            Element parent, List<String> folderPath, List<GroupEntry> groups, List<RawHost> hosts,
            Counter counter, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("SecureCRT 会话目录层级过深");
        for (Element key : directChildren(parent, "key")) {
            counter.increment();
            String name = attribute(key, "name");
            Map<String, String> values = directValues(key);
            if (isHost(values)) {
                if (!"SSH2".equalsIgnoreCase(values.getOrDefault("Protocol Name", ""))) {
                    counter.skipped++;
                    continue;
                }
                String host = value(values, "Hostname", "Host Name");
                String username = value(values, "Username", "User Name");
                if (host.isBlank() || name.isBlank()) {
                    counter.skipped++;
                    continue;
                }
                int port = parseDword(value(values, "[SSH2] Port", "Port"), 22);
                String groupKey = groupKey(folderPath);
                hosts.add(new RawHost(
                        String.join("/", append(folderPath, name)), name, Optional.ofNullable(groupKey), host, port,
                        username, value(values, "Credential Title"),
                        parseBoolean(value(values, "Session Password Saved", "Password Saved")),
                        value(values, "Password V2"), value(values, "Identity Filename V2", "Identity Filename"),
                        authenticationValues(key), value(values, "Firewall Name", "Firewall", "[SSH2] Firewall"),
                        hosts.size() * 10));
            } else {
                List<String> next = append(folderPath, name);
                ensureGroups(next, groups);
                walkHosts(key, next, groups, hosts, counter, depth + 1);
            }
        }
    }

    private static void ensureGroups(List<String> path, List<GroupEntry> groups) {
        if (path.isEmpty()) return;
        String primaryKey = "group:" + path.get(0);
        if (groups.stream().noneMatch(group -> group.sourceKey().equals(primaryKey))) {
            groups.add(new GroupEntry(primaryKey, Optional.empty(), path.get(0), groups.size() * 10));
        }
        if (path.size() > 1) {
            String secondaryName = String.join(" / ", path.subList(1, path.size()));
            String secondaryKey = primaryKey + "/" + secondaryName;
            if (groups.stream().noneMatch(group -> group.sourceKey().equals(secondaryKey))) {
                groups.add(new GroupEntry(secondaryKey, Optional.of(primaryKey), secondaryName, groups.size() * 10));
            }
        }
    }

    private static String groupKey(List<String> path) {
        if (path.isEmpty()) return null;
        String primary = "group:" + path.get(0);
        return path.size() == 1 ? primary : primary + "/" + String.join(" / ", path.subList(1, path.size()));
    }

    private static List<RawCredential> parseCredentials(Element root, Counter counter) throws IOException {
        if (root == null) return List.of();
        List<RawCredential> result = new ArrayList<>();
        for (Element key : directChildren(root, "key")) {
            counter.increment();
            Map<String, String> values = directValues(key);
            String title = attribute(key, "name");
            if (title.isBlank()) continue;
            result.add(new RawCredential(title, value(values, "Username", "User Name"),
                    parseBoolean(value(values, "Session Password Saved", "Password Saved")),
                    value(values, "Password V2"), value(values, "Identity Filename V2", "Identity Filename"),
                    authenticationValues(key)));
        }
        return result;
    }

    private static List<String> authenticationValues(Element key) {
        for (Element child : directChildren(key, null)) {
            if ("SSH2 Authentications V2".equals(attribute(child, "name"))) {
                List<String> result = new ArrayList<>();
                for (Element value : directChildren(child, null)) {
                    String text = value.getTextContent().strip();
                    if (!text.isBlank()) result.add(text);
                }
                return result;
            }
        }
        return List.of();
    }

    private static Map<String, String> directValues(Element parent) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Element child : directChildren(parent, null)) {
            if ("key".equals(child.getTagName())) continue;
            String name = attribute(child, "name");
            if (!name.isBlank()) result.put(name, child.getTextContent().strip());
        }
        return result;
    }

    private static boolean isHost(Map<String, String> values) {
        return parseBoolean(values.getOrDefault("Is Session", ""));
    }

    private static int parseDword(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            String stripped = value.strip();
            long parsed = stripped.matches("(?i)[0-9a-f]{8}")
                    ? Long.parseUnsignedLong(stripped, 16) : Long.parseLong(stripped);
            return parsed >= 1 && parsed <= 65_535 ? (int) parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String value) {
        if (value == null) return false;
        String normalized = value.strip();
        if (normalized.isEmpty()) return false;
        if ("true".equalsIgnoreCase(normalized)) return true;
        try {
            return Long.parseUnsignedLong(normalized, 16) != 0;
        } catch (NumberFormatException ignored) {
            return "1".equals(normalized);
        }
    }

    private static Element directKey(Element parent, String name) {
        return directChildren(parent, "key").stream()
                .filter(child -> name.equals(attribute(child, "name"))).findFirst().orElse(null);
    }

    private static List<Element> directChildren(Element parent, String tag) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element && (tag == null || tag.equals(element.getTagName()))) {
                result.add(element);
            }
        }
        return result;
    }

    private static String attribute(Element element, String name) {
        return element.hasAttribute(name) ? element.getAttribute(name).strip() : "";
    }

    private static String value(Map<String, String> values, String... names) {
        for (String name : names) {
            String result = values.get(name);
            if (result != null) return result;
        }
        return "";
    }

    private static List<String> append(List<String> source, String value) {
        ArrayList<String> result = new ArrayList<>(source);
        if (value != null && !value.isBlank()) result.add(value.strip());
        return List.copyOf(result);
    }

    private static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static Optional<String> resolveFirewallHost(String configured, Map<String, String> hostKeys) {
        if (configured == null || configured.isBlank()) return Optional.empty();
        String value = configured.strip();
        if (value.regionMatches(true, 0, "Session:", 0, "Session:".length())) {
            value = value.substring("Session:".length());
        }
        return Optional.ofNullable(hostKeys.get(normalizeRouteName(value)));
    }

    private static String normalizeRouteName(String value) {
        String normalized = value.strip().replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        return normalized.toLowerCase(Locale.ROOT);
    }

    static char[] decryptPassword(String encoded, char[] passphrase) throws IOException {
        Objects.requireNonNull(encoded, "encoded");
        Objects.requireNonNull(passphrase, "passphrase");
        int separator = encoded.indexOf(':');
        if (separator != 2) throw new IOException("SecureCRT 密码字段格式不受支持");
        String version = encoded.substring(0, separator);
        byte[] cipherText;
        byte[] passwordBytes = null;
        byte[] derived = null;
        byte[] clear = null;
        try {
            cipherText = java.util.HexFormat.of().parseHex(encoded.substring(separator + 1).strip());
            ByteBuffer encodedPassword = StandardCharsets.UTF_8.encode(java.nio.CharBuffer.wrap(passphrase));
            passwordBytes = new byte[encodedPassword.remaining()];
            encodedPassword.get(passwordBytes);
            byte[] encrypted;
            byte[] key;
            byte[] iv;
            if ("02".equals(version)) {
                key = MessageDigest.getInstance("SHA-256").digest(passwordBytes);
                iv = ZERO_IV;
                encrypted = cipherText;
                derived = key;
            } else if ("03".equals(version)) {
                if (cipherText.length < 32) throw new IOException("SecureCRT 密码字段已截断");
                byte[] salt = Arrays.copyOfRange(cipherText, 0, 16);
                try {
                    derived = BCrypt.pbkdfGenerate(passwordBytes, salt, 16, 48);
                } finally {
                    Arrays.fill(salt, (byte) 0);
                }
                key = Arrays.copyOfRange(derived, 0, 32);
                iv = Arrays.copyOfRange(derived, 32, 48);
                encrypted = Arrays.copyOfRange(cipherText, 16, cipherText.length);
            } else {
                throw new IOException("暂不支持此 SecureCRT 密码版本：" + version);
            }
            try {
                Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
                clear = cipher.doFinal(encrypted);
            } finally {
                if (key != derived) Arrays.fill(key, (byte) 0);
                if (iv != ZERO_IV) Arrays.fill(iv, (byte) 0);
                if (encrypted != cipherText) Arrays.fill(encrypted, (byte) 0);
            }
            if (clear.length < 36) throw new IOException("SecureCRT 源口令错误或密码字段损坏");
            int length = ByteBuffer.wrap(clear, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (length < 0 || length > clear.length - 36) throw new IOException("SecureCRT 源口令错误或密码字段损坏");
            byte[] secretBytes = Arrays.copyOfRange(clear, 4, 4 + length);
            byte[] expected = Arrays.copyOfRange(clear, 4 + length, 4 + length + 32);
            byte[] actual = MessageDigest.getInstance("SHA-256").digest(secretBytes);
            try {
                if (!MessageDigest.isEqual(expected, actual)) {
                    throw new IOException("SecureCRT 源口令错误或密码字段损坏");
                }
                var decoder = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
                return decoder.decode(ByteBuffer.wrap(secretBytes)).toString().toCharArray();
            } catch (CharacterCodingException failure) {
                throw new IOException("SecureCRT 密码不是有效的 UTF-8 文本", failure);
            } finally {
                Arrays.fill(secretBytes, (byte) 0);
                Arrays.fill(expected, (byte) 0);
                Arrays.fill(actual, (byte) 0);
            }
        } catch (IllegalArgumentException | GeneralSecurityException failure) {
            throw new IOException("SecureCRT 密码字段无法解密", failure);
        } finally {
            if (passwordBytes != null) Arrays.fill(passwordBytes, (byte) 0);
            if (derived != null) Arrays.fill(derived, (byte) 0);
            if (clear != null) Arrays.fill(clear, (byte) 0);
        }
    }

    public record Inspection(
            int hosts, int groups, int sharedCredentials, int encryptedPasswords,
            int certificateReferences, int readableCertificates, int missingCertificates, int skippedHosts) {
        public boolean hasSecrets() {
            return encryptedPasswords > 0 || readableCertificates > 0;
        }
    }

    public record GroupEntry(String sourceKey, Optional<String> parentKey, String name, int sortOrder) {}

    public static final class CredentialEntry implements AutoCloseable {
        private final String sourceKey;
        private final String label;
        private final String username;
        private final AuthenticationType authentication;
        private final Optional<Path> privateKeySource;
        private final boolean reusable;
        private char[] secret;

        CredentialEntry(String sourceKey, String label, String username, AuthenticationType authentication,
                char[] secret, Optional<Path> privateKeySource, boolean reusable) {
            this.sourceKey = sourceKey;
            this.label = label;
            this.username = username;
            this.authentication = authentication;
            this.secret = secret;
            this.privateKeySource = privateKeySource;
            this.reusable = reusable;
        }

        public String sourceKey() { return sourceKey; }
        public String label() { return label; }
        public String username() { return username; }
        public AuthenticationType authentication() { return authentication; }
        public Optional<Path> privateKeySource() { return privateKeySource; }
        public boolean reusable() { return reusable; }
        public synchronized char[] copySecret() {
            if (secret == null) throw new IllegalStateException("SecureCRT credential has been cleared");
            return secret.clone();
        }
        @Override public synchronized void close() {
            if (secret != null) Arrays.fill(secret, '\0');
            secret = null;
        }
    }

    public record HostEntry(
            String sourceKey, String name, Optional<String> groupKey, String host, int port, String username,
            AuthenticationType authentication, Optional<String> credentialKey, Optional<String> jumpSourceKey,
            int sortOrder) {}

    public record ImportBundle(
            List<GroupEntry> groups, List<CredentialEntry> credentials, List<HostEntry> hosts,
            Inspection inspection) implements AutoCloseable {
        public ImportBundle {
            groups = List.copyOf(groups);
            credentials = List.copyOf(credentials);
            hosts = List.copyOf(hosts);
        }
        @Override public void close() { credentials.forEach(CredentialEntry::close); }
    }

    private record RawCredential(String title, String username, boolean passwordSaved, String encryptedPassword,
            String identityFile, List<String> authentications) {}

    private record RawHost(
            String sourceKey, String name, Optional<String> groupKey, String host, int port, String username,
            String credentialTitle, boolean passwordSaved, String encryptedPassword, String identityFile,
            List<String> authentications, String firewall, int sortOrder) {
        RawCredential asCredential() {
            return new RawCredential(name, username, passwordSaved, encryptedPassword, identityFile, authentications);
        }
    }

    private record Parsed(List<GroupEntry> groups, List<RawCredential> credentials, List<RawHost> hosts,
            Inspection inspection, String firstEncryptedPassword) {}

    private static final class Counter {
        private int records;
        private int skipped;
        void increment() throws IOException {
            if (++records > MAX_RECORDS) throw new IOException("SecureCRT XML 记录数量超过安全限制");
        }
    }
}
