// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.command.CommandPaletteUsage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.time.Instant;
import javafx.scene.input.MouseButton;
import org.junit.jupiter.api.Test;

class CommandPaletteTest {
    private static final Runnable NO_OP = () -> { };

    @Test
    void contextualMatchesAlwaysLeadButNonmatchingActionsDisappearAndHistoryCannotReorderThem() {
        var first = CommandPalette.Entry.contextual("workspace:goto", "Go to", "Current panel",
                List.of("path"), LucideIconView.Icon.FOLDER, NO_OP);
        var second = CommandPalette.Entry.contextual("workspace:copy", "Copy path", "Current panel",
                List.of("path"), LucideIconView.Icon.COPY, NO_OP);
        var global = entry("path", "Global", "path");
        var usage = Map.of(global.usageKey(), new CommandPaletteUsage(global.usageKey(), 1000, Instant.now()),
                second.usageKey(), new CommandPaletteUsage(second.usageKey(), 1000, Instant.now()));
        assertEquals(List.of(first, second, global), CommandPalette.filter(List.of(global, first, second), "", usage));
        var matches = CommandPalette.filter(List.of(global, first, second), "path", usage);
        assertEquals(global, matches.getLast());
        assertEquals(List.of(global), CommandPalette.filter(List.of(first, second, global), "global", usage));
    }

    @Test
    void pathDrillDownFiltersAndRestoresTheOriginalQueryIncludingAnEmptySubmenu() {
        var path = entry("/srv/projects", "Current panel", "path");
        var go = CommandPalette.Entry.submenu("goto", "Go to", "Current panel", List.of("go"),
                LucideIconView.Icon.FOLDER, List.of(path));
        var navigation = new CommandPalette.Navigation(List.of(go));
        assertTrue(navigation.enter(go, "goto"));
        assertEquals(List.of(path), CommandPalette.filter(navigation.entries(), "proj"));
        assertFalse(navigation.enter(path, "proj"));
        assertEquals("goto", navigation.back());
        assertEquals(List.of(go), navigation.entries());
        var empty = CommandPalette.Entry.submenu("empty", "Go to", "Panel", List.of(),
                LucideIconView.Icon.FOLDER, List.of());
        assertTrue(navigation.enter(empty, ""));
        assertTrue(navigation.inSubmenu());
        assertTrue(navigation.entries().isEmpty());
        assertEquals("", navigation.back());
        assertFalse(navigation.inSubmenu());
    }

    @Test
    void searchesActionsAndSessionsAcrossMultipleTerms() {
        CommandPalette.Entry quick = entry("快速连接", "打开快速连接对话框", "SSH 临时连接");
        CommandPalette.Entry session = entry(
                "生产数据库", "连接会话 · 生产环境 / 数据库 · root@db.example.com:22", "会话 主机");
        CommandPalette.Entry variables = entry("打开变量库", "管理命令模板变量", "变量 模板");

        assertEquals(List.of(session), CommandPalette.filter(List.of(quick, session, variables), "生产 root"));
        assertEquals(List.of(quick), CommandPalette.filter(List.of(quick, session, variables), "ssh"));
        assertEquals(List.of(variables), CommandPalette.filter(List.of(quick, session, variables), "模板 变量"));
    }

    @Test
    void ranksAnExactTitleBeforeKeywordMatchesAndPreservesDefaultOrder() {
        CommandPalette.Entry keyword = entry("会话管理", "搜索快速连接记录", "");
        CommandPalette.Entry exact = entry("快速连接", "打开对话框", "");
        List<CommandPalette.Entry> entries = List.of(keyword, exact);

        assertEquals(entries, CommandPalette.filter(entries, ""));
        assertEquals(List.of(exact, keyword), CommandPalette.filter(entries, "快速连接"));
    }

