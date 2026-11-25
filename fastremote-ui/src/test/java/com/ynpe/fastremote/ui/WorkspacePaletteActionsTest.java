// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.settings.InterfaceLanguage;
import com.ynpe.fastremote.i18n.Localizer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static com.ynpe.fastremote.ui.WorkspacePaletteActions.Action.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkspacePaletteActionsTest {
    @Test
    void actionsAndPathChildrenRecheckSourceAvailabilityWithoutRetargeting() {
        var alive = new AtomicBoolean(true);
        var executed = new AtomicInteger();
        var rejected = new AtomicInteger();
        var entries = WorkspacePaletteActions.entries(Localizer.forLanguage(InterfaceLanguage.ENGLISH),
                false, "source", "Source", List.of("/a", "/a", "/b"), path -> executed.incrementAndGet(),
                Map.of(COPY_PATH, executed::incrementAndGet), alive::get, rejected::incrementAndGet);
        assertEquals(2, entries.size());
        assertEquals(2, entries.getFirst().children().size());
        entries.getLast().action().run();
        entries.getFirst().children().getFirst().action().run();
        alive.set(false);
        entries.getLast().action().run();
        entries.getFirst().children().getFirst().action().run();
        assertEquals(2, executed.get());
        assertEquals(2, rejected.get());
    }

    @Test
    void allTwelveLanguagesResolveActionLabelsAndRespectPinyinPreference() {
        for (var language : InterfaceLanguage.publishedLanguages()) {
            var actions = new java.util.EnumMap<WorkspacePaletteActions.Action, Runnable>(WorkspacePaletteActions.Action.class);
            for (var action : WorkspacePaletteActions.Action.values()) actions.put(action, () -> {});
            var entries = WorkspacePaletteActions.entries(Localizer.forLanguage(language), false,
                    "panel", "Panel", List.of(), ignored -> {}, actions, () -> true, () -> {});
            assertTrue(entries.stream().allMatch(entry -> !entry.title().isBlank()));
            assertTrue(CommandPalette.filter(entries, "fuzhi").isEmpty());
            assertEquals(1, CommandPalette.filter(entries, "copy path").size());
        }
    }
}
