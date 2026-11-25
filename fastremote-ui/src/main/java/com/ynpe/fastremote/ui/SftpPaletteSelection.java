// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.transfer.RemoteFileEntry;
import java.util.List;
import javafx.scene.Node;

/** Snapshot before the palette takes keyboard focus, including directory-tree selection. */
record SftpPaletteSelection(String directory, List<LocalFileEntry> localEntries,
        List<RemoteFileEntry> remoteEntries, boolean local) {
    SftpPaletteSelection {
        localEntries = List.copyOf(localEntries);
        remoteEntries = List.copyOf(remoteEntries);
    }

    static SftpPaletteSelection capture(OpenSession session, boolean local, Node focus) {
        if (local) {
            var tree = session.localTree;
            boolean fromTree = WorkspacePaletteActions.contains(tree, focus);
            var item = fromTree ? tree.getSelectionModel().getSelectedItem() : null;
            var entry = item == null ? null : item.getValue();
            var entries = SftpBrowserModel.transferableLocalEntries(entry != null ? List.of(entry)
                    : fromTree || session.localTable == null ? List.of() : List.copyOf(session.localTable.getSelectionModel().getSelectedItems()));
            return new SftpPaletteSelection(entry == null ? session.currentLocalPath : entry.path().toString(),
                    entries, List.of(), true);
        }
        var tree = session.remoteTree;
        boolean fromTree = WorkspacePaletteActions.contains(tree, focus);
        var item = fromTree ? tree.getSelectionModel().getSelectedItem() : null;
        var entry = item == null ? null : item.getValue();
        var entries = SftpBrowserModel.transferableRemoteEntries(entry != null ? List.of(entry)
                : fromTree || session.remoteTable == null ? List.of() : List.copyOf(session.remoteTable.getSelectionModel().getSelectedItems()));
        return new SftpPaletteSelection(entry == null ? session.currentRemotePath : entry.path(),
                List.of(), entries, false);
    }

    boolean hasSelection() { return !localEntries.isEmpty() || !remoteEntries.isEmpty(); }
    List<String> paths() {
        if (!hasSelection()) return List.of(directory);
        return local ? localEntries.stream().map(value -> value.path().toString()).toList()
                : remoteEntries.stream().map(RemoteFileEntry::path).toList();
    }
    List<String> names() {
        return local ? localEntries.stream().map(LocalFileEntry::name).toList()
                : remoteEntries.stream().map(RemoteFileEntry::name).toList();
    }
}
