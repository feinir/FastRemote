// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.transfer.TransferConflictChoice;
import java.util.Objects;

record SftpConflictDecision(TransferConflictChoice choice, boolean applyToRemaining, boolean timedOut) {
    SftpConflictDecision(TransferConflictChoice choice, boolean applyToRemaining) {
        this(choice, applyToRemaining, false);
    }
    SftpConflictDecision {
        Objects.requireNonNull(choice, "choice");
    }
}
