// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.vault.VaultException;
import com.ynpe.fastremote.domain.vault.VaultFailureType;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import javafx.application.Platform;

/** Reads on demand and asks for the master password only when the vault reports it is locked. */
final class SavedCredentialAccess {
    private SavedCredentialAccess() {}

    static CompletionStage<ResolvedCredential> read(Supplier<CompletionStage<ResolvedCredential>> read,
            Supplier<CompletionStage<Boolean>> unlock) {
        CompletableFuture<ResolvedCredential> result = new CompletableFuture<>();
        read.get().whenComplete((credential, failure) -> {
            if (failure == null) {
                result.complete(credential);
                return;
            }
            Throwable cause = failure;
            while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
            if (!(cause instanceof VaultException vault) || vault.type() != VaultFailureType.LOCKED) {
                result.completeExceptionally(cause);
                return;
            }
            Platform.runLater(() -> {
                try {
                    unlock.get().whenComplete((unlocked, unlockFailure) -> {
                        if (unlockFailure != null) result.completeExceptionally(unlockFailure);
                        else if (!Boolean.TRUE.equals(unlocked)) result.completeExceptionally(new CancellationException());
                        else {
                            try {
                                read.get().whenComplete((loaded, readFailure) -> {
                                    if (readFailure != null) result.completeExceptionally(readFailure);
                                    else result.complete(loaded);
                                });
                            } catch (RuntimeException error) { result.completeExceptionally(error); }
                        }
                    });
                } catch (RuntimeException error) { result.completeExceptionally(error); }
            });
        });
        return result;
    }
}
