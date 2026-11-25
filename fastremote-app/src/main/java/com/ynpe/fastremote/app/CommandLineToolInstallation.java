// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinReg;
import com.sun.jna.platform.win32.WinUser;
import com.ynpe.fastremote.i18n.Texts;
import com.ynpe.fastremote.storage.FastRemotePaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Creates per-user launchers without modifying the application bundle or requiring elevation. */
public final class CommandLineToolInstallation {
    // https://learn.microsoft.com/en-us/windows/win32/winmsg/wm-settingchange
    private static final int WM_SETTINGCHANGE = 0x001A;
    private static final String MARKER = "FastRemote managed fr command";
    private static final String PATH_MARKER = "# FastRemote command-line tools";

    private CommandLineToolInstallation() { }

    public static boolean available() {
        return ApplicationEdition.current() != ApplicationEdition.STORE;
    }

    public static synchronized Path prepare(Map<String, String> environment) throws IOException {
        Path bin = FastRemotePaths.applicationDataDirectory().resolve("cli/bin");
        writeLauncher(bin, CommandLineTool.cliCommand(), windows());
        prependPath(environment, bin, windows());
        return bin;
    }

    public static synchronized Path install() throws IOException {
        Path home = Path.of(System.getProperty("user.home"));
        boolean windows = windows();
        Path bin = windows ? Path.of(System.getenv().getOrDefault("LOCALAPPDATA",
                home.resolve("AppData/Local").toString()), "FastRemote", "bin") : home.resolve(".local/bin");
        Path launcher = writeLauncher(bin, CommandLineTool.cliCommand(), windows);
        if (windows) {
            installWindowsPath(bin);
        } else {
            installPosixPath(home, bin, System.getenv("ZDOTDIR"));
        }
        return launcher;
    }

