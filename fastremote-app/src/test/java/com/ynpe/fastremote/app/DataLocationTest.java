// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import static org.junit.jupiter.api.Assertions.*;

import com.ynpe.fastremote.storage.FastRemotePaths;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DataLocationTest {
    @TempDir Path temporary;

    @org.junit.jupiter.api.BeforeEach void canonicalTemporary() throws IOException {
        temporary = temporary.toRealPath();
    }

    @Test void macNeverOffersProgramStorageAndLinuxResolvesInstallationRoot() {
        assertNull(DataLocationService.programDirectory("Mac OS X", "/Applications/FastRemote.app/Contents/MacOS/FastRemote"));
        assertNull(DataLocationService.programDirectory("Linux", ""));
        assertEquals(temporary.resolve("FastRemote/config"),
                DataLocationService.programDirectory("Linux", temporary.resolve("FastRemote/bin/FastRemote").toString()));
        assertEquals(temporary.resolve("FastRemote/config"),
                DataLocationService.programDirectory("Windows 11", temporary.resolve("FastRemote/FastRemote.exe").toString()));
    }

    @Test void macRejectsApplicationBundleDestinationsButAcceptsCustomDirectory() throws Exception {
        assertThrows(IOException.class, () -> DataLocationService.validateDestination(temporary.resolve("FastRemote.app/config"), true));
        DataLocationService.validateDestination(temporary.resolve("custom"), true);
        Files.writeString(temporary.resolve("unrelated.txt"), "preserve");
        assertThrows(IOException.class, () -> DataLocationService.validateDestination(temporary, false));
    }

    @Test void independentPreferenceRoundTripsPendingAndRejectsCorruption() throws Exception {
        MemoryPreferences memory = new MemoryPreferences();
        DataLocationPreference store = new DataLocationPreference(memory, temporary);
        assertEquals(new DataLocationPreference.State(temporary, null), store.read());
        var pending = new DataLocationPreference.State(temporary, temporary.resolveSibling("other"));
        store.write(pending);
        assertEquals(pending, store.read());
        memory.put("location-v1", "2\n/tmp/data\n");
        assertThrows(IOException.class, store::read);
        memory.put("location-v1", "1\nrelative\n\n0\n0");
        assertThrows(IOException.class, store::read);
    }

    @Test void failedPreferenceWriteRestoresPreviousValue() throws Exception {
        MemoryPreferences memory = new MemoryPreferences();
        DataLocationPreference store = new DataLocationPreference(memory, temporary);
        var initial = new DataLocationPreference.State(temporary, null);
        store.write(initial);
        memory.failFlush = true;
        assertThrows(IOException.class, () -> store.write(new DataLocationPreference.State(temporary, temporary.resolve("next"))));
        assertEquals(initial, store.read());
    }

    @Test void copiesEveryDataFileAndRetainsSourceAndExistingDestination() throws Exception {
        Path source = Files.createDirectory(temporary.resolve("source"));
        Path target = Files.createDirectory(temporary.resolve("target"));
        Files.createDirectories(source.resolve("license"));
        Files.write(source.resolve("global-config.yaml"), new byte[] {0, 1, 2, -1});
        Files.writeString(source.resolve("fastremote.db-wal"), "offline WAL");
        Files.writeString(source.resolve("license/state"), "encrypted-license-state");
        Files.writeString(source.resolve("instance.properties"), "stale token");
        Files.writeString(target.resolve("previous"), "keep me");
        DataDirectoryMigration.copyAndInstall(source, target);
        assertEquals(-1, Files.mismatch(source.resolve("global-config.yaml"), target.resolve("global-config.yaml")));
        assertEquals("offline WAL", Files.readString(target.resolve("fastremote.db-wal")));
        assertEquals("encrypted-license-state", Files.readString(target.resolve("license/state")));
        assertTrue(Files.exists(source.resolve("global-config.yaml")));
        assertFalse(Files.exists(target.resolve("instance.properties")));
        assertFalse(Files.exists(target.resolve("previous")));
        try (var entries = Files.list(temporary)) {
            Path backup = entries.filter(p -> p.getFileName().toString().startsWith("target.backup-")).findFirst().orElseThrow();
            assertEquals("keep me", Files.readString(backup.resolve("previous")));
        }
    }

    @Test void rejectsOverlapAndLinksWithoutReplacingExistingData() throws Exception {
        Path source = Files.createDirectory(temporary.resolve("source"));
        Path target = Files.createDirectory(temporary.resolve("target"));
        Files.writeString(target.resolve("original"), "original");
        assertThrows(IOException.class, () -> DataDirectoryMigration.copyAndInstall(source, source.resolve("nested")));
        Files.createSymbolicLink(source.resolve("link"), target.resolve("original"));
        assertThrows(IOException.class, () -> DataDirectoryMigration.copyAndInstall(source, target));
        assertEquals("original", Files.readString(target.resolve("original")));
    }

    @Test void startupCommitsOnlyAfterCopyAndLocksNewDirectory() throws Exception {
        Path source = Files.createDirectory(temporary.resolve("source"));
        Path target = temporary.resolve("target");
        Files.writeString(source.resolve("global-config.yaml"), "ciphertext");
        DataLocationPreference store = new DataLocationPreference(new MemoryPreferences(), source);
        store.write(new DataLocationPreference.State(source, target));
        withPropertiesRestored(() -> {
            try (SingleInstanceBroker broker = DataLocationService.startManaged(LaunchRequest.parse(java.util.List.of()), store)) {
                assertNotNull(broker);
                assertEquals(target, FastRemotePaths.applicationDataDirectory());
                assertEquals(new DataLocationPreference.State(target, null), store.read());
                assertEquals("ciphertext", Files.readString(target.resolve("global-config.yaml")));
            }
        });
    }

    @Test void processStillDrainingAfterInstanceLockReleaseCannotBeMigrated() throws Exception {
        Path source = Files.createDirectory(temporary.resolve("source"));
        Path target = temporary.resolve("target");
        ProcessHandle process = ProcessHandle.current();
        var pending = new DataLocationPreference.State(source, target, process.pid(),
                process.info().startInstant().orElseThrow().toEpochMilli());
        DataLocationPreference store = new DataLocationPreference(new MemoryPreferences(), source);
        store.write(pending);
        assertThrows(IOException.class, () -> DataLocationService.startManaged(LaunchRequest.parse(java.util.List.of()), store));
        assertEquals(pending, store.read());
        assertFalse(Files.exists(target));
        assertFalse(DataLocationService.sourceProcessAlive(new DataLocationPreference.State(source, target, process.pid(), 1)));
    }

    @Test void failedCommitKeepsOriginalSelectionAndRetryRecovers() throws Exception {
        Path source = Files.createDirectory(temporary.resolve("source"));
        Path target = temporary.resolve("target");
        Files.writeString(source.resolve("global-config.yaml"), "ciphertext");
        Files.writeString(source.resolve("fastremote.db"), "database bytes");
        MemoryPreferences memory = new MemoryPreferences();
        DataLocationPreference store = new DataLocationPreference(memory, source);
        var pending = new DataLocationPreference.State(source, target);
        store.write(pending);
        memory.failFlush = true;
        assertThrows(IOException.class, () -> DataLocationService.startManaged(LaunchRequest.parse(java.util.List.of()), store));
        assertEquals(pending, store.read());
        assertEquals("ciphertext", Files.readString(source.resolve("global-config.yaml")));
        memory.failFlush = false;
        withPropertiesRestored(() -> {
            try (var broker = DataLocationService.startManaged(LaunchRequest.parse(java.util.List.of()), store)) {
                assertNotNull(broker);
                assertEquals(new DataLocationPreference.State(target, null), store.read());
            }
        });
    }

    @Test void migratedSqliteDatabaseAndEncryptedBlobRemainReadable() throws Exception {
        Path source = Files.createDirectory(temporary.resolve("source"));
        Path target = temporary.resolve("target");
        try (var db = java.sql.DriverManager.getConnection("jdbc:sqlite:" + source.resolve("fastremote.db"));
                var statement = db.createStatement()) {
            statement.execute("CREATE TABLE credential(id INTEGER PRIMARY KEY, ciphertext BLOB NOT NULL)");
            statement.execute("INSERT INTO credential VALUES (1, X'00FF1280')");
        }
        DataDirectoryMigration.copyAndInstall(source, target);
        try (var db = java.sql.DriverManager.getConnection("jdbc:sqlite:" + target.resolve("fastremote.db"));
                var statement = db.createStatement(); var result = statement.executeQuery("SELECT ciphertext FROM credential WHERE id = 1")) {
            assertTrue(result.next());
            assertArrayEquals(new byte[] {0, -1, 18, -128}, result.getBytes(1));
        }
        assertTrue(Files.exists(source.resolve("fastremote.db")));
    }

    @Test void busyDestinationLeavesSourceAndPreferenceUntouched() throws Exception {
        Path source = Files.createDirectory(temporary.resolve("source"));
        Path target = Files.createDirectory(temporary.resolve("target"));
        DataLocationPreference store = new DataLocationPreference(new MemoryPreferences(), source);
        var pending = new DataLocationPreference.State(source, target);
        store.write(pending);
        try (var channel = FileChannel.open(target.resolve("instance.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                var lock = channel.lock()) {
            assertThrows(IOException.class, () -> DataLocationService.startManaged(LaunchRequest.parse(java.util.List.of()), store));
            assertEquals(pending, store.read());
        }
    }

    @Test void liveSourceReceivesLaunchWithoutCopyingOrSwitching() throws Exception {
        Path source = Files.createDirectory(temporary.resolve("source"));
        Path target = temporary.resolve("target");
        DataLocationPreference store = new DataLocationPreference(new MemoryPreferences(), source);
        var pending = new DataLocationPreference.State(source, target);
        store.write(pending);
        var request = LaunchRequest.parse(java.util.List.of());
        try (var broker = SingleInstanceBroker.startOrForward(source, request)) {
            assertNotNull(broker);
            assertNull(DataLocationService.startManaged(request, store));
            assertEquals(pending, store.read());
            assertFalse(Files.exists(target));
        }
    }

    private void withPropertiesRestored(CheckedAction action) throws Exception {
        String[] keys = {FastRemotePaths.DATA_DIRECTORY_PROPERTY, "fastremote.dataDirectory.managed"};
        String[] previous = {System.getProperty(keys[0]), System.getProperty(keys[1])};
        try { action.run(); }
        finally {
            for (int i = 0; i < keys.length; i++) {
                if (previous[i] == null) System.clearProperty(keys[i]); else System.setProperty(keys[i], previous[i]);
            }
        }
    }
    @FunctionalInterface interface CheckedAction { void run() throws Exception; }
    static final class MemoryPreferences extends AbstractPreferences {
        private final Map<String, String> values = new HashMap<>();
        boolean failFlush;
        MemoryPreferences() { super(null, ""); }
        @Override protected void putSpi(String key, String value) { values.put(key, value); }
        @Override protected String getSpi(String key) { return values.get(key); }
        @Override protected void removeSpi(String key) { values.remove(key); }
        @Override protected void removeNodeSpi() { values.clear(); }
        @Override protected String[] keysSpi() { return values.keySet().toArray(String[]::new); }
        @Override protected String[] childrenNamesSpi() { return new String[0]; }
        @Override protected AbstractPreferences childSpi(String name) { throw new UnsupportedOperationException(); }
        @Override protected void syncSpi() { }
        @Override protected void flushSpi() throws BackingStoreException {
            if (failFlush) throw new BackingStoreException("simulated failure");
        }
    }
}
