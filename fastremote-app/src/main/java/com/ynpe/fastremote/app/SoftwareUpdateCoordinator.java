// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import com.ynpe.fastremote.domain.license.LicenseSnapshot;
import com.ynpe.fastremote.domain.update.SemanticVersion;
import com.ynpe.fastremote.domain.update.SoftwareRelease;
import com.ynpe.fastremote.domain.update.SoftwareUpdateSettings;
import com.ynpe.fastremote.storage.YamlSoftwareUpdateRepository;
import com.ynpe.fastremote.ui.SoftwareUpdateDialog;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.scene.control.Dialog;
import javafx.stage.Window;
import javafx.stage.Stage;

final class SoftwareUpdateCoordinator implements AutoCloseable {
    private static final Duration CHECK_INTERVAL = Duration.ofHours(48);
    private static final Duration FAILURE_RETRY = Duration.ofHours(1);
    private final SoftwareUpdateClient client;
    private final YamlSoftwareUpdateRepository settings;
    private final Supplier<CompletionStage<LicenseSnapshot>> licenseStatus;
    private final Clock clock;
    private final ThreadPoolExecutor workers;
    private final ScheduledThreadPoolExecutor scheduler;
    private volatile ScheduledFuture<?> scheduled;
    private volatile boolean closed;
    private volatile boolean automaticChecksEnabled = true;
    private volatile Window owner;
    private PauseTransition presentationDelay;

    SoftwareUpdateCoordinator(URI serverBaseUri, YamlSoftwareUpdateRepository settings,
            Supplier<CompletionStage<LicenseSnapshot>> licenseStatus) {
        this(new SoftwareUpdateClient(serverBaseUri), settings, licenseStatus,
                Clock.systemUTC());
    }

