// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiChatViewTest {
    @Test void externalLinksOnlyAllowAbsoluteHttpWithoutCredentials() {
        assertEquals("https://example.com/path", AiChatView.validatedExternalUri("https://example.com/path").toString());
        assertEquals("http://localhost:11434/", AiChatView.validatedExternalUri("http://localhost:11434/").toString());
        for (String url : List.of("javascript:alert(1)", "file:///etc/passwd", "/relative", "https://user:password@example.com")) assertNull(AiChatView.validatedExternalUri(url));
    }
    @Test void boundsUntrustedMessageAndAttachmentInput() {
        assertThrows(IllegalArgumentException.class, () -> new AiChatView.Message("m", "ASSISTANT", "AI", "x".repeat(512 * 1024 + 1), true));
        assertThrows(IllegalArgumentException.class, () -> new AiChatView.ImageAttachment("i", new byte[4 * 1024 * 1024 + 1], "image"));
        byte[] input = {1, 2}; var image = new AiChatView.ImageAttachment("i", input, "alt"); input[0] = 9;
        assertEquals(1, image.thumbnail()[0]); image.thumbnail()[0] = 8; assertEquals(1, image.thumbnail()[0]);
    }
}