    @Test
    void supportsEnglishPrefixesPinyinInitialsAndMixedWordOrder() {
        CommandPalette.Entry settings = entry("打开配置中心", "修改工作区、外观和快捷键",
                List.of("设置", "配置", "shezhi", "sz", "settings", "configuration"));
        CommandPalette.Entry quickConnect = entry("快速连接", "打开快速连接对话框",
                List.of("kuaisu lianjie", "quick connect", "connection", "conn"));
        CommandPalette.Entry newHost = entry("新建主机", "创建连接配置",
                List.of("new host", "add", "create", "creat"));
        CommandPalette.Entry newCommand = entry("新建命令", "创建命令模板",
                List.of("new command", "add", "create", "creat"));
        CommandPalette.Entry noisyCommand = entry("CPU 信息", "命令库",
                List.of("print system environment text and thread details"));

        assertEquals(List.of(settings),
                CommandPalette.filter(List.of(settings, quickConnect, noisyCommand), "sett"));
        assertEquals(List.of(settings), CommandPalette.filter(List.of(settings, quickConnect), "sz"));
        assertEquals(List.of(quickConnect), CommandPalette.filter(List.of(settings, quickConnect), "conn quick"));
        assertEquals(List.of(newHost, newCommand),
                CommandPalette.filter(List.of(newHost, newCommand, settings), "add creat"));
    }

    @Test
    void exposesEveryCommandAndSessionOperationThroughTheSameEntityTerms() {
        List<String> commandTerms = List.of("Ping 检测", "ping -c 4 example.com", "生产环境");
        CommandPalette.Entry send = entry("发送命令 · Ping 检测", "命令库 · 生产环境", commandTerms);
        CommandPalette.Entry paste = entry("仅粘贴命令 · Ping 检测", "命令库 · 生产环境", commandTerms);
        CommandPalette.Entry edit = entry("编辑命令 · Ping 检测", "命令库 · 生产环境", commandTerms);
        List<String> sessionTerms = List.of("生产数据库", "root@db.example.com:22", "session host machine");
        CommandPalette.Entry terminal = entry("连接终端 · 生产数据库", "生产环境", sessionTerms);
        CommandPalette.Entry sftp = entry("打开 SFTP · 生产数据库", "生产环境", sessionTerms);

        assertEquals(List.of(send, paste, edit), CommandPalette.filter(List.of(send, paste, edit), "ping"));
        assertEquals(List.of(terminal, sftp), CommandPalette.filter(List.of(terminal, sftp), "db.example"));
    }

    @Test
    void goIntentListsEveryPathAndCanBeCombinedWithAPathFragment() {
        CommandPalette.Entry payments = entry("转到 · /srv/payments/releases", "收藏路径",
                List.of("/srv/payments/releases", "go goto go-to path directory folder cd jump navigate"));
        CommandPalette.Entry reports = entry("转到 · /srv/reports", "收藏路径",
                List.of("/srv/reports", "go goto go-to path directory folder cd jump navigate"));

        assertEquals(List.of(payments, reports),
                CommandPalette.filter(List.of(payments, reports), "go"));
        assertEquals(List.of(payments),
                CommandPalette.filter(List.of(payments, reports), "go pay"));
        assertEquals(List.of(payments),
                CommandPalette.filter(List.of(payments, reports), "path payment"));
    }

    @Test
    void includesPinyinAliasesOnlyWhenThePreferenceIsEnabled() {
        assertEquals(List.of("settings", "configuration"), CommandPalette.enabledSearchTerms(false,
                "settings", "pinyin:shezhi", "pinyin:sz", "configuration"));
        assertEquals(List.of("settings", "shezhi", "sz", "configuration"),
                CommandPalette.enabledSearchTerms(true,
                        "settings", "pinyin:shezhi", "pinyin:sz", "configuration"));
    }

    @Test
    void localizedDynamicActionsAlwaysKeepEnglishAliasesWhenPinyinIsDisabled() {
        List<String> terms = CommandPalette.enabledSearchTerms(false,
                "转到", "go goto go-to path", "pinyin:zhuandao tiaozhuan");
        CommandPalette.Entry payment = entry("转到 · /srv/payment/releases", "收藏路径",
                java.util.stream.Stream.concat(
                        java.util.stream.Stream.of("/srv/payment/releases"), terms.stream()).toList());

        assertEquals(List.of("转到", "go goto go-to path"), terms);
        assertEquals(List.of(payment), CommandPalette.filter(List.of(payment), "go"));
        assertEquals(List.of(payment), CommandPalette.filter(List.of(payment), "go pay"));
        assertEquals(List.of(payment), CommandPalette.filter(List.of(payment), "转到 pay"));
    }

