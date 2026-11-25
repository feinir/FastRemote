// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.stage.FileChooser;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;
import org.junit.jupiter.api.Test;

class DesktopPortalFileChooserTest {
    @Test
    void productionFileChoosersCanOnlyBeShownThroughThePlatformFacade() throws Exception {
        Path module = Path.of(System.getProperty("user.dir"));
        Path sourceRoot = module.resolve("src/main/java");
        if (!Files.isDirectory(sourceRoot)) sourceRoot = module.resolve("fastremote-ui/src/main/java");
        try (var sources = Files.walk(sourceRoot)) {
            List<Path> bypasses = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.endsWith("PlatformFileChooser.java"))
                    .filter(path -> {
                        try {
                            String source = Files.readString(path);
                            return java.util.regex.Pattern.compile(
                                    "(?<!PlatformFileChooser)\\.show(?:OpenDialog|OpenMultipleDialog|SaveDialog|Dialog)\\(")
                                    .matcher(source).find();
                        } catch (java.io.IOException unreadable) {
                            throw new java.io.UncheckedIOException(unreadable);
                        }
                    })
                    .toList();
            assertTrue(bypasses.isEmpty(), "file chooser bypasses: " + bypasses);
        }
    }

    @Test
    void facadeWaitsOffThreadWhileKeepingTheFxEventLoopResponsive() throws Exception {
        ensureJavaFxStarted();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicBoolean nestedPulseRan = new AtomicBoolean();
        AtomicReference<DesktopPortalFileChooser.Outcome> result = new AtomicReference<>();
        Platform.runLater(() -> {
            Platform.runLater(() -> nestedPulseRan.set(true));
            result.set(PlatformFileChooser.portalOutcome(
                    new DesktopPortalFileChooser.Request(
                            DesktopPortalFileChooser.Mode.OPEN, "Open", false, false,
                            null, null, List.of(), null),
                    true,
                    ignored -> {
                        try {
                            Thread.sleep(80L);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        }
                        return DesktopPortalFileChooser.Outcome.cancelled();
                    }));
            completed.countDown();
        });

        assertTrue(completed.await(5, TimeUnit.SECONDS));
        assertTrue(nestedPulseRan.get(), "FX events must continue while the portal is open");
        assertInstanceOf(DesktopPortalFileChooser.Outcome.Cancelled.class, result.get());
    }

    @Test
    void mapsJavaFxOpenChooserConfigurationToPortalRequest() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import colors");
        chooser.setInitialDirectory(Path.of(System.getProperty("java.io.tmpdir")).toFile());
        FileChooser.ExtensionFilter colors =
                new FileChooser.ExtensionFilter("Colors", "*.json", "*.itermcolors");
        FileChooser.ExtensionFilter all = new FileChooser.ExtensionFilter("All", "*.*");
        chooser.getExtensionFilters().setAll(colors, all);
        chooser.setSelectedExtensionFilter(all);

        DesktopPortalFileChooser.Request request = PlatformFileChooser.request(chooser, true, false);

        assertEquals(DesktopPortalFileChooser.Mode.OPEN, request.mode());
        assertEquals("Import colors", request.title());
        assertTrue(request.multiple());
        assertEquals(List.of("*.json", "*.itermcolors"), request.filters().getFirst().patterns());
        assertEquals(List.of("*"), request.filters().get(1).patterns());
        assertEquals(1, request.selectedFilterIndex());
    }

    @Test
    void serializesDirectoryAndFiltersWithPortalSignatures() {
        Path folder = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        DesktopPortalFileChooser.Request request = new DesktopPortalFileChooser.Request(
                DesktopPortalFileChooser.Mode.OPEN, "Choose folder", false, true, folder, null,
                List.of(new DesktopPortalFileChooser.Filter("Text", List.of("*.txt"))), 0);

        Map<String, Variant<?>> options = DesktopPortalFileChooser.options(request, "fastremote_test");

        assertEquals("fastremote_test", options.get("handle_token").getValue());
        assertEquals(Boolean.TRUE, options.get("directory").getValue());
        assertEquals("a(sa(us))", options.get("filters").getSig());
        assertEquals("(sa(us))", options.get("current_filter").getSig());
        byte[] expectedFolder = java.util.Arrays.copyOf(
                folder.toString().getBytes(StandardCharsets.UTF_8),
                folder.toString().getBytes(StandardCharsets.UTF_8).length + 1);
        assertArrayEquals(expectedFolder, (byte[]) options.get("current_folder").getValue());
    }

    @Test
    void mapsSuccessCancellationAndInvalidUrisWithoutConfusingFallback() {
        String fileUri = Path.of(System.getProperty("java.io.tmpdir"), "portal file.txt").toUri().toString();
        DesktopPortalFileChooser.Outcome selected = DesktopPortalFileChooser.outcome(
                response(0, new Variant<>(List.of(fileUri), "as")), false);
        DesktopPortalFileChooser.Outcome.Selected selection = assertInstanceOf(
                DesktopPortalFileChooser.Outcome.Selected.class, selected);
        assertEquals(Path.of(URI.create(fileUri)).toAbsolutePath().normalize(), selection.paths().getFirst());

        assertInstanceOf(DesktopPortalFileChooser.Outcome.Cancelled.class,
                DesktopPortalFileChooser.outcome(response(1, null), false));
        assertInstanceOf(DesktopPortalFileChooser.Outcome.Failed.class,
                DesktopPortalFileChooser.outcome(
                        response(0, new Variant<>(List.of("https://example.invalid/file"), "as")), false));
        assertInstanceOf(DesktopPortalFileChooser.Outcome.Failed.class,
                DesktopPortalFileChooser.outcome(
                        response(0, new Variant<>(List.of(fileUri, fileUri), "as")), false));
    }

    private static DesktopPortalFileChooser.PortalResponse response(
            long code, Variant<?> uris) {
        return new DesktopPortalFileChooser.PortalResponse(
                new UInt32(code), uris == null ? Map.of() : Map.of("uris", uris));
    }

    private static void ensureJavaFxStarted() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(5, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }
}
