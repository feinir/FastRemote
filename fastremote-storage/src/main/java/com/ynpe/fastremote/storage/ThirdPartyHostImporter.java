// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import com.ynpe.fastremote.domain.host.HostImportPlan;
import com.ynpe.fastremote.domain.host.HostImportSource;
import com.ynpe.fastremote.domain.connection.AuthenticationType;
import static com.ynpe.fastremote.domain.host.HostImportPlan.Notice.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.net.URLDecoder;
import java.util.*;
import java.util.zip.ZipInputStream;

/** Connection metadata only: never executes registry files, startup scripts or proxy commands. */
public final class ThirdPartyHostImporter {
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final int MAX_FILES = 4096;

    public HostImportPlan read(HostImportSource source, Path path) throws IOException {
        if (source == HostImportSource.BITVISE) return new BitviseProfileImporter().read(path);
        if (source == HostImportSource.TERMIUS) throw new IOException("Termius requires its local data reader");
        List<HostImportPlan.Entry> result = new ArrayList<>();
        if (Files.isDirectory(path)) {
            List<Path> files;
            try (var walk = Files.walk(path, 12)) {
                files = walk.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                        .filter(p -> matches(source, p.getFileName().toString())).limit(MAX_FILES + 1).sorted().toList();
            }
            if (files.size() > MAX_FILES) throw new IOException("Too many session files");
            long bytes = 0;
            for (Path file : files) {
                bytes += Files.size(file);
                if (bytes > MAX_BYTES) throw new IOException("Session data exceeds size limit");
                parse(source, path.relativize(file).toString().replace('\\', '/'), readText(file), result);
            }
        } else if (source == HostImportSource.XSHELL && path.toString().toLowerCase(Locale.ROOT).endsWith(".xts")) {
            readArchive(path, result);
        } else parse(source, path.getFileName().toString(), readText(path), result);
        if (result.size() > 20_000) throw new IOException("Too many sessions");
        HostImportPlan plan = new HostImportPlan(source, result, Map.of());
        try {
            for (var entry : plan.entries()) {
                if (!entry.importable() || entry.keyReference().isBlank()) continue;
                Path key = resolveKey(source, path, entry.keyReference());
                if (key == null) continue;
                try {
                    char[] material = ImportedPrivateKey.read(key);
                    try {
                        if (!ImportedPrivateKey.encrypted(material)) {
                            try (var secret = new HostImportPlan.Secret(new char[0], material)) { plan.attachSecret(entry.id(), secret); }
                        }
                    } finally { Arrays.fill(material, '\0'); }
                } catch (IOException unsupportedKey) {
                    // Keep KEY_REQUIRED visible; the user can select a portable key in the preview.
                }
            }
            return plan;
        } catch (Throwable failure) { plan.close(); throw failure; }
    }

    private static boolean matches(HostImportSource source, String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return switch (source) {
            case XSHELL -> lower.endsWith(".xsh") || lower.endsWith(".xfp");
            case MOBAXTERM -> lower.endsWith(".mxtsessions") || lower.equals("mobaxterm.ini");
            case PUTTY -> true;
            default -> false;
        };
    }

    private static String readText(Path file) throws IOException {
        if (Files.size(file) > MAX_BYTES) throw new IOException("Session file exceeds size limit");
        try (var stream = Files.newInputStream(file)) {
            byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IOException("Session file exceeds size limit");
            return decode(bytes);
        }
    }

    private static String decode(byte[] bytes) throws IOException {
        if (bytes.length >= 2 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xfe)
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        if (bytes.length >= 2 && bytes[0] == (byte) 0xfe && bytes[1] == (byte) 0xff)
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        try { return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString().replaceFirst("^\\ufeff", ""); }
        catch (CharacterCodingException invalidUtf8) {
            try { return Charset.forName("GB18030").newDecoder().decode(ByteBuffer.wrap(bytes)).toString(); }
            catch (CharacterCodingException invalidText) { throw new IOException("Unsupported session text encoding"); }
        }
    }

