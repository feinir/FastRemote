// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class AsyncResourceBootstrapTest {
    @Test
    void startsFactoryOffCallerThreadAndTransfersOwnershipOnce() throws Exception {
        Thread caller = Thread.currentThread();
        AtomicBoolean ranOffCaller = new AtomicBoolean();
        try (var bootstrap = AsyncResourceBootstrap.start("test-runtime-bootstrap", () -> {
            ranOffCaller.set(Thread.currentThread() != caller);
            return new TestResource();
        })) {
            TestResource resource = bootstrap.claim();
            assertTrue(ranOffCaller.get());
            assertFalse(resource.closed.get());
            assertThrows(IllegalStateException.class, bootstrap::claim);
            resource.close();
            assertTrue(resource.closed.get());
        }
    }

    @Test
    void closesResourceThatWasNeverClaimed() throws Exception {
        CountDownLatch created = new CountDownLatch(1);
        TestResource resource = new TestResource();
        var bootstrap = AsyncResourceBootstrap.start("test-runtime-bootstrap", () -> {
            created.countDown();
            return resource;
        });
        assertTrue(created.await(5, TimeUnit.SECONDS));
        bootstrap.close();
        for (int attempt = 0; attempt < 50 && !resource.closed.get(); attempt++) {
            Thread.sleep(10);
        }
        assertTrue(resource.closed.get());
        assertEquals(1, resource.closeCount);
    }

    private static final class TestResource implements AutoCloseable {
        private final AtomicBoolean closed = new AtomicBoolean();
        private int closeCount;

        @Override
        public void close() {
            closeCount++;
            closed.set(true);
        }
    }
}
