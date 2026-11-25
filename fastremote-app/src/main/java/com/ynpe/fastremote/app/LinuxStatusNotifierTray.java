// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.util.Duration;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.Tuple;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.DBusProperty;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

/** Linux tray integration using the desktop StatusNotifierItem and DBusMenu protocols. */
public final class LinuxStatusNotifierTray implements AutoCloseable {
    static final String ITEM_INTERFACE = "org.kde.StatusNotifierItem";
    static final String MENU_INTERFACE = "com.canonical.dbusmenu";
    static final String ITEM_PATH = "/StatusNotifierItem";
    static final String MENU_PATH = "/MenuBar";
    private static final String WATCHER_BUS = "org.kde.StatusNotifierWatcher";
    private static final String WATCHER_PATH = "/StatusNotifierWatcher";
    private static final String ICON_NAME = "fastremote-tray";
    private static final System.Logger LOGGER =
            System.getLogger(LinuxStatusNotifierTray.class.getName());

    private final AtomicBoolean closed = new AtomicBoolean();
    private final DBusConnection connection;
    private final String busName;
    private final Path iconDirectory;

    static LinuxStatusNotifierTray create(
            String showLabel,
            String quickConnectLabel,
            String localTerminalLabel,
            String exitLabel,
            Runnable showWindow,
            Runnable showQuickConnect,
            Runnable openLocalTerminal,
            Runnable exitApplication) throws IOException, DBusException {
        return new LinuxStatusNotifierTray(showLabel, quickConnectLabel, localTerminalLabel,
                exitLabel, showWindow, showQuickConnect, openLocalTerminal, exitApplication);
    }

    private LinuxStatusNotifierTray(
            String showLabel,
            String quickConnectLabel,
            String localTerminalLabel,
            String exitLabel,
            Runnable showWindow,
            Runnable showQuickConnect,
            Runnable openLocalTerminal,
            Runnable exitApplication) throws IOException, DBusException {
        Objects.requireNonNull(showWindow, "showWindow");
        Objects.requireNonNull(showQuickConnect, "showQuickConnect");
        Objects.requireNonNull(openLocalTerminal, "openLocalTerminal");
        Objects.requireNonNull(exitApplication, "exitApplication");

        iconDirectory = createIconDirectory();
        DBusConnection opened = null;
        String requestedName = "org.freedesktop.StatusNotifierItem-"
                + ProcessHandle.current().pid() + "-1";
        try {
            opened = DBusConnectionBuilder.forSessionBus().build();
            opened.requestBusName(requestedName);
            StatusNotifierItemObject item = new StatusNotifierItemObject(iconDirectory,
                    onFx(showWindow));
            DBusMenuObject menu = new DBusMenuObject(List.of(
                    MenuItem.action(1, showLabel, afterSystemMenuDismissed(showWindow)),
                    MenuItem.action(2, quickConnectLabel, afterSystemMenuDismissed(showQuickConnect)),
                    MenuItem.action(3, localTerminalLabel, afterSystemMenuDismissed(openLocalTerminal)),
                    MenuItem.separator(4),
                    MenuItem.action(5, exitLabel, afterSystemMenuDismissed(exitApplication))));
            opened.exportObject(ITEM_PATH, item);
            opened.exportObject(MENU_PATH, menu);
            StatusNotifierWatcher watcher = opened.getRemoteObject(
                    WATCHER_BUS, WATCHER_PATH, StatusNotifierWatcher.class);
            watcher.RegisterStatusNotifierItem(requestedName);
            connection = opened;
            busName = requestedName;
            LOGGER.log(System.Logger.Level.INFO,
                    "Linux system tray registered through StatusNotifierItem D-Bus");
        } catch (RuntimeException | DBusException | LinkageError failure) {
            if (opened != null) closeConnection(opened, requestedName);
            deleteIconDirectory(iconDirectory);
            throw failure;
        }
    }

    private static Runnable onFx(Runnable action) {
        return () -> Platform.runLater(action);
    }

