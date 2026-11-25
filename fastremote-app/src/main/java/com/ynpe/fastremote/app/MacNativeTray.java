// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import com.sun.jna.Callback;
import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;

/** Native AppKit menu-bar integration. All AppKit calls are confined to the JavaFX/Cocoa UI thread. */
public final class MacNativeTray implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(MacNativeTray.class.getName());
    private static final double NS_VARIABLE_STATUS_ITEM_LENGTH = -1.0;
    private static final long SHOW_TAG = 1;
    private static final long QUICK_CONNECT_TAG = 2;
    private static final long LOCAL_TERMINAL_TAG = 3;
    private static final long EXIT_TAG = 4;

    private final String showText;
    private final String quickConnectText;
    private final String localTerminalText;
    private final String exitText;
    private final Runnable showWindow;
    private final Runnable showQuickConnect;
    private final Runnable openLocalTerminal;
    private final Runnable exitApplication;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ObjectiveCAction actionCallback = this::handleAction;

    private Pointer statusBar;
    private Pointer statusItem;
    private Pointer actionTarget;
    private Path extractedIcon;

    private MacNativeTray(
            String showText,
            String quickConnectText,
            String localTerminalText,
            String exitText,
            Runnable showWindow,
            Runnable showQuickConnect,
            Runnable openLocalTerminal,
            Runnable exitApplication) {
        this.showText = Objects.requireNonNull(showText, "showText");
        this.quickConnectText = Objects.requireNonNull(quickConnectText, "quickConnectText");
        this.localTerminalText = Objects.requireNonNull(localTerminalText, "localTerminalText");
        this.exitText = Objects.requireNonNull(exitText, "exitText");
        this.showWindow = Objects.requireNonNull(showWindow, "showWindow");
        this.showQuickConnect = Objects.requireNonNull(showQuickConnect, "showQuickConnect");
        this.openLocalTerminal = Objects.requireNonNull(openLocalTerminal, "openLocalTerminal");
        this.exitApplication = Objects.requireNonNull(exitApplication, "exitApplication");
    }

    static MacNativeTray create(
            String showText,
            String quickConnectText,
            String localTerminalText,
            String exitText,
            Runnable showWindow,
            Runnable showQuickConnect,
            Runnable openLocalTerminal,
            Runnable exitApplication) {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("The macOS menu-bar item must be created on the JavaFX thread");
        }
        Cocoa.ensureLoaded();
        return new MacNativeTray(showText, quickConnectText, localTerminalText, exitText,
                showWindow, showQuickConnect, openLocalTerminal, exitApplication);
    }

    void setEnabled(boolean enabled) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> setEnabled(enabled));
            return;
        }
        if (closed.get()) return;
        if (enabled && statusItem == null) install();
        if (!enabled && statusItem != null) uninstall();
    }

    boolean isAvailable() {
        return statusItem != null && !closed.get();
    }

    private void install() {
        try {
            Pointer nsStatusBar = Cocoa.classNamed("NSStatusBar");
            statusBar = Cocoa.sendPointer(nsStatusBar, "systemStatusBar");
            statusItem = Cocoa.sendPointer(statusBar, "statusItemWithLength:", NS_VARIABLE_STATUS_ITEM_LENGTH);
            if (statusItem == null) throw new IllegalStateException("NSStatusBar returned no status item");
            // Keep an explicit application-owned reference across autorelease-pool drains;
            // balance it only after removeStatusItem: completes during shutdown.
            Cocoa.sendVoid(statusItem, "retain");

            actionTarget = createActionTarget();
            Pointer menu = createMenu();
            Cocoa.sendVoid(statusItem, "setMenu:", menu);
            Cocoa.release(menu);

            Pointer button = Cocoa.sendPointer(statusItem, "button");
            if (button != null) configureButton(button);
        } catch (IOException | RuntimeException | LinkageError failure) {
            uninstall();
            throw new IllegalStateException("Cannot install the native macOS menu-bar item", failure);
        }
    }

    private Pointer createActionTarget() {
        String className = "FastRemoteStatusItemTarget_" + ProcessHandle.current().pid();
        Pointer targetClass = Cocoa.runtime.objc_getClass(className);
        if (targetClass == null) {
            Pointer nsObject = Cocoa.classNamed("NSObject");
            targetClass = Cocoa.runtime.objc_allocateClassPair(nsObject, className, new NativeLong(0));
            if (targetClass == null) {
                throw new IllegalStateException("Cannot allocate the AppKit action target class");
            }
            Pointer action = Cocoa.selector("fastRemoteStatusItemAction:");
            if (!Cocoa.runtime.class_addMethod(targetClass, action, actionCallback, "v@:@")) {
                Cocoa.runtime.objc_disposeClassPair(targetClass);
                throw new IllegalStateException("Cannot register the AppKit action callback");
            }
            Cocoa.runtime.objc_registerClassPair(targetClass);
        }
        return Cocoa.sendPointer(targetClass, "new");
    }

    private Pointer createMenu() {
        Pointer menu = Cocoa.sendPointer(Cocoa.classNamed("NSMenu"), "alloc");
        Pointer title = Cocoa.string("FastRemote");
        try {
            menu = Cocoa.sendPointer(menu, "initWithTitle:", title);
        } finally {
            Cocoa.release(title);
        }
        Cocoa.sendVoid(menu, "setAutoenablesItems:", (byte) 0);
        addMenuItem(menu, showText, SHOW_TAG);
        addMenuItem(menu, quickConnectText, QUICK_CONNECT_TAG);
        addMenuItem(menu, localTerminalText, LOCAL_TERMINAL_TAG);
        Pointer separator = Cocoa.sendPointer(Cocoa.classNamed("NSMenuItem"), "separatorItem");
        Cocoa.sendVoid(menu, "addItem:", separator);
        addMenuItem(menu, exitText, EXIT_TAG);
        return menu;
    }

    private void addMenuItem(Pointer menu, String title, long tag) {
        Pointer item = Cocoa.sendPointer(Cocoa.classNamed("NSMenuItem"), "alloc");
        Pointer titleValue = Cocoa.string(title);
        Pointer empty = Cocoa.string("");
        try {
            item = Cocoa.sendPointer(item, "initWithTitle:action:keyEquivalent:",
                    titleValue, Cocoa.selector("fastRemoteStatusItemAction:"), empty);
            Cocoa.sendVoid(item, "setTarget:", actionTarget);
            Cocoa.sendVoid(item, "setTag:", new NativeLong(tag));
            Cocoa.sendVoid(menu, "addItem:", item);
        } finally {
            Cocoa.release(titleValue);
            Cocoa.release(empty);
            Cocoa.release(item);
        }
    }

    private void configureButton(Pointer button) throws IOException {
        extractedIcon = extractIcon();
        Pointer path = Cocoa.string(extractedIcon.toString());
        Pointer image = Cocoa.sendPointer(Cocoa.classNamed("NSImage"), "alloc");
        try {
            image = Cocoa.sendPointer(image, "initWithContentsOfFile:", path);
            if (image == null) throw new IOException("AppKit could not load the menu-bar icon");
            Cocoa.sendVoid(image, "setSize:", new NSSize(18.0, 18.0));
            Cocoa.sendVoid(image, "setTemplate:", (byte) 1);
            Cocoa.sendVoid(button, "setImage:", image);
            Cocoa.sendVoid(button, "setImageScaling:", new NativeLong(0));
            Pointer label = Cocoa.string("FastRemote");
            try {
                Cocoa.sendVoid(button, "setToolTip:", label);
                Cocoa.sendVoid(button, "setAccessibilityTitle:", label);
            } finally {
                Cocoa.release(label);
            }
        } finally {
            Cocoa.release(path);
            Cocoa.release(image);
        }
    }

    private void handleAction(Pointer self, Pointer command, Pointer sender) {
        try {
            long tag = Cocoa.sendLong(sender, "tag");
            Runnable action = switch (menuCommand(tag)) {
                case SHOW_WINDOW -> showWindow;
                case QUICK_CONNECT -> showQuickConnect;
                case LOCAL_TERMINAL -> openLocalTerminal;
                case EXIT -> exitApplication;
                case NONE -> null;
            };
            if (action != null) Platform.runLater(action);
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.log(System.Logger.Level.WARNING, "Native macOS menu action failed", failure);
        }
    }

    static MenuCommand menuCommand(long tag) {
        if (tag == SHOW_TAG) return MenuCommand.SHOW_WINDOW;
        if (tag == QUICK_CONNECT_TAG) return MenuCommand.QUICK_CONNECT;
        if (tag == LOCAL_TERMINAL_TAG) return MenuCommand.LOCAL_TERMINAL;
        if (tag == EXIT_TAG) return MenuCommand.EXIT;
        return MenuCommand.NONE;
    }

    private void uninstall() {
        Pointer item = statusItem;
        Pointer bar = statusBar;
        statusItem = null;
        statusBar = null;
        if (item != null) {
            try {
                Cocoa.sendVoid(item, "setMenu:", Pointer.NULL);
                if (bar != null) Cocoa.sendVoid(bar, "removeStatusItem:", item);
            } finally {
                Cocoa.release(item);
            }
        }
        if (actionTarget != null) {
            Cocoa.release(actionTarget);
            actionTarget = null;
        }
        if (extractedIcon != null) {
            try {
                Files.deleteIfExists(extractedIcon);
            } catch (IOException failure) {
                LOGGER.log(System.Logger.Level.DEBUG, "Cannot remove temporary macOS menu-bar icon", failure);
            }
            extractedIcon = null;
        }
    }

    private static Path extractIcon() throws IOException {
        Path target = Files.createTempFile("fastremote-menu-bar-", ".png");
        try (InputStream input = MacNativeTray.class.getResourceAsStream("icons/logo-menubar-template.png")) {
            if (input == null) throw new IOException("Missing menu-bar icon resource");
            Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            Files.deleteIfExists(target);
            throw failure;
        }
        return target;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (Platform.isFxApplicationThread()) uninstall();
        else Platform.runLater(this::uninstall);
    }

    public interface ObjectiveCAction extends Callback {
        void invoke(Pointer self, Pointer command, Pointer sender);
    }

    enum MenuCommand { NONE, SHOW_WINDOW, QUICK_CONNECT, LOCAL_TERMINAL, EXIT }

    @Structure.FieldOrder({"width", "height"})
    public static final class NSSize extends Structure implements Structure.ByValue {
        public double width;
        public double height;

        public NSSize() { }

        public NSSize(double width, double height) {
            this.width = width;
            this.height = height;
        }
    }

    private interface ObjectiveCRuntime extends com.sun.jna.Library {
        Pointer objc_getClass(String name);
        Pointer sel_registerName(String name);
        Pointer objc_allocateClassPair(Pointer superclass, String name, NativeLong extraBytes);
        boolean class_addMethod(Pointer targetClass, Pointer selector, Callback implementation, String types);
        void objc_registerClassPair(Pointer targetClass);
        void objc_disposeClassPair(Pointer targetClass);
    }

    private static final class Cocoa {
        private static final ObjectiveCRuntime runtime = Native.load("objc", ObjectiveCRuntime.class);
        private static final NativeLibrary objectiveC = NativeLibrary.getInstance("objc");
        private static final Function messageSend = objectiveC.getFunction("objc_msgSend");

        private Cocoa() { }

        static void ensureLoaded() {
            NativeLibrary.getInstance("AppKit");
        }

        static Pointer classNamed(String name) {
            Pointer value = runtime.objc_getClass(name);
            if (value == null) throw new IllegalStateException("Missing AppKit class " + name);
            return value;
        }

        static Pointer selector(String name) {
            return runtime.sel_registerName(name);
        }

        static Pointer sendPointer(Pointer receiver, String selector, Object... arguments) {
            Object[] values = prepend(receiver, selector(selector), arguments);
            Pointer result = messageSend.invokePointer(values);
            return result == null ? Pointer.NULL : result;
        }

        static void sendVoid(Pointer receiver, String selector, Object... arguments) {
            messageSend.invokeVoid(prepend(receiver, selector(selector), arguments));
        }

        static long sendLong(Pointer receiver, String selector, Object... arguments) {
            Object result = messageSend.invoke(NativeLong.class, prepend(receiver, selector(selector), arguments));
            return ((NativeLong) result).longValue();
        }

        static Pointer string(String text) {
            byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
            Memory buffer = new Memory(utf8.length + 1L);
            buffer.write(0, utf8, 0, utf8.length);
            buffer.setByte(utf8.length, (byte) 0);
            Pointer allocated = sendPointer(classNamed("NSString"), "alloc");
            return sendPointer(allocated, "initWithUTF8String:", buffer);
        }

        static void release(Pointer value) {
            if (value != null) sendVoid(value, "release");
        }

        private static Object[] prepend(Pointer receiver, Pointer selector, Object[] arguments) {
            Object[] values = new Object[arguments.length + 2];
            values[0] = receiver;
            values[1] = selector;
            System.arraycopy(arguments, 0, values, 2, arguments.length);
            return values;
        }
    }
}
