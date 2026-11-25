// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.host;

public enum HostImportSource {
    MOBAXTERM("MobaXterm"), XSHELL("XShell"), PUTTY("PuTTY"), TERMIUS("Termius"), BITVISE("Bitvise SSH Client");

    private final String label;

    HostImportSource(String label) { this.label = label; }

    public String label() { return label; }
}
