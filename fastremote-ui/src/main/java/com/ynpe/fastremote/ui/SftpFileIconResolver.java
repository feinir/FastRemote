// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.ui.LucideIconView.Icon;
import java.util.Locale;
import java.util.Set;

/** Chooses SFTP graphics from names only; it never probes or downloads remote file content. */
final class SftpFileIconResolver {
    enum Type {
        FOLDER(Icon.FOLDER, "sftp-folder-icon"),
        FILE(Icon.FILE, "sftp-file-icon"),
        TEXT(Icon.FILE_TEXT, "sftp-text-icon"),
        LOG(Icon.SCROLL_TEXT, "sftp-log-icon"),
        SHELL(Icon.FILE_TERMINAL, "sftp-shell-icon"),
        SOURCE(Icon.FILE_CODE_2, "sftp-source-icon"),
        WEB(Icon.GLOBE, "sftp-web-icon"),
        MARKDOWN(Icon.FILE_TYPE_2, "sftp-markdown-icon"),
        CONFIG(Icon.FILE_COG, "sftp-config-icon"),
        DATA(Icon.FILE_JSON_2, "sftp-data-icon"),
        DATABASE(Icon.DATABASE, "sftp-database-icon"),
        PDF(Icon.FILE_TEXT, "sftp-pdf-icon"),
        DOCUMENT(Icon.FILES, "sftp-document-icon"),
        SPREADSHEET(Icon.FILE_SPREADSHEET, "sftp-spreadsheet-icon"),
        PRESENTATION(Icon.PRESENTATION, "sftp-presentation-icon"),
        ARCHIVE(Icon.FILE_ARCHIVE, "sftp-archive-icon"),
        IMAGE(Icon.FILE_IMAGE, "sftp-image-icon"),
        AUDIO(Icon.AUDIO_LINES, "sftp-audio-icon"),
        VIDEO(Icon.FILE_VIDEO, "sftp-video-icon"),
        CERTIFICATE(Icon.KEY_ROUND, "sftp-certificate-icon"),
        EXECUTABLE(Icon.BINARY, "sftp-executable-icon"),
        FONT(Icon.TYPE, "sftp-font-icon"),
        PACKAGE(Icon.PACKAGE, "sftp-package-icon");

        private final Icon icon;
        private final String styleClass;

        Type(Icon icon, String styleClass) {
            this.icon = icon;
            this.styleClass = styleClass;
        }

        Icon icon() {
            return icon;
        }

        String styleClass() {
            return styleClass;
        }
    }

    private static final Set<String> SHELL_NAMES = Set.of(
            ".bashrc", ".bash_profile", ".bash_aliases", ".zshrc", ".zprofile", ".profile");
    private static final Set<String> SOURCE_NAMES = Set.of(
            "dockerfile", "makefile", "gnumakefile", "cmakelists.txt", "jenkinsfile");
    private static final Set<String> TEXT_NAMES = Set.of(
            "readme", "license", "copying", "notice", "authors", "changelog");

    private SftpFileIconResolver() {}

    static Type resolve(String name, boolean directory) {
        if (directory) return Type.FOLDER;
        String normalized = name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
        if (SHELL_NAMES.contains(normalized)) return Type.SHELL;
        if (SOURCE_NAMES.contains(normalized)) return Type.SOURCE;
        if (TEXT_NAMES.contains(normalized)) return Type.TEXT;
        int dot = normalized.lastIndexOf('.');
        String extension = dot >= 0 && dot + 1 < normalized.length() ? normalized.substring(dot + 1) : "";
        return switch (extension) {
            case "txt", "text" -> Type.TEXT;
            case "log", "out" -> Type.LOG;
            case "sh", "bash", "zsh", "ksh", "fish", "command" -> Type.SHELL;
            case "c", "h", "cc", "cpp", "cxx", "hpp", "hxx", "cs", "csx", "java", "kt", "kts",
                    "go", "rs", "py", "pyw", "pyi", "rb", "php", "phtml", "swift", "dart", "r",
                    "lua", "pl", "pm", "scala", "sc", "groovy", "gradle", "js", "mjs", "cjs",
                    "jsx", "ts", "mts", "cts", "tsx", "vue", "svelte", "proto" -> Type.SOURCE;
            case "html", "htm", "css", "scss", "sass", "less" -> Type.WEB;
            case "md", "markdown", "rst", "adoc" -> Type.MARKDOWN;
            case "conf", "config", "cnf", "cfg", "ini", "properties", "env", "toml", "yaml", "yml",
                    "tf", "tfvars", "hcl", "service", "socket", "timer", "mount", "target", "path" -> Type.CONFIG;
            case "json", "jsonc", "json5", "jsonl", "ndjson", "geojson", "xml", "xsd", "xsl", "xslt",
                    "plist", "csv", "tsv", "graphql", "gql" -> Type.DATA;
            case "sql", "ddl", "dml", "db", "sqlite", "sqlite3" -> Type.DATABASE;
            case "pdf" -> Type.PDF;
            case "doc", "docx", "odt", "rtf", "pages" -> Type.DOCUMENT;
            case "xls", "xlsx", "ods", "numbers" -> Type.SPREADSHEET;
            case "ppt", "pptx", "odp" -> Type.PRESENTATION;
            case "zip", "7z", "rar", "tar", "gz", "bz2", "xz", "tgz", "tbz2" -> Type.ARCHIVE;
            case "png", "jpg", "jpeg", "gif", "webp", "svg", "ico", "bmp", "tif", "tiff", "avif" -> Type.IMAGE;
            case "mp3", "wav", "flac", "aac", "ogg", "m4a" -> Type.AUDIO;
            case "mp4", "mkv", "mov", "avi", "webm", "m4v" -> Type.VIDEO;
            case "pem", "crt", "cer", "key", "pub", "p12", "pfx", "jks" -> Type.CERTIFICATE;
            case "bin", "exe", "dll", "so", "dylib", "class" -> Type.EXECUTABLE;
            case "ttf", "otf", "woff", "woff2" -> Type.FONT;
            case "deb", "rpm", "dmg", "pkg", "appimage", "jar", "war", "ear", "apk" -> Type.PACKAGE;
            default -> Type.FILE;
        };
    }
}
