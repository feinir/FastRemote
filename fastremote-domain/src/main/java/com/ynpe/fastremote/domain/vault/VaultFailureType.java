// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.vault;

public enum VaultFailureType {
    NOT_CREATED,
    ALREADY_CREATED,
    LOCKED,
    INVALID_PASSWORD_OR_CORRUPTED,
    SECRET_NOT_FOUND,
    STORAGE_FAILED,
    CRYPTOGRAPHY_FAILED
}
