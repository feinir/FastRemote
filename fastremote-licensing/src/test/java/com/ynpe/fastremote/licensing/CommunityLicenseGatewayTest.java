// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.licensing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ynpe.fastremote.domain.license.LicenseCapability;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class CommunityLicenseGatewayTest {
    @Test void grantsLocalCapabilitiesWithoutACommercialEntitlement() {
        try (var gateway = new CommunityLicenseGateway()) {
            var status = gateway.status().toCompletableFuture().join();
            assertFalse(status.status().permitsCommercialUse());
            assertFalse(status.activationRequired());
            assertTrue(status.lease().isEmpty());
            for (var capability : LicenseCapability.values()) {
                assertTrue(gateway.permits(capability).toCompletableFuture().join());
            }
            char[] input = {'t', 'e', 's', 't'};
            assertThrows(CompletionException.class, () -> gateway.activate(input).toCompletableFuture().join());
            assertArrayEquals(new char[4], input);
        }
    }
}
