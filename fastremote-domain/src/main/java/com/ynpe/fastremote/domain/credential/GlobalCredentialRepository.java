// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.credential;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import com.ynpe.fastremote.domain.vault.SecretId;

public interface GlobalCredentialRepository {
    List<GlobalCredential> list() throws IOException;

    void save(GlobalCredential credential) throws IOException;

    Optional<GlobalCredential> find(SecretId id) throws IOException;

    List<CredentialGroup> listGroups() throws IOException;

    void saveGroup(CredentialGroup group) throws IOException;

    void deleteGroup(CredentialGroupId id) throws IOException;
}
