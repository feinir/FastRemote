// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.transport.ssh;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.common.config.keys.FilePasswordProvider;

/** Uses exactly the SSH runtime's supported private-key parsers for migration validation. */
public final class PrivateKeyImportValidator {
    private PrivateKeyImportValidator() { }
    public static void validate(byte[] key,char[] passphrase)throws IOException {
        try {
            var identities=SecurityUtils.loadKeyPairIdentities(null,()->"imported-key",new ByteArrayInputStream(key),FilePasswordProvider.of(new String(passphrase)));
            if(identities==null || !identities.iterator().hasNext())throw new IOException("No private key found");
        }catch(GeneralSecurityException invalid){throw new IOException("Private key format or passphrase is invalid");}
    }
}
