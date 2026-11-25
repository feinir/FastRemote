// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.transfer.RemoteFileEntry;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javafx.scene.control.TreeItem;

/** Path, listing and lazy-tree policy shared by local and remote SFTP browser views. */
final class SftpBrowserModel {
    private static final String BLANK_ROW = "\u2063";

    private SftpBrowserModel() { }

    static boolean navigationEntry(String name) {
        return "..".equals(name);
    }

    static boolean blankFileEntry(String name) {
        return BLANK_ROW.equals(name);
    }

    static boolean blankTableItem(Object item) {
        if (item instanceof LocalFileEntry local) return blankFileEntry(local.name());
        if (item instanceof RemoteFileEntry remote) return blankFileEntry(remote.name());
        return false;
    }

    static List<LocalFileEntry> transferableLocalEntries(List<LocalFileEntry> entries) {
        return entries.stream().filter(entry -> !navigationEntry(entry.name()) && !blankFileEntry(entry.name())).toList();
    }

    static List<RemoteFileEntry> transferableRemoteEntries(List<RemoteFileEntry> entries) {
        return entries.stream().filter(entry -> !navigationEntry(entry.name()) && !blankFileEntry(entry.name())).toList();
    }

    static void pruneLocalTreeEntries(TreeItem<LocalFileEntry> root, Set<Path> removedPaths) {
        pruneTreeEntries(root, removedPaths, LocalFileEntry::path);
    }

    static Path localDirectoryAfterDeletion(Path current, java.util.Collection<Path> deleted) {
        Path target = current.toAbsolutePath().normalize();
        for (Path path : deleted) {
            Path removed = path.toAbsolutePath().normalize();
            if (target.startsWith(removed) && removed.getParent() != null) {
                target = removed.getParent();
            }
        }
        return target;
    }

    static Path localDirectoryAfterRename(Path current, Path source, Path target) {
        Path location = current.toAbsolutePath().normalize();
        Path previous = source.toAbsolutePath().normalize();
        return location.startsWith(previous)
                ? target.toAbsolutePath().normalize().resolve(previous.relativize(location)) : location;
    }

    static void pruneRemoteTreeEntries(TreeItem<RemoteFileEntry> root, Set<String> removedPaths) {
        Set<String> normalized = removedPaths.stream().map(SftpBrowserModel::normalizeRemotePath)
                .collect(java.util.stream.Collectors.toSet());
        pruneTreeEntries(root, normalized, entry -> normalizeRemotePath(entry.path()));
    }

    private static <T, P> void pruneTreeEntries(
            TreeItem<T> root, Set<P> removedPaths, Function<T, P> path) {
        if (root == null || removedPaths.isEmpty()) return;
        root.getChildren().removeIf(item -> item.getValue() != null
                && removedPaths.stream().anyMatch(removed -> Objects.equals(path.apply(item.getValue()), removed)));
        root.getChildren().forEach(item -> pruneTreeEntries(item, removedPaths, path));
    }

    static TreeItem<LocalFileEntry> findLocalTreeItem(TreeItem<LocalFileEntry> root, Path target) {
        if (root == null) return null;
        if (root.getValue() != null && root.getValue().path().equals(target)) return root;
        for (TreeItem<LocalFileEntry> child : root.getChildren()) {
            TreeItem<LocalFileEntry> found = findLocalTreeItem(child, target);
            if (found != null) return found;
        }
        return null;
    }

    static TreeItem<RemoteFileEntry> findRemoteTreeItem(TreeItem<RemoteFileEntry> root, String target) {
        if (root == null) return null;
        if (root.getValue() != null && normalizeRemotePath(root.getValue().path()).equals(target)) return root;
        for (TreeItem<RemoteFileEntry> child : root.getChildren()) {
            TreeItem<RemoteFileEntry> found = findRemoteTreeItem(child, target);
            if (found != null) return found;
        }
        return null;
    }

    static void expandParents(TreeItem<?> item) {
        for (TreeItem<?> current = item.getParent(); current != null; current = current.getParent()) {
            current.setExpanded(true);
        }
    }

