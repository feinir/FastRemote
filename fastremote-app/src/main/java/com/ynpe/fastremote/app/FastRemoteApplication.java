// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import com.ynpe.fastremote.storage.FastRemotePaths;
import com.ynpe.fastremote.storage.IncompatibleDataBackup;
import com.ynpe.fastremote.storage.YamlGlobalConfigStore;
import com.ynpe.fastremote.storage.YamlGlobalSettingsRepository;
import com.ynpe.fastremote.ui.MainView;
import com.ynpe.fastremote.ui.InterfaceFontPolicy;
import com.ynpe.fastremote.ui.MainWindowController;
import com.ynpe.fastremote.ui.AiAssistantController;
import com.ynpe.fastremote.ui.HostLibraryController;
import com.ynpe.fastremote.ui.SnippetLibraryController;
import com.ynpe.fastremote.ui.VariableLibraryController;
import com.ynpe.fastremote.ui.CredentialLibraryController;
import com.ynpe.fastremote.ui.DiagnosticLogController;
import com.ynpe.fastremote.ui.DialogSpacing;
import com.ynpe.fastremote.ui.LocalizedTextInputContextMenus;
import com.ynpe.fastremote.i18n.Localizer;
import com.ynpe.fastremote.i18n.Texts;
import com.ynpe.fastremote.licensing.LicenseGateway;
import com.ynpe.fastremote.domain.license.LicenseSnapshot;
import java.io.IOException;
import java.io.InputStream;
import java.awt.Desktop;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.fxml.FXMLLoader;
import javafx.collections.ListChangeListener;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

public final class FastRemoteApplication extends Application {
    static final String PRODUCT_WINDOW_ICON = "/com/ynpe/fastremote/app/icons/logo.png";
    private static final Duration LICENSE_REFRESH_INTERVAL = Duration.hours(2);
    static final double ONLINE_ACTIVATION_WIDTH = 540.0;
    static final double ONLINE_ACTIVATION_HEIGHT = 380.0;
    static final double OFFLINE_ACTIVATION_WIDTH = 640.0;
    static final double OFFLINE_ACTIVATION_HEIGHT = 700.0;
    private MainWindowController controller;
    private LicenseGateway licenseGateway;
    private Timeline licenseRecheck;
    private SoftwareUpdateCoordinator softwareUpdates;
    private ApplicationRuntime runtime;
    private Stage mainStage;
    private Stage initialStage;
    private Stage linuxFallbackStage;
    private SystemTrayIntegration systemTray;
    private boolean initialMacOsMenuBarIconEnabled = true;
    private Path applicationDataDirectory;
    private LaunchRequest pendingLaunchRequest = LaunchRequest.parse(List.of());
    private boolean pendingQuickConnect;
    private boolean reopenAfterClose;
    private WindowState windowState = WindowState.CLOSED;
    private final AtomicBoolean exiting = new AtomicBoolean();
    private boolean processServicesStarted;
    private PauseTransition postShowWarmup;
    private Stage activationStage;
    private Alert licenseReminder;
    private PauseTransition licenseReminderDelay;
    private Alert trialExpiryReminder;
    private PauseTransition trialExpiryReminderDelay;
    private boolean trialExpiryReminderShown;
    private boolean offlineExpiryReminderShown;
    private final LicenseReminderPolicy licenseReminderPolicy = new LicenseReminderPolicy();
    private Localizer localizer;
    private LocalizedTextInputContextMenus localizedTextInputContextMenus;
    private LinuxMenuPopupFrameSync linuxMenuPopupFrameSync;
    private Image productWindowIcon;
    private ListChangeListener<Window> productWindowIconListener;
    private final NativeOpenRequestBridge nativeOpenRequests = FastRemoteLauncher.nativeOpenRequests();

    @Override
    public void init() throws IOException {
        linuxMenuPopupFrameSync = LinuxMenuPopupFrameSync.loadIfSupported().orElse(null);
        Path applicationDataDirectory = FastRemotePaths.applicationDataDirectory().toAbsolutePath().normalize();
        var bootstrapConfig = new YamlGlobalConfigStore(applicationDataDirectory.resolve("global-config.yaml"));
        var bootstrapSettings = new YamlGlobalSettingsRepository(bootstrapConfig).load();
        initialMacOsMenuBarIconEnabled = bootstrapSettings.windowSettings().macOsMenuBarIconEnabled();
        localizer = Localizer.forLanguage(bootstrapSettings.windowSettings().interfaceLanguage());
        Texts.configure(localizer);
        Locale.setDefault(Locale.Category.DISPLAY, localizer.locale());
        Locale.setDefault(Locale.Category.FORMAT, localizer.locale());
    }

