// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.automation;

/** Operating system reported by the remote SSH host. It does not identify the active terminal shell. */
public record RemoteHostSystem(String operatingSystem, SystemFamily systemFamily) {
    public enum SystemFamily { WINDOWS, POSIX, UNKNOWN }

    public static RemoteHostSystem unknown() {
        return new RemoteHostSystem("Unknown", SystemFamily.UNKNOWN);
    }
}
