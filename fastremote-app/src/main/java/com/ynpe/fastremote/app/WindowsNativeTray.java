// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HICON;
import com.sun.jna.platform.win32.WinDef.HINSTANCE;
import com.sun.jna.platform.win32.WinDef.HMENU;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinDef.UINT_PTR;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinUser.MSG;
import com.sun.jna.platform.win32.WinUser.WNDCLASSEX;
import com.sun.jna.platform.win32.WinUser.WindowProc;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;

/** Windows 10/11 notification-area icon and menu implemented directly with Win32. */
public final class WindowsNativeTray implements AutoCloseable {
    static final int WM_CONTEXTMENU = 0x007B;
    static final int WM_RBUTTONUP = 0x0205;
    static final int NIN_SELECT = 0x0400;
    static final int NIN_KEYSELECT = 0x0401;
    private static final int WM_CLOSE = 0x0010;
    private static final int WM_DESTROY = 0x0002;
    private static final int WM_NULL = 0x0000;
    private static final int TRAY_CALLBACK_MESSAGE = 0x8001;
    private static final int TRAY_ICON_ID = 1;
    private static final int MENU_SHOW = 1001;
    private static final int MENU_QUICK_CONNECT = 1002;
    private static final int MENU_LOCAL_TERMINAL = 1003;
    private static final int MENU_EXIT = 1004;
    private static final int NIM_ADD = 0x00000000;
    private static final int NIM_DELETE = 0x00000002;
    private static final int NIM_SETVERSION = 0x00000004;
    private static final int NOTIFYICON_VERSION_4 = 4;
    private static final int NIF_MESSAGE = 0x00000001;
    private static final int NIF_ICON = 0x00000002;
    private static final int NIF_TIP = 0x00000004;
    private static final int NIF_SHOWTIP = 0x00000080;
    private static final int IMAGE_ICON = 1;
    private static final int LR_LOADFROMFILE = 0x00000010;
    private static final int SM_CXSMICON = 49;
    private static final int SM_CYSMICON = 50;
    private static final int MF_STRING = 0x00000000;
    private static final int MF_SEPARATOR = 0x00000800;
    private static final int TPM_RIGHTBUTTON = 0x0002;
    private static final int TPM_RETURNCMD = 0x0100;
    private static final int WS_EX_TOOLWINDOW = 0x00000080;
    private static final int WS_POPUP = 0x80000000;
    private static final System.Logger LOGGER =
            System.getLogger(WindowsNativeTray.class.getName());

    private final String showLabel;
    private final String quickConnectLabel;
    private final String localTerminalLabel;
    private final String exitLabel;
    private final Runnable showWindow;
    private final Runnable showQuickConnect;
    private final Runnable openLocalTerminal;
    private final Runnable exitApplication;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final CompletableFuture<Boolean> available = new CompletableFuture<>();
    private final String windowClassName =
            "FastRemoteNativeTrayWindow." + ProcessHandle.current().pid();

    private volatile User32 user32;
    private volatile NativeUser32 nativeUser32;
    private volatile NativeShell32 shell32;
    private volatile HWND window;
    private volatile HMENU menu;
    private volatile HICON icon;
    private volatile NotifyIconData notifyIconData;
    private volatile int taskbarCreatedMessage;
    private volatile WindowProc windowProc;
    private volatile HINSTANCE module;
    private volatile Path iconDirectory;

    static WindowsNativeTray create(
            String showLabel,
            String quickConnectLabel,
            String localTerminalLabel,
            String exitLabel,
            Runnable showWindow,
            Runnable showQuickConnect,
            Runnable openLocalTerminal,
            Runnable exitApplication) {
        WindowsNativeTray tray = new WindowsNativeTray(
                showLabel, quickConnectLabel, localTerminalLabel, exitLabel,
                showWindow, showQuickConnect, openLocalTerminal, exitApplication);
        tray.start();
        return tray;
    }

