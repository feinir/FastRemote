// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import com.ynpe.fastremote.app.workflow.BoundedAutomationGateway;
import com.ynpe.fastremote.app.workflow.DefaultAiAssistantWorkflow;
import com.ynpe.fastremote.app.workflow.PtyLocalTerminalWorkflow;
import com.ynpe.fastremote.app.workflow.SqliteHostCatalogWorkflow;
import com.ynpe.fastremote.app.workflow.SqliteSnippetWorkflow;
import com.ynpe.fastremote.app.workflow.SqliteCommandPaletteUsageWorkflow;
import com.ynpe.fastremote.app.workflow.SshConnectionWorkflow;
import com.ynpe.fastremote.app.workflow.YamlSettingsWorkflow;
import com.ynpe.fastremote.licensing.LicenseGateway;
import com.ynpe.fastremote.storage.FastRemoteDatabase;
import com.ynpe.fastremote.storage.FileConnectionProfileRepository;
import com.ynpe.fastremote.storage.FileKnownHostRepository;
import com.ynpe.fastremote.storage.SqliteAiChatRepository;
import com.ynpe.fastremote.storage.SqliteHostCatalogRepository;
import com.ynpe.fastremote.storage.SqliteSnippetRepository;
import com.ynpe.fastremote.storage.SqliteCommandPaletteUsageRepository;
import com.ynpe.fastremote.storage.YamlAiSettingsRepository;
import com.ynpe.fastremote.storage.YamlGlobalConfigStore;
import com.ynpe.fastremote.storage.YamlGlobalCredentialRepository;
import com.ynpe.fastremote.storage.YamlGlobalSettingsRepository;
import com.ynpe.fastremote.storage.YamlSecretVault;
import com.ynpe.fastremote.storage.YamlSoftwareUpdateRepository;
import com.ynpe.fastremote.transport.ssh.MinaSshSessionConnector;
import java.nio.file.Path;

/** Process-scoped services shared by every main-window controller created during one run. */
final class ApplicationRuntime implements AutoCloseable {
    final LicenseGateway licenseGateway;
    final YamlGlobalSettingsRepository settingsRepository;
    final YamlAiSettingsRepository aiSettingsRepository;
    final SshConnectionWorkflow connectionWorkflow;
    final SqliteHostCatalogWorkflow catalogWorkflow;
    final YamlSettingsWorkflow settingsWorkflow;
    final SqliteSnippetWorkflow snippetWorkflow;
    final SqliteCommandPaletteUsageWorkflow commandPaletteUsageWorkflow;
    final PtyLocalTerminalWorkflow localTerminalWorkflow;
    final BoundedAutomationGateway automationGateway;
    final DefaultAiAssistantWorkflow aiWorkflow;
    final DailyFileDiagnosticLogOutput diagnosticLogOutput;
    final SoftwareUpdateCoordinator softwareUpdates;
    private final RuntimeResources resources;

    private ApplicationRuntime(
            RuntimeResources resources,
            LicenseGateway licenseGateway,
            YamlGlobalSettingsRepository settingsRepository,
            YamlAiSettingsRepository aiSettingsRepository,
            SshConnectionWorkflow connectionWorkflow,
            SqliteHostCatalogWorkflow catalogWorkflow,
            YamlSettingsWorkflow settingsWorkflow,
            SqliteSnippetWorkflow snippetWorkflow,
            SqliteCommandPaletteUsageWorkflow commandPaletteUsageWorkflow,
            PtyLocalTerminalWorkflow localTerminalWorkflow,
            BoundedAutomationGateway automationGateway,
            DefaultAiAssistantWorkflow aiWorkflow,
            DailyFileDiagnosticLogOutput diagnosticLogOutput,
            SoftwareUpdateCoordinator softwareUpdates) {
        this.resources = resources;
        this.licenseGateway = licenseGateway;
        this.settingsRepository = settingsRepository;
        this.aiSettingsRepository = aiSettingsRepository;
        this.connectionWorkflow = connectionWorkflow;
        this.catalogWorkflow = catalogWorkflow;
        this.settingsWorkflow = settingsWorkflow;
        this.snippetWorkflow = snippetWorkflow;
        this.commandPaletteUsageWorkflow = commandPaletteUsageWorkflow;
        this.localTerminalWorkflow = localTerminalWorkflow;
        this.automationGateway = automationGateway;
        this.aiWorkflow = aiWorkflow;
        this.diagnosticLogOutput = diagnosticLogOutput;
        this.softwareUpdates = softwareUpdates;
    }

