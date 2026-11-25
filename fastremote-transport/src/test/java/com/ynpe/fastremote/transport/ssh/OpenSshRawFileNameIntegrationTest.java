// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.transport.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.transfer.RemoteFileEntry;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.subsystem.SubsystemFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives the real OpenSSH sftp-server against names that are not valid UTF-8, as left by
 * unzipping GBK or Shift_JIS archives on a UTF-8 Linux host. Skipped where no sftp-server binary
 * exists or the file system rejects such names (macOS APFS, Windows).
 */
@Timeout(60)
class OpenSshRawFileNameIntegrationTest {
    private static final List<Path> SFTP_SERVERS = List.of(
            Path.of("/usr/libexec/openssh/sftp-server"),
            Path.of("/usr/lib/openssh/sftp-server"),
            Path.of("/usr/libexec/sftp-server"),
            Path.of("/usr/lib/ssh/sftp-server"));
    private static final byte[] GBK_NAME = "[表情].png".getBytes(Charset.forName("GBK"));
    private static final byte[] GBK_DELETE = "删除我.txt".getBytes(Charset.forName("GBK"));
    private static final byte[] SHIFT_JIS_FOLDER = "日本語フォルダ".getBytes(Charset.forName("Shift_JIS"));

    @TempDir Path directory;
    private SshServer server;
    private SshClient client;
    private ClientSession session;
    private MinaRemoteFileService files;
    private String root;

    @BeforeEach
    void connectFixture() throws Exception {
        Path sftpServer = SFTP_SERVERS.stream().filter(Files::isExecutable).findFirst().orElse(null);
        Assumptions.assumeTrue(sftpServer != null, "OpenSSH sftp-server is not installed");
        Path tree = directory.resolve("tree");
        Files.createDirectory(tree);
        root = tree.toRealPath().toString();
        Assumptions.assumeTrue(createRaw(GBK_NAME, false) && createRaw(GBK_DELETE, false)
                && createRaw(SHIFT_JIS_FOLDER, true), "File system rejects non-UTF-8 names");

        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        var keys = new SimpleGeneratorHostKeyProvider(directory.resolve("fixture-host-key"));
        server.setKeyPairProvider(keys);
        server.setPasswordAuthenticator((user, password, connection) -> password.equals("fixture"));
        server.setSubsystemFactories(List.of(new ProcessSubsystemFactory(sftpServer)));
        server.start();
        var publicKey = keys.loadKeys(null).iterator().next().getPublic();
        client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier((connection, address, key) -> key.equals(publicKey));
        MinaSshSessionConnector.configureClient(client,
                com.ynpe.fastremote.domain.settings.ConnectionSettings.defaults());
        client.start();
        session = client.connect("fixture", "127.0.0.1", server.getPort()).verify(5000).getSession();
        session.addPasswordIdentity("fixture");
        session.auth().verify(5000);
        files = new MinaRemoteFileService(session);
    }

    @AfterEach
    void closeFixture() throws Exception {
        if (session != null) session.close();
        if (client != null) client.stop();
        if (server != null) server.stop(true);
    }

    @Test
    void listedNonUtf8NamesCanBeRenamedDeletedAndEntered() throws Exception {
        List<RemoteFileEntry> listed = files.list(root);
        assertEquals(3, listed.size());
        listed.forEach(entry -> assertFalse(entry.name().contains("�"), entry.name()));

        RemoteFileEntry folder = entry(listed, SHIFT_JIS_FOLDER);
        assertTrue(folder.directory());
        assertEquals(List.of("inner.txt"), files.list(folder.path()).stream().map(RemoteFileEntry::name).toList());
        assertEquals(folder.path(), files.readAttributes(folder.path()).path());

        files.rename(entry(listed, GBK_NAME).path(), root + "/renamed.png");
        files.delete(entry(listed, GBK_DELETE).path());
        files.delete(folder.path());

        assertEquals(Set.of("renamed.png"), names(files.list(root)));
        assertEquals(Set.of("renamed.png"), rawNames());
    }

    private RemoteFileEntry entry(List<RemoteFileEntry> listed, byte[] raw) {
        String hex = HexFormat.of().formatHex(raw);
        return listed.stream()
                .filter(entry -> HexFormat.of().formatHex(entry.name().getBytes(SftpFileNameCharset.INSTANCE)).equals(hex))
                .findFirst().orElseThrow(() -> new AssertionError("Missing raw name " + hex));
    }

    private static Set<String> names(List<RemoteFileEntry> entries) {
        return entries.stream().map(RemoteFileEntry::name).collect(Collectors.toSet());
    }

    /** Lists the directory with ls so the check does not depend on JDK file name decoding. */
    private Set<String> rawNames() throws Exception {
        Process process = new ProcessBuilder("ls", "-A", root).redirectErrorStream(true).start();
        byte[] output = process.getInputStream().readAllBytes();
        assertEquals(0, process.waitFor());
        return Set.of(new String(output, SftpFileNameCharset.INSTANCE).strip().split("\n"));
    }

    /** Creates a child with raw name bytes through the shell, bypassing JDK path encoding. */
    private boolean createRaw(byte[] name, boolean folder) throws Exception {
        StringBuilder escaped = new StringBuilder();
        for (byte value : name) escaped.append(String.format("\\%03o", value & 0xFF));
        String script = "cd \"$1\" && name=$(printf '" + escaped + "') && "
                + (folder ? "mkdir \"$name\" && printf x > \"$name/inner.txt\"" : "printf x > \"$name\"")
                + " && ls -A | grep -qxF \"$name\"";
        Process process = new ProcessBuilder("sh", "-c", script, "sh", root).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        return process.waitFor() == 0;
    }

    /** Runs a local subsystem binary and connects its stdio to the channel. */
    private static final class ProcessSubsystemFactory implements SubsystemFactory {
        private final Path executable;

        private ProcessSubsystemFactory(Path executable) { this.executable = executable; }

        @Override public String getName() { return "sftp"; }

        @Override public Command createSubsystem(ChannelSession channel) {
            return new ProcessCommand(executable);
        }
    }

    private static final class ProcessCommand implements Command {
        private final Path executable;
        private InputStream input;
        private OutputStream output;
        private ExitCallback exit;
        private Process process;

        private ProcessCommand(Path executable) { this.executable = executable; }

        @Override public void setInputStream(InputStream in) { input = in; }
        @Override public void setOutputStream(OutputStream out) { output = out; }
        @Override public void setErrorStream(OutputStream err) { }
        @Override public void setExitCallback(ExitCallback callback) { exit = callback; }

        @Override public void start(ChannelSession channel, Environment env) throws IOException {
            process = new ProcessBuilder(executable.toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            Thread.ofVirtual().start(() -> pump(input, process.getOutputStream()));
            Thread.ofVirtual().start(() -> {
                pump(process.getInputStream(), output);
                try { exit.onExit(process.waitFor()); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            });
        }

        private static void pump(InputStream from, OutputStream to) {
            byte[] buffer = new byte[32 * 1024];
            try (to) {
                for (int read; (read = from.read(buffer)) >= 0; ) {
                    to.write(buffer, 0, read);
                    to.flush();
                }
            } catch (IOException ignored) {
                // The channel or process closed; the exit callback reports completion.
            }
        }

        @Override public void destroy(ChannelSession channel) {
            if (process != null) process.destroyForcibly();
        }
    }
}
