// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.i18n.Localizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import static com.ynpe.fastremote.ui.LucideIconView.Icon;

/** Presents a small, ordered set of capabilities supplied by the source workspace. */
final class WorkspacePaletteActions {
    enum Action {
        FILES("localFiles.openPaired", Icon.FOLDER, "files local file", "wenjian wj"),
        SFTP("", Icon.FOLDER, "sftp remote files", "wenjian wj"),
        TERMINAL("sftp.goToTerminal", Icon.SQUARE_TERMINAL, "terminal shell console", "zhongduan zd"),
        COPY_PATH("legacy.java.b97c49acb93028ec", Icon.COPY, "copy path directory", "fuzhi lujing fz lj"),
        COPY_NAME("legacy.java.886775196a71d984", Icon.COPY, "copy filename name", "fuzhi wenjianming fz wjm"),
        FAVORITE("localFiles.addFavorite", Icon.STAR, "favorite bookmark add", "shoucang sc"),
        REFRESH("legacy.java.aee88743413144a2", Icon.REFRESH_CW, "refresh reload", "shuaxin sx"),
        NEW_FOLDER("legacy.java.d5f6f645ff5e4bd9", Icon.FOLDER_PLUS, "new folder directory mkdir", "xinjian wenjianjia xj wjj"),
        DOWNLOAD("legacy.java.4673a23061656125", Icon.FOLDER, "download", "xiazai xz"),
        UPLOAD("legacy.java.9e07e3c0532d4976", Icon.FOLDER, "upload", "shangchuan sc"),
        DUPLICATE("terminal.context.duplicate", Icon.COPY, "duplicate terminal", "fuzhi zhongduan fz zd"),
        NEW_TERMINAL("terminal.context.new", Icon.PLUS, "new terminal shell", "xinjian zhongduan xj zd"),
        REVEAL("", Icon.FOLDER, "reveal finder explorer file manager", "xianshi xs");

        final String key;
        final Icon icon;
        final String aliases;
        final String pinyin;
        Action(String key, Icon icon, String aliases, String pinyin) {
            this.key = key; this.icon = icon; this.aliases = aliases; this.pinyin = pinyin;
        }
    }

    static List<CommandPalette.Entry> entries(Localizer localizer, boolean pinyin,
            String kind, String detail, List<String> paths, Consumer<String> navigate,
            Map<Action, Runnable> actions, BooleanSupplier available, Runnable unavailable) {
        List<CommandPalette.Entry> entries = new ArrayList<>();
        String go = localizer.text("legacy.java.d69b5ab453f7d2d6");
        List<String> goTerms = CommandPalette.enabledSearchTerms(pinyin,
                "go goto cd jump navigate path", "pinyin:zhuandao tiaozhuan zd tz");
        List<CommandPalette.Entry> children = paths.stream().distinct().map(path ->
                CommandPalette.Entry.content(path, detail, goTerms, Icon.LOCATE_FIXED,
                        guarded(available, unavailable, () -> navigate.accept(path)))).toList();
        entries.add(CommandPalette.Entry.submenu("workspace:" + kind + ":goto", go, detail,
                goTerms, Icon.LOCATE_FIXED, children));
        // Enum order defines stable muscle-memory positions; absent capabilities are omitted.
        for (Action action : Action.values()) {
            Runnable execute = actions.get(action);
            if (execute == null) continue;
            String title = action == Action.REVEAL ? PlatformFileManagerLabel.reveal()
                    : action == Action.SFTP ? "SFTP" : localizer.text(action.key);
            entries.add(CommandPalette.Entry.contextual("workspace:" + kind + ":" + action.name(),
                    title, detail, CommandPalette.enabledSearchTerms(pinyin,
                            action.aliases, "pinyin:" + action.pinyin), action.icon,
                    guarded(available, unavailable, execute)));
        }
        return List.copyOf(entries);
    }

    private static Runnable guarded(BooleanSupplier available, Runnable unavailable, Runnable execute) {
        return () -> { if (available.getAsBoolean()) execute.run(); else unavailable.run(); };
    }

    private WorkspacePaletteActions() { }

    static boolean contains(javafx.scene.Node ancestor, javafx.scene.Node node) {
        if (ancestor == null) return false;
        for (var current = node; current != null; current = current.getParent()) {
            if (current == ancestor) return true;
        }
        return false;
    }
}
