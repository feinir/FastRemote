// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static com.ynpe.fastremote.ui.UiFailureMessages.unwrap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

final class LibraryMutationSequence {
    private LibraryMutationSequence() {}

    static CompletionStage<List<Throwable>> runDeleteOperations(
            List<Supplier<CompletionStage<Void>>> operations) {
        CompletionStage<List<Throwable>> result = CompletableFuture.completedFuture(new ArrayList<>());
        for (Supplier<CompletionStage<Void>> operation : operations) {
            result = result.thenCompose(errors -> operation.get().handle((ignored, error) -> {
                if (error != null) errors.add(unwrap(error));
                return errors;
            }));
        }
        return result;
    }
}