    @Override
    public void start(Stage stage) throws IOException {
        Platform.setImplicitExit(false);
        if (linuxMenuPopupFrameSync != null) linuxMenuPopupFrameSync.install();
        MacDockIcon.restoreBundleIcon();
        installProductWindowIcons();
        applyProductWindowIcon(stage);
        localizedTextInputContextMenus = LocalizedTextInputContextMenus.install(localizer);
        nativeOpenRequests.installJavaFxHandlerIfNeeded();
        LaunchRequest launchRequest = nativeOpenRequests.drainInitialRequest(
                LaunchRequest.parse(getParameters().getRaw()));
        applicationDataDirectory = FastRemotePaths.applicationDataDirectory().toAbsolutePath().normalize();
        runtime = FastRemoteLauncher.claimRuntime();
        licenseGateway = runtime.licenseGateway;
        softwareUpdates = runtime.softwareUpdates;
        initialStage = stage;
        runtime.aiWorkflow.setExternalMcpRequestObserver(this::ensureWindowForExternalMcpRequest);
        Consumer<LaunchRequest> consumer = request -> Platform.runLater(() -> openMainWindow(request));
        nativeOpenRequests.installConsumer(consumer);
        nativeOpenRequests.installReopenConsumer(() -> Platform.runLater(() ->
                openMainWindow(LaunchRequest.parse(List.of()))));
        nativeOpenRequests.installQuitConsumer(this::requestApplicationExit);
        SingleInstanceBroker broker = FastRemoteLauncher.primaryBroker();
        if (broker != null) broker.installConsumer(consumer);
        systemTray = new SystemTrayIntegration(localizer,
                () -> openMainWindow(LaunchRequest.parse(List.of())),
                this::requestTrayQuickConnect,
                this::requestTrayLocalTerminal,
                this::requestApplicationExit,
                initialMacOsMenuBarIconEnabled);
        runtime.settingsWorkflow.setSavedListener(settings -> Platform.runLater(() -> {
            SystemTrayIntegration currentTray = systemTray;
            if (currentTray != null) {
                currentTray.setMacOsMenuBarIconEnabled(
                        settings.windowSettings().macOsMenuBarIconEnabled());
            }
        }));
        systemTray.availability().thenAccept(available -> {
            if (!available) return;
            Platform.runLater(() -> {
                if (windowState == WindowState.CLOSED && linuxFallbackStage != null) {
                    linuxFallbackStage.close();
                    linuxFallbackStage = null;
                }
            });
        });
        openMainWindow(launchRequest);
    }

    enum WindowState { CLOSED, OPENING, OPEN, CLOSING, EXITING }

    private void startRuntimeLicenseChecks(Stage stage) {
        ApplicationEdition edition = ApplicationEdition.current();
        if (edition != ApplicationEdition.STORE) softwareUpdates.start(stage);
        if (edition != ApplicationEdition.COMMERCIAL) return;
        checkLicenseInBackground(true);
        licenseRecheck = new Timeline(new KeyFrame(LICENSE_REFRESH_INTERVAL, event ->
                checkLicenseInBackground(false)));
        licenseRecheck.setCycleCount(Timeline.INDEFINITE);
        licenseRecheck.play();
    }

    private CompletionStage<LicenseSnapshot> checkLicenseInBackground(boolean startup) {
        var check = licenseGateway.status().thenCompose(snapshot ->
                refreshInstalledOnlineEntitlement(snapshot));
        var resolved = check.exceptionallyCompose(error -> licenseGateway.status());
        resolved.whenComplete((snapshot, error) ->
                Platform.runLater(() -> {
                    Stage owner = mainStage;
                    if (owner == null || controller == null) return;
                    if (error == null && snapshot.status().permitsCommercialUse()) {
                        licenseReminderPolicy.markAuthorized();
                        closeLicenseReminder();
                        if (controller != null) controller.updateLicenseControls(snapshot);
                        if (startup && LicenseStartupPolicy.shouldRemindTrialExpiry(snapshot, Instant.now())) {
                            showTrialExpiryReminder(owner, snapshot);
                        }
                        if (startup && LicenseStartupPolicy.shouldRemindOfflineExpiry(snapshot, Instant.now())) {
                            showOfflineExpiryReminder(owner, snapshot);
                        }
                        return;
                    }
                    if (error == null && snapshot.activationRequired()) {
                        showActivationWindow(owner);
                        return;
                    }
                    showLicenseReminder(owner, error == null ? snapshot.status().name() : "AGENT_UNAVAILABLE");
                }));
        return resolved;
    }

