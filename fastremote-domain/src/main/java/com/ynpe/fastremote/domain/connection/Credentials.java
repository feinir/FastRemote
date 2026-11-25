// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.connection;

public sealed interface Credentials extends AutoCloseable permits PasswordCredentials, PrivateKeyCredentials {
    char[] copySecret();

    @Override
    void close();
}
