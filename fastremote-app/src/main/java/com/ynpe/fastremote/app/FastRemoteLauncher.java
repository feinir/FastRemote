// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import com.ynpe.fastremote.storage.FastRemotePaths;
import com.ynpe.fastremote.storage.IncompatibleDataBackup;
import com.ynpe.fastremote.storage.YamlGlobalConfigStore;
import com.ynpe.fastremote.storage.YamlGlobalSettingsRepository;
import java.io.IOException;
import java.util.Arrays;

public final class FastRemoteLauncher {
    private static volatile SingleInstanceBroker primaryBroker;
    private static volatile AsyncResourceBootstrap<ApplicationRuntime> runtimeBootstrap;
    private static volatile DesktopDisplayScale.Resolution displayScale =
            new DesktopDisplayScale.Resolution(1.0, DesktopDisplayScale.Source.DEFAULT, false);
    private static volatile DesktopDisplayScale.Resolution nativeDisplayScale = displayScale;
    private static final NativeOpenRequestBridge nativeOpenRequests = new NativeOpenRequestBridge();

    private FastRemoteLauncher() {
    }

    public static void main(String[] args) {
        StartupOptions options;
        try {
            options = StartupOptions.parse(Arrays.asList(args));
            options.dataDirectory().ifPresent(directory -> {
                System.clearProperty("fastremote.dataDirectory.managed");
                System.setProperty(FastRemotePaths.DATA_DIRECTORY_PROPERTY, directory.toString());
            });
        } catch (IllegalArgumentException failure) {
            System.err.println("[FastRemote] " + failure.getMessage());
            System.exit(2);
            return;
        }
        // Register before JavaFX starts: macOS may deliver the first Finder open event while the
        // application runtime is still being initialized.
        nativeOpenRequests.installIfSupported();
        LaunchRequest request = LaunchRequest.parse(options.applicationArguments());
        try (SingleInstanceBroker broker = DataLocationService.start(request)) {
            if (broker == null) return;
            primaryBroker = broker;
            IncompatibleDataBackup.backupIfRequired(
                    FastRemotePaths.applicationDataDirectory().toAbsolutePath().normalize());
            nativeDisplayScale = DesktopDisplayScale.detectNativeScale();
            displayScale = DesktopDisplayScale.configureBeforeJavaFx(windowSettings(), nativeDisplayScale);
            System.err.println("[FastRemote] display scale=" + displayScale.factor()
                    + " source=" + displayScale.source()
                    + "; tray scale=" + nativeDisplayScale.factor()
                    + " source=" + nativeDisplayScale.source());
            runtimeBootstrap = AsyncResourceBootstrap.start("fastremote-runtime-bootstrap",
                    () -> ApplicationRuntime.create(FastRemotePaths.applicationDataDirectory()
                            .toAbsolutePath().normalize()));
            PlatformSystemIntegration.installForPackagedApplication();
            FastRemoteApplication.main(options.applicationArguments().toArray(String[]::new));
        } catch (IOException failure) {
            System.err.println("[FastRemote] " + failure.getMessage());
            System.exit(2);
        } finally {
            AsyncResourceBootstrap<ApplicationRuntime> bootstrap = runtimeBootstrap;
            runtimeBootstrap = null;
            if (bootstrap != null) bootstrap.close();
            primaryBroker = null;
        }
    }

    private static com.ynpe.fastremote.domain.settings.WindowSettings windowSettings() {
        try {
            var store = new YamlGlobalConfigStore(
                    FastRemotePaths.applicationDataDirectory().resolve("global-config.yaml"));
            return new YamlGlobalSettingsRepository(store).load().windowSettings();
        } catch (IOException | RuntimeException failure) {
            System.err.println("[FastRemote] Unable to read HiDPI preference; using automatic detection");
            return com.ynpe.fastremote.domain.settings.WindowSettings.defaults();
        }
    }

    static DesktopDisplayScale.Resolution displayScale() {
        return displayScale;
    }

    static DesktopDisplayScale.Resolution nativeDisplayScale() {
        return nativeDisplayScale;
    }

    static SingleInstanceBroker primaryBroker() {
        return primaryBroker;
    }

    static ApplicationRuntime claimRuntime() throws IOException {
        AsyncResourceBootstrap<ApplicationRuntime> bootstrap = runtimeBootstrap;
        if (bootstrap == null) {
            return ApplicationRuntime.create(FastRemotePaths.applicationDataDirectory()
                    .toAbsolutePath().normalize());
        }
        return bootstrap.claim();
    }

    static NativeOpenRequestBridge nativeOpenRequests() {
        return nativeOpenRequests;
    }
}