    private CompletionStage<LicenseSnapshot> refreshInstalledOnlineEntitlement(LicenseSnapshot snapshot) {
        if (!LicenseStartupPolicy.shouldRefreshOnlineEntitlement(snapshot)) {
            return CompletableFuture.completedFuture(snapshot);
        }
        return "trial".equals(snapshot.kind()) ? licenseGateway.syncTrial() : licenseGateway.refresh();
    }

    private void showTrialExpiryReminder(Stage owner, LicenseSnapshot snapshot) {
        if (trialExpiryReminderShown || trialExpiryReminder != null) return;
        if (activationStage != null || modalWindowShowing(owner)) {
            if (trialExpiryReminderDelay == null) {
                trialExpiryReminderDelay = new PauseTransition(Duration.millis(250));
            }
            trialExpiryReminderDelay.stop();
            trialExpiryReminderDelay.setOnFinished(event -> {
                trialExpiryReminderDelay = null;
                showTrialExpiryReminder(owner, snapshot);
            });
            trialExpiryReminderDelay.play();
            return;
        }
        String expiry = snapshot.expiresAt().map(value -> localizer.formatDateTime(value,
                java.time.ZoneId.systemDefault())).orElse("-");
        trialExpiryReminderShown = true;
        Alert reminder = new Alert(Alert.AlertType.WARNING,
                localizer.text("license.trialExpiring.body", expiry), ButtonType.OK);
        DialogSpacing.prepare(reminder);
        trialExpiryReminder = reminder;
        reminder.initOwner(owner);
        reminder.initModality(javafx.stage.Modality.NONE);
        reminder.setTitle(localizer.text("license.title"));
        reminder.setHeaderText(localizer.text("license.trialExpiring.header"));
        reminder.setOnHidden(event -> trialExpiryReminder = null);
        reminder.show();
    }

    private void showOfflineExpiryReminder(Stage owner, LicenseSnapshot snapshot) {
        if (offlineExpiryReminderShown || activationStage != null || modalWindowShowing(owner)) return;
        String expiry = snapshot.expiresAt().map(value -> localizer.formatDateTime(value,
                java.time.ZoneId.systemDefault())).orElse("-");
        offlineExpiryReminderShown = true;
        Alert reminder = new Alert(Alert.AlertType.WARNING,
                localizer.text("license.offline.expiring.body", expiry), ButtonType.OK);
        DialogSpacing.prepare(reminder);
        reminder.initOwner(owner);
        reminder.initModality(javafx.stage.Modality.NONE);
        reminder.setTitle(localizer.text("license.title"));
        reminder.setHeaderText(localizer.text("license.offline.expiring.header"));
        reminder.show();
    }

    private void createMainWindow(Stage stage, LaunchRequest launchRequest) throws IOException {
        MainWindowController nextController = new MainWindowController(
                runtime.connectionWorkflow, runtime.catalogWorkflow, runtime.settingsWorkflow,
                runtime.snippetWorkflow, runtime.localTerminalWorkflow, runtime.aiWorkflow,
                runtime.automationGateway, localizer, licenseGateway::permits,
                licenseGateway::status, licenseGateway::deactivate,
                () -> showActivationWindow(stage),
                () -> showLicenseReminder(stage, "LOCAL_TERMINAL"), runtime.diagnosticLogOutput);
        nextController.configureApplicationLifecycle(
                this::requestMainWindowClose, this::requestApplicationExit);
        ApplicationEdition edition = ApplicationEdition.current();
        nextController.configureDistribution(edition == ApplicationEdition.COMMERCIAL,
                edition != ApplicationEdition.STORE);
        nextController.configureCommandPaletteUsage(runtime.commandPaletteUsageWorkflow);
        nextController.configureSoftwareUpdates(softwareUpdates::automaticChecksEnabled,
                softwareUpdates::setAutomaticChecksEnabled, softwareUpdates::checkManually);
        if (launchRequest.openLocalTerminal()) {
            nextController.requestInitialLocalTerminal(launchRequest.initialDirectory().orElse(null));
        }
        FXMLLoader loader = new FXMLLoader(MainView.fxml(), localizer.bundle());
        loader.setControllerFactory(type -> {
            if (type == MainWindowController.class) {
                return nextController;
            }
            if (type == AiAssistantController.class) {
                return new AiAssistantController();
            }
            if (type == HostLibraryController.class) return new HostLibraryController();
            if (type == SnippetLibraryController.class) return new SnippetLibraryController();
            if (type == VariableLibraryController.class) return new VariableLibraryController();
            if (type == CredentialLibraryController.class) return new CredentialLibraryController();
            if (type == DiagnosticLogController.class) return new DiagnosticLogController();
            throw new IllegalArgumentException("Unsupported FXML controller: " + type.getName());
        });
        Parent root = loader.load();
        nextController.requestInitialLocalFileDirectories(launchRequest.localDirectories());
        nextController.requestInitialEditorFiles(launchRequest.files());
        InterfaceFontPolicy.apply(root);
        Scene scene = new Scene(root);
        scene.getStylesheets().add(MainView.stylesheet().toExternalForm());

        stage.setMinWidth(540.0);
        stage.setMinHeight(340.0);
        stage.setWidth(1420.0);
        stage.setHeight(860.0);
        stage.setTitle("FastRemote");
        stage.setScene(scene);
        nextController.attachMainWindow(stage);
        controller = nextController;
        mainStage = stage;
        nextController.initialWindowReady().whenComplete((ignored, error) -> Platform.runLater(() -> {
            if (controller != nextController || windowState == WindowState.EXITING) return;
            if (!stage.isShowing()) stage.show();
            stage.toFront();
            stage.requestFocus();
            windowState = WindowState.OPEN;
            softwareUpdates.updateOwner(stage);
            startProcessServicesIfNeeded();
            reportIncompatibleDataBackups(nextController);
            LaunchRequest queued = pendingLaunchRequest;
            pendingLaunchRequest = LaunchRequest.parse(List.of());
            if (queued.opensWorkspace()) consumeExternalLaunchRequest(stage, queued);
            if (pendingQuickConnect) {
                pendingQuickConnect = false;
                nextController.requestQuickConnectDialog();
            }
        }));
    }

