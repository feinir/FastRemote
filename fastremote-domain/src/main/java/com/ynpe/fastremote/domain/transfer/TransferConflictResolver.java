// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.transfer;

@FunctionalInterface
public interface TransferConflictResolver {
    TransferConflictChoice resolve(TransferConflict conflict);
}
