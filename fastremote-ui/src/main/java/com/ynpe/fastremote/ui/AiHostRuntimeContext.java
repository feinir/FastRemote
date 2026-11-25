// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.ai.AiHostAssociation;
import com.ynpe.fastremote.domain.automation.AutomationActiveHost;
import com.ynpe.fastremote.domain.catalog.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import javafx.application.Platform;
import javafx.scene.control.Tab;

/** Builds secret-free, FX-thread snapshots for association and activity tools. */
final class AiHostRuntimeContext {
    static AiHostAssociation association(OpenSession session) {
        return session.catalogHostId == null ? AiHostAssociationSelector.quick(session)
                : new AiHostAssociation(Optional.of(session.catalogHostId), session.name,
                        session.profile.username().value(), session.profile.host().value(), session.profile.port().value());
    }
    static boolean matches(OpenSession session, HostId id) {
        return !session.closed.get() && association(session).targetId().equals(id);
    }
    static List<AutomationActiveHost> active(Collection<OpenSession> sessions, Function<Tab, String> workspaceId) {
        Map<HostId, AiHostAssociation> hosts = new LinkedHashMap<>();
        Map<HostId, List<String>> tabs = new LinkedHashMap<>();
        for (var session : sessions.stream().distinct().toList()) {
            if (session.closed.get() || session.tabs.isEmpty()) continue;
            var host = association(session);
            hosts.putIfAbsent(host.targetId(), host);
            var ids = tabs.computeIfAbsent(host.targetId(), ignored -> new ArrayList<>());
            session.tabs.values().stream().map(workspaceId).forEach(ids::add);
        }
        return hosts.entrySet().stream().map(entry -> new AutomationActiveHost(entry.getValue(), tabs.get(entry.getKey()))).toList();
    }
    static Optional<SavedHost> resolve(Collection<OpenSession> sessions, HostId id) {
        return sessions.stream().filter(session -> matches(session, id)).findFirst().map(session ->
                new SavedHost(id, Optional.empty(), association(session).displayName(), session.profile,
                        Optional.empty(), false, 0, Instant.EPOCH, Instant.EPOCH));
    }
    static <T> CompletionStage<T> onFx(Supplier<T> snapshot) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try { result.complete(snapshot.get()); }
            catch (RuntimeException failure) { result.completeExceptionally(failure); }
        });
        return result;
    }
}
