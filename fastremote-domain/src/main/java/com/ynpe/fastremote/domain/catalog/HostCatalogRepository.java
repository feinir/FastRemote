// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.catalog;

import com.ynpe.fastremote.domain.connection.ConnectionProfile;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface HostCatalogRepository {
    List<HostGroup> listGroups() throws IOException;

    List<SavedHost> searchHosts(String query, int limit) throws IOException;

    List<SavedHost> listHosts() throws IOException;

    Optional<SavedHost> findHost(HostId id) throws IOException;

    List<RecentConnection> listRecentConnections(int limit) throws IOException;

    void recordSuccessfulConnection(
            Optional<HostId> hostId, ConnectionProfile profile, Instant connectedAt) throws IOException;

    void saveGroup(HostGroup group) throws IOException;

    void moveGroup(GroupId id, Optional<GroupId> parentId, int targetIndex) throws IOException;

    void deleteGroup(GroupId id) throws IOException;

    void saveHost(SavedHost host) throws IOException;

    void moveHost(HostId id, Optional<GroupId> groupId, int targetIndex) throws IOException;

    List<String> deleteHost(HostId id) throws IOException;
}
