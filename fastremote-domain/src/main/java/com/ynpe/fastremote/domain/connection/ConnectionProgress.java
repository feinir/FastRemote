// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.connection;

/** Non-secret milestones emitted while an SSH connection is being established. */
public enum ConnectionProgress {
    NETWORK_CONNECTING,
    HOST_KEY_VALIDATING,
    PASSWORD_AUTHENTICATING,
    PRIVATE_KEY_AUTHENTICATING,
    SESSION_ESTABLISHED
}
