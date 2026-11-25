// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import java.awt.Desktop;
import java.awt.desktop.OpenFilesEvent;
import java.awt.desktop.AppReopenedListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/** Converts platform open-file events (not command-line arguments) into the common launch model. */
final class NativeOpenRequestBridge {
    private final ConcurrentLinkedQueue<LaunchRequest> pending = new ConcurrentLinkedQueue<>();
    private final Object dispatchMonitor = new Object();
    private Consumer<LaunchRequest> consumer;
    private volatile Runnable reopenConsumer = () -> { };
    private volatile Runnable quitConsumer;
    private AppReopenedListener appReopenedListener;

    void installIfSupported() {
        // Initializing AWT before JavaFX on macOS makes Glass enter embedded mode, in which it does
        // not become NSApplication's delegate. Glass is installed from start() on that platform.
        if (isMacOs()) return;
        try {
            if (!Desktop.isDesktopSupported()) return;
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) return;
            desktop.setOpenFileHandler(this::handle);
        } catch (SecurityException | UnsupportedOperationException ignored) {
            // Command-line and single-instance forwarding remain available on platforms without it.
        }
    }

    /**
     * JavaFX 21 owns the macOS NSApplication delegate, so later Finder events do not reach the
     * AWT Desktop handler. Wrap Glass' existing handler without replacing any lifecycle behavior.
     */
    void installJavaFxHandlerIfNeeded() {
        if (!isMacOs()) return;
        com.sun.glass.ui.Application application = com.sun.glass.ui.Application.GetApplication();
        if (application == null) return;
        com.sun.glass.ui.Application.EventHandler delegate = application.getEventHandler();
        if (delegate instanceof ForwardingGlassEventHandler) return;
        application.setEventHandler(new ForwardingGlassEventHandler(
                delegate, this::acceptFiles, this::notifyReopen, this::notifyQuit));
    }

    private static boolean isMacOs() {
        return System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT).contains("mac");
    }

    LaunchRequest drainInitialRequest(LaunchRequest commandLineRequest) {
        Objects.requireNonNull(commandLineRequest, "commandLineRequest");
        LaunchRequest result = commandLineRequest;
        synchronized (dispatchMonitor) {
            LaunchRequest request;
            while ((request = pending.poll()) != null) result = result.merge(request);
        }
        return result;
    }

    void installConsumer(Consumer<LaunchRequest> nextConsumer) {
        Objects.requireNonNull(nextConsumer, "nextConsumer");
        List<LaunchRequest> queued = new ArrayList<>();
        synchronized (dispatchMonitor) {
            if (consumer != null) throw new IllegalStateException("Open-file consumer is already installed");
            consumer = nextConsumer;
            LaunchRequest request;
            while ((request = pending.poll()) != null) queued.add(request);
        }
        queued.forEach(nextConsumer);
    }

    void installReopenConsumer(Runnable nextConsumer) {
        reopenConsumer = Objects.requireNonNull(nextConsumer, "nextConsumer");
        if (!isMacOs()) return;
        try {
            if (!Desktop.isDesktopSupported()) return;
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.APP_EVENT_REOPENED)) return;
            appReopenedListener = event -> notifyReopen();
            desktop.addAppEventListener(appReopenedListener);
        } catch (SecurityException | UnsupportedOperationException ignored) {
            // Glass activation/unhide callbacks and single-instance forwarding remain available.
        }
    }

    void installQuitConsumer(Runnable nextConsumer) {
        quitConsumer = Objects.requireNonNull(nextConsumer, "nextConsumer");
    }

    void clearConsumers() {
        reopenConsumer = () -> { };
        quitConsumer = null;
        synchronized (dispatchMonitor) {
            consumer = null;
            pending.clear();
        }
        if (appReopenedListener != null && Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().removeAppEventListener(appReopenedListener);
            } catch (SecurityException | UnsupportedOperationException ignored) { }
        }
        appReopenedListener = null;
    }

    private void notifyReopen() {
        reopenConsumer.run();
    }

    boolean notifyQuit() {
        Runnable current = quitConsumer;
        if (current == null) return false;
        current.run();
        return true;
    }

    private void handle(OpenFilesEvent event) {
        acceptFiles(event.getFiles());
    }

    void acceptFiles(List<File> files) {
        LaunchRequest request = requestForFiles(files);
        if (!request.opensWorkspace()) return;
        Consumer<LaunchRequest> current;
        synchronized (dispatchMonitor) {
            current = consumer;
            if (current == null) {
                pending.add(request);
                return;
            }
        }
        current.accept(request);
    }

    static LaunchRequest requestForFiles(List<File> files) {
        Objects.requireNonNull(files, "files");
        List<String> arguments = new ArrayList<>(files.size());
        files.stream().filter(Objects::nonNull).map(File::getPath).forEach(arguments::add);
        return LaunchRequest.parse(arguments);
    }

    static final class ForwardingGlassEventHandler
            extends com.sun.glass.ui.Application.EventHandler {
        private final com.sun.glass.ui.Application.EventHandler delegate;
        private final Consumer<List<File>> openFilesConsumer;
        private final Runnable reopenConsumer;
        private final BooleanSupplier quitConsumer;

        ForwardingGlassEventHandler(
                com.sun.glass.ui.Application.EventHandler delegate,
                Consumer<List<File>> openFilesConsumer,
                Runnable reopenConsumer,
                BooleanSupplier quitConsumer) {
            this.delegate = delegate;
            this.openFilesConsumer = openFilesConsumer;
            this.reopenConsumer = reopenConsumer;
            this.quitConsumer = quitConsumer;
        }

        @Override public void handleWillFinishLaunchingAction(
                com.sun.glass.ui.Application application, long time) {
            if (delegate != null) delegate.handleWillFinishLaunchingAction(application, time);
        }

        @Override public void handleDidFinishLaunchingAction(
                com.sun.glass.ui.Application application, long time) {
            if (delegate != null) delegate.handleDidFinishLaunchingAction(application, time);
        }

        @Override public void handleWillBecomeActiveAction(
                com.sun.glass.ui.Application application, long time) {
            if (delegate != null) delegate.handleWillBecomeActiveAction(application, time);
        }

        @Override public void handleDidBecomeActiveAction(
                com.sun.glass.ui.Application application, long time) {
            if (delegate != null) delegate.handleDidBecomeActiveAction(application, time);
            // A Dock click always activates the process, but some macOS/JavaFX combinations omit
            // the paired did-unhide callback after the last Stage was closed. The application
            // consumer is state-aware, so an activation while a window is already open only
            // raises that window and cannot create another controller.
            reopenConsumer.run();
        }

        @Override public void handleWillResignActiveAction(
                com.sun.glass.ui.Application application, long time) {
            if (delegate != null) delegate.handleWillResignActiveAction(application, time);
        }

        @Override public void handleDidResignActiveAction(
                com.sun.glass.ui.Application application, long time) {
            if (delegate != null) delegate.handleDidResignActiveAction(application, time);
        }

        @Override public void handleDidReceiveMemoryWarning(
                com.sun.glass.ui.Application application, long time) {
            if (delegate != null) delegate.handleDidReceiveMemoryWarning(application, time);
        }

        @Override public void handleWillHideAction(
                com.sun.glass.ui.Application application, long time) {
            if (delegate != null) delegate.handleWillHideAction(application, time);
        }

        @Override public void handleDidHideAction(
                com.sun.glass.ui.Application application, long time) {
            if (delegate != null) delegate.handleDidHideAction(application, time);
        }

        @Override public void handleWillUnhideAction(
                com.sun.glass.ui.Application application, long time) {
            if (delegate != null) delegate.handleWillUnhideAction(application, time);
        }

        @Override public void handleDidUnhideAction(
                com.sun.glass.ui.Application application, long time) {
            if (delegate != null) delegate.handleDidUnhideAction(application, time);
            reopenConsumer.run();
        }

        @Override public void handleOpenFilesAction(
                com.sun.glass.ui.Application application, long time, String[] files) {
            if (files != null) openFilesConsumer.accept(
                    java.util.Arrays.stream(files).map(File::new).toList());
            if (delegate != null) delegate.handleOpenFilesAction(application, time, files);
        }

        @Override public void handleQuitAction(
                com.sun.glass.ui.Application application, long time) {
            if (!quitConsumer.getAsBoolean() && delegate != null) {
                delegate.handleQuitAction(application, time);
            }
        }

        @Override public boolean handleThemeChanged(String themeName) {
            return delegate != null && delegate.handleThemeChanged(themeName);
        }
    }
}
