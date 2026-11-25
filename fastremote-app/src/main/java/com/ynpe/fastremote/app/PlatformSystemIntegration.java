// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import com.ynpe.fastremote.i18n.Localizer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Installs idempotent per-user shell integration when running from a native jpackage launcher. */
final class PlatformSystemIntegration {
    private PlatformSystemIntegration() { }

    static void installForPackagedApplication() {
        String appPathValue = System.getProperty("jpackage.app-path", "").strip();
        if (appPathValue.isEmpty()) return;
        Path appPath;
        try {
            appPath = Path.of(appPathValue).toAbsolutePath().normalize();
        } catch (RuntimeException invalid) {
            return;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win") && appPath.getFileName().toString().equalsIgnoreCase("FastRemote.exe")) {
                installWindowsContextMenus(appPath);
            } else if (os.contains("linux")) {
                installLinuxDesktopEntry(appPath);
            }
        } catch (IOException failure) {
            System.getLogger(PlatformSystemIntegration.class.getName()).log(
                    System.Logger.Level.WARNING, "Unable to install FastRemote shell integration", failure);
        }
    }

    static List<List<String>> windowsRegistryCommands(Path executable, String menuText) {
        String command = quoteWindows(executable.toString());
        String icon = executable + ",0";
        List<List<String>> commands = new ArrayList<>();
        addWindowsVerb(commands, "Directory\\shell\\FastRemote", menuText, icon,
                command + " \"%1\"");
        addWindowsVerb(commands, "Directory\\Background\\shell\\FastRemote", menuText, icon,
                command + " \"%V\"");
        addWindowsVerb(commands, "*\\shell\\FastRemote", menuText, icon,
                command + " \"%1\"");
        return List.copyOf(commands);
    }

    private static void addWindowsVerb(
            List<List<String>> commands, String relativeKey, String menuText, String icon, String command) {
        String key = "HKCU\\Software\\Classes\\" + relativeKey;
        commands.add(List.of("reg.exe", "add", key, "/ve", "/d", menuText, "/f"));
        commands.add(List.of("reg.exe", "add", key, "/v", "Icon", "/d", icon, "/f"));
        commands.add(List.of("reg.exe", "add", key + "\\command", "/ve", "/d", command, "/f"));
    }

    private static void installWindowsContextMenus(Path executable) throws IOException {
        String menuText = Localizer.systemDefault().text("action.openWithFastRemote");
        for (List<String> command : windowsRegistryCommands(executable, menuText)) {
            run(command);
        }
    }

    static String linuxDesktopEntry(Path executable) {
        return linuxDesktopEntry(executable, Localizer.systemDefault().text("action.openWithFastRemote"));
    }

    static String linuxDesktopEntry(Path executable, String menuText) {
        String escapedExecutable = executable.toString().replace("\\", "\\\\").replace("\"", "\\\"");
        return """
                [Desktop Entry]
                Type=Application
                Name=%s
                Comment=Open a local terminal in this directory
                Exec=\"%s\" %%F
                Terminal=false
                NoDisplay=true
                MimeType=inode/directory;
                Categories=Network;Utility;
                """.formatted(menuText, escapedExecutable);
    }

    static String linuxLauncherDesktopEntry(Path executable) {
        String escapedExecutable = executable.toString().replace("\\", "\\\\").replace("\"", "\\\"");
        Path applicationRoot = executable.getParent().getParent();
        String icon = applicationRoot.resolve("lib").resolve("FastRemote.png").toString();
        return """
                [Desktop Entry]
                Version=1.0
                Type=Application
                Name=FastRemote
                Comment=FastRemote SSH and SFTP client
                Exec="%s"
                TryExec=%s
                Icon=%s
                Terminal=false
                NoDisplay=false
                Categories=Network;Utility;
                StartupNotify=true
                StartupWMClass=com.ynpe.fastremote.app.FastRemoteApplication
                """.formatted(escapedExecutable, executable, icon);
    }

    private static void installLinuxDesktopEntry(Path executable) throws IOException {
        Path applications = Path.of(System.getProperty("user.home"), ".local", "share", "applications");
        Files.createDirectories(applications);
        writeDesktopEntry(applications, "fastremote.desktop", linuxLauncherDesktopEntry(executable));
        writeDesktopEntry(applications, "fastremote-open.desktop", linuxDesktopEntry(executable));
        Path updater = findOnPath("update-desktop-database");
        if (updater != null) run(List.of(updater.toString(), applications.toString()));
    }

    private static void writeDesktopEntry(Path applications, String fileName, String content) throws IOException {
        Path target = applications.resolve(fileName);
        Path temporary = Files.createTempFile(applications, "fastremote-open-", ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Path findOnPath(String executable) {
        String path = System.getenv("PATH");
        if (path == null) return null;
        for (String item : path.split(java.io.File.pathSeparator)) {
            Path candidate = Path.of(item, executable);
            if (Files.isExecutable(candidate)) return candidate;
        }
        return null;
    }

    private static void run(List<String> command) throws IOException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        try {
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Shell integration command timed out: " + command.getFirst());
            }
            if (process.exitValue() != 0) {
                throw new IOException("Shell integration command failed: " + command.getFirst());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Shell integration command was interrupted", interrupted);
        }
    }

    private static String quoteWindows(String value) {
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }
}