    private void reportIncompatibleDataBackups(MainWindowController targetController) {
        Thread.ofVirtual().name("fastremote-incompatible-data-notice").start(() -> {
            try {
                List<Path> backups = IncompatibleDataBackup.populatedBackupDirectories(applicationDataDirectory);
                for (Path backup : backups) {
                    String command = incompatibleDataLaunchCommand(
                            backup, System.getProperty("os.name", ""));
                    String message = localizer.text("startup.incompatibleDataBackup", backup, command);
                    Platform.runLater(() -> {
                        if (!exiting.get() && controller == targetController) {
                            targetController.showStartupDataBackupNotice(message);
                        }
                    });
                }
            } catch (IOException failure) {
                System.getLogger(FastRemoteApplication.class.getName()).log(
                        System.Logger.Level.WARNING, "Unable to inspect incompatible data backups", failure);
            }
        });
    }

    static String incompatibleDataLaunchCommand(Path backup, String osName) {
        String quotedBackup = "\"" + backup.toAbsolutePath().normalize() + "\"";
        String normalizedOs = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        String executable;
        if (normalizedOs.contains("win")) {
            executable = "FastRemote.exe";
        } else if (normalizedOs.contains("mac")) {
            executable = "FastRemote.app/Contents/MacOS/FastRemote";
        } else {
            executable = "FastRemote";
        }
        return executable + " --user-data-dir " + quotedBackup;
    }

    private void startProcessServicesIfNeeded() {
        if (processServicesStarted) return;
        processServicesStarted = true;
        startRuntimeLicenseChecks(mainStage);
        MainWindowController startupController = controller;
        postShowWarmup = new PauseTransition(Duration.millis(75));
        postShowWarmup.setOnFinished(event -> {
            postShowWarmup = null;
            if (exiting.get() || controller != startupController) return;
            startupController.warmUpAiAssistant();
            runtime.aiWorkflow.startConfiguredMcpServer().whenComplete((ignored, error) -> {
                if (error != null) System.getLogger(FastRemoteApplication.class.getName()).log(
                        System.Logger.Level.ERROR, "Unable to start configured MCP server", error);
            });
        });
        postShowWarmup.play();
    }

