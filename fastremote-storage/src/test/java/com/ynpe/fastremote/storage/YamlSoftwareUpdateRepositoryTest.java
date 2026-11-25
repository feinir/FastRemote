// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlSoftwareUpdateRepositoryTest {
    @TempDir Path temporary;

    @Test void defaultsToEnabledAndPersistsIndependentState() throws Exception {
        var repository = new YamlSoftwareUpdateRepository(
                new YamlGlobalConfigStore(temporary.resolve("global-config.yaml")));
        assertTrue(repository.load().automaticChecksEnabled());
        Instant checked = Instant.parse("2026-08-31T01:02:03Z");
        repository.recordSuccessfulCheck(checked);
        repository.skipVersion("1.2.3");
        repository.setAutomaticChecksEnabled(false);
        var loaded = repository.load();
        assertFalse(loaded.automaticChecksEnabled());
        assertEquals(checked, loaded.lastSuccessfulCheckAt().orElseThrow());
        assertEquals("1.2.3", loaded.skippedVersion());
    }
}
