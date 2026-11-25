// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ai;

import java.util.Objects;

public record AiToolExecution(String name, String summary, boolean succeeded) {
    public AiToolExecution {
        name = Objects.requireNonNull(name, "name");
        summary = Objects.requireNonNull(summary, "summary");
    }
}
