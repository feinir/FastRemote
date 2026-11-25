// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;
import java.util.List;
import com.ynpe.fastremote.domain.connection.ConnectionHop;
    record AutomationRoute(List<ConnectionHop> hops) implements AutoCloseable {
        AutomationRoute { hops = List.copyOf(hops); }
        @Override public void close() { hops.forEach(hop -> hop.credentials().close()); }
    }

