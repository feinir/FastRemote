// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.ai.AiHostAssociation;
import com.ynpe.fastremote.domain.catalog.*;
import java.util.*;
import java.util.function.Consumer;
import javafx.scene.control.MenuButton;

/** Adapts the searchable Hosts Library picker without its direct-connection option. */
final class AiHostAssociationSelector {
    private final SearchableJumpHostSelector selector;
    private Map<HostId, AiHostAssociation> choices = Map.of();
    private Optional<AiHostAssociation> selected = Optional.empty();
    private List<SavedHost> previousHosts = List.of();
    private List<HostGroup> previousGroups = List.of();
    private List<AiHostAssociation> previousQuick = List.of();

    AiHostAssociationSelector(MenuButton node, Consumer<Optional<AiHostAssociation>> changed) {
        selector = new SearchableJumpHostSelector(node, List.of(unassociatedOption()), Optional.empty(),
                com.ynpe.fastremote.i18n.Texts.text("ai.host.none"));
        node.setAccessibleText(com.ynpe.fastremote.i18n.Texts.text("ai.host.label"));
        selector.setOnSelectionChanged(() -> {
            selected = selector.selectedHostId().map(choices::get);
            changed.accept(selected);
        });
    }

    void update(List<SavedHost> hosts, List<HostGroup> groups, List<AiHostAssociation> quick,
            Optional<AiHostAssociation> association) {
        if (previousHosts.equals(hosts) && previousGroups.equals(groups) && previousQuick.equals(quick)
                && selected.equals(association)) return;
        selected = association;
        previousHosts = List.copyOf(hosts);
        previousGroups = List.copyOf(groups);
        previousQuick = List.copyOf(quick);
        Map<HostId, SavedHost> byId = new HashMap<>();
        hosts.forEach(host -> byId.put(host.id(), host));
        Map<HostId, AiHostAssociation> available = new LinkedHashMap<>();
        List<SearchableJumpHostSelector.Option> options = new ArrayList<>();
        options.add(unassociatedOption());
        for (var option : SearchableJumpHostSelector.options(hosts, groups, Optional.empty())) {
            if (option.id().isEmpty()) continue;
            var saved = byId.get(option.id().orElseThrow());
            var value = saved(saved);
            available.put(value.targetId(), value);
            options.add(new SearchableJumpHostSelector.Option(option.id(), saved.name(), option.hostName(),
                    option.host(), option.group(), option.secondaryText()));
        }
        for (var value : quick) {
            if (available.putIfAbsent(value.targetId(), value) == null) options.add(option(value));
        }
        association.filter(value -> !available.containsKey(value.targetId())).ifPresent(value -> {
            available.put(value.targetId(), value);
            options.add(option(value));
        });
        choices = Map.copyOf(available);
        selector.rebuildOptions(options, association.map(AiHostAssociation::targetId));
    }

    private static SearchableJumpHostSelector.Option unassociatedOption() {
        return new SearchableJumpHostSelector.Option(Optional.empty(),
                com.ynpe.fastremote.i18n.Texts.text("ai.host.none"), "", "", "", "");
    }

    SearchableJumpHostSelector picker() { return selector; }

    static AiHostAssociation saved(SavedHost host) {
        return new AiHostAssociation(Optional.of(host.id()), host.name(),
                host.profile().username().value(), host.profile().host().value(), host.profile().port().value());
    }
    static AiHostAssociation quick(OpenSession session) {
        return new AiHostAssociation(Optional.empty(), "", session.profile.username().value(), session.profile.host().value(), session.profile.port().value());
    }
    private static SearchableJumpHostSelector.Option option(AiHostAssociation value) {
        return new SearchableJumpHostSelector.Option(Optional.of(value.targetId()), value.displayName(),
                value.displayName(), value.host(), "", value.endpoint());
    }
}
