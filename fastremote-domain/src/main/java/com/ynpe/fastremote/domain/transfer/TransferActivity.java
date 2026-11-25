// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.transfer;

import java.util.Objects;

/** One file-system object currently being processed by a recursive transfer or deletion. */
public record TransferActivity(Operation operation, ObjectType objectType, String source, String target) {
    public TransferActivity {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(objectType, "objectType");
        source = Objects.requireNonNull(source, "source");
        target = Objects.requireNonNull(target, "target");
    }

    public enum Operation { UPLOAD, DOWNLOAD, DELETE }

    public enum ObjectType { FILE, DIRECTORY, SYMBOLIC_LINK, OTHER }
}
