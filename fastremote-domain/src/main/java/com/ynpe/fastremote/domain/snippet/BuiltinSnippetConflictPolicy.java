// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.snippet;

/** Controls how a built-in catalog import handles an already mapped stable ID. */
public enum BuiltinSnippetConflictPolicy {
    OVERWRITE,
    SKIP
}
