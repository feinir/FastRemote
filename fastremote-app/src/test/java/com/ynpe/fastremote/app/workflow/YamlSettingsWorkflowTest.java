// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.settings.GlobalSettings;
import com.ynpe.fastremote.domain.settings.GlobalSettingsRepository;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Set;
import org.junit.jupiter.api.Test;

class YamlSettingsWorkflowTest {
    @Test
    void closeDrainsSettingsAlreadyQueuedForPersistence() throws Exception {
        var repository = new BlockingSettingsRepository();
        var workflow = new YamlSettingsWorkflow(repository);
        GlobalSettings changed = new GlobalSettings(false, true, true, 301.0, 276.0,
                com.ynpe.fastremote.domain.settings.DockSide.RIGHT,
                com.ynpe.fastremote.domain.settings.DockSide.LEFT,
                com.ynpe.fastremote.domain.settings.DockSide.LEFT,
                com.ynpe.fastremote.domain.settings.DockSide.RIGHT,
                Set.of("8abc7fbd-d58e-4425-a66d-5685f148369a"),
                Set.of(com.ynpe.fastremote.domain.settings.HostTabTitlePart.NAME), true);

        workflow.load();
        assertTrue(repository.loadStarted.await(2, TimeUnit.SECONDS));
        var save = workflow.save(changed).toCompletableFuture();
        workflow.close();
        repository.allowLoadToFinish.countDown();

        save.get(2, TimeUnit.SECONDS);
        assertEquals(changed, repository.saved.get());
    }

    private static final class BlockingSettingsRepository implements GlobalSettingsRepository {
        private final CountDownLatch loadStarted = new CountDownLatch(1);
        private final CountDownLatch allowLoadToFinish = new CountDownLatch(1);
        private final AtomicReference<GlobalSettings> saved = new AtomicReference<>();

        @Override
        public GlobalSettings load() throws IOException {
            loadStarted.countDown();
            try {
                if (!allowLoadToFinish.await(2, TimeUnit.SECONDS)) {
                    throw new IOException("Timed out waiting to finish test load");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Settings load was interrupted", interrupted);
            }
            return GlobalSettings.defaults();
        }

        @Override
        public void save(GlobalSettings settings) {
            saved.set(settings);
        }
    }
}
