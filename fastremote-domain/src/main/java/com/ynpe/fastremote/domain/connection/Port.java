// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.connection;

public record Port(int value) {
    public Port {
        if (value < 1 || value > 65_535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
    }
}
