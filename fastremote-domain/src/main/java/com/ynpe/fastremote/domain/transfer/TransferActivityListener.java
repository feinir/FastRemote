// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.transfer;

@FunctionalInterface
public interface TransferActivityListener {
    TransferActivityListener NONE = activity -> { };

    void processing(TransferActivity activity);

    /** An acknowledged per-entry outcome; SCANNED directories can still have failing children. */
    default void completed(String source, String target, String state, String detail) { }

}