    static boolean remotePathContains(String parent, String target) {
        String normalizedParent = normalizeRemotePath(parent);
        String normalizedTarget = normalizeRemotePath(target);
        return normalizedParent.equals("/") || normalizedTarget.equals(normalizedParent)
                || normalizedTarget.startsWith(normalizedParent + "/");
    }

    static String normalizeRemotePath(String path) {
        String normalized = path == null || path.isBlank() ? "/" : path.replace('\\', '/');
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    static DirectoryStatistics directoryStatistics(
            long files, long directories, long symbolicLinks, long bytes) {
        return new DirectoryStatistics(files, directories, symbolicLinks, files + directories, bytes);
    }

    static DirectoryStatistics localDirectoryStatistics(List<LocalFileEntry> entries) {
        long files = entries.stream().filter(entry -> !entry.directory()).count();
        long directories = entries.stream().filter(LocalFileEntry::directory).count();
        long symbolicLinks = entries.stream().filter(LocalFileEntry::symbolicLink).count();
        long bytes = entries.stream().filter(entry -> !entry.directory()).mapToLong(LocalFileEntry::size).sum();
        return directoryStatistics(files, directories, symbolicLinks, bytes);
    }

    static DirectoryStatistics remoteDirectoryStatistics(List<RemoteFileEntry> entries) {
        long files = entries.stream().filter(entry -> !entry.directory()).count();
        long directories = entries.stream().filter(RemoteFileEntry::directory).count();
        long symbolicLinks = entries.stream().filter(RemoteFileEntry::symbolicLink).count();
        long bytes = entries.stream().filter(entry -> !entry.directory()).mapToLong(RemoteFileEntry::size).sum();
        return directoryStatistics(files, directories, symbolicLinks, bytes);
    }

    static List<LocalFileEntry> withLocalNavigation(Path directory, List<LocalFileEntry> entries) {
        Path normalized = directory.toAbsolutePath().normalize();
        Path parent = normalized.getParent() == null ? normalized : normalized.getParent();
        List<LocalFileEntry> result = new ArrayList<>(entries.size() + 3);
        result.add(new LocalFileEntry("..", parent, true, false, 0L, Optional.empty(), false));
        result.addAll(entries);
        result.add(new LocalFileEntry(BLANK_ROW, normalized, false, false, 0L, Optional.empty(), false));
        result.add(new LocalFileEntry(BLANK_ROW, normalized, false, false, 0L, Optional.empty(), false));
        return List.copyOf(result);
    }

    static List<RemoteFileEntry> withRemoteNavigation(String directory, List<RemoteFileEntry> entries) {
        String normalized = normalizeRemotePath(directory);
        List<RemoteFileEntry> result = new ArrayList<>(entries.size() + 3);
        result.add(new RemoteFileEntry("..", parentRemotePath(normalized), true, false, 0L, Optional.empty()));
        result.addAll(entries);
        result.add(new RemoteFileEntry(BLANK_ROW, normalized, false, false, 0L, Optional.empty()));
        result.add(new RemoteFileEntry(BLANK_ROW, normalized, false, false, 0L, Optional.empty()));
        return List.copyOf(result);
    }

    static boolean hasTreePlaceholder(TreeItem<?> item) {
        return item.getChildren().size() == 1 && item.getChildren().getFirst().getValue() == null;
    }

    static <T> void installTreeBranchLoader(TreeItem<T> root, Consumer<TreeItem<T>> loader) {
        root.addEventHandler(TreeItem.branchExpandedEvent(), event -> {
            @SuppressWarnings("unchecked")
            TreeItem<T> expanded = (TreeItem<T>) event.getTreeItem();
            loader.accept(expanded);
        });
    }

    static <T> List<T> visibleTreeDirectories(
            List<T> entries, Predicate<T> directory, Predicate<T> hidden, boolean showHidden) {
        return entries.stream().filter(directory).filter(entry -> showHidden || !hidden.test(entry)).toList();
    }

    static <T> void replaceTreeChildren(TreeItem<T> item, List<TreeItem<T>> children) {
        item.getChildren().setAll(children);
    }

    /**
     * Updates the directory children of an already loaded node in place. Folders that still exist keep their
     * TreeItem, so their expansion state and loaded descendants survive; vanished folders are dropped and new
     * ones are added in listing order. A node that was never expanded keeps its lazy placeholder.
     */
    static <T, P> void mergeTreeChildren(TreeItem<T> item, List<T> directories,
            Function<T, P> path, Function<T, TreeItem<T>> factory) {
        if (hasTreePlaceholder(item)) return;
        Map<P, TreeItem<T>> existing = new HashMap<>();
        for (TreeItem<T> child : item.getChildren()) {
            if (child.getValue() != null) existing.put(path.apply(child.getValue()), child);
        }
        List<TreeItem<T>> merged = new ArrayList<>(directories.size());
        for (T directory : directories) {
            TreeItem<T> kept = existing.get(path.apply(directory));
            if (kept == null) {
                merged.add(factory.apply(directory));
            } else {
                kept.setValue(directory);
                merged.add(kept);
            }
        }
        if (!merged.equals(item.getChildren())) item.getChildren().setAll(merged);
    }

    /**
     * Fills a tree node from a directory listing the file list has already loaded, so the node can be expanded
     * without listing the directory again. Unloaded nodes get their children; loaded nodes are merged in place.
     */
    static <T, P> void populateTreeChildren(TreeItem<T> item, List<T> directories,
            Function<T, P> path, Function<T, TreeItem<T>> factory) {
        if (hasTreePlaceholder(item)) {
            item.getChildren().setAll(directories.stream().map(factory).toList());
        } else {
            mergeTreeChildren(item, directories, path, factory);
        }
    }

    static <T> void restoreTreePlaceholderAndCollapse(TreeItem<T> item) {
        item.setExpanded(false);
        if (!hasTreePlaceholder(item)) item.getChildren().setAll(new TreeItem<>());
    }

    static String parentRemotePath(String path) {
        String normalized = normalizeRemotePath(path);
        if (normalized.equals("/")) return "/";
        int separator = normalized.lastIndexOf('/');
        return separator <= 0 ? "/" : normalized.substring(0, separator);
    }

    static String joinRemotePath(String directory, String name) {
        String normalized = normalizeRemotePath(directory);
        return normalized.equals("/") ? "/" + name : normalized + "/" + name;
    }

    static String remoteBaseName(String path) {
        String normalized = normalizeRemotePath(path);
        if (normalized.equals("/")) return "/";
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    record DirectoryStatistics(long files, long directories, long symbolicLinks, long total, long bytes) {
        DirectoryStatistics {
            if (files < 0 || directories < 0 || symbolicLinks < 0 || total < 0 || bytes < 0) {
                throw new IllegalArgumentException("directory statistics must not be negative");
            }
        }

        String summary() {
            var localizer = com.ynpe.fastremote.i18n.Texts.localizer();
            String links = symbolicLinks == 0 ? "" : com.ynpe.fastremote.i18n.Texts.text("legacy.java.035f6dc3843e382f") + localizer.formatNumber(symbolicLinks);
            return com.ynpe.fastremote.i18n.Texts.text("legacy.java.6969e0e84bc42022") + localizer.formatNumber(files) + com.ynpe.fastremote.i18n.Texts.text("legacy.java.6e0c8a74dc411d8e")
                    + localizer.formatNumber(directories) + links + com.ynpe.fastremote.i18n.Texts.text("legacy.java.0815451b2f0524b5")
                    + localizer.formatNumber(total) + com.ynpe.fastremote.i18n.Texts.text("legacy.java.cf2148cfa7876f43") + humanSize(bytes);
        }
    }

    private static String humanSize(long size) {
        return com.ynpe.fastremote.i18n.Texts.localizer().formatBytes(size);
    }

}
