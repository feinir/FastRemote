// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.connection;

public enum ConnectionFailureType {
    CANCELLED,
    HOST_KEY_REJECTED,
    AUTHENTICATION_FAILED,
    CONNECTION_TIMED_OUT,
    CONNECTION_FAILED,
    KEY_LOADING_FAILED,
    SESSION_CLOSED
}
