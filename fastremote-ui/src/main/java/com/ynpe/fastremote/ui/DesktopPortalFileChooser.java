// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

/** Minimal org.freedesktop.portal.FileChooser client used only by the Linux chooser facade. */
final class DesktopPortalFileChooser {
    private static final String BUS_NAME = "org.freedesktop.portal.Desktop";
    private static final String OBJECT_PATH = "/org/freedesktop/portal/desktop";
    private static final String FILE_CHOOSER_INTERFACE = "org.freedesktop.portal.FileChooser";
    private static final long RESPONSE_TIMEOUT_MINUTES = 30L;

    private DesktopPortalFileChooser() { }

    enum Mode { OPEN, SAVE }

    record Filter(String name, List<String> patterns) {
        Filter {
            name = Objects.requireNonNullElse(name, "");
            patterns = List.copyOf(patterns);
        }
    }

    record Request(
            Mode mode,
            String title,
            boolean multiple,
            boolean directory,
            Path initialDirectory,
            String initialFileName,
            List<Filter> filters,
            Integer selectedFilterIndex) {
        Request {
            Objects.requireNonNull(mode, "mode");
            title = Objects.requireNonNullElse(title, "");
            filters = List.copyOf(filters);
            if (directory && (mode != Mode.OPEN || multiple)) {
                throw new IllegalArgumentException("directory selection must be a single OpenFile request");
            }
        }
    }

    sealed interface Outcome {
        record Selected(List<Path> paths) implements Outcome {
            public Selected {
                paths = List.copyOf(paths);
                if (paths.isEmpty()) throw new IllegalArgumentException("selected paths cannot be empty");
            }
        }

        record Cancelled() implements Outcome { }
        record Failed() implements Outcome { }

        static Outcome cancelled() { return new Cancelled(); }
        static Outcome failed() { return new Failed(); }
    }

    static Outcome choose(Request request) {
        return choose(request, ignored -> { });
    }