    static ApplicationRuntime create(Path applicationDataDirectory) throws java.io.IOException {
        var resources = new RuntimeResources();
        resources.stopped().exceptionally(failure -> {
            System.getLogger(ApplicationRuntime.class.getName()).log(System.Logger.Level.ERROR,
                    "Application resource shutdown failed", failure);
            return null;
        });
        try {
            Path globalConfigPath = applicationDataDirectory.resolve("global-config.yaml");
            Path databasePath = applicationDataDirectory.resolve("fastremote.db");
            var licenseGateway = resources.share(LicenseRuntime.create(applicationDataDirectory));
            var knownHosts = FileKnownHostRepository.inDefaultLocation();
            var profileRepository = FileConnectionProfileRepository.inDefaultLocation();
            var connector = new MinaSshSessionConnector(knownHosts);
            var connectionWorkflow = resources.own(new SshConnectionWorkflow(connector, profileRepository));
            var database = resources.share(new FastRemoteDatabase(databasePath));
            var globalConfig = new YamlGlobalConfigStore(globalConfigPath);
            var vault = resources.share(new YamlSecretVault(globalConfig));
            var hostRepository = new SqliteHostCatalogRepository(database);
            var settingsRepository = new YamlGlobalSettingsRepository(globalConfig);
            var updateRepository = new YamlSoftwareUpdateRepository(globalConfig);
            var aiSettingsRepository = new YamlAiSettingsRepository(globalConfig);
            var credentials = new YamlGlobalCredentialRepository(globalConfig);
            var catalogWorkflow = resources.own(new SqliteHostCatalogWorkflow(
                    database, hostRepository, vault, credentials, false));
            resources.waitFor(catalogWorkflow.stopped());
            var settingsWorkflow = resources.own(new YamlSettingsWorkflow(settingsRepository));
            resources.waitFor(settingsWorkflow.stopped());
            var diagnosticLogOutput = resources.share(new DailyFileDiagnosticLogOutput(globalConfigPath));
            diagnosticLogOutput.configure(settingsRepository.load().diagnosticLogSettings());
            var snippetRepository = new SqliteSnippetRepository(database);
            var snippetWorkflow = resources.own(new SqliteSnippetWorkflow(snippetRepository));
            resources.waitFor(snippetWorkflow.stopped());
            catalogWorkflow.configureArchive(globalConfig, snippetRepository, snippetWorkflow);
            var commandPaletteUsageWorkflow = resources.own(new SqliteCommandPaletteUsageWorkflow(
                    new SqliteCommandPaletteUsageRepository(database)));
            resources.waitFor(commandPaletteUsageWorkflow.stopped());
            var automationGateway = resources.own(new BoundedAutomationGateway(connector, hostRepository,
                    catalogWorkflow::loadCredential, settingsRepository, aiSettingsRepository, snippetRepository,
                    licenseGateway::permits, credentials, catalogWorkflow));
            resources.waitFor(automationGateway.stopped());
            var aiWorkflow = resources.own(new DefaultAiAssistantWorkflow(
                    aiSettingsRepository, new SqliteAiChatRepository(database), vault, automationGateway,
                    applicationDataDirectory.resolve("codex")));
            resources.waitFor(aiWorkflow.stopped());
            boolean community = ApplicationEdition.current() == ApplicationEdition.COMMUNITY;
            var updateClient = new SoftwareUpdateClient(community
                    ? java.net.URI.create("https://download.ynpe.com") : LicenseRuntime.serviceUri(), community);
            var updates = resources.own(new SoftwareUpdateCoordinator(updateClient, updateRepository,
                    licenseGateway::status, java.time.Clock.systemUTC()));
            return new ApplicationRuntime(resources, licenseGateway, settingsRepository, aiSettingsRepository,
                    connectionWorkflow, catalogWorkflow, settingsWorkflow, snippetWorkflow,
                    commandPaletteUsageWorkflow,
                    resources.own(new PtyLocalTerminalWorkflow()), automationGateway, aiWorkflow,
                    diagnosticLogOutput, updates);
        } catch (java.io.IOException | RuntimeException | Error failure) {
            resources.close();
            throw failure;
        }
    }

    @Override
    public void close() {
        resources.close();
    }
}
