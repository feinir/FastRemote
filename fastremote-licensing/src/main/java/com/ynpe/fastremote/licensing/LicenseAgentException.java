// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.licensing;

/** Carries a licensing error code to the desktop UI. */
public final class LicenseAgentException extends RuntimeException {
    private final String code;

    LicenseAgentException(String code, Throwable cause) {
        super(code, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
