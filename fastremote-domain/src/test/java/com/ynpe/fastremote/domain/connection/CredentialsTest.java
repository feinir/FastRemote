// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CredentialsTest {
    @Test
    void protectsAndClearsPasswordBuffer() {
        char[] source = "secret".toCharArray();
        PasswordCredentials credentials = new PasswordCredentials(source);
        Arrays.fill(source, 'x');

        char[] copy = credentials.copySecret();
        assertEquals("secret", new String(copy));
        Arrays.fill(copy, '\0');

        credentials.close();
        assertThrows(IllegalStateException.class, credentials::copySecret);
        assertFalse(credentials.toString().contains("secret"));
    }

    @Test
    void protectsAndClearsEmbeddedPrivateKeyMaterial() {
        byte[] source = "private-key".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        PrivateKeyCredentials credentials = new PrivateKeyCredentials(new char[0], source);
        Arrays.fill(source, (byte) 0);

        assertEquals("private-key", new String(credentials.copyPrivateKey(), java.nio.charset.StandardCharsets.UTF_8));
        credentials.close();
        assertThrows(IllegalStateException.class, credentials::copyPrivateKey);
    }
}