    private static Runnable afterSystemMenuDismissed(Runnable action) {
        return () -> Platform.runLater(() -> {
            // DBusMenu sends "clicked" before GNOME has torn down its popup and pointer
            // grab.  Returning from the D-Bus call and allowing one short desktop frame
            // prevents a modal dialog opened by the action from inheriting that grab.
            PauseTransition dismissal = new PauseTransition(Duration.millis(150.0));
            dismissal.setOnFinished(ignored -> Platform.runLater(action));
            dismissal.play();
        });
    }

    private static Path createIconDirectory() throws IOException {
        Path directory = Files.createTempDirectory("fastremote-tray-");
        Path icon = directory.resolve(ICON_NAME + ".png");
        try (InputStream input = LinuxStatusNotifierTray.class.getResourceAsStream(
                "/com/ynpe/fastremote/app/icons/logo.png")) {
            if (input == null) throw new IOException("Tray icon resource is missing");
            Files.copy(input, icon);
        } catch (IOException failure) {
            deleteIconDirectory(directory);
            throw failure;
        }
        return directory;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        closeConnection(connection, busName);
        deleteIconDirectory(iconDirectory);
    }

    String busName() {
        return busName;
    }

    private static void closeConnection(DBusConnection connection, String busName) {
        try {
            connection.releaseBusName(busName);
        } catch (DBusException | RuntimeException failure) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Unable to release Linux tray D-Bus name", failure);
        }
        try {
            connection.close();
        } catch (IOException | RuntimeException failure) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Unable to close Linux tray D-Bus connection", failure);
        }
    }

    private static void deleteIconDirectory(Path directory) {
        try {
            Files.deleteIfExists(directory.resolve(ICON_NAME + ".png"));
            Files.deleteIfExists(directory);
        } catch (IOException failure) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Unable to remove temporary Linux tray icon", failure);
        }
    }

    @DBusInterfaceName("org.kde.StatusNotifierWatcher")
    public interface StatusNotifierWatcher extends DBusInterface {
        @SuppressWarnings("checkstyle:methodname")
        void RegisterStatusNotifierItem(String service);
    }

    @DBusInterfaceName(ITEM_INTERFACE)
    @DBusProperty(name = "Category", type = String.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "Id", type = String.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "Title", type = String.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "Status", type = String.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "WindowId", type = UInt32.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "IconName", type = String.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "IconThemePath", type = String.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "ItemIsMenu", type = Boolean.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "Menu", type = DBusPath.class, access = DBusProperty.Access.READ)
    public interface StatusNotifierItem extends DBusInterface {
        @SuppressWarnings("checkstyle:methodname")
        void ContextMenu(int x, int y);

        @SuppressWarnings("checkstyle:methodname")
        void Activate(int x, int y);

        @SuppressWarnings("checkstyle:methodname")
        void SecondaryActivate(int x, int y);

        @SuppressWarnings("checkstyle:methodname")
        void Scroll(int delta, String orientation);
    }

    static final class StatusNotifierItemObject implements StatusNotifierItem, Properties {
        private final Map<String, Variant<?>> properties;
        private final Runnable activate;

        StatusNotifierItemObject(Path iconDirectory, Runnable activate) {
            this.activate = Objects.requireNonNull(activate, "activate");
            Map<String, Variant<?>> values = new LinkedHashMap<>();
            values.put("Category", new Variant<>("ApplicationStatus"));
            values.put("Id", new Variant<>("fastremote"));
            values.put("Title", new Variant<>("FastRemote"));
            values.put("Status", new Variant<>("Active"));
            values.put("WindowId", new Variant<>(new UInt32(0)));
            values.put("IconName", new Variant<>(ICON_NAME));
            values.put("IconThemePath", new Variant<>(iconDirectory.toString()));
            values.put("ItemIsMenu", new Variant<>(false));
            values.put("Menu", new Variant<>(new DBusPath(MENU_PATH)));
            properties = Map.copyOf(values);
        }

        @Override public String getObjectPath() { return ITEM_PATH; }
        @Override public void ContextMenu(int x, int y) { }
        @Override public void Activate(int x, int y) { activate.run(); }
        @Override public void SecondaryActivate(int x, int y) { activate.run(); }
        @Override public void Scroll(int delta, String orientation) { }

        @Override
        @SuppressWarnings("unchecked")
        public <A> A Get(String interfaceName, String propertyName) {
            if (!ITEM_INTERFACE.equals(interfaceName)) return null;
            Variant<?> value = properties.get(propertyName);
            return value == null ? null : (A) value.getValue();
        }

        @Override public <A> void Set(String interfaceName, String propertyName, A value) { }

        @Override
        public Map<String, Variant<?>> GetAll(String interfaceName) {
            return ITEM_INTERFACE.equals(interfaceName) ? properties : Map.of();
        }
    }

    @DBusInterfaceName(MENU_INTERFACE)
    @DBusProperty(name = "Version", type = UInt32.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "Status", type = String.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "TextDirection", type = String.class, access = DBusProperty.Access.READ)
    public interface DBusMenu extends DBusInterface {
        @SuppressWarnings("checkstyle:methodname")
        MenuLayoutResult<UInt32, MenuLayout> GetLayout(
                int parentId, int recursionDepth, List<String> propertyNames);

        @SuppressWarnings("checkstyle:methodname")
        List<MenuPropertySet> GetGroupProperties(List<Integer> ids, List<String> propertyNames);

        @SuppressWarnings("checkstyle:methodname")
        Variant<?> GetProperty(int id, String name);

        @SuppressWarnings("checkstyle:methodname")
        void Event(int id, String eventId, Variant<?> data, UInt32 timestamp);

        @SuppressWarnings("checkstyle:methodname")
        List<Integer> EventGroup(List<MenuEvent> events);

        @SuppressWarnings("checkstyle:methodname")
        boolean AboutToShow(int id);

        @SuppressWarnings("checkstyle:methodname")
        AboutToShowGroupResult<List<Integer>, List<Integer>> AboutToShowGroup(
                List<Integer> ids);
    }

    static final class DBusMenuObject implements DBusMenu, Properties {
        private static final UInt32 VERSION = new UInt32(3);
        private final List<MenuItem> items;
        private final Map<Integer, MenuItem> itemsById;
        private final Map<String, Variant<?>> properties = Map.of(
                "Version", new Variant<>(VERSION),
                "Status", new Variant<>("normal"),
                "TextDirection", new Variant<>("ltr"));

        DBusMenuObject(List<MenuItem> items) {
            this.items = List.copyOf(items);
            Map<Integer, MenuItem> byId = new LinkedHashMap<>();
            this.items.forEach(item -> byId.put(item.id(), item));
            itemsById = Map.copyOf(byId);
        }

        @Override public String getObjectPath() { return MENU_PATH; }

        @Override
        public MenuLayoutResult<UInt32, MenuLayout> GetLayout(
                int parentId, int recursionDepth, List<String> propertyNames) {
            if (parentId == 0) {
                List<Variant<?>> children = new ArrayList<>();
                if (recursionDepth != 0) {
                    items.forEach(item -> children.add(
                            new Variant<>(item.layout(propertyNames))));
                }
                return new MenuLayoutResult<>(VERSION,
                        new MenuLayout(0, Map.of("children-display", new Variant<>("submenu")), children));
            }
            MenuItem item = itemsById.get(parentId);
            return new MenuLayoutResult<>(VERSION, item == null
                    ? new MenuLayout(parentId, Map.of(), List.of())
                    : item.layout(propertyNames));
        }

        @Override
        public List<MenuPropertySet> GetGroupProperties(
                List<Integer> ids, List<String> propertyNames) {
            List<MenuItem> requested = ids.isEmpty() ? items : ids.stream()
                    .map(itemsById::get).filter(Objects::nonNull).toList();
            return requested.stream().map(item -> new MenuPropertySet(
                    item.id(), item.filteredProperties(propertyNames))).toList();
        }

        @Override
        public Variant<?> GetProperty(int id, String name) {
            MenuItem item = itemsById.get(id);
            return item == null ? null : item.properties().get(name);
        }

        @Override
        public void Event(int id, String eventId, Variant<?> data, UInt32 timestamp) {
            if (!"clicked".equals(eventId)) return;
            MenuItem item = itemsById.get(id);
            if (item != null && item.action() != null) item.action().run();
        }

        @Override
        public List<Integer> EventGroup(List<MenuEvent> events) {
            List<Integer> errors = new ArrayList<>();
            for (MenuEvent event : events) {
                MenuItem item = itemsById.get(event.id);
                if (item == null) errors.add(event.id);
                else Event(event.id, event.eventId, event.data, event.timestamp);
            }
            return List.copyOf(errors);
        }

        @Override public boolean AboutToShow(int id) { return false; }

        @Override
        public AboutToShowGroupResult<List<Integer>, List<Integer>> AboutToShowGroup(
                List<Integer> ids) {
            List<Integer> errors = ids.stream().filter(id -> !itemsById.containsKey(id)).toList();
            return new AboutToShowGroupResult<>(List.of(), errors);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <A> A Get(String interfaceName, String propertyName) {
            if (!MENU_INTERFACE.equals(interfaceName)) return null;
            Variant<?> value = properties.get(propertyName);
            return value == null ? null : (A) value.getValue();
        }

        @Override public <A> void Set(String interfaceName, String propertyName, A value) { }

        @Override
        public Map<String, Variant<?>> GetAll(String interfaceName) {
            return MENU_INTERFACE.equals(interfaceName) ? properties : Map.of();
        }
    }

    record MenuItem(int id, Map<String, Variant<?>> properties, Runnable action) {
        MenuItem {
            properties = Map.copyOf(properties);
        }

        static MenuItem action(int id, String label, Runnable action) {
            return new MenuItem(id, Map.of("label", new Variant<>(escapeLabel(label))), action);
        }

        static MenuItem separator(int id) {
            return new MenuItem(id, Map.of("type", new Variant<>("separator")), null);
        }

        MenuLayout layout(List<String> requested) {
            return new MenuLayout(id, filteredProperties(requested), List.of());
        }

        Map<String, Variant<?>> filteredProperties(List<String> requested) {
            if (requested == null || requested.isEmpty()) return properties;
            Map<String, Variant<?>> filtered = new LinkedHashMap<>();
            requested.forEach(name -> {
                Variant<?> value = properties.get(name);
                if (value != null) filtered.put(name, value);
            });
            return Map.copyOf(filtered);
        }

        private static String escapeLabel(String value) {
            return Objects.requireNonNull(value, "value").replace("_", "__");
        }
    }

    public static final class MenuLayout extends Struct {
        @Position(0) public final int id;
        @Position(1) public final Map<String, Variant<?>> properties;
        @Position(2) public final List<Variant<?>> children;

        public MenuLayout(int id, Map<String, Variant<?>> properties, List<Variant<?>> children) {
            this.id = id;
            this.properties = Map.copyOf(properties);
            this.children = List.copyOf(children);
        }
    }

    public static final class MenuPropertySet extends Struct {
        @Position(0) public final int id;
        @Position(1) public final Map<String, Variant<?>> properties;

        public MenuPropertySet(int id, Map<String, Variant<?>> properties) {
            this.id = id;
            this.properties = Map.copyOf(properties);
        }
    }

    public static final class MenuEvent extends Struct {
        @Position(0) public final int id;
        @Position(1) public final String eventId;
        @Position(2) public final Variant<?> data;
        @Position(3) public final UInt32 timestamp;

        public MenuEvent(int id, String eventId, Variant<?> data, UInt32 timestamp) {
            this.id = id;
            this.eventId = eventId;
            this.data = data;
            this.timestamp = timestamp;
        }
    }

    public static final class MenuLayoutResult<A, B> extends Tuple {
        @Position(0) public final A revision;
        @Position(1) public final B layout;

        public MenuLayoutResult(A revision, B layout) {
            this.revision = revision;
            this.layout = layout;
        }
    }

    public static final class AboutToShowGroupResult<A, B> extends Tuple {
        @Position(0) public final A updatesNeeded;
        @Position(1) public final B idErrors;

        public AboutToShowGroupResult(A updatesNeeded, B idErrors) {
            this.updatesNeeded = updatesNeeded;
            this.idErrors = idErrors;
        }
    }
}
