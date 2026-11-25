// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.transfer;

import java.io.IOException;

/** Cooperative cancellation and pause gate checked between transfer chunks. */
public interface TransferControl {
    void checkpoint() throws IOException;

    /** A pause-capable gate must invoke beforePause before blocking, outside its state lock. */
    default void checkpoint(Runnable beforePause) throws IOException { checkpoint(); }
}
