// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.security;

@FunctionalInterface
public interface HostKeyPrompt {
    HostKeyDecision decide(HostKeyCheck check) throws InterruptedException;
}