    private static void readArchive(Path path, List<HostImportPlan.Entry> result) throws IOException {
        int total = 0, count = 0;
        try (var zip = new ZipInputStream(Files.newInputStream(path))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++count > MAX_FILES) throw new IOException("Too many archive entries");
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("../") || name.contains(":")) throw new IOException("Invalid archive path");
                byte[] bytes = zip.readNBytes(MAX_BYTES - total + 1);
                total += bytes.length;
                if (total > MAX_BYTES) throw new IOException("Session archive exceeds size limit");
                if (!entry.isDirectory() && matches(HostImportSource.XSHELL, name))
                    parse(HostImportSource.XSHELL, name, decode(bytes), result);
            }
        }
        if (count == 0) throw new IOException("Unsupported XShell archive; select session files or the Sessions folder");
    }

    private static void parse(HostImportSource source, String file, String text, List<HostImportPlan.Entry> out) throws IOException {
        switch (source) {
            case MOBAXTERM -> moba(file, text, out);
            case XSHELL -> xshell(file, text, out);
            case PUTTY -> putty(file, text, out);
            default -> throw new IOException("Unsupported source");
        }
    }

    static Map<String, Map<String, String>> ini(String text) {
        Map<String, Map<String, String>> sections = new LinkedHashMap<>();
        Map<String, String> current = new LinkedHashMap<>();
        sections.put("", current);
        for (String line : text.split("\\R")) {
            line = line.strip();
            if (line.startsWith("[") && line.endsWith("]")) {
                current = sections.computeIfAbsent(line.substring(1, line.length() - 1), k -> new LinkedHashMap<>());
            } else if (!line.startsWith(";") && !line.startsWith("#") && line.contains("=")) {
                int i = line.indexOf('=');
                current.put(line.substring(0, i).strip(), line.substring(i + 1).strip());
            }
        }
        return sections;
    }

    private static String value(Map<String, String> fields, String name, String fallback) {
        return fields.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name)).map(Map.Entry::getValue).findFirst().orElse(fallback);
    }

    private static void xshell(String file, String text, List<HostImportPlan.Entry> out) {
        var sections = new LinkedHashMap<String,Map<String,String>>();
        ini(text).forEach((k,v)->sections.put(k.toLowerCase(Locale.ROOT),v));
        var c = sections.getOrDefault("connection", Map.of());
        var a = sections.getOrDefault("connection:authentication", Map.of());
        String host = value(c, "Host", "");
        String protocol = value(c, "Protocol", "");
        // Xftp session files use [Connection] and [Authentication].
        if (a.isEmpty()) a = sections.getOrDefault("authentication", Map.of());
        var proxy = sections.getOrDefault("connection:proxy", Map.of());
        String route = value(proxy, "Proxy", "0");
        String key = value(a, "UserKey", "");
        String name = file.substring(file.lastIndexOf('/') + 1).replaceFirst("(?i)\\.(xsh|xfp)$", "");
        String group = file.contains("/") ? file.substring(0, file.lastIndexOf('/')) : "";
        List<HostImportPlan.Notice> notices = new ArrayList<>();
        if (!protocol.equalsIgnoreCase("SSH") && !protocol.equalsIgnoreCase("SFTP")) notices.add(UNSUPPORTED_PROTOCOL);
        if (!route.isBlank() && !route.equals("0")) notices.add(UNSUPPORTED_ROUTE);
        if (!key.isBlank()) notices.add(KEY_REQUIRED);
        else notices.add(PASSWORD_REQUIRED);
        add(out, file, name, group, host, value(c,"Port","22"), value(a,"UserName",""), !key.isBlank(), notices);
        reference(out, key);
    }

    private static void moba(String file, String text, List<HostImportPlan.Entry> out) {
        for (var section : ini(text).entrySet()) {
            if (!section.getKey().toLowerCase(Locale.ROOT).matches("bookmarks(?:_\\d+)?")) continue;
            String group = value(section.getValue(), "SubRep", "").replace('\\', '/');
            for (var row : section.getValue().entrySet()) {
                if (row.getKey().equalsIgnoreCase("SubRep") || row.getKey().equalsIgnoreCase("ImgNum")) continue;
                String[] chunks = row.getValue().split("#", -1);
                String[] fields = chunks.length > 2 ? chunks[2].split("%", -1) : new String[0];
                List<HostImportPlan.Notice> notices = new ArrayList<>();
                if (fields.length < 4 || !fields[0].equals("0")) {
                    notices.add(UNSUPPORTED_PROTOCOL);
                    add(out, file+":"+section.getKey()+":"+row.getKey(), row.getKey(), group, "", "22", "", false, notices);
                    continue;
                }
                // The SSH bookmark field layout: protocol, host, port, user; private key at 14,
                // gateway host/port/user at 20/21/22. Unknown routes must never become direct hosts.
                boolean key = fields.length > 14 && !fields[14].isBlank();
                if (fields.length > 20 && !fields[20].isBlank()) notices.add(UNSUPPORTED_ROUTE);
                notices.add(key ? KEY_REQUIRED : PASSWORD_REQUIRED);
                add(out, file+":"+section.getKey()+":"+row.getKey(), row.getKey(), group, fields[1], fields[2], fields[3], key, notices);
                reference(out, key ? fields[14] : "");
            }
        }
    }

    private static void putty(String file, String text, List<HostImportPlan.Entry> out) throws IOException {
        boolean registry = text.stripLeading().startsWith("Windows Registry Editor") || text.startsWith("REGEDIT4");
        if (!registry) {
            addPutty(file, file, ini(text).getOrDefault("", Map.of()), out);
            return;
        }
        String prefix = "hkey_current_user\\software\\simontatham\\putty\\sessions\\";
        for (var section : ini(text).entrySet()) {
            if (!section.getKey().toLowerCase(Locale.ROOT).startsWith(prefix)) continue;
            String encoded = section.getKey().substring(prefix.length());
            if (encoded.contains("\\")) continue;
            Map<String, String> fields = new LinkedHashMap<>();
            for (var field : section.getValue().entrySet()) {
                String k = unquote(field.getKey());
                String v = field.getValue();
                if (v.startsWith("dword:")) {
                    try { v = Long.toString(Long.parseLong(v.substring(6),16)); }
                    catch (NumberFormatException invalid) { throw new IOException("Invalid PuTTY numeric field"); }
                } else v = unquote(v);
                fields.put(k,v);
            }
            String name;
            try { name = URLDecoder.decode(encoded.replace("+", "%2B"), StandardCharsets.UTF_8); }
            catch (IllegalArgumentException malformed) { throw new IOException("Invalid PuTTY session name"); }
            addPutty(file+":"+encoded, name, fields, out);
        }
    }

    private static String unquote(String s) {
        if (s.length() < 2 || !s.startsWith("\"") || !s.endsWith("\"")) return s;
        StringBuilder result = new StringBuilder();
        for (int i=1;i<s.length()-1;i++) {
            char c=s.charAt(i);
            if(c=='\\' && i+1<s.length()-1 && (s.charAt(i+1)=='\\' || s.charAt(i+1)=='"')) c=s.charAt(++i);
            result.append(c);
        }
        return result.toString();
    }

    private static void addPutty(String id, String name, Map<String,String> c, List<HostImportPlan.Entry> out) {
        String host = value(c,"HostName","");
        if (host.isBlank()) return; // Empty Default Settings is a template, not a saved host.
        boolean key = !value(c,"PublicKeyFile","").isBlank();
        List<HostImportPlan.Notice> notices = new ArrayList<>();
        if (!value(c,"Protocol","ssh").equalsIgnoreCase("ssh")) notices.add(UNSUPPORTED_PROTOCOL);
        if (!value(c,"ProxyMethod","0").equals("0")) notices.add(UNSUPPORTED_ROUTE);
        notices.add(key ? KEY_REQUIRED : PASSWORD_REQUIRED);
        add(out, id, name, "", host, value(c,"PortNumber","22"), value(c,"UserName",""), key, notices);
        reference(out, value(c,"PublicKeyFile",""));
    }

    private static void reference(List<HostImportPlan.Entry> entries,String reference) {
        var e=entries.getLast();entries.set(entries.size()-1,new HostImportPlan.Entry(e.id(),e.name(),e.group(),e.host(),e.port(),e.username(),e.authentication(),e.jumpId(),e.notices(),e.importable(),reference));
    }
    private static Path resolveKey(HostImportSource source,Path selected,String reference) {
        Path root=Files.isDirectory(selected)?selected:selected.toAbsolutePath().getParent();
        String ref=reference.strip();
        if(ref.startsWith("_CurrentDrive_") && root.getRoot()!=null)ref=root.getRoot()+ref.substring("_CurrentDrive_".length()).replaceFirst("^[\\\\/]", "");
        Path direct;
        try {direct=Path.of(ref);}catch(InvalidPathException invalid){return null;}
        if(Files.isRegularFile(direct))return direct;
        if(!direct.isAbsolute() && Files.isRegularFile(root.resolve(direct)))return root.resolve(direct);
        if(source==HostImportSource.XSHELL && !ref.contains("/") && !ref.contains("\\")) {
            for(Path parent=root;parent!=null;parent=parent.getParent()){
                Path key=parent.resolve("SECSH/UserKeys").resolve(ref.endsWith(".pri")?ref:ref+".pri");
                if(Files.isRegularFile(key))return key;
            }
        }
        return null;
    }

    private static void add(List<HostImportPlan.Entry> out, String id, String name, String group, String host,
                            String portText, String user, boolean key, List<HostImportPlan.Notice> notices) {
        int port = 22;
        try {
            port = Integer.parseInt(portText);
            new com.ynpe.fastremote.domain.connection.Host(host);
            new com.ynpe.fastremote.domain.connection.Port(port);
            if (name.isBlank() || name.length()>255 || group.length()>255) throw new IllegalArgumentException();
        } catch (IllegalArgumentException invalid) { notices.add(INVALID_RECORD); }
        if (user.isBlank()) notices.add(USERNAME_REQUIRED);
        if (group.contains("/")) notices.add(GROUP_FLATTENED);
        boolean valid = !notices.contains(INVALID_RECORD) && !notices.contains(UNSUPPORTED_PROTOCOL) && !notices.contains(UNSUPPORTED_ROUTE);
        out.add(new HostImportPlan.Entry(id, name, group, host, port, user,
                key ? AuthenticationType.PRIVATE_KEY : AuthenticationType.PASSWORD, "", notices, valid));
        if (out.size() > 20_000) throw new IllegalArgumentException("Too many sessions");
    }
}