    private WindowsNativeTray(
            String showLabel,
            String quickConnectLabel,
            String localTerminalLabel,
            String exitLabel,
            Runnable showWindow,
            Runnable showQuickConnect,
            Runnable openLocalTerminal,
            Runnable exitApplication) {
        this.showLabel = Objects.requireNonNull(showLabel, "showLabel");
        this.quickConnectLabel = Objects.requireNonNull(quickConnectLabel, "quickConnectLabel");
        this.localTerminalLabel = Objects.requireNonNull(localTerminalLabel, "localTerminalLabel");
        this.exitLabel = Objects.requireNonNull(exitLabel, "exitLabel");
        this.showWindow = Objects.requireNonNull(showWindow, "showWindow");
        this.showQuickConnect = Objects.requireNonNull(showQuickConnect, "showQuickConnect");
        this.openLocalTerminal = Objects.requireNonNull(openLocalTerminal, "openLocalTerminal");
        this.exitApplication = Objects.requireNonNull(exitApplication, "exitApplication");
    }

    private void start() {
        Thread.ofPlatform()
                .daemon(true)
                .name("fastremote-windows-tray")
                .start(this::runMessageLoop);
    }

    CompletionStage<Boolean> availability() {
        return available;
    }

    boolean isAvailable() {
        return Boolean.TRUE.equals(available.getNow(false)) && window != null;
    }

    private void runMessageLoop() {
        try {
            user32 = User32.INSTANCE;
            nativeUser32 = Native.load("user32", NativeUser32.class, W32APIOptions.UNICODE_OPTIONS);
            shell32 = Native.load("shell32", NativeShell32.class, W32APIOptions.UNICODE_OPTIONS);
            if (!initializeNativeTray() || closed.get()) {
                available.complete(false);
                return;
            }
            available.complete(true);

            MSG message = new MSG();
            int result;
            while ((result = user32.GetMessage(message, null, 0, 0)) > 0) {
                user32.TranslateMessage(message);
                user32.DispatchMessage(message);
            }
            if (result < 0) {
                throw new IllegalStateException("GetMessage failed with error "
                        + Kernel32.INSTANCE.GetLastError());
            }
        } catch (RuntimeException | LinkageError | IOException failure) {
            available.complete(false);
            LOGGER.log(System.Logger.Level.WARNING,
                    "Unable to initialize the native Windows notification-area icon", failure);
        } finally {
            cleanupNativeResources();
        }
    }

    private boolean initializeNativeTray() throws IOException {
        module = Kernel32.INSTANCE.GetModuleHandle(null);
        taskbarCreatedMessage = user32.RegisterWindowMessage("TaskbarCreated");
        windowProc = this::handleWindowMessage;

        WNDCLASSEX windowClass = new WNDCLASSEX();
        windowClass.cbSize = windowClass.size();
        windowClass.lpfnWndProc = windowProc;
        windowClass.hInstance = module;
        windowClass.lpszClassName = windowClassName;
        if (user32.RegisterClassEx(windowClass).intValue() == 0) {
            throw new IllegalStateException("RegisterClassEx failed with error "
                    + Kernel32.INSTANCE.GetLastError());
        }

        window = user32.CreateWindowEx(
                WS_EX_TOOLWINDOW,
                windowClassName,
                "FastRemote notification area",
                WS_POPUP,
                0, 0, 0, 0,
                null, null, module, null);
        if (window == null) {
            throw new IllegalStateException("CreateWindowEx failed with error "
                    + Kernel32.INSTANCE.GetLastError());
        }

        menu = nativeUser32.CreatePopupMenu();
        if (menu == null
                || !nativeUser32.AppendMenuW(menu, MF_STRING, new UINT_PTR(MENU_SHOW), new WString(showLabel))
                || !nativeUser32.AppendMenuW(menu, MF_STRING, new UINT_PTR(MENU_QUICK_CONNECT), new WString(quickConnectLabel))
                || !nativeUser32.AppendMenuW(menu, MF_STRING, new UINT_PTR(MENU_LOCAL_TERMINAL), new WString(localTerminalLabel))
                || !nativeUser32.AppendMenuW(menu, MF_SEPARATOR, new UINT_PTR(0), null)
                || !nativeUser32.AppendMenuW(menu, MF_STRING, new UINT_PTR(MENU_EXIT), new WString(exitLabel))) {
            throw new IllegalStateException("Unable to create the native Windows tray menu; error "
                    + Kernel32.INSTANCE.GetLastError());
        }

        icon = loadSmallIcon();
        notifyIconData = new NotifyIconData();
        notifyIconData.cbSize = notifyIconData.size();
        notifyIconData.hWnd = window;
        notifyIconData.uID = TRAY_ICON_ID;
        notifyIconData.uFlags = NIF_MESSAGE | NIF_ICON | NIF_TIP | NIF_SHOWTIP;
        notifyIconData.uCallbackMessage = TRAY_CALLBACK_MESSAGE;
        notifyIconData.hIcon = icon;
        copyWideString("FastRemote", notifyIconData.szTip);
        notifyIconData.write();
        if (!shell32.Shell_NotifyIconW(NIM_ADD, notifyIconData)) {
            throw new IllegalStateException("Shell_NotifyIconW(NIM_ADD) failed with error "
                    + Kernel32.INSTANCE.GetLastError());
        }
        setNotificationVersion();
        LOGGER.log(System.Logger.Level.INFO,
                "Windows notification-area icon enabled through Shell_NotifyIconW");
        return true;
    }

