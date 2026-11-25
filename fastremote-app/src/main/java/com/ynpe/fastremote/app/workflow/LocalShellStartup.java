// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import com.ynpe.fastremote.terminal.ShellIntegration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/** Private, per-process startup files; the user's dotfiles are sourced, never edited or copied. */
final class LocalShellStartup implements AutoCloseable {
    private final Path directory;
    private final String nonce = UUID.randomUUID().toString().replace("-", "");
    private final String[] command;

    static LocalShellStartup prepare(String[] original, Map<String, String> environment) throws IOException {
        String shell = Path.of(original[0]).getFileName().toString();
        if (!shell.equals("zsh") && !shell.equals("bash")) return null;
        return new LocalShellStartup(original[0], shell, environment);
    }

    private LocalShellStartup(String executable, String shell, Map<String, String> environment) throws IOException {
        directory = Files.createTempDirectory("fastremote-shell-");
        try {
            Path hooks = directory.resolve("integration.sh");
            Files.writeString(hooks, ShellIntegration.startupScript(nonce));
            if (shell.equals("zsh")) {
                String restore = "if [ \"$_fr_zdotdir_set\" = 1 ]; then export ZDOTDIR=\"$_fr_zdotdir\"; else unset ZDOTDIR; fi\n";
                String capture = "_fr_zdotdir=${ZDOTDIR-$HOME}; _fr_zdotdir_set=${+ZDOTDIR}\nexport ZDOTDIR=" + quote(directory.toString()) + "\n";
                String initial = environment.containsKey("ZDOTDIR")
                        ? "export ZDOTDIR=" + quote(environment.get("ZDOTDIR")) + "\n" : "unset ZDOTDIR\n";
                Files.writeString(directory.resolve(".zshenv"), initial + sourceZsh(".zshenv") + capture);
                for (String file : new String[]{".zprofile", ".zshrc"}) {
                    Files.writeString(directory.resolve(file), restore + sourceZsh(file) + capture);
                }
                Files.writeString(directory.resolve(".zlogin"), restore + sourceZsh(".zlogin")
                        + ". " + quote(hooks.toString()) + "\nunset _fr_zdotdir _fr_zdotdir_set\n");
                environment.put("ZDOTDIR", directory.toString());
                command = new String[]{executable, "-l", "-i"};
            } else {
                // Bash ignores --rcfile for login shells. Source the login profile chain from an
                // interactive rcfile instead, retaining its normal first-readable-file rule.
                Path rc = directory.resolve("bashrc");
                Files.writeString(rc, "[ ! -r /etc/profile ] || . /etc/profile\n"
                        + "for _fr_profile in \"$HOME/.bash_profile\" \"$HOME/.bash_login\" \"$HOME/.profile\"; do\n"
                        + " if [ -r \"$_fr_profile\" ]; then . \"$_fr_profile\"; break; fi\ndone\nunset _fr_profile\n"
                        + ". " + quote(hooks.toString()) + "\n");
                command = new String[]{executable, "--noprofile", "--rcfile", rc.toString(), "-i"};
            }
        } catch (IOException | RuntimeException failure) {
            close(); throw failure;
        }
    }

    private static String sourceZsh(String file) {
        return "if [ -r \"${ZDOTDIR-$HOME}/" + file + "\" ]; then . \"${ZDOTDIR-$HOME}/" + file + "\"; fi\n";
    }
    private static String quote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }
    String nonce() { return nonce; }
    String[] command() { return command.clone(); }
    Path directory() { return directory; }

    @Override public void close() {
        // Only these files belong to us. Never recursively delete a directory modified by a shell.
        for (String file : new String[]{"integration.sh", ".zshenv", ".zprofile", ".zshrc", ".zlogin", "bashrc"}) {
            try { Files.deleteIfExists(directory.resolve(file)); } catch (IOException ignored) { }
        }
        try { Files.deleteIfExists(directory); } catch (IOException ignored) { }
    }
}
