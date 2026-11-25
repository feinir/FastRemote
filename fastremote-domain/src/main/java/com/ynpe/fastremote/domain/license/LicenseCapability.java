// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.license;

public enum LicenseCapability {
    SSH("ssh"), SFTP("sftp"), LOCAL_TERMINAL("local_terminal"), TRANSFER("transfer"),
    AI("ai"), AUTOMATION("automation");

    private final String wireName;
    LicenseCapability(String wireName) { this.wireName = wireName; }
    public String wireName() { return wireName; }
}
