// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.transfer;

@FunctionalInterface
public interface TransferProgressListener {
    void updated(long discoveredBytes, long transferredBytes);
}
