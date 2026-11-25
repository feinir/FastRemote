// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.connection.ConnectionProfile;
import com.ynpe.fastremote.domain.settings.HostTabTitlePart;
import com.ynpe.fastremote.domain.settings.WindowBounds;
import com.ynpe.fastremote.ui.LucideIconView.Icon;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;

/** Pure presentation and ordering policy for the main workspace and its tabs. */
final class WorkspaceViewModel {
    private static final double TERMINAL_ACTIVITY_DEFAULT_HEIGHT = 100.0;

    private WorkspaceViewModel() { }

    enum WorkspaceType { TERMINAL, SFTP }
    enum ConnectionState { CONNECTING, CONNECTED, DISCONNECTED }

    static String localTerminalTitle() {
        return com.ynpe.fastremote.i18n.Texts.text("legacy.java.51b72143be70e638");
    }

    static <T> List<T> itemsBefore(List<T> items, T target) {
        int index = items.indexOf(target);
        return index <= 0 ? List.of() : List.copyOf(items.subList(0, index));
    }

    static <T> List<T> itemsAfter(List<T> items, T target) {
        int index = items.indexOf(target);
        return index < 0 || index + 1 >= items.size()
                ? List.of() : List.copyOf(items.subList(index + 1, items.size()));
    }

    static <T> boolean moveItem(List<T> items, T item, int targetIndex) {
        int sourceIndex = items.indexOf(item);
        if (sourceIndex < 0 || items.size() < 2) return false;
        int destination = Math.max(0, Math.min(targetIndex, items.size() - 1));
        if (sourceIndex == destination) return false;
        items.remove(sourceIndex);
        items.add(destination, item);
        return true;
    }

    static WindowBounds visibleWindowBounds(
            WindowBounds saved, List<Rectangle2D> screens, double minimumWidth, double minimumHeight) {
        if (screens.isEmpty()) return saved;
        Rectangle2D target = new Rectangle2D(saved.x(), saved.y(), saved.width(), saved.height());
        Rectangle2D screen = screens.stream()
                .max(Comparator.comparingDouble(candidate -> intersectionArea(target, candidate)))
                .filter(candidate -> intersectionArea(target, candidate) > 0.0)
                .orElse(screens.getFirst());
        double width = Math.min(Math.max(saved.width(), minimumWidth), screen.getWidth());
        double height = Math.min(Math.max(saved.height(), minimumHeight), screen.getHeight());
        double x = clamp(saved.x(), screen.getMinX(), screen.getMaxX() - width);
        double y = clamp(saved.y(), screen.getMinY(), screen.getMaxY() - height);
        return new WindowBounds(x, y, width, height);
    }

    private static double intersectionArea(Rectangle2D left, Rectangle2D right) {
        double width = Math.max(0.0, Math.min(left.getMaxX(), right.getMaxX())
                - Math.max(left.getMinX(), right.getMinX()));
        double height = Math.max(0.0, Math.min(left.getMaxY(), right.getMaxY())
                - Math.max(left.getMinY(), right.getMinY()));
        return width * height;
    }

    static String workspaceTabTitle(
            String hostName,
            ConnectionProfile profile,
            Set<HostTabTitlePart> configured,
            WorkspaceType type) {
        Set<HostTabTitlePart> parts = configured.isEmpty()
                ? Set.of(HostTabTitlePart.NAME) : configured;
        List<String> values = new ArrayList<>(4);
        for (HostTabTitlePart part : HostTabTitlePart.values()) {
            if (!parts.contains(part)) continue;
            switch (part) {
                case NAME -> {
                    if (hostName != null && !hostName.isBlank()) values.add(hostName.strip());
                }
                case USER -> values.add(profile.username().value());
                case HOST -> values.add(profile.host().value());
                case PORT -> values.add(Integer.toString(profile.port().value()));
            }
        }
        if (values.isEmpty()) values.add(profile.host().value());
        return String.join("-", values);
    }

    static String connectionDetail(String hostName, ConnectionProfile profile) {
        String endpoint = profile.username().value() + '@' + profile.host().value() + ':' + profile.port().value();
        return hostName == null || hostName.isBlank() ? endpoint : hostName.strip() + '-' + endpoint;
    }

    static String connectionLogName(String hostName, ConnectionProfile profile) {
        Objects.requireNonNull(profile, "profile");
        return hostName == null || hostName.isBlank()
                ? profile.host().value() : hostName.strip();
    }

    static String workspaceCounterpartLabel(WorkspaceType type) {
        return type == WorkspaceType.TERMINAL ? "SFTP" : com.ynpe.fastremote.i18n.Texts.text("legacy.java.e2a76ef1f12e147f");
    }

    static double workspaceActivityDivider(WorkspaceType type, double paneHeight, double activityHeight) {
        if (paneHeight <= 0.0) return 1.0;
        return clamp((paneHeight - activityHeight) / paneHeight, 0.0, 1.0);
    }

    static double terminalActivityDefaultHeight() {
        return TERMINAL_ACTIVITY_DEFAULT_HEIGHT;
    }

    static Node workspaceCounterpartIcon(WorkspaceType type) {
        LucideIconView icon = new LucideIconView(type == WorkspaceType.TERMINAL
                ? Icon.FOLDER : Icon.SQUARE_TERMINAL);
        icon.getStyleClass().add("workspace-action-icon");
        StackPane slot = new StackPane(icon);
        slot.setMinSize(14.0, 14.0);
        slot.setPrefSize(14.0, 14.0);
        slot.setMaxSize(14.0, 14.0);
        slot.setMouseTransparent(true);
        return slot;
    }

    static boolean workspaceActivityInitiallyVisible(WorkspaceType type, boolean configuredDefault) {
        return type == WorkspaceType.SFTP;
    }

    static String workspaceStatusStyle(ConnectionState state) {
        return switch (state) {
            case CONNECTING -> "workspace-tab-status-connecting";
            case CONNECTED -> "workspace-tab-status-connected";
            case DISCONNECTED -> "workspace-tab-status-disconnected";
        };
    }

    static boolean shouldRetainWorkspace(boolean connectedOnce, ConnectionState state) {
        return connectedOnce && state != ConnectionState.CONNECTED;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