    static Outcome choose(Request request, Consumer<Runnable> cancellationScheduler) {
        Objects.requireNonNull(cancellationScheduler, "cancellationScheduler");
        try (DBusConnection connection = DBusConnectionBuilder.forSessionBus()
                .withShared(false).build()) {
            Properties properties = connection.getRemoteObject(BUS_NAME, OBJECT_PATH, Properties.class);
            Object versionValue = properties.Get(FILE_CHOOSER_INTERFACE, "version");
            long version = uintValue(versionValue);
            if (request.directory() && version < 3L) return Outcome.failed();

            FileChooserPortal portal = connection.getRemoteObject(
                    BUS_NAME, OBJECT_PATH, FileChooserPortal.class);
            String token = "fastremote_" + UUID.randomUUID().toString().replace("-", "");
            String sender = connection.getUniqueName().substring(1).replace('.', '_');
            String expectedPath = OBJECT_PATH + "/request/" + sender + "/" + token;
            String portalOwner = connection.getDBusOwnerName(BUS_NAME);
            CompletableFuture<PortalResponse> response = new CompletableFuture<>();
            RequestPortal expectedRequest = connection.getRemoteObject(
                    BUS_NAME, expectedPath, RequestPortal.class);

            try (AutoCloseable subscription = connection.addSigHandler(
                    RequestPortal.Response.class, portalOwner, expectedRequest,
                    signal -> response.complete(new PortalResponse(signal.response(), signal.results())))) {
                Map<String, Variant<?>> options = options(request, token);
                DBusPath handle = request.mode() == Mode.SAVE
                        ? portal.SaveFile("", request.title(), options)
                        : portal.OpenFile("", request.title(), options);
                if (handle == null || !expectedPath.equals(handle.getPath())) return Outcome.failed();
                cancellationScheduler.accept(() -> {
                    try {
                        expectedRequest.Close();
                    } finally {
                        response.complete(new PortalResponse(new UInt32(1), Map.of()));
                    }
                });
                try {
                    PortalResponse portalResponse = response.get(RESPONSE_TIMEOUT_MINUTES, TimeUnit.MINUTES);
                    return outcome(portalResponse, request.multiple());
                } catch (TimeoutException timedOut) {
                    expectedRequest.Close();
                    return Outcome.failed();
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Outcome.failed();
        } catch (Exception unavailableOrInvalid) {
            return Outcome.failed();
        }
    }

    static Map<String, Variant<?>> options(Request request, String token) {
        Map<String, Variant<?>> options = new LinkedHashMap<>();
        options.put("handle_token", new Variant<>(token));
        options.put("modal", new Variant<>(Boolean.TRUE));
        if (request.mode() == Mode.OPEN) {
            options.put("multiple", new Variant<>(request.multiple()));
            if (request.directory()) options.put("directory", new Variant<>(Boolean.TRUE));
        }
        if (request.initialDirectory() != null) {
            options.put("current_folder", new Variant<>(nulTerminated(request.initialDirectory())));
        }
        if (request.mode() == Mode.SAVE && request.initialFileName() != null
                && !request.initialFileName().isBlank()) {
            options.put("current_name", new Variant<>(request.initialFileName()));
        }
        if (!request.filters().isEmpty()) {
            List<PortalFilter> filters = request.filters().stream().map(PortalFilter::new).toList();
            options.put("filters", new Variant<>(filters, "a(sa(us))"));
            Integer selectedIndex = request.selectedFilterIndex();
            if (selectedIndex != null && selectedIndex >= 0 && selectedIndex < filters.size()) {
                options.put("current_filter", new Variant<>(filters.get(selectedIndex), "(sa(us))"));
            }
        }
        return options;
    }

    static Outcome outcome(PortalResponse response, boolean multiple) {
        long code = response.response().longValue();
        if (code == 1L) return Outcome.cancelled();
        if (code != 0L) return Outcome.failed();
        Variant<?> urisVariant = response.results().get("uris");
        if (urisVariant == null) return Outcome.failed();
        List<?> values;
        Object raw = urisVariant.getValue();
        if (raw instanceof List<?> list) values = list;
        else if (raw instanceof String[] array) values = List.of(array);
        else return Outcome.failed();
        List<Path> paths = new ArrayList<>();
        try {
            for (Object value : values) {
                if (!(value instanceof String text)) return Outcome.failed();
                URI uri = URI.create(text);
                if (!"file".equalsIgnoreCase(uri.getScheme())) return Outcome.failed();
                paths.add(Path.of(uri).toAbsolutePath().normalize());
            }
        } catch (RuntimeException invalidUri) {
            return Outcome.failed();
        }
        if (paths.isEmpty() || (!multiple && paths.size() != 1)) return Outcome.failed();
        return new Outcome.Selected(paths);
    }

    private static byte[] nulTerminated(Path path) {
        byte[] encoded = path.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8);
        return java.util.Arrays.copyOf(encoded, encoded.length + 1);
    }

    private static long uintValue(Object value) {
        if (value instanceof UInt32 number) return number.longValue();
        if (value instanceof Variant<?> variant) return uintValue(variant.getValue());
        if (value instanceof Number number) return number.longValue();
        throw new IllegalArgumentException("unexpected portal version type");
    }

    record PortalResponse(UInt32 response, Map<String, Variant<?>> results) {
        PortalResponse {
            Objects.requireNonNull(response, "response");
            results = Map.copyOf(results);
        }
    }

    static final class PortalFilter extends Struct {
        @Position(0)
        public final String name;
        @Position(1)
        public final List<PortalFilterCondition> conditions;

        PortalFilter(Filter filter) {
            name = filter.name();
            conditions = filter.patterns().stream().map(PortalFilterCondition::new).toList();
        }
    }

    static final class PortalFilterCondition extends Struct {
        @Position(0)
        public final UInt32 kind = new UInt32(0);
        @Position(1)
        public final String pattern;

        PortalFilterCondition(String pattern) {
            this.pattern = pattern;
        }
    }

    @DBusInterfaceName(FILE_CHOOSER_INTERFACE)
    @SuppressWarnings("checkstyle:methodname")
    interface FileChooserPortal extends DBusInterface {
        DBusPath OpenFile(String parentWindow, String title, Map<String, Variant<?>> options);
        DBusPath SaveFile(String parentWindow, String title, Map<String, Variant<?>> options);
    }

    @DBusInterfaceName("org.freedesktop.portal.Request")
    interface RequestPortal extends DBusInterface {
        @SuppressWarnings("checkstyle:methodname")
        void Close();

        final class Response extends DBusSignal {
            private final UInt32 response;
            private final Map<String, Variant<?>> results;

            public Response(String path, UInt32 response, Map<String, Variant<?>> results)
                    throws DBusException {
                super(path, response, results);
                this.response = response;
                this.results = results;
            }

            UInt32 response() { return response; }
            Map<String, Variant<?>> results() { return results; }
        }
    }
}
