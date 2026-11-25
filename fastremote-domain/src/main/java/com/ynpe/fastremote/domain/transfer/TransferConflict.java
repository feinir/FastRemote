// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.transfer;

import java.util.Objects;

public record TransferConflict(String source, String target, boolean directory) {
    public TransferConflict {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
    }
}