    SoftwareUpdateCoordinator(SoftwareUpdateClient client, YamlSoftwareUpdateRepository settings,
            Supplier<CompletionStage<LicenseSnapshot>> licenseStatus, Clock clock) {
        this.client = Objects.requireNonNull(client, "client");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.licenseStatus = Objects.requireNonNull(licenseStatus, "licenseStatus");
        this.clock = Objects.requireNonNull(clock, "clock");
        workers = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(8), runnable -> {
            Thread thread = new Thread(runnable, "fastremote-software-update");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
        scheduler = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "fastremote-software-update-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.setRemoveOnCancelPolicy(true);
    }

    void start(Window owner) {
        updateOwner(Objects.requireNonNull(owner, "owner"));
        if (closed) return;
        CompletableFuture.runAsync(() -> {
            try {
                var state = settings.load();
                automaticChecksEnabled = state.automaticChecksEnabled();
                if (!automaticChecksEnabled) return;
                Duration delay = nextAutomaticCheckDelay(state, clock.instant());
                // Re-read the local snapshot immediately before any version request so an offline
                // activation completed during startup can still suppress all version traffic.
                schedule(delay, null);
            } catch (IOException error) {
                log("Unable to load software-update settings", error);
            }
        }, workers);
    }

    void updateOwner(Window owner) {
        this.owner = owner;
    }

    CompletionStage<Void> setAutomaticChecksEnabled(boolean enabled) {
        return CompletableFuture.runAsync(() -> {
            try {
                settings.setAutomaticChecksEnabled(enabled);
                automaticChecksEnabled = enabled;
                if (!enabled) cancelScheduled();
                else schedule(Duration.ZERO, null);
            } catch (IOException error) {
                throw new java.util.concurrent.CompletionException(error);
            }
        }, workers);
    }

    boolean automaticChecksEnabled() {
        return automaticChecksEnabled;
    }

    void checkManually() {
        Window requestOwner = owner;
        if (closed || requestOwner == null) return;
        Dialog<Void> checking = SoftwareUpdateDialog.showChecking(requestOwner);
        licenseStatus.get().whenComplete((snapshot, statusError) -> {
            if (statusError != null) {
                Platform.runLater(() -> {
                    checking.hide();
                    if (owner == requestOwner && requestOwner.isShowing()) {
                        SoftwareUpdateDialog.showError(requestOwner);
                    }
                });
                return;
            }
            if (offline(snapshot)) {
                Platform.runLater(() -> {
                    checking.hide();
                    if (owner == requestOwner && requestOwner.isShowing()) {
                        SoftwareUpdateDialog.showOfflineDisabled(requestOwner);
                    }
                });
                return;
            }
            checkLatest(true).whenComplete((release, error) -> Platform.runLater(() -> {
                checking.hide();
                if (error == null && automaticChecksEnabled) schedule(CHECK_INTERVAL, null);
                if (owner != requestOwner || !requestOwner.isShowing()) return;
                if (error != null) SoftwareUpdateDialog.showError(requestOwner);
                else presentManualResult(release);
            }));
        });
    }

    private synchronized void schedule(Duration delay, LicenseSnapshot knownSnapshot) {
        if (closed || !automaticChecksEnabled) return;
        cancelScheduled();
        long milliseconds = Math.max(0, delay.toMillis());
        scheduled = scheduler.schedule(() -> automaticCheck(knownSnapshot), milliseconds, TimeUnit.MILLISECONDS);
    }

    private void automaticCheck(LicenseSnapshot knownSnapshot) {
        if (closed || !automaticChecksEnabled) return;
        CompletionStage<LicenseSnapshot> status = knownSnapshot == null
                ? licenseStatus.get() : CompletableFuture.completedFuture(knownSnapshot);
        status.whenComplete((snapshot, statusError) -> {
            if (statusError != null) {
                schedule(FAILURE_RETRY, null);
            } else if (offline(snapshot)) {
                schedule(CHECK_INTERVAL, null);
            } else {
                checkLatest(false).whenComplete((release, error) -> {
                    if (!automaticChecksEnabled || closed) return;
                    if (error != null) {
                        logAutomaticCheckFailure(error);
                        schedule(FAILURE_RETRY, null);
                        return;
                    }
                    schedule(CHECK_INTERVAL, null);
                    if (release != null) Platform.runLater(() -> presentAutomaticResult(release));
                });
            }
        });
    }

    private CompletionStage<SoftwareRelease> checkLatest(boolean manual) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                SoftwareRelease release = client.latestStable();
                settings.recordSuccessfulCheck(clock.instant());
                if (release == null) return null;
                SemanticVersion current = SemanticVersion.parse(ApplicationVersion.current());
                if (SemanticVersion.parse(release.version()).compareTo(current) <= 0) return null;
                if (!manual && release.version().equals(settings.load().skippedVersion())) return null;
                return release;
            } catch (IOException error) {
                throw new java.util.concurrent.CompletionException(error);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new java.util.concurrent.CompletionException(interrupted);
            }
        }, workers);
    }

    private void presentManualResult(SoftwareRelease release) {
        if (release == null) SoftwareUpdateDialog.showNoUpdate(owner, ApplicationVersion.current());
        else presentRelease(release);
    }

    private void presentAutomaticResult(SoftwareRelease release) {
        if (owner == null || !owner.isShowing()) return;
        if (ownedWindowShowing()) {
            if (presentationDelay == null) presentationDelay = new PauseTransition(javafx.util.Duration.millis(500));
            presentationDelay.stop();
            presentationDelay.setOnFinished(event -> presentAutomaticResult(release));
            presentationDelay.play();
            return;
        }
        presentationDelay = null;
        presentRelease(release);
    }

    private boolean ownedWindowShowing() {
        return Window.getWindows().stream().anyMatch(window -> window != owner && window.isShowing()
                && ownedBy(window, owner));
    }

    private static boolean ownedBy(Window window, Window possibleOwner) {
        for (Window current = ownerOf(window); current != null; current = ownerOf(current)) {
            if (current == possibleOwner) return true;
        }
        return false;
    }

    private static Window ownerOf(Window window) {
        return window instanceof Stage stage ? stage.getOwner()
                : window instanceof javafx.stage.PopupWindow popup ? popup.getOwnerWindow() : null;
    }

    private void presentRelease(SoftwareRelease release) {
        SoftwareUpdateDialog.Choice choice = SoftwareUpdateDialog.showAvailable(
                owner, ApplicationVersion.current(), release, client.downloadPage());
        if (choice == SoftwareUpdateDialog.Choice.SKIP) {
            CompletableFuture.runAsync(() -> {
                try { settings.skipVersion(release.version()); }
                catch (IOException error) { log("Unable to save skipped software version", error); }
            }, workers);
        } else if (choice == SoftwareUpdateDialog.Choice.OPEN_WEBSITE) {
            // Only an explicit click on the link/button returns OPEN_WEBSITE. Checks never open a browser.
            CompletableFuture.runAsync(() -> {
                try {
                    if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        throw new IOException("Desktop browser is unavailable");
                    }
                    Desktop.getDesktop().browse(client.downloadPage());
                } catch (IOException | UnsupportedOperationException error) {
                    log("Unable to open download page", error);
                    Platform.runLater(() -> SoftwareUpdateDialog.showError(owner));
                }
            }, workers);
        }
    }

    static URI downloadPage() {
        // A fixed HTML page, never the executable URL supplied by the version service.
        return URI.create("https://www.ynpe.com/download/");
    }

    static boolean offline(LicenseSnapshot snapshot) {
        return snapshot != null && ("offline".equals(snapshot.kind())
                || snapshot.status() == com.ynpe.fastremote.domain.license.LicenseStatus.LICENSED_OFFLINE);
    }

    static Duration nextAutomaticCheckDelay(SoftwareUpdateSettings state, Instant now) {
        Duration elapsed = state.lastSuccessfulCheckAt()
                .map(last -> Duration.between(last, now)).orElse(CHECK_INTERVAL);
        return elapsed.isNegative() ? CHECK_INTERVAL
                : elapsed.compareTo(CHECK_INTERVAL) >= 0 ? Duration.ZERO : CHECK_INTERVAL.minus(elapsed);
    }

    private synchronized void cancelScheduled() {
        if (scheduled != null) scheduled.cancel(false);
        scheduled = null;
    }

    private static void log(String message, Throwable error) {
        System.getLogger(SoftwareUpdateCoordinator.class.getName()).log(System.Logger.Level.WARNING, message, error);
    }

    private static void logAutomaticCheckFailure(Throwable error) {
        String routineFailure = routineNetworkFailureName(error);
        if (routineFailure != null) {
            System.getLogger(SoftwareUpdateCoordinator.class.getName()).log(
                    System.Logger.Level.WARNING,
                    "Automatic software-update check unavailable (" + routineFailure
                            + "); retrying in 1 hour");
            return;
        }
        log("Automatic software-update check failed", error);
    }

    static String routineNetworkFailureName(Throwable error) {
        java.util.Set<Throwable> visited = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<>());
        for (Throwable current = error; current != null && visited.add(current); current = current.getCause()) {
            if (current instanceof UnknownHostException
                    || current instanceof HttpTimeoutException
                    || current instanceof SocketTimeoutException
                    || current instanceof SocketException) {
                return current.getClass().getSimpleName();
            }
        }
        return null;
    }

    @Override public void close() {
        closed = true;
        if (presentationDelay != null) presentationDelay.stop();
        cancelScheduled();
        scheduler.shutdownNow();
        workers.shutdownNow();
    }
}
