// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.catalog;

import com.ynpe.fastremote.domain.connection.AuthenticationType;
import com.ynpe.fastremote.domain.connection.Host;
import com.ynpe.fastremote.domain.connection.Port;
import com.ynpe.fastremote.domain.connection.Username;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Non-secret metadata for a successfully established saved or quick connection. */
public record RecentConnection(
        Source source,
        String sourceId,
        Optional<HostId> hostId,
        String name,
        Host host,
        Port port,
        Username username,
        AuthenticationType authenticationType,
        Instant connectedAt,
        HostIcon icon) {

    public enum Source { SAVED_HOST, QUICK_CONNECTION }

    public RecentConnection {
        Objects.requireNonNull(source, "source");
        sourceId = Objects.requireNonNull(sourceId, "sourceId").strip();
        hostId = Objects.requireNonNull(hostId, "hostId");
        name = Objects.requireNonNull(name, "name").strip();
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(port, "port");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(authenticationType, "authenticationType");
        Objects.requireNonNull(connectedAt, "connectedAt");
        Objects.requireNonNull(icon, "icon");
        if (sourceId.isEmpty()) throw new IllegalArgumentException("Recent connection source ID must not be blank");
        if (name.isEmpty()) throw new IllegalArgumentException("Recent connection name must not be blank");
        if ((source == Source.SAVED_HOST) != hostId.isPresent()) {
            throw new IllegalArgumentException("Only saved-host history may reference a host ID");
        }
    }

    public boolean savedHost() {
        return source == Source.SAVED_HOST;
    }
}