    @Test
    void acceptsOnlyASinglePrimaryClickOnANonEmptyResult() {
        assertTrue(CommandPalette.shouldAcceptMouseClick(MouseButton.PRIMARY, 1, true));
        assertFalse(CommandPalette.shouldAcceptMouseClick(MouseButton.PRIMARY, 2, true));
        assertFalse(CommandPalette.shouldAcceptMouseClick(MouseButton.SECONDARY, 1, true));
        assertFalse(CommandPalette.shouldAcceptMouseClick(MouseButton.PRIMARY, 1, false));
    }

    @Test
    void promotesFrequentlyUsedMatchesWithoutDisplacingAnExactTitle() {
        CommandPalette.Entry prefix = entry("Ping status", "Command library", "ping");
        CommandPalette.Entry frequent = entry("Run ping diagnostics", "Command library", "ping");
        CommandPalette.Entry exact = entry("ping", "Command library", "ping");
        Map<String, CommandPaletteUsage> usage = Map.of(
                frequent.usageKey(), new CommandPaletteUsage(
                        frequent.usageKey(), 6, Instant.parse("2026-09-13T12:00:00Z")));

        assertEquals(List.of(frequent, prefix),
                CommandPalette.filter(List.of(prefix, frequent), "ping", usage));
        assertEquals(List.of(exact, frequent, prefix),
                CommandPalette.filter(List.of(prefix, frequent, exact), "ping", usage));
    }

    @Test
    void listsFrequentlyAndRecentlyUsedEntriesFirstBeforeTyping() {
        CommandPalette.Entry first = entry("First", "Action", "first");
        CommandPalette.Entry older = entry("Older", "Action", "older");
        CommandPalette.Entry newer = entry("Newer", "Action", "newer");
        Map<String, CommandPaletteUsage> usage = Map.of(
                older.usageKey(), new CommandPaletteUsage(
                        older.usageKey(), 3, Instant.parse("2026-09-13T10:00:00Z")),
                newer.usageKey(), new CommandPaletteUsage(
                        newer.usageKey(), 3, Instant.parse("2026-09-13T11:00:00Z")));

        assertEquals(List.of(newer, older, first),
                CommandPalette.filter(List.of(first, older, newer), "", usage));
    }

    @Test
    void listsGeneralActionsBeforeDynamicContentByDefault() {
        CommandPalette.Entry openFile = entry("Open file", "Editor", "open");
        CommandPalette.Entry log = entry("Audit log", "Tools", "log");
        CommandPalette.Entry command = CommandPalette.Entry.content(
                "Run uptime", "Command library", List.of("uptime"),
                LucideIconView.Icon.COMMAND, NO_OP);
        CommandPalette.Entry path = CommandPalette.Entry.content(
                "Go to /srv", "Favorite path", List.of("/srv"),
                LucideIconView.Icon.COMMAND, NO_OP);
        Map<String, CommandPaletteUsage> usage = Map.of(
                command.usageKey(), new CommandPaletteUsage(
                        command.usageKey(), 100, Instant.parse("2026-09-13T12:00:00Z")),
                path.usageKey(), new CommandPaletteUsage(
                        path.usageKey(), 10, Instant.parse("2026-09-13T11:00:00Z")));

        assertEquals(List.of(openFile, log, command, path),
                CommandPalette.filter(List.of(openFile, command, path, log), "", usage));
        assertEquals(List.of(command),
                CommandPalette.filter(List.of(openFile, command), "uptime", usage));
    }

    private static CommandPalette.Entry entry(String title, String detail, String keywords) {
        return new CommandPalette.Entry(title, detail, keywords, LucideIconView.Icon.COMMAND, NO_OP);
    }

    private static CommandPalette.Entry entry(String title, String detail, List<String> searchTerms) {
        return new CommandPalette.Entry(title, detail, searchTerms, LucideIconView.Icon.COMMAND, NO_OP);
    }
}
