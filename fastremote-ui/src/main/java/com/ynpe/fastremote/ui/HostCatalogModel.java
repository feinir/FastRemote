// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.catalog.GroupId;
import com.ynpe.fastremote.domain.catalog.RecentConnection;
import com.ynpe.fastremote.domain.catalog.SavedHost;
import com.ynpe.fastremote.domain.catalog.HostGroup;
import com.ynpe.fastremote.domain.connection.AuthenticationType;
import com.ynpe.fastremote.domain.connection.ConnectionProfile;
import com.ynpe.fastremote.domain.credential.CredentialGroup;
import com.ynpe.fastremote.domain.credential.GlobalCredential;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Search, filtering and display policy for the saved-host and credential catalogs. */
final class HostCatalogModel {
    private HostCatalogModel() { }

    enum HostFilter { ALL, FAVORITES, RECENT }

    static boolean credentialMatchesSearch(
            GlobalCredential credential, List<CredentialGroup> groups, String normalizedQuery) {
        if (normalizedQuery.isEmpty()
                || credential.label().toLowerCase(java.util.Locale.ROOT).contains(normalizedQuery)
                || credential.username().toLowerCase(java.util.Locale.ROOT).contains(normalizedQuery)) {
            return true;
        }
        return credential.groupId().flatMap(groupId -> groups.stream()
                        .filter(group -> group.id().equals(groupId)).findFirst())
                .map(CredentialGroup::name)
                .map(name -> name.toLowerCase(java.util.Locale.ROOT).contains(normalizedQuery))
                .orElse(false);
    }

    static List<RecentConnection> homeRecentConnections(HostCatalogSnapshot snapshot) {
        return snapshot.recentConnections().stream()
                .sorted(Comparator.comparing(RecentConnection::connectedAt).reversed()
                        .thenComparing(RecentConnection::name, String.CASE_INSENSITIVE_ORDER))
                .limit(8)
                .toList();
    }

    static String homeRecentConnectionDetail(RecentConnection connection, HostCatalogSnapshot snapshot) {
        String source = com.ynpe.fastremote.i18n.Texts.text("legacy.java.bec3d896410b5294");
        if (connection.hostId().isPresent()) {
            SavedHost host = snapshot.hosts().stream()
                    .filter(candidate -> candidate.id().equals(connection.hostId().orElseThrow()))
                    .findFirst().orElse(null);
            if (host != null) {
                Map<GroupId, HostGroup> byId = groupsById(snapshot.groups());
                source = host.groupId().map(id -> groupPath(id, byId)).orElse(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e86ea3f280d920a2"));
            }
        }
        String authentication = connection.authenticationType() == AuthenticationType.PASSWORD
                ? com.ynpe.fastremote.i18n.Texts.text("legacy.java.a621ab606db2a11f") : com.ynpe.fastremote.i18n.Texts.text("legacy.java.7ea96a871de9fe44");
        return source + " · " + authentication + " · " + connection.username().value() + "@"
                + connection.host().value() + ':' + connection.port().value() + " · "
                + com.ynpe.fastremote.i18n.Texts.localizer().formatDateTime(
                        connection.connectedAt(), java.time.ZoneId.systemDefault());
    }

    static String homeHostDetail(SavedHost host, List<HostGroup> groups) {
        Map<GroupId, HostGroup> byId = groupsById(groups);
        String group = host.groupId().map(id -> groupPath(id, byId)).orElse(com.ynpe.fastremote.i18n.Texts.text("legacy.java.e86ea3f280d920a2"));
        ConnectionProfile profile = host.profile();
        return group + " · " + profile.username().value() + "@" + profile.host().value()
                + ':' + profile.port().value();
    }

    static HostCatalogSnapshot filteredCatalog(HostCatalogSnapshot source, String query) {
        return filteredCatalog(source, query, HostFilter.ALL);
    }

    static HostCatalogSnapshot filteredCatalog(HostCatalogSnapshot source, String query, HostFilter filter) {
        String normalized = query == null ? "" : query.strip().toLowerCase(java.util.Locale.ROOT);
        Map<GroupId, HostGroup> groupsById = groupsById(source.groups());
        java.util.stream.Stream<SavedHost> candidates = source.hosts().stream();
        if (filter == HostFilter.FAVORITES) {
            candidates = candidates.filter(SavedHost::favorite);
        } else if (filter == HostFilter.RECENT) {
            var recentIds = source.recentConnections().stream()
                    .filter(RecentConnection::savedHost)
                    .sorted(Comparator.comparing(RecentConnection::connectedAt).reversed())
                    .limit(20)
                    .flatMap(connection -> connection.hostId().stream())
                    .collect(Collectors.toSet());
            candidates = candidates.filter(host -> recentIds.contains(host.id()));
        }
        List<SavedHost> matches = candidates.filter(host -> normalized.isEmpty() ||
                (host.name() + " "
                        + host.groupId().map(id -> groupPath(id, groupsById)).orElse("") + " "
                        + host.profile().host().value() + " "
                        + host.profile().username().value() + " " + host.profile().port().value())
                        .toLowerCase(java.util.Locale.ROOT).contains(normalized)).toList();
        return new HostCatalogSnapshot(source.groups(), matches, source.recentConnections(), source.credentials(),
                source.credentialGroups(), source.vaultStatus());
    }

    static String hostDisplayName(SavedHost host) {
        return host.name();
    }

    private static Map<GroupId, HostGroup> groupsById(List<HostGroup> groups) {
        return groups.stream().collect(Collectors.toMap(HostGroup::id, Function.identity()));
    }

    private static String groupPath(GroupId id, Map<GroupId, HostGroup> groups) {
        HostGroup group = groups.get(id);
        if (group == null) return com.ynpe.fastremote.i18n.Texts.text("legacy.java.e86ea3f280d920a2");
        return group.parentId().map(groups::get)
                .map(parent -> parent.name() + " / " + group.name())
                .orElse(group.name());
    }
}
