// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.transfer;

import java.util.concurrent.CompletionStage;

/** An authenticated SSH connection that exposes only the SFTP subsystem. */
public interface SftpConnection extends AutoCloseable {
    RemoteFileService remoteFiles();

    boolean isOpen();

    /** Completes when the underlying authenticated SSH session closes for any reason. */
    CompletionStage<Void> termination();

    @Override
    void close();
}
