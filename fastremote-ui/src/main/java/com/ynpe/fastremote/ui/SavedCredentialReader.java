// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.vault.SecretId;
import java.util.concurrent.CompletionStage;
import javafx.stage.Window;

@FunctionalInterface
interface SavedCredentialReader {
    CompletionStage<ResolvedCredential> read(Window owner, SecretId id);
}
