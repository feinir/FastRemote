// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.security;

import com.ynpe.fastremote.domain.connection.Host;
import com.ynpe.fastremote.domain.connection.Port;
import java.io.IOException;
import java.util.Optional;

public interface KnownHostRepository {
    Optional<HostKeyIdentity> find(Host host, Port port) throws IOException;

    void save(HostKeyIdentity identity) throws IOException;
}
