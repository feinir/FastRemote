// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import com.ynpe.fastremote.domain.settings.TerminalColorPalette;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Safely imports Windows Terminal JSON schemes and iTerm2 .itermcolors plist files. */
public final class TerminalColorSchemeImporter {
    private static final int MAX_FILE_BYTES = 1024 * 1024;
    private static final String[] WINDOWS_COLORS = {
            "black", "red", "green", "yellow", "blue", "purple", "cyan", "white",
            "brightBlack", "brightRed", "brightGreen", "brightYellow",
            "brightBlue", "brightPurple", "brightCyan", "brightWhite"
    };

    public List<TerminalColorPalette> importFile(Path source) throws IOException {
        Path path = source.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IOException("配色文件不可读取");
        }
        long size = Files.size(path);
        if (size <= 0L || size > MAX_FILE_BYTES) {
            throw new IOException("配色文件必须小于 1 MiB 且不能为空");
        }
        byte[] bytes = Files.readAllBytes(path);
        String prefix = new String(bytes, 0, Math.min(bytes.length, 128), StandardCharsets.UTF_8).stripLeading();
        if (prefix.startsWith("\uFEFF")) prefix = prefix.substring(1).stripLeading();
        return prefix.startsWith("<") ? List.of(importIterm(path, bytes)) : importWindows(path, bytes);
    }

    private static List<TerminalColorPalette> importWindows(Path source, byte[] bytes) throws IOException {
        Load loader = new Load(LoadSettings.builder()
                .setLabel("Windows Terminal color scheme")
                .setAllowDuplicateKeys(false)
                .setMaxAliasesForCollections(0)
                .setCodePointLimit(MAX_FILE_BYTES)
                .build());
        Object loaded;
        try {
            String json = new String(bytes, StandardCharsets.UTF_8);
            if (json.startsWith("\uFEFF")) json = json.substring(1);
            loaded = loader.loadFromString(stripJsonComments(json));
        } catch (RuntimeException invalid) {
            throw new IOException("Windows Terminal 配色 JSON 无效", invalid);
        }
        if (!(loaded instanceof Map<?, ?> root)) throw new IOException("Windows Terminal 配色必须是 JSON 对象");
        Object schemes = root.get("schemes");
        List<?> entries = schemes instanceof List<?> list ? list : List.of(root);
        if (entries.isEmpty() || entries.size() > 256) throw new IOException("配色文件没有可导入方案或方案过多");
        List<TerminalColorPalette> result = new ArrayList<>(entries.size());
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> values)) throw new IOException("Windows Terminal schemes 必须包含对象");
            String name = text(values, "name", fallbackName(source));
            List<Integer> ansi = new ArrayList<>(16);
            for (String field : WINDOWS_COLORS) {
                Object value = values.get(field);
                if (value == null && field.equals("purple")) value = values.get("magenta");
                if (value == null && field.equals("brightPurple")) value = values.get("brightMagenta");
                ansi.add(color(value, field));
            }
            int foreground = color(values.get("foreground"), "foreground");
            int background = color(values.get("background"), "background");
            int cursor = values.containsKey("cursorColor")
                    ? color(values.get("cursorColor"), "cursorColor") : foreground;
            int selection = values.containsKey("selectionBackground")
                    ? color(values.get("selectionBackground"), "selectionBackground") : ansi.get(4);
            result.add(new TerminalColorPalette(name, foreground, background, cursor, selection, ansi));
        }
        return List.copyOf(result);
    }

    private static TerminalColorPalette importIterm(Path source, byte[] bytes) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Element root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes)).getDocumentElement();
            Element dictionary = firstChild(root, "dict");
            Map<String, Element> values = plistDictionary(dictionary);
            String name = elementText(values.get("Name"), fallbackName(source));
            List<Integer> ansi = new ArrayList<>(16);
            for (int index = 0; index < 16; index++) {
                ansi.add(itermColor(values.get("Ansi " + index + " Color"), "Ansi " + index + " Color"));
            }
            int foreground = itermColor(values.get("Foreground Color"), "Foreground Color");
            int background = itermColor(values.get("Background Color"), "Background Color");
            int cursor = values.containsKey("Cursor Color")
                    ? itermColor(values.get("Cursor Color"), "Cursor Color") : foreground;
            int selection = values.containsKey("Selection Color")
                    ? itermColor(values.get("Selection Color"), "Selection Color") : ansi.get(4);
            return new TerminalColorPalette(name, foreground, background, cursor, selection, ansi);
        } catch (IOException expected) {
            throw expected;
        } catch (Exception invalid) {
            throw new IOException("iTerm2 .itermcolors 文件无效", invalid);
        }
    }

    private static Map<String, Element> plistDictionary(Element dictionary) throws IOException {
        Map<String, Element> result = new LinkedHashMap<>();
        for (Node node = dictionary.getFirstChild(); node != null;) {
            if (!(node instanceof Element key) || !key.getTagName().equals("key")) {
                node = node.getNextSibling();
                continue;
            }
            Node valueNode = key.getNextSibling();
            while (valueNode != null && !(valueNode instanceof Element)) valueNode = valueNode.getNextSibling();
            if (!(valueNode instanceof Element value)) throw new IOException("iTerm2 plist 键缺少值");
            String name = key.getTextContent();
            if (result.putIfAbsent(name, value) != null) throw new IOException("iTerm2 plist 包含重复键：" + name);
            node = value.getNextSibling();
        }
        return result;
    }

    private static int itermColor(Element value, String field) throws IOException {
        if (value == null || !value.getTagName().equals("dict")) throw new IOException("iTerm2 缺少颜色：" + field);
        Map<String, Element> parts = plistDictionary(value);
        int red = component(parts.get("Red Component"), field);
        int green = component(parts.get("Green Component"), field);
        int blue = component(parts.get("Blue Component"), field);
        return (red << 16) | (green << 8) | blue;
    }

    private static int component(Element value, String field) throws IOException {
        try {
            double component = Double.parseDouble(elementText(value, ""));
            if (!Double.isFinite(component) || component < 0.0 || component > 1.0) throw new NumberFormatException();
            return (int) Math.round(component * 255.0);
        } catch (NumberFormatException invalid) {
            throw new IOException("iTerm2 颜色分量无效：" + field, invalid);
        }
    }

    private static Element firstChild(Element parent, String name) throws IOException {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && element.getTagName().equals(name)) return element;
        }
        throw new IOException("iTerm2 plist 缺少 " + name);
    }

    private static int color(Object value, String field) throws IOException {
        if (!(value instanceof String text)) throw new IOException("Windows Terminal 配色缺少 " + field);
        String normalized = text.strip();
        if (normalized.matches("#[0-9A-Fa-f]{6}")) return Integer.parseInt(normalized.substring(1), 16);
        if (normalized.matches("#[0-9A-Fa-f]{3}")) {
            int r = Character.digit(normalized.charAt(1), 16);
            int g = Character.digit(normalized.charAt(2), 16);
            int b = Character.digit(normalized.charAt(3), 16);
            return (r * 17 << 16) | (g * 17 << 8) | b * 17;
        }
        throw new IOException("Windows Terminal 颜色 " + field + " 必须是 #RRGGBB");
    }

    private static String text(Map<?, ?> values, String field, String fallback) throws IOException {
        Object value = values.get(field);
        if (value == null) return fallback;
        if (value instanceof String text && !text.isBlank()) return text.strip();
        throw new IOException("配色名称无效");
    }

    private static String elementText(Element value, String fallback) {
        return value == null || value.getTextContent().isBlank() ? fallback : value.getTextContent().strip();
    }

    private static String fallbackName(Path source) {
        String name = source.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String stripJsonComments(String source) throws IOException {
        StringBuilder result = new StringBuilder(source.length());
        boolean quoted = false;
        boolean escaped = false;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            if (quoted) {
                result.append(current);
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == '"') quoted = false;
                continue;
            }
            if (current == '"') {
                quoted = true;
                result.append(current);
                continue;
            }
            if (current == '/' && index + 1 < source.length()) {
                char next = source.charAt(index + 1);
                if (next == '/') {
                    index += 2;
                    while (index < source.length() && source.charAt(index) != '\n'
                            && source.charAt(index) != '\r') index++;
                    if (index < source.length()) result.append(source.charAt(index));
                    continue;
                }
                if (next == '*') {
                    int end = source.indexOf("*/", index + 2);
                    if (end < 0) throw new IOException("Windows Terminal JSON 注释未闭合");
                    index = end + 1;
                    continue;
                }
            }
            result.append(current);
        }
        if (quoted) throw new IOException("Windows Terminal JSON 字符串未闭合");
        return result.toString();
    }
}
