// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.ynpe.fastremote.domain.license.LicenseSnapshot;
import com.ynpe.fastremote.domain.license.LicenseStatus;
import com.ynpe.fastremote.domain.update.SoftwareRelease;
import com.ynpe.fastremote.domain.update.SoftwareUpdateSettings;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SoftwareUpdateClientTest {

    @Test void queriesStableTargetWithoutDownloadingPackage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> query = new AtomicReference<>();
        var packageRequests = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/package.msi", exchange -> {
            packageRequests.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.createContext("/api/client/v1/versions/latest", exchange -> {
            query.set(exchange.getRequestURI().getQuery());
            byte[] body = ("{\"ok\":true,\"data\":{"
                    + "\"version\":\"1.2.3\",\"channel\":\"stable\","
                    + "\"platform\":\"linux\",\"architecture\":\"arm64\","
                    + "\"download_url\":\"http://127.0.0.1:" + server.getAddress().getPort() + "/package.msi\","
                    + "\"sha256\":\"" + "a".repeat(64) + "\",\"file_size\":42,"
                    + "\"release_notes\":\"Fixed first thing\\nFixed second thing\","
                    + "\"published_at\":\"2026-08-31T00:00:00Z\"}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var client = new SoftwareUpdateClient(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    HttpClient.newHttpClient(), new SoftwareUpdateClient.Target("linux", "arm64", ".tar.gz"));
            SoftwareRelease release = client.latestStable();
            assertEquals("http://127.0.0.1:" + server.getAddress().getPort() + "/package.msi", release.downloadUri().toString());
            assertEquals("Fixed first thing\nFixed second thing", release.releaseNotes());
            assertTrue(query.get().contains("platform=linux"));
            assertTrue(query.get().contains("architecture=arm64"));
            assertTrue(query.get().contains("channel=stable"));
            assertEquals(0, packageRequests.get());
            assertEquals("https://www.ynpe.com/download/", SoftwareUpdateCoordinator.downloadPage().toString());
        } finally {
            server.stop(0);
        }
    }

    @Test void treatsMissingPublishedReleaseAsNoAvailableUpdate() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/client/v1/versions/latest", exchange -> {
            byte[] body = "{\"ok\":false,\"error\":{\"code\":\"VERSION_NOT_FOUND\"}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var client = new SoftwareUpdateClient(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    HttpClient.newHttpClient(), new SoftwareUpdateClient.Target("macos", "arm64", ".dmg"));
            assertNull(client.latestStable());
        } finally {
            server.stop(0);
        }
    }

    @Test void offlineLicenseSnapshotsBlockVersionTraffic() {
        LicenseSnapshot offline = new LicenseSnapshot(LicenseStatus.LICENSED_OFFLINE, "offline",
                Optional.empty(), Optional.empty(), Optional.empty(), Set.of(), "lease");
        LicenseSnapshot unactivated = new LicenseSnapshot(LicenseStatus.NEEDS_ACTIVATION, "",
                Optional.empty(), Optional.empty(), Optional.empty(), Set.of(), "");
        assertTrue(SoftwareUpdateCoordinator.offline(offline));
        assertFalse(SoftwareUpdateCoordinator.offline(unactivated));
    }

    @Test void successfulChecksThrottleAcrossRestartsForFortyEightHours() {
        Instant now = Instant.parse("2026-08-31T12:00:00Z");
        assertEquals(java.time.Duration.ZERO, SoftwareUpdateCoordinator.nextAutomaticCheckDelay(
                SoftwareUpdateSettings.defaults(), now));
        assertEquals(java.time.Duration.ofHours(36), SoftwareUpdateCoordinator.nextAutomaticCheckDelay(
                new SoftwareUpdateSettings(true, Optional.of(now.minus(java.time.Duration.ofHours(12))), ""), now));
        assertEquals(java.time.Duration.ZERO, SoftwareUpdateCoordinator.nextAutomaticCheckDelay(
                new SoftwareUpdateSettings(true, Optional.of(now.minus(java.time.Duration.ofHours(48))), ""), now));
        assertEquals(java.time.Duration.ofHours(48), SoftwareUpdateCoordinator.nextAutomaticCheckDelay(
                new SoftwareUpdateSettings(true, Optional.of(now.plusSeconds(60)), ""), now));
    }

    @Test void routineAutomaticNetworkFailuresUseConciseLogging() {
        assertEquals("ConnectException", SoftwareUpdateCoordinator.routineNetworkFailureName(
                new java.util.concurrent.CompletionException(new java.net.ConnectException())));
        assertEquals("UnknownHostException", SoftwareUpdateCoordinator.routineNetworkFailureName(
                new java.io.IOException("request failed", new java.net.UnknownHostException())));
        assertEquals("HttpTimeoutException", SoftwareUpdateCoordinator.routineNetworkFailureName(
                new java.net.http.HttpTimeoutException("timed out")));
        assertEquals("SocketTimeoutException", SoftwareUpdateCoordinator.routineNetworkFailureName(
                new java.net.SocketTimeoutException("timed out")));
        assertNull(SoftwareUpdateCoordinator.routineNetworkFailureName(
                new java.io.IOException("Version service returned invalid JSON")));
    }
}
