// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.ai;

import com.ynpe.fastremote.domain.catalog.HostId;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Non-secret conversation target. Quick connections are identified by username/address/port. */
public record AiHostAssociation(Optional<HostId> savedHostId, String name, String username, String host, int port) {
    public AiHostAssociation(Optional<HostId> savedHostId, String name, String username, String host) {
        this(savedHostId, name, username, host, 22);
    }
    public AiHostAssociation {
        savedHostId = Objects.requireNonNull(savedHostId, "savedHostId");
        name = bounded(name, 255);
        username = bounded(username, 255);
        host = bounded(host, 253);
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid associated host port");
        if (host.isEmpty()) throw new IllegalArgumentException("Associated host address is empty");
    }
    public String displayName() { return savedHostId.isPresent() ? name : endpoint(); }
    public String endpoint() { return username + "@" + (host.contains(":") ? "[" + host + "]" : host) + ":" + port; }
    public HostId targetId() {
        return savedHostId.orElseGet(() -> new HostId(UUID.nameUUIDFromBytes(
                ("FastRemote:quick:" + username + "@" + host.toLowerCase(java.util.Locale.ROOT) + ":" + port)
                        .getBytes(StandardCharsets.UTF_8))));
    }
    private static String bounded(String value, int maximum) {
        String safe = Objects.requireNonNull(value, "value").strip();
        if (safe.length() > maximum) throw new IllegalArgumentException("Associated host field is too long");
        return safe;
    }
}
