// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AiSettingsTest {
    @Test
    void rejectsCredentialsInGeminiUrls() {
        assertThrows(IllegalArgumentException.class, () -> new ModelProviderConfig("gemini", "Gemini", ModelProviderType.GEMINI_API,
                java.net.URI.create("https://generativelanguage.googleapis.com/v1beta?key=fixture"), "", java.util.Optional.empty(), false));
    }

    @Test
    void normalizesRemoteRootsIndependentlyOfTheLocalOperatingSystem() {
        assertEquals("/", AiSettings.normalizeRemoteRoot(" / "));
        assertEquals("/srv/releases", AiSettings.normalizeRemoteRoot("/srv/releases///"));
        assertEquals("/srv/releases", AiSettings.normalizeRemoteRoot("/srv/releases\\\\"));
        assertEquals("/srv/releases", AiSettings.normalizeRemoteRoot("/srv/./build/../releases/"));
        assertEquals("/C:/Users/example", AiSettings.normalizeRemoteRoot("/C:\\Users\\example\\"));
        assertEquals("D:/FastRemote", AiSettings.normalizeRemoteRoot("D:\\FastRemote\\"));
        assertEquals("D:/FastRemote", AiSettings.normalizeRemoteRoot("d:/FastRemote/"));
        assertEquals("D:/", AiSettings.normalizeRemoteRoot("D:\\"));
    }

    @Test
    void rejectsRelativeRemoteRoots() {
        assertThrows(IllegalArgumentException.class,
                () -> AiSettings.normalizeRemoteRoot("srv/releases/"));
        assertThrows(IllegalArgumentException.class,
                () -> AiSettings.normalizeRemoteRoot("D:FastRemote"));
    }
}
