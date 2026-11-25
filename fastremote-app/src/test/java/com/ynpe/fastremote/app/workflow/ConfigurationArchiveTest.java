// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import static org.junit.jupiter.api.Assertions.*;
import com.ynpe.fastremote.storage.*;
import com.ynpe.fastremote.ui.ConfigurationSelection;
import com.ynpe.fastremote.domain.catalog.*;
import com.ynpe.fastremote.domain.connection.*;
import com.ynpe.fastremote.domain.credential.*;
import com.ynpe.fastremote.domain.settings.GlobalSettings;
import com.ynpe.fastremote.domain.vault.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigurationArchiveTest {
    @TempDir Path directory;
    private static final ConfigurationSelection ALL = new ConfigurationSelection(true, true, true, false, true, true);
    private static final char[] PASSWORD = "test-only-password".toCharArray();

    @Test void roundTripsSettingsHostsCommandsVariablesAndReencryptsCredentials() throws Exception {
        try (var source = fixture("source"); var target = fixture("target")) {
            populate(source);
            source.vault.create(PASSWORD, true);
            var secret = source.vault.put(SecretType.PASSWORD, "fixture-secret".getBytes(StandardCharsets.UTF_8));
            source.credentials.save(new GlobalCredential(secret, "Shared", "ops", AuthenticationType.PASSWORD,
                    CredentialScope.REUSABLE, Optional.empty()));
            Path file = directory.resolve("all.frc");
            String beforeExport = Files.readString(source.store.path());
            source.archive.exportTo(file, ALL, PASSWORD);
            assertEquals(beforeExport, Files.readString(source.store.path()), "export must be read only");
            String text = Files.readString(file);
            assertTrue(text.startsWith("#FastRemoteConfig"));
            assertFalse(text.contains("fixture-secret"));
            assertFalse(text.contains("rememberedKek"));
            assertFalse(text.contains("test-only-password"));
            target.vault.create("different-target-password".toCharArray());
            target.archive.importFrom(file, PASSWORD);
            assertFalse(new YamlGlobalSettingsRepository(target.store).load().showSftpDirectoryTree());
            assertEquals(1, target.hosts.listHosts().size());
            assertEquals(1, target.snippets.listSnippets().size());
            assertEquals("a.example||b.example", target.snippets.listVariables().getFirst().value());
            assertArrayEquals("fixture-secret".getBytes(StandardCharsets.UTF_8), target.vault.read(secret));
        }
    }

    @Test void wrongPasswordRollsBackProgramAndLibraryWrites() throws Exception {
        try (var source = fixture("source"); var target = fixture("target")) {
            populate(source);
            source.vault.create(PASSWORD);
            Path file = directory.resolve("encrypted.frc");
            source.archive.exportTo(file, ALL, PASSWORD);
            target.vault.create("target-password".toCharArray());
            String before = Files.readString(target.store.path());
            assertThrows(Exception.class, () -> target.archive.importFrom(file, "wrong-password".toCharArray()));
            assertEquals(before, Files.readString(target.store.path()));
            assertTrue(target.hosts.listHosts().isEmpty());
            assertTrue(target.snippets.listSnippets().isEmpty());
            assertTrue(target.snippets.listVariables().isEmpty());
        }
    }

    @Test void variableOnlyAndCommentsLeaveOtherSectionsUntouched() throws Exception {
        try (var source = fixture("source"); var target = fixture("target")) {
            populate(source);
            Path file = directory.resolve("variables.frc");
            source.archive.exportTo(file, new ConfigurationSelection(false, false, false, false, false, true), new char[0]);
            Files.writeString(file, Files.readString(file) + "\n## user comment\n");
            target.archive.importFrom(file, new char[0]);
            assertEquals(1, target.snippets.listVariables().size());
            assertTrue(target.snippets.listSnippets().isEmpty());
            assertTrue(target.hosts.listHosts().isEmpty());
            assertEquals(GlobalSettings.defaults(), new YamlGlobalSettingsRepository(target.store).load());
        }
    }

    @Test void resetPreservesLibrariesAndVaultWhileRestoringPreferences() throws Exception {
        try (var fixture = fixture("reset")) {
            populate(fixture);
            fixture.vault.create(PASSWORD, true);
            var secret = fixture.vault.put(SecretType.PASSWORD, new byte[] {1, 2, 3});
            fixture.credentials.save(new GlobalCredential(secret, "Keep", "ops", AuthenticationType.PASSWORD,
                    CredentialScope.REUSABLE, Optional.empty()));
            new YamlSoftwareUpdateRepository(fixture.store).setAutomaticChecksEnabled(false);
            Path activation = directory.resolve("activation-fixture");
            Files.writeString(activation, "activation unchanged");
            fixture.archive.resetProgram();
            assertEquals(GlobalSettings.defaults(), new YamlGlobalSettingsRepository(fixture.store).load());
            assertTrue(new YamlSoftwareUpdateRepository(fixture.store).load().automaticChecksEnabled());
            assertEquals(1, fixture.hosts.listHosts().size());
            assertEquals(1, fixture.snippets.listVariables().size());
            assertEquals(1, fixture.snippets.listSnippets().size());
            assertEquals(1, fixture.credentials.list().size());
            assertArrayEquals(new byte[] {1, 2, 3}, fixture.vault.read(secret));
            assertFalse(fixture.vault.rememberedUnlockConfigured());
            assertEquals("activation unchanged", Files.readString(activation));
        }
    }

    @Test void rejectsLegacyDuplicateUnknownAndMalformedSectionsWithoutWrites() throws Exception {
        for (String text : List.of("format: FastRemote\nversion: 2", "@FASTREMOTE-VARIABLES:1",
                "#FastRemoteConfig\nversion: 1\n#Variables\n#Variables\n",
                "#FastRemoteConfig\nversion: 1\n#Unknown\nvalue")) {
            assertThrows(java.io.IOException.class, () -> ConfigurationTextFile.decode(text));
        }
        try (var fixture = fixture("invalid")) {
            Path file = directory.resolve("invalid.frc");
            ConfigurationTextFile.write(file, Map.of("ProgramConfig", "workspace: bad"));
            assertThrows(Exception.class, () -> fixture.archive.importFrom(file, new char[0]));
            assertTrue(fixture.hosts.listHosts().isEmpty());
            assertFalse(Files.exists(fixture.store.path()));
        }
    }

    @Test void emptyLibrariesAndDefaultSettingsAreValidAndHostSecretsRequireHosts() throws Exception {
        try (var source = fixture("empty"); var target = fixture("target")) {
            Path file = directory.resolve("empty.frc");
            source.archive.exportTo(file, new ConfigurationSelection(true, true, false, false, true, true), new char[0]);
            target.archive.importFrom(file, new char[0]);
            assertEquals(GlobalSettings.defaults(), new YamlGlobalSettingsRepository(target.store).load());
            assertThrows(Exception.class, () -> source.archive.exportTo(file,
                    new ConfigurationSelection(false, false, false, true, false, false), PASSWORD));
        }
    }

    private void populate(Fixture fixture) throws Exception {
        new YamlGlobalSettingsRepository(fixture.store).save(GlobalSettings.defaults().withShowSftpDirectoryTree(false));
        fixture.hosts.saveHost(SavedHost.create("Host", Optional.empty(), new ConnectionProfile(
                new Host("example.com"), new Port(22), new Username("ops"), AuthenticationType.PASSWORD, Optional.empty())));
        fixture.library.importArchive(List.of("@FASTREMOTE-VARIABLES:1",
                "@variable\t\tHosts\tcomm_urls\tARRAY\t||\ta.example||b.example\t",
                "@FASTREMOTE-COMMANDS:1", "@command\t\tHTTP\tcurl {{variable,comm_urls}}\t"), true);
    }

    private Fixture fixture(String name) throws Exception {
        var store = new YamlGlobalConfigStore(directory.resolve(name + ".yaml"));
        var vault = new YamlSecretVault(store);
        var credentials = new YamlGlobalCredentialRepository(store);
        var database = new FastRemoteDatabase(directory.resolve(name + ".db"));
        var hosts = new SqliteHostCatalogRepository(database);
        var snippets = new SqliteSnippetRepository(database);
        var library = new SqliteSnippetWorkflow(snippets);
        var archive = new ConfigurationArchive(database, store, new HostCatalogArchive(hosts, vault, credentials), snippets, library, vault);
        return new Fixture(store, database, vault, credentials, hosts, snippets, library, archive);
    }

    private record Fixture(YamlGlobalConfigStore store, FastRemoteDatabase database, YamlSecretVault vault,
            YamlGlobalCredentialRepository credentials, SqliteHostCatalogRepository hosts,
            SqliteSnippetRepository snippets, SqliteSnippetWorkflow library, ConfigurationArchive archive) implements AutoCloseable {
        public void close() { library.close(); vault.close(); database.close(); }
    }
}