    private void showLicenseReminder(Stage owner, String status) {
        if (ApplicationEdition.current() != ApplicationEdition.COMMERCIAL) return;
        if (licenseReminderPolicy.wasShown() || licenseReminder != null || activationStage != null) return;
        if (modalWindowShowing(owner)) {
            licenseReminderPolicy.claim(true);
            if (licenseReminderDelay == null) {
                licenseReminderDelay = new PauseTransition(Duration.millis(250));
            }
            licenseReminderDelay.stop();
            licenseReminderDelay.setOnFinished(event -> {
                licenseReminderDelay = null;
                showLicenseReminder(owner, status);
            });
            licenseReminderDelay.play();
            return;
        }
        if (!licenseReminderPolicy.claim(false)) return;
        boolean onlineRevalidation = "ONLINE_REVALIDATION_REQUIRED".equals(status);
        ButtonType activate = new ButtonType(localizer.text(
                onlineRevalidation ? "license.retry" : "license.activateNow"),
                ButtonBar.ButtonData.OK_DONE);
        ButtonType later = new ButtonType(localizer.text("license.later"),
                ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert reminder = new Alert(Alert.AlertType.INFORMATION, localizer.text(
                onlineRevalidation ? "license.onlineRevalidation.body" : "license.reminder.body"),
                activate, later);
        DialogSpacing.prepare(reminder);
        DialogSpacing.configureInitialWindowWidth(reminder, 520.0, 440.0);
        licenseReminder = reminder;
        reminder.initOwner(owner);
        reminder.initModality(javafx.stage.Modality.NONE);
        reminder.setTitle(localizer.text("license.title"));
        reminder.setHeaderText(onlineRevalidation
                ? localizer.text("license.onlineRevalidation.header")
                : localizer.text("license.reminder.header", status));
        reminder.setOnHidden(event -> {
            licenseReminder = null;
            if (reminder.getResult() != activate) return;
            if (onlineRevalidation) {
                licenseReminderPolicy.markAuthorized();
                checkLicenseInBackground(false);
            } else {
                showActivationWindow(owner);
            }
        });
        reminder.show();
    }

    private static boolean modalWindowShowing(Stage mainWindow) {
        return Window.getWindows().stream()
                .filter(Window::isShowing)
                .filter(window -> window != mainWindow)
                .anyMatch(window -> window instanceof Stage stage
                        && stage.getModality() != javafx.stage.Modality.NONE);
    }

    private void showActivationWindow(Stage owner) {
        if (activationStage != null) {
            activationStage.show();
            activationStage.toFront();
            activationStage.requestFocus();
            return;
        }
        closeLicenseReminder();
        try {
            FXMLLoader loader = new FXMLLoader(FastRemoteApplication.class.getResource(
                    "/com/ynpe/fastremote/app/LicenseBootstrapView.fxml"), localizer.bundle());
            var licenseController = new LicenseBootstrapController(licenseGateway, localizer,
                    () -> switchToOfflineActivation(owner));
            loader.setController(licenseController);
            Parent root = loader.load();
            InterfaceFontPolicy.apply(root);
            Stage window = activationWindow(owner, root,
                    ONLINE_ACTIVATION_WIDTH, ONLINE_ACTIVATION_HEIGHT,
                    ONLINE_ACTIVATION_WIDTH, ONLINE_ACTIVATION_HEIGHT,
                    localizer.text("license.title"));
            observeAuthorization(licenseController.authorized(), window);
            window.show();
        } catch (IOException error) {
            System.getLogger(FastRemoteApplication.class.getName()).log(
                    System.Logger.Level.ERROR, "Unable to open license activation window", error);
        }
    }

    private void showOfflineActivationWindow(Stage owner) {
        try {
            FXMLLoader loader = new FXMLLoader(FastRemoteApplication.class.getResource(
                    "/com/ynpe/fastremote/app/OfflineLicenseView.fxml"), localizer.bundle());
            var licenseController = new OfflineLicenseController(licenseGateway, localizer,
                    () -> switchToOnlineActivation(owner));
            loader.setController(licenseController);
            Parent root = loader.load();
            InterfaceFontPolicy.apply(root);
            Stage window = activationWindow(owner, root,
                    OFFLINE_ACTIVATION_WIDTH, OFFLINE_ACTIVATION_HEIGHT,
                    OFFLINE_ACTIVATION_WIDTH, OFFLINE_ACTIVATION_HEIGHT,
                    localizer.text("license.offline.title"));
            observeAuthorization(licenseController.authorized(), window);
            window.show();
        } catch (IOException error) {
            System.getLogger(FastRemoteApplication.class.getName()).log(
                    System.Logger.Level.ERROR, "Unable to open offline license activation window", error);
        }
    }

    private Stage activationWindow(Stage owner, Parent root, double width, double height,
                                   double minWidth, double minHeight, String title) {
        Stage window = new Stage();
        activationStage = window;
        window.initOwner(owner);
        window.setResizable(activationWindowResizable());
        window.setTitle(title);
        Scene scene = width > 0 && height > 0
                ? new Scene(root, width, height)
                : new Scene(root);
        scene.getStylesheets().add(MainView.stylesheet().toExternalForm());
        scene.getStylesheets().add(FastRemoteApplication.class.getResource(
                "/com/ynpe/fastremote/app/license-bootstrap.css").toExternalForm());
        window.setScene(scene);
        window.setOnShown(event -> applyContentMinimum(window, scene, minWidth, minHeight));
        DialogSpacing.centerOnOwnerWhenShown(window);
        window.setOnHidden(event -> {
            if (activationStage == window) activationStage = null;
        });
        return window;
    }

    static boolean activationWindowResizable() {
        return false;
    }

    /**
     * Stage dimensions include native decorations while Scene dimensions do not. Calculate the
     * decoration delta only after the window manager has created the window so every platform
     * receives the same minimum content area.
     */
    static void applyContentMinimum(Stage window, Scene scene, double minContentWidth,
                                    double minContentHeight) {
        double decorationWidth = nativeDecorationExtent(window.getWidth(), scene.getWidth());
        double decorationHeight = nativeDecorationExtent(window.getHeight(), scene.getHeight());
        if (minContentWidth > 0.0) {
            window.setMinWidth(minContentWidth + decorationWidth);
        }
        if (minContentHeight > 0.0) {
            window.setMinHeight(minContentHeight + decorationHeight);
        }
    }

    static double nativeDecorationExtent(double outerExtent, double contentExtent) {
        if (!Double.isFinite(outerExtent) || !Double.isFinite(contentExtent)) return 0.0;
        return Math.max(0.0, outerExtent - contentExtent);
    }

    private void observeAuthorization(CompletionStage<LicenseSnapshot> result, Stage window) {
        result.whenComplete((snapshot, error) -> Platform.runLater(() -> {
            if (error != null) return;
            String expiry = snapshot.expiresAt().map(value -> localizer.formatDateTime(value,
                    java.time.ZoneId.systemDefault())).orElse("-");
            Alert success = new Alert(Alert.AlertType.INFORMATION,
                    localizer.text("license.authorized", expiry), ButtonType.OK);
            DialogSpacing.prepare(success);
            success.initOwner(window);
            success.setTitle(localizer.text("license.title"));
            success.setHeaderText(localizer.text("license.activationSuccess"));
            success.showAndWait();
            licenseReminderPolicy.markAuthorized();
            if (controller != null) controller.updateLicenseControls(snapshot);
            if (window.isShowing()) window.close();
        }));
    }

    private void switchToOfflineActivation(Stage owner) {
        closeActivationWindow();
        showOfflineActivationWindow(owner);
    }

    private void switchToOnlineActivation(Stage owner) {
        closeActivationWindow();
        showActivationWindow(owner);
    }

    private void closeActivationWindow() {
        Stage window = activationStage;
        activationStage = null;
        if (window != null) window.close();
    }

    private void closeLicenseReminder() {
        if (licenseReminderDelay != null) {
            licenseReminderDelay.stop();
            licenseReminderDelay = null;
        }
        Alert reminder = licenseReminder;
        if (reminder != null) {
            licenseReminder = null;
            reminder.close();
        }
    }

    private void closeTrialExpiryReminder() {
        if (trialExpiryReminderDelay != null) {
            trialExpiryReminderDelay.stop();
            trialExpiryReminderDelay = null;
        }
        Alert reminder = trialExpiryReminder;
        if (reminder != null) {
            trialExpiryReminder = null;
            reminder.close();
        }
    }

    private void openMainWindow(LaunchRequest request) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> openMainWindow(request));
            return;
        }
        if (exiting.get() || windowState == WindowState.EXITING) return;
        if (windowState == WindowState.OPEN && controller != null && mainStage != null) {
            consumeExternalLaunchRequest(mainStage, request);
            return;
        }
        if (shouldReuseOpeningWindowForLaunchRequest(windowState, request.opensWorkspace())
                && controller != null) {
            if (request.openLocalTerminal()) {
                controller.requestInitialLocalTerminal(request.initialDirectory().orElse(null));
            }
            controller.requestInitialLocalFileDirectories(request.localDirectories());
            controller.requestInitialEditorFiles(request.files());
            return;
        }
        if (windowState == WindowState.OPENING || windowState == WindowState.CLOSING) {
            pendingLaunchRequest = pendingLaunchRequest.merge(request);
            if (windowState == WindowState.CLOSING) reopenAfterClose = true;
            return;
        }
        windowState = WindowState.OPENING;
        if (isMacOs() && initialStage == null) MacDockIcon.showApplication();
        Stage stage = initialStage == null ? new Stage() : initialStage;
        initialStage = null;
        if (linuxFallbackStage != null) {
            Stage fallback = linuxFallbackStage;
            linuxFallbackStage = null;
            fallback.hide();
            fallback.close();
        }
        try {
            createMainWindow(stage, request);
        } catch (IOException | RuntimeException failure) {
            windowState = WindowState.CLOSED;
            System.getLogger(FastRemoteApplication.class.getName()).log(
                    System.Logger.Level.ERROR, "Unable to create main window", failure);
        }
    }

    private void requestMainWindowClose() {
        if (windowState != WindowState.OPEN || controller == null || mainStage == null) return;
        if (controller.exitOnMainWindowClose()) {
            requestApplicationExit();
            return;
        }
        if (!controller.confirmWindowClose()) return;
        if (shouldRetainHiddenMainWindow(isLinux(),
                systemTray != null && systemTray.isAvailable())) {
            controller.closeAllWorkspaceTabs();
            mainStage.hide();
            softwareUpdates.updateOwner(null);
            return;
        }
        windowState = WindowState.CLOSING;
        MainWindowController closingController = controller;
        Stage closingStage = mainStage;
        controller = null;
        mainStage = null;
        closingController.close();
        softwareUpdates.updateOwner(null);
        backgroundOrCloseStage(closingStage);
        windowState = WindowState.CLOSED;
        if (reopenAfterClose || pendingLaunchRequest.opensWorkspace()) {
            LaunchRequest queued = pendingLaunchRequest;
            pendingLaunchRequest = LaunchRequest.parse(List.of());
            reopenAfterClose = false;
            openMainWindow(queued);
        }
    }

    private void requestTrayQuickConnect() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::requestTrayQuickConnect);
            return;
        }
        if (exiting.get() || windowState == WindowState.EXITING) return;
        if (windowState == WindowState.OPEN && controller != null && mainStage != null) {
            consumeExternalLaunchRequest(mainStage, LaunchRequest.parse(List.of()));
            controller.requestQuickConnectDialog();
            return;
        }
        pendingQuickConnect = true;
        openMainWindow(LaunchRequest.parse(List.of()));
    }

    private void requestTrayLocalTerminal() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::requestTrayLocalTerminal);
            return;
        }
        if (exiting.get() || windowState == WindowState.EXITING) return;
        // Native tray callbacks do not carry a Finder/Explorer/file-manager location. Reuse the
        // existing platform-open request path; an absent directory deliberately resolves to Home.
        openMainWindow(LaunchRequest.fromExternal(true, null));
    }

    /**
     * MCP tool calls only create the main window when none exists. An already open window,
     * including an iconified one, must never be restored, raised or focused by this observer.
     */
    private void ensureWindowForExternalMcpRequest() {
        if (exiting.get()) return;
        CompletableFuture<Void> ready = new CompletableFuture<>();
        Platform.runLater(() -> {
            if (shouldCreateMainWindowForExternalMcpRequest(windowState)) {
                openMainWindow(LaunchRequest.parse(List.of()));
            }
            MainWindowController current = controller;
            if (shouldRevealHiddenMainWindowForExternalMcpRequest(
                    windowState, current != null, mainStage != null && mainStage.isShowing())) {
                consumeExternalLaunchRequest(mainStage, LaunchRequest.parse(List.of()));
            }
            if (windowState == WindowState.OPEN && current != null) {
                ready.complete(null);
            } else if (current != null) {
                current.initialWindowReady().whenComplete((ignored, error) -> {
                    if (error == null) ready.complete(null);
                    else ready.completeExceptionally(error);
                });
            } else {
                ready.completeExceptionally(new IllegalStateException("Main window was not created"));
            }
        });
        try {
            ready.get(30, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while opening the main window", interrupted);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException failure) {
            throw new IllegalStateException("Unable to open the main window for an MCP tool call", failure);
        }
    }

    static boolean shouldCreateMainWindowForExternalMcpRequest(WindowState state) {
        return state == WindowState.CLOSED;
    }

    static boolean shouldReuseOpeningWindowForLaunchRequest(
            WindowState state, boolean opensLocalTerminal) {
        return state == WindowState.OPENING && opensLocalTerminal;
    }

    static boolean shouldRetainHiddenMainWindow(boolean linux, boolean trayAvailable) {
        return linux && !trayAvailable;
    }

    static boolean shouldRevealHiddenMainWindowForExternalMcpRequest(
            WindowState state, boolean controllerAvailable, boolean stageShowing) {
        return state == WindowState.OPEN && controllerAvailable && !stageShowing;
    }

    private void backgroundOrCloseStage(Stage stage) {
        if (isMacOs()) {
            stage.close();
            // JavaFX or another desktop integration can replace Glass' process event handler.
            // Recheck it at the exact point from which Dock/Finder must recreate the window.
            nativeOpenRequests.installJavaFxHandlerIfNeeded();
            MacDockIcon.hideApplication();
            return;
        }
        if (systemTray != null && systemTray.isAvailable()) {
            stage.close();
            return;
        }
        // A tray-less Linux/Windows desktop needs a native taskbar entry. Keep only a tiny
        // placeholder Stage; the disposed controller and its scene are not retained.
        stage.setScene(new Scene(new javafx.scene.layout.StackPane(), 320.0, 120.0));
        stage.setTitle("FastRemote");
        linuxFallbackStage = stage;
        stage.iconifiedProperty().addListener((ignored, previous, iconified) -> {
            if (!iconified && linuxFallbackStage == stage && !exiting.get()) {
                openMainWindow(LaunchRequest.parse(List.of()));
            }
        });
        if (!stage.isShowing()) stage.show();
        stage.setIconified(true);
    }

    private void requestApplicationExit() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::requestApplicationExit);
            return;
        }
        if (exiting.get()) return;
        if (controller != null && !controller.confirmApplicationExit()) return;
        closeApplication();
    }

    private void closeApplication() {
        if (!exiting.compareAndSet(false, true)) return;
        windowState = WindowState.EXITING;
        MainWindowController closingController = controller;
        controller = null;
        if (closingController != null) closingController.close();
        Stage closingStage = mainStage;
        mainStage = null;
        if (closingStage != null) closingStage.close();
        if (linuxFallbackStage != null) {
            linuxFallbackStage.close();
            linuxFallbackStage = null;
        }
        if (systemTray != null) systemTray.close();
        nativeOpenRequests.clearConsumers();
        SingleInstanceBroker broker = FastRemoteLauncher.primaryBroker();
        if (broker != null) broker.close();
        stopProcessPresentation();
        if (runtime != null) runtime.close();
        Platform.exit();
    }

    private void stopProcessPresentation() {
        if (postShowWarmup != null) {
            postShowWarmup.stop();
            postShowWarmup = null;
        }
        if (licenseRecheck != null) {
            licenseRecheck.stop();
            licenseRecheck = null;
        }
        closeLicenseReminder();
        closeTrialExpiryReminder();
        closeActivationWindow();
    }

    private static boolean isMacOs() {
        return System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT).contains("mac");
    }

    private static boolean isLinux() {
        return System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT).contains("linux");
    }

    private void installProductWindowIcons() {
        if (!usesProductWindowIcon(System.getProperty("os.name", ""))) return;
        try (InputStream input = FastRemoteApplication.class.getResourceAsStream(PRODUCT_WINDOW_ICON)) {
            if (input == null) throw new IOException("Missing product window icon");
            productWindowIcon = new Image(input);
        } catch (IOException | RuntimeException failure) {
            System.getLogger(FastRemoteApplication.class.getName()).log(
                    System.Logger.Level.WARNING, "Unable to load the product window icon", failure);
            return;
        }
        productWindowIconListener = change -> {
            while (change.next()) {
                if (!change.wasAdded()) continue;
                change.getAddedSubList().forEach(this::applyProductWindowIcon);
            }
        };
        Window.getWindows().forEach(this::applyProductWindowIcon);
        Window.getWindows().addListener(productWindowIconListener);
    }

    private void applyProductWindowIcon(Window window) {
        if (productWindowIcon != null && window instanceof Stage stage && stage.getIcons().isEmpty()) {
            stage.getIcons().add(productWindowIcon);
        }
    }

    static boolean usesProductWindowIcon(String osName) {
        return osName != null && osName.toLowerCase(java.util.Locale.ROOT).contains("windows");
    }

    private void consumeExternalLaunchRequest(Stage stage, LaunchRequest request) {
        if (request.openLocalTerminal()) {
            controller.requestAdditionalLocalTerminal(request.initialDirectory().orElse(null));
        }
        controller.requestAdditionalLocalFileDirectories(request.localDirectories());
        controller.requestAdditionalEditorFiles(request.files());
        if (stage.isIconified()) stage.setIconified(false);
        if (!stage.isShowing()) stage.show();
        softwareUpdates.updateOwner(stage);
        stage.toFront();
        stage.requestFocus();
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) {
                Desktop.getDesktop().requestForeground(true);
            }
        } catch (SecurityException | UnsupportedOperationException ignored) { }
    }

    @Override
    public void stop() {
        if (linuxMenuPopupFrameSync != null) {
            linuxMenuPopupFrameSync.close();
            linuxMenuPopupFrameSync = null;
        }
        if (productWindowIconListener != null) {
            Window.getWindows().removeListener(productWindowIconListener);
            productWindowIconListener = null;
            productWindowIcon = null;
        }
        if (localizedTextInputContextMenus != null) {
            localizedTextInputContextMenus.close();
            localizedTextInputContextMenus = null;
        }
        if (controller != null) {
            controller.close();
            controller = null;
        }
        stopProcessPresentation();
        if (systemTray != null) systemTray.close();
        nativeOpenRequests.clearConsumers();
        if (runtime != null) runtime.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