    private HICON loadSmallIcon() throws IOException {
        iconDirectory = Files.createTempDirectory("fastremote-windows-tray-");
        Path iconFile = iconDirectory.resolve("fastremote-tray.ico");
        try (InputStream input = WindowsNativeTray.class.getResourceAsStream(
                "/com/ynpe/fastremote/app/icons/logo.ico")) {
            if (input == null) throw new IOException("Windows tray icon resource is missing");
            Files.copy(input, iconFile);
        }
        int systemDpi = nativeUser32.GetDpiForSystem();
        int width = Math.max(1, nativeUser32.GetSystemMetricsForDpi(SM_CXSMICON, systemDpi));
        int height = Math.max(1, nativeUser32.GetSystemMetricsForDpi(SM_CYSMICON, systemDpi));
        HANDLE loaded = user32.LoadImage(
                null, iconFile.toString(), IMAGE_ICON, width, height, LR_LOADFROMFILE);
        if (loaded == null) {
            throw new IllegalStateException("LoadImageW failed with error "
                    + Kernel32.INSTANCE.GetLastError());
        }
        return new HICON(loaded);
    }

    private LRESULT handleWindowMessage(
            HWND handle,
            int message,
            WPARAM wParam,
            LPARAM lParam) {
        if (message == taskbarCreatedMessage) {
            if (!closed.get() && notifyIconData != null) {
                notifyIconData.write();
                if (shell32.Shell_NotifyIconW(NIM_ADD, notifyIconData)) setNotificationVersion();
            }
            return new LRESULT(0);
        }
        if (message == TRAY_CALLBACK_MESSAGE) {
            NativeAction action = nativeAction(lowWord(lParam.longValue()));
            if (action == NativeAction.SHOW_WINDOW) {
                Platform.runLater(showWindow);
            } else if (action == NativeAction.SHOW_MENU) {
                showNativeMenu(handle);
            }
            return new LRESULT(0);
        }
        if (message == WM_CLOSE) {
            removeNotificationIcon();
            user32.DestroyWindow(handle);
            return new LRESULT(0);
        }
        if (message == WM_DESTROY) {
            user32.PostQuitMessage(0);
            return new LRESULT(0);
        }
        return user32.DefWindowProc(handle, message, wParam, lParam);
    }

    private void showNativeMenu(HWND owner) {
        POINT point = new POINT();
        if (!user32.GetCursorPos(point)) return;
        user32.SetForegroundWindow(owner);
        int selection = nativeUser32.TrackPopupMenu(
                menu,
                TPM_RIGHTBUTTON | TPM_RETURNCMD,
                point.x,
                point.y,
                0,
                owner,
                null);
        user32.PostMessage(owner, WM_NULL, new WPARAM(0), new LPARAM(0));
        Runnable action = menuAction(selection);
        if (action != null) Platform.runLater(action);
    }

