// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.crypto.generators.BCrypt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecureCrtXmlImporterTest {
    @TempDir Path directory;

    @Test
    void importsDependentHostFirewallAsJumpRoute() throws Exception {
        Path xml = directory.resolve("dependent-hosts.xml");
        Files.writeString(xml, """
                <?xml version="1.0" encoding="UTF-8"?>
                <VanDyke version="3.0">
                  <key name="Sessions">
                    <key name="Project">
                      <key name="Bastion">
                        <dword name="Is Session">00000001</dword>
                        <string name="Protocol Name">SSH2</string>
                        <string name="Hostname">edge.example.com</string>
                        <string name="Username">jump</string>
                      </key>
                      <key name="Internal">
                        <dword name="Is Session">00000001</dword>
                        <string name="Protocol Name">SSH2</string>
                        <string name="Hostname">10.0.0.15</string>
                        <string name="Username">app</string>
                        <string name="Firewall Name">Session:Project/Bastion</string>
                      </key>
                    </key>
                  </key>
                </VanDyke>
                """);

        try (var bundle = new SecureCrtXmlImporter().read(xml, new char[0], "（主机专属）")) {
            var internal = bundle.hosts().stream()
                    .filter(host -> host.name().equals("Internal")).findFirst().orElseThrow();
            assertEquals("Project/Bastion", internal.jumpSourceKey().orElseThrow());
        }
    }

    @Test
    void decryptsLegacy02PasswordEnvelope() throws Exception {
        char[] sourcePassphrase = chars(108, 101, 103, 97, 99, 121, 45, 115, 111, 117, 114, 99, 101);
        char[] expectedSecret = chars(108, 101, 103, 97, 99, 121, 45, 115, 101, 99, 114, 101, 116);
        String encrypted = encrypt02(expectedSecret, sourcePassphrase);
        char[] actual = SecureCrtXmlImporter.decryptPassword(encrypted, sourcePassphrase);
        try {
            org.junit.jupiter.api.Assertions.assertArrayEquals(expectedSecret, actual);
        } finally {
            Arrays.fill(actual, '\0');
            Arrays.fill(expectedSecret, '\0');
            Arrays.fill(sourcePassphrase, '\0');
        }
    }

    @Test
    void readsOnlySshHostsCredentialsAndReadableKeyReferences() throws Exception {
        Path key = directory.resolve("id_test");
        Path publicKey = directory.resolve("id_test.pub");
        Files.write(key, new byte[] {11, 22, 33, 44, 55});
        Files.write(publicKey, new byte[] {66, 77, 88});
        char[] sourcePassphrase = chars(115, 111, 117, 114, 99, 101, 45, 112, 97, 115, 115);
        char[] expectedSecret = chars(115, 101, 115, 115, 105, 111, 110, 45, 115, 101, 99, 114, 101, 116);
        String password = encrypt03(expectedSecret, sourcePassphrase);
        Path xml = directory.resolve("hosts.xml");
        Files.writeString(xml, """
                <?xml version="1.0" encoding="UTF-8"?>
                <VanDyke version="3.0">
                  <key name="Credentials">
                    <key name="Shared Key">
                      <string name="Username">deploy</string>
                      <dword name="Session Password Saved">00000000</dword>
                      <string name="Identity Filename V2">%s</string>
                      <array name="SSH2 Authentications V2"><string>publickey</string></array>
                    </key>
                  </key>
                  <key name="Sessions">
                    <key name="Project">
                      <key name="Host A">
                        <dword name="Is Session">00000001</dword>
                        <string name="Protocol Name">SSH2</string>
                        <string name="Hostname">host.example</string>
                        <string name="Username">ops</string>
                        <dword name="[SSH2] Port">000008AE</dword>
                        <dword name="Session Password Saved">00000001</dword>
                        <string name="Password V2">%s</string>
                        <array name="SSH2 Authentications V2"><string>password</string></array>
                        <string name="Color Scheme">ignored</string>
                      </key>
                      <key name="Local">
                        <dword name="Is Session">00000001</dword>
                        <string name="Protocol Name">Local Shell</string>
                      </key>
                    </key>
                  </key>
                </VanDyke>
                """.formatted(xml(publicKey), password));

        SecureCrtXmlImporter importer = new SecureCrtXmlImporter();
        var info = importer.inspect(xml);
        assertEquals(1, info.hosts());
        assertEquals(1, info.groups());
        assertEquals(1, info.sharedCredentials());
        assertEquals(1, info.encryptedPasswords());
        assertEquals(1, info.readableCertificates());
        assertEquals(1, info.skippedHosts());

        importer.verifyPassphrase(xml, sourcePassphrase);
        char[] wrongPassphrase = chars(119, 114, 111, 110, 103);
        try {
            assertThrows(IOException.class, () -> importer.verifyPassphrase(xml, wrongPassphrase));
        } finally {
            Arrays.fill(wrongPassphrase, '\0');
        }
        try (var bundle = importer.read(xml, sourcePassphrase, "（主机专属）")) {
            assertEquals(1, bundle.hosts().size());
            assertEquals(2, bundle.credentials().size());
            assertEquals(2222, bundle.hosts().getFirst().port());
            var bound = bundle.credentials().stream().filter(value -> !value.reusable()).findFirst().orElseThrow();
            char[] clear = bound.copySecret();
            try {
                org.junit.jupiter.api.Assertions.assertArrayEquals(expectedSecret, clear);
            } finally {
                Arrays.fill(clear, '\0');
            }
            assertFalse(bundle.groups().getFirst().name().contains("ignored"));
            var sharedKey = bundle.credentials().stream().filter(value -> value.reusable()).findFirst().orElseThrow();
            assertEquals(key, sharedKey.privateKeySource().orElseThrow());
        } finally {
            Arrays.fill(sourcePassphrase, '\0');
            Arrays.fill(expectedSecret, '\0');
        }
    }

    @Test
    void sharedCredentialUsernameOverridesStaleHostUsername() throws Exception {
        Path key = directory.resolve("shared-key");
        Files.write(key, new byte[] {11, 22, 33, 44});
        Path xml = directory.resolve("shared-credential.xml");
        Files.writeString(xml, """
                <?xml version="1.0" encoding="UTF-8"?>
                <VanDyke version="3.0">
                  <key name="Credentials">
                    <key name="VPS Key">
                      <string name="Username">root</string>
                      <string name="Identity Filename V2">%s</string>
                      <array name="SSH2 Authentications V2"><string>publickey</string></array>
                    </key>
                  </key>
                  <key name="Sessions">
                    <key name="VPS">
                      <key name="Server2">
                        <dword name="Is Session">00000001</dword>
                        <string name="Protocol Name">SSH2</string>
                        <string name="Hostname">server2.example</string>
                        <string name="Username">admin</string>
                        <string name="Credential Title">VPS Key</string>
                      </key>
                    </key>
                  </key>
                </VanDyke>
                """.formatted(xml(key)));

        try (var bundle = new SecureCrtXmlImporter().read(xml, new char[0], "（主机专属）")) {
            var host = bundle.hosts().getFirst();
            assertEquals("root", host.username());
            assertEquals("shared:VPS Key", host.credentialKey().orElseThrow());
        }
    }

    @Test
    void rejectsDoctypeAndExternalEntities() throws Exception {
        Path secret = directory.resolve("external.txt");
        Files.writeString(secret, "must-not-be-read");
        Path xml = directory.resolve("xxe.xml");
        Files.writeString(xml, """
                <?xml version="1.0"?>
                <!DOCTYPE VanDyke [<!ENTITY leak SYSTEM "%s">]>
                <VanDyke version="3.0"><key name="Sessions">&leak;</key></VanDyke>
                """.formatted(xml(secret)));

        IOException failure = assertThrows(IOException.class, () -> new SecureCrtXmlImporter().inspect(xml));
        assertTrue(failure.getMessage().contains("格式无效"));
    }

    private static String encrypt03(char[] secret, char[] passphrase) throws Exception {
        byte[] clear = envelope(secret);
        byte[] salt = HexFormat.of().parseHex("00112233445566778899aabbccddeeff");
        byte[] passphraseBytes = utf8(passphrase);
        byte[] derived = BCrypt.pbkdfGenerate(passphraseBytes, salt, 16, 48);
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(derived, 0, 32, "AES"),
                    new IvParameterSpec(derived, 32, 16));
            byte[] encrypted = cipher.doFinal(clear);
            byte[] result = ByteBuffer.allocate(salt.length + encrypted.length).put(salt).put(encrypted).array();
            return "03:" + HexFormat.of().formatHex(result);
        } finally {
            Arrays.fill(clear, (byte) 0);
            Arrays.fill(passphraseBytes, (byte) 0);
            Arrays.fill(derived, (byte) 0);
        }
    }

    private static String encrypt02(char[] secret, char[] passphrase) throws Exception {
        byte[] clear = envelope(secret);
        byte[] passphraseBytes = utf8(passphrase);
        byte[] key = MessageDigest.getInstance("SHA-256").digest(passphraseBytes);
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(new byte[16]));
            return "02:" + HexFormat.of().formatHex(cipher.doFinal(clear));
        } finally {
            Arrays.fill(clear, (byte) 0);
            Arrays.fill(passphraseBytes, (byte) 0);
            Arrays.fill(key, (byte) 0);
        }
    }

    private static byte[] envelope(char[] secret) throws Exception {
        byte[] secretBytes = utf8(secret);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(secretBytes);
        int rawLength = 4 + secretBytes.length + digest.length;
        int paddedLength = ((rawLength + 15) / 16) * 16;
        if (paddedLength - rawLength < 8) paddedLength += 16;
        try {
            return ByteBuffer.allocate(paddedLength).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(secretBytes.length).put(secretBytes).put(digest).array();
        } finally {
            Arrays.fill(secretBytes, (byte) 0);
            Arrays.fill(digest, (byte) 0);
        }
    }

    private static byte[] utf8(char[] value) {
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(java.nio.CharBuffer.wrap(value));
        byte[] result = new byte[encoded.remaining()];
        encoded.get(result);
        return result;
    }

    private static String xml(Path path) {
        return path.toString().replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    private static char[] chars(int... values) {
        char[] result = new char[values.length];
        for (int i = 0; i < values.length; i++) result[i] = (char) values[i];
        return result;
    }
}
