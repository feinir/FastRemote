// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.profile;

import com.ynpe.fastremote.domain.connection.ConnectionProfile;
import java.io.IOException;
import java.util.Optional;

public interface ConnectionProfileRepository {
    Optional<ConnectionProfile> loadLastProfile() throws IOException;

    void saveLastProfile(ConnectionProfile profile) throws IOException;
}