    private void setNotificationVersion() {
        notifyIconData.uVersion = NOTIFYICON_VERSION_4;
        notifyIconData.write();
        if (!shell32.Shell_NotifyIconW(NIM_SETVERSION, notifyIconData)) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Shell_NotifyIconW(NIM_SETVERSION) rejected version 4");
        }
    }

    private void removeNotificationIcon() {
        NotifyIconData data = notifyIconData;
        notifyIconData = null;
        if (data == null || shell32 == null) return;
        data.write();
        shell32.Shell_NotifyIconW(NIM_DELETE, data);
    }

    private void cleanupNativeResources() {
        removeNotificationIcon();
        HMENU currentMenu = menu;
        menu = null;
        if (currentMenu != null && nativeUser32 != null) nativeUser32.DestroyMenu(currentMenu);
        HICON currentIcon = icon;
        icon = null;
        if (currentIcon != null && user32 != null) user32.DestroyIcon(currentIcon);
        HWND currentWindow = window;
        window = null;
        if (currentWindow != null && user32 != null && user32.IsWindow(currentWindow)) {
            user32.DestroyWindow(currentWindow);
        }
        if (module != null && user32 != null) user32.UnregisterClass(windowClassName, module);
        Path directory = iconDirectory;
        iconDirectory = null;
        if (directory != null) {
            try {
                Files.deleteIfExists(directory.resolve("fastremote-tray.ico"));
                Files.deleteIfExists(directory);
            } catch (IOException failure) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Unable to remove the temporary Windows tray icon", failure);
            }
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        HWND currentWindow = window;
        if (currentWindow != null && user32 != null) {
            user32.PostMessage(currentWindow, WM_CLOSE, new WPARAM(0), new LPARAM(0));
        }
        available.complete(false);
    }

    static NativeAction nativeAction(int event) {
        return switch (event) {
            case NIN_SELECT, NIN_KEYSELECT -> NativeAction.SHOW_WINDOW;
            case WM_CONTEXTMENU, WM_RBUTTONUP -> NativeAction.SHOW_MENU;
            default -> NativeAction.NONE;
        };
    }

    private static int lowWord(long value) {
        return (int) value & 0xffff;
    }

    private Runnable menuAction(int selection) {
        return switch (menuCommand(selection)) {
            case SHOW_WINDOW -> showWindow;
            case QUICK_CONNECT -> showQuickConnect;
            case LOCAL_TERMINAL -> openLocalTerminal;
            case EXIT -> exitApplication;
            case NONE -> null;
        };
    }

    static MenuCommand menuCommand(int selection) {
        return switch (selection) {
            case MENU_SHOW -> MenuCommand.SHOW_WINDOW;
            case MENU_QUICK_CONNECT -> MenuCommand.QUICK_CONNECT;
            case MENU_LOCAL_TERMINAL -> MenuCommand.LOCAL_TERMINAL;
            case MENU_EXIT -> MenuCommand.EXIT;
            default -> MenuCommand.NONE;
        };
    }

    private static void copyWideString(String value, char[] target) {
        int length = Math.min(value.length(), target.length - 1);
        value.getChars(0, length, target, 0);
        target[length] = '\0';
    }

    enum NativeAction { NONE, SHOW_WINDOW, SHOW_MENU }

    enum MenuCommand { NONE, SHOW_WINDOW, QUICK_CONNECT, LOCAL_TERMINAL, EXIT }

    @Structure.FieldOrder({
            "cbSize", "hWnd", "uID", "uFlags", "uCallbackMessage", "hIcon", "szTip",
            "dwState", "dwStateMask", "szInfo", "uVersion", "szInfoTitle",
            "dwInfoFlags", "guidItem", "hBalloonIcon"
    })
    public static final class NotifyIconData extends Structure {
        public int cbSize;
        public HWND hWnd;
        public int uID;
        public int uFlags;
        public int uCallbackMessage;
        public HICON hIcon;
        public char[] szTip = new char[128];
        public int dwState;
        public int dwStateMask;
        public char[] szInfo = new char[256];
        public int uVersion;
        public char[] szInfoTitle = new char[64];
        public int dwInfoFlags;
        public GUID guidItem = new GUID();
        public HICON hBalloonIcon;
    }

    private interface NativeShell32 extends StdCallLibrary {
        boolean Shell_NotifyIconW(int message, NotifyIconData data);
    }

    private interface NativeUser32 extends StdCallLibrary {
        HMENU CreatePopupMenu();

        boolean AppendMenuW(HMENU menu, int flags, UINT_PTR itemId, WString text);

        int TrackPopupMenu(
                HMENU menu,
                int flags,
                int x,
                int y,
                int reserved,
                HWND owner,
                Structure excludedRectangle);

        boolean DestroyMenu(HMENU menu);

        int GetDpiForSystem();

        int GetSystemMetricsForDpi(int index, int dpi);
    }
}
