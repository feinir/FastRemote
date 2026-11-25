// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.terminal;

import com.ynpe.fastremote.domain.transfer.RemoteFileService;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

public interface TerminalConnection extends AutoCloseable {
    default RemoteFileService remoteFiles() {
        throw new UnsupportedOperationException("This terminal connection does not provide remote files");
    }
    InputStream input();

    OutputStream output();

    default RemoteCommandResult executeCommand(String command, Duration timeout, int maximumOutputBytes)
            throws IOException, InterruptedException {
        throw new UnsupportedOperationException("This terminal connection does not provide remote commands");
    }

    boolean isOpen();

    void resize(int columns, int rows);

    CompletionStage<TerminalTermination> termination();

    default TerminalTermination waitForTermination() throws InterruptedException {
        try {
            return termination().toCompletableFuture().get();
        } catch (ExecutionException failure) {
            throw new IllegalStateException("Terminal termination signal failed", failure.getCause());
        }
    }

    default int waitForExit() throws InterruptedException {
        return waitForTermination().exitCode().orElse(0);
    }

    @Override
    void close();
}
