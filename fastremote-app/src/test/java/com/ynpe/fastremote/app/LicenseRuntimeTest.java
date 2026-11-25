// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class LicenseRuntimeTest {
    @Test
    void packagedRuntimeUsesTheFixedProductServiceOrigin() {
        String previous = System.getProperty("fastremote.license.serverUrl");
        try {
            System.clearProperty("fastremote.license.serverUrl");
            assertEquals("https://license.ynpe.com", LicenseRuntime.serviceUri().toString());
        } finally {
            if (previous == null) System.clearProperty("fastremote.license.serverUrl");
            else System.setProperty("fastremote.license.serverUrl", previous);
        }
    }
}