    static Path writeLauncher(Path bin, List<String> command, boolean windows) throws IOException {
        Files.createDirectories(bin);
        Path file = bin.resolve(windows ? "fr.cmd" : "fr");
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)
                && (Files.isSymbolicLink(file) || !readSmall(file).contains(MARKER))) {
            throw new IOException(Texts.text("cli.install.conflict", file));
        }
        String script = script(command, windows);
        if (!Files.exists(file) || !Files.readString(file).equals(script)) writeAtomic(file, script, true);
        return file;
    }

    static String script(List<String> command, boolean windows) {
        if (windows) {
            String line = command.stream().map(CommandLineToolInstallation::cmdQuote)
                    .collect(java.util.stream.Collectors.joining(" "));
            return "@echo off\r\nrem " + MARKER + "\r\nsetlocal DisableDelayedExpansion\r\n"
                    + "for /f \"tokens=2 delims=:\" %%G in ('chcp') do set \"_fr_cp=%%G\"\r\n"
                    + "chcp 65001 >nul\r\n" + line + " %*\r\nset \"_fr_exit=%errorlevel%\"\r\n"
                    + "chcp %_fr_cp% >nul\r\nexit /b %_fr_exit%\r\n";
        }
        return "#!/bin/sh\n# " + MARKER + "\nexec " + command.stream()
                .map(CommandLineToolInstallation::shellQuote).collect(java.util.stream.Collectors.joining(" "))
                + " \"$@\"\n";
    }

    public static void configureShell(Map<String, String> environment, String[] command, Path bin) {
        if (command.length == 0) return;
        String name = Path.of(command[0]).getFileName().toString();
        if ("bash".equals(name)) {
            // Some Linux /etc/profile files replace PATH entirely. An exported function keeps
            // fr available in this PTY without writing to or replacing the user's startup files.
            environment.put("BASH_FUNC_fr%%", "() { " + shellQuote(bin.resolve("fr").toString()) + " \"$@\";\n}");
        }
    }

    static void prependPath(Map<String, String> environment, Path bin, boolean windows) {
        String key = windows ? environment.keySet().stream().filter(name -> name.equalsIgnoreCase("PATH"))
                .findFirst().orElse("Path") : "PATH";
        String previous = environment.getOrDefault(key, "");
        environment.put(key, bin + (previous.isEmpty() ? "" : (windows ? ";" : ":") + previous));
    }

    static void installPosixPath(Path home, Path bin, String zdotdir) throws IOException {
        String path = shellQuote(bin.toString());
        String block = PATH_MARKER + "\ncase \":$PATH:\" in\n  *:" + path
                + ":*) ;;\n  *) export PATH=" + path + ":\"$PATH\" ;;\nesac\n";
        Path zsh = zdotdir == null || zdotdir.isBlank() ? home : Path.of(zdotdir);
        appendPathBlock(zsh.resolve(".zshrc"), block);
        appendPathBlock(home.resolve(".bashrc"), block);
        // Do not create .bash_profile: that would mask an existing .profile.
        Path login = Files.exists(home.resolve(".bash_profile")) ? home.resolve(".bash_profile")
                : Files.exists(home.resolve(".bash_login")) ? home.resolve(".bash_login") : home.resolve(".profile");
        appendPathBlock(login, block);
        String fishBlock = PATH_MARKER + "\nfish_add_path --path --prepend " + shellQuote(bin.toString()) + "\n";
        appendPathBlock(home.resolve(".config/fish/conf.d/fastremote-cli.fish"), fishBlock);
    }

    private static void appendPathBlock(Path file, String block) throws IOException {
        if (Files.isSymbolicLink(file)) throw new IOException(Texts.text("cli.install.conflict", file));
        String existing = Files.exists(file) ? readSmall(file) : "";
        if (existing.contains(PATH_MARKER)) return;
        Files.createDirectories(file.getParent());
        writeAtomic(file, existing + (existing.isEmpty() || existing.endsWith("\n") ? "" : "\n")
                + "\n" + block, false);
    }

    private static void installWindowsPath(Path bin) throws IOException {
        try {
            String previous = Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, "Environment", "Path")
                    ? Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, "Environment", "Path") : "";
            if (java.util.Arrays.stream(previous.split(";")).noneMatch(value -> value.equalsIgnoreCase(bin.toString()))) {
                Advapi32Util.registrySetExpandableStringValue(WinReg.HKEY_CURRENT_USER, "Environment", "Path",
                        bin + (previous.isBlank() ? "" : ";" + previous));
            }
            try (Memory value = new Memory(24)) {
                value.setWideString(0, "Environment");
                User32.INSTANCE.SendMessageTimeout(WinUser.HWND_BROADCAST, WM_SETTINGCHANGE,
                        new WinDef.WPARAM(0), new WinDef.LPARAM(Pointer.nativeValue(value)),
                        WinUser.SMTO_ABORTIFHUNG, 2000, new WinDef.DWORDByReference());
            }
        } catch (com.sun.jna.platform.win32.Win32Exception failure) {
            throw new IOException(Texts.text("cli.install.pathFailed"), failure);
        }
    }

    private static String readSmall(Path file) throws IOException {
        if (Files.size(file) > 1024 * 1024) throw new IOException(Texts.text("cli.install.conflict", file));
        return Files.readString(file);
    }

    private static void writeAtomic(Path file, String contents, boolean executable) throws IOException {
        Path temporary = Files.createTempFile(file.getParent(), ".fastremote-cli-", ".tmp");
        try {
            Files.writeString(temporary, contents, StandardCharsets.UTF_8);
            if (Files.getFileStore(temporary).supportsFileAttributeView("posix")) {
                var permissions = Files.exists(file) ? Files.getPosixFilePermissions(file)
                        : PosixFilePermissions.fromString(executable ? "rwx------" : "rw-------");
                if (executable) permissions.add(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE);
                Files.setPosixFilePermissions(temporary, permissions);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static String shellQuote(String value) { return "'" + value.replace("'", "'\"'\"'") + "'"; }

    private static String cmdQuote(String value) {
        if (value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Invalid command-line launcher argument.");
        }
        return "\"" + value.replace("%", "%%") + "\"";
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
